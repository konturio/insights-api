package io.kontur.insightsapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.FileUploadResultDto;
import io.kontur.insightsapi.dto.IndicatorState;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import io.kontur.insightsapi.exception.ConnectionException;
import io.kontur.insightsapi.exception.TableDataCopyException;
import io.kontur.insightsapi.mapper.BivariateIndicatorRowMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IndicatorRepository {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorRepository.class);
    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper;

    private final DataSource dataSource;

    @Value("classpath:/sql.queries/insert_bivariate_indicators.sql")
    private Resource insertBivariateIndicators;

    @Value("classpath:/sql.queries/update_bivariate_indicators.sql")
    private Resource updateBivariateIndicators;

    private final QueryFactory queryFactory;

    private final BivariateIndicatorRowMapper bivariateIndicatorRowMapper;

    //TODO: temporary field, remove when we have final version of transposed stat_h3 table
    @Value("${calculations.bivariate.transposed.table}")
    private String transposedTableName;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsTestTableName;

    @Value("${calculations.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;

    public String createOrUpdateIndicator(BivariateIndicatorDto bivariateIndicatorDto, String owner, boolean update)
            throws JsonProcessingException {

        var paramSource = initParams(bivariateIndicatorDto, owner);
        String bivariateIndicatorsQuery;

        if (update) {
            bivariateIndicatorsQuery = String.format(queryFactory.getSql(updateBivariateIndicators),
                    bivariateIndicatorsTestTableName, owner);
        } else {
            bivariateIndicatorsQuery = String.format(queryFactory.getSql(insertBivariateIndicators),
                    bivariateIndicatorsTestTableName);
        }

        return namedParameterJdbcTemplate.queryForObject(bivariateIndicatorsQuery, paramSource, String.class);
    }

    //TODO: get rid of intermediate table. InputStream with a CSV file from request have to be adjusted with an UUID of indicator for every row and have to be uploaded to stat_h3_transposed straightaway
    public FileUploadResultDto uploadCSVFileIntoTempTable(FileItemStream file) throws IOException, ConnectionException {

        String tempTableName = generateTempTableName();

        String tempTableQuery = String.format("CREATE UNLOGGED TABLE %s (h3 h3index, indicator_value double " +
                "precision, CONSTRAINT valid_cell CHECK (h3_is_valid_cell(h3::h3index)))", tempTableName);
        jdbcTemplate.update(tempTableQuery);

        var copyManagerQuery = String.format("COPY %s FROM STDIN DELIMITER ',' null 'NULL'", tempTableName);

        long numberOfInsertedRows;

        try (InputStream fileInputStream = file.openStream()) {
            Connection connection = DataSourceUtils.getConnection(dataSource);

            if (connection.isWrapperFor(Connection.class)) {

                CopyManager copyManager = new CopyManager((BaseConnection) connection.unwrap(Connection.class));
                numberOfInsertedRows = copyManager.copyIn(copyManagerQuery, fileInputStream);
                return new FileUploadResultDto(tempTableName, numberOfInsertedRows, null);
            } else {
                logger.error("Could not connect to Copy Manager");
                throw new ConnectionException("Connection was closed unpredictably. Can not obtain connection for " +
                        "CopyManager");
            }
        } catch (Exception e) {
            return new FileUploadResultDto(null, 0,
                    adjustMessageForKnownExceptions(e.getMessage()));
        }
    }

    private String adjustMessageForKnownExceptions(String message) {
        String tempMessage = message.substring(message.indexOf(", line") + 2, message.indexOf(", column",
                message.indexOf(", line")));
        if (message.contains("stringToH3")) {
            return String.format("Unable to represent %s from the file as H3", tempMessage);
        } else if (message.contains("valid_cell")) {
            return String.format("Incorrect H3index found in the file: %s", message.substring(message.indexOf(", line")
                    + 2, message.indexOf(": \"", message.indexOf(", line"))));
        } else if (message.contains("double precision")) {
            return String.format("Incorrect value found in the file: %s", tempMessage);
        } else {
            return message;
        }
    }

    public ResponseEntity<String> copyDataToStatH3(FileUploadResultDto fileUploadResultDto, String uuid, boolean update)
            throws TableDataCopyException {
        try {
            if (update) {
                jdbcTemplate.update(String.format("DELETE FROM %s WHERE indicator_uuid = '%s'::uuid",
                        transposedTableName, uuid));
            }
            var copyDataFromTempToStatH3WithUuidQuery = String.format("INSERT INTO %s select h3, '%s', " +
                    "indicator_value from %s", transposedTableName, uuid, fileUploadResultDto.getTempTableName());
            long numberOfCopiedRows = jdbcTemplate.update(copyDataFromTempToStatH3WithUuidQuery);

            deleteTempTable(fileUploadResultDto.getTempTableName());

            if (numberOfCopiedRows != fileUploadResultDto.getNumberOfUploadedRows()) {
                logger.warn(String.format("No errors during uploading occurred but records number validation " +
                        "did not pass: uploaded from CSV = %s, number of records put in database = %s, " +
                        "uuid = %s", fileUploadResultDto.getNumberOfUploadedRows(), numberOfCopiedRows, uuid));
                return ResponseEntity.ok().body(String.format("No errors during uploading occurred but records " +
                                "number validation did not pass: uploaded from CSV = %s, number of records put in " +
                                "database = %s, uuid = %s", fileUploadResultDto.getNumberOfUploadedRows(),
                        numberOfCopiedRows, uuid));
            }

            return ResponseEntity.ok().body(uuid);
        } catch (Exception exception) {
            throw new TableDataCopyException(exception);
        }
    }

    private String generateTempTableName() {
        return "_" + RandomStringUtils.randomAlphanumeric(29).toLowerCase();
    }

    public void deleteIndicator(String uuid) {
        jdbcTemplate.update(String.format("DELETE FROM %s WHERE param_uuid = '%s'::uuid",
                bivariateIndicatorsTestTableName, uuid));
    }

    public void deleteTempTable(String tempTableName) {
        jdbcTemplate.update(String.format("DROP TABLE %s", tempTableName));
    }

    public BivariateIndicatorDto getIndicatorByIdAndOwner(String id, String owner)
            throws BivariateIndicatorsPRViolationException {
        List<BivariateIndicatorDto> bivariateIndicatorDtos = jdbcTemplate.query(
                String.format("SELECT * FROM %s WHERE param_id = '%s' AND owner = '%s'",
                        bivariateIndicatorsTestTableName,
                        id,
                        owner),
                bivariateIndicatorRowMapper);

        return switch (bivariateIndicatorDtos.size()) {
            case 0 -> null;
            case 1 -> bivariateIndicatorDtos.get(0);
            default -> throw new BivariateIndicatorsPRViolationException(String.format("More then one indicator " +
                    "found with name: %s, for user: %s", id, owner));
        };
    }

    private MapSqlParameterSource initParams(BivariateIndicatorDto bivariateIndicatorDto, String owner)
            throws JsonProcessingException {
        return new MapSqlParameterSource()
                .addValue("id", bivariateIndicatorDto.getId())
                .addValue("label", bivariateIndicatorDto.getLabel())
                .addValue("copyrights",
                        bivariateIndicatorDto.getCopyrights() == null ? null :
                                objectMapper.writeValueAsString(bivariateIndicatorDto.getCopyrights()))
                .addValue("direction",
                        bivariateIndicatorDto.getDirection() == null ? null :
                                objectMapper.writeValueAsString(bivariateIndicatorDto.getDirection()))
                .addValue("isBase", bivariateIndicatorDto.getIsBase())
                .addValue("isPublic", bivariateIndicatorDto.getIsPublic())
                .addValue("allowedUsers",
                        bivariateIndicatorDto.getAllowedUsers() == null ? null :
                                objectMapper.writeValueAsString(bivariateIndicatorDto.getAllowedUsers()))
                .addValue("owner", owner)
                //TODO: think about state and date
                .addValue("description", bivariateIndicatorDto.getDescription())
                .addValue("coverage", bivariateIndicatorDto.getCoverage())
                //TODO: discuss these values, should be some default values if not specified
                .addValue("updateFrequency", bivariateIndicatorDto.getUpdateFrequency())
                .addValue("application", bivariateIndicatorDto.getApplication())
                .addValue("unitId", bivariateIndicatorDto.getUnitId())
                .addValue("lastUpdated", bivariateIndicatorDto.getLastUpdated());

    }

    //TODO: possibly will be added something about owner field here
    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getAllBivariateIndicators() {
        return jdbcTemplate.query(String.format("SELECT * FROM %s", bivariateIndicatorsTestTableName),
                bivariateIndicatorRowMapper);
    }

    public BivariateIndicatorDto getIndicatorByUuid(String uuid) {
        return jdbcTemplate.queryForObject(String.format("SELECT * FROM %s where param_uuid = '%s'::uuid",
                bivariateIndicatorsTestTableName, uuid), bivariateIndicatorRowMapper);
    }

    //TODO: remove after transition from param_id to uuid as an identifier for indicator. Use 'getIndicatorByUuid' method in future instead
    @Deprecated
    public String getLabelByParamId(String paramId) {
        return jdbcTemplate.queryForObject(String.format("SELECT param_label FROM %s where param_id = '%s'",
                bivariateIndicatorsTableName, paramId), String.class);
    }

    public void updateIndicatorsLastUpdateDate(Instant lastUpdated) {
        jdbcTemplate.update(String.format("UPDATE %s SET last_updated = '%s'", bivariateIndicatorsTestTableName,
                Timestamp.from(lastUpdated)));
    }

    public Instant getIndicatorsLastUpdateDate() {
        Timestamp lastUpdated = jdbcTemplate.queryForObject(String.format("SELECT MAX(last_updated) FROM %s",
                bivariateIndicatorsTestTableName), Timestamp.class);
        return lastUpdated != null ? lastUpdated.toInstant() : null;
    }

    public void updateIndicatorState(String uuid, IndicatorState state) {
        jdbcTemplate.update(String.format("UPDATE %s SET state = '%s' WHERE param_uuid = '%s'::uuid",
                bivariateIndicatorsTestTableName, state.name(), uuid));
    }
}
