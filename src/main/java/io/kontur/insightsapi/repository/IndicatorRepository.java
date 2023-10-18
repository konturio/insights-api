package io.kontur.insightsapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import io.kontur.insightsapi.mapper.BivariateIndicatorRowMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.fileupload.FileItemStream;
import org.postgresql.copy.CopyIn;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.US_ASCII;

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

    @Value("classpath:/sql.queries/14855_update_stat_h3_geom.sql")
    private Resource updateStatH3Geom;

    private final QueryFactory queryFactory;

    private final BivariateIndicatorRowMapper bivariateIndicatorRowMapper;

    //TODO: temporary field, remove when we have final version of transposed stat_h3 table
    @Value("${calculations.bivariate.transposed.table}")
    private String transposedTableName;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsMetadataTableName;

    @Value("${calculations.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    public String createOrUpdateIndicator(BivariateIndicatorDto bivariateIndicatorDto, String owner, boolean update)
            throws JsonProcessingException {

        var paramSource = initParams(bivariateIndicatorDto, owner);
        String bivariateIndicatorsQuery;

        if (update) {
            bivariateIndicatorsQuery = format(queryFactory.getSql(updateBivariateIndicators),
                    bivariateIndicatorsMetadataTableName, owner);
        } else {
            bivariateIndicatorsQuery = format(queryFactory.getSql(insertBivariateIndicators),
                    bivariateIndicatorsMetadataTableName);
        }

        return namedParameterJdbcTemplate.queryForObject(bivariateIndicatorsQuery, paramSource, String.class);
    }

    public void uploadCsvFileIntoStatH3Table(FileItemStream file, String uuid, boolean update) {
        try (Connection connection = dataSource.getConnection();
             BufferedReader reader = new BufferedReader(new InputStreamReader(file.openStream(), US_ASCII))) {
            connection.setAutoCommit(false);
            try {
                if (update) {
                    try (PreparedStatement ps = connection.prepareStatement(format("DELETE FROM %s WHERE indicator_uuid = '%s'::uuid",
                            transposedTableName, uuid))) {
                        ps.executeUpdate();
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage(), e);
                    }
                }
                CopyManager copyManager = new CopyManager((BaseConnection) connection.unwrap(Connection.class));
                CopyIn copyIn = copyManager.copyIn(format("COPY %s FROM STDIN DELIMITER ',' null 'NULL'", transposedTableName));
                try {
                    String row;
                    while ((row = reader.readLine()) != null) {
                        String[] rowValues = row.split(",");
                        String transformedRow = String.join(",", rowValues[0], uuid, rowValues[1]) + "\n";
                        byte[] bytes = transformedRow.getBytes();
                        copyIn.writeToCopy(bytes, 0, bytes.length);
                    }
                    copyIn.endCopy();
                } finally {
                    if (copyIn.isActive()) {
                        copyIn.cancelCopy();
                    }
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw new RuntimeException(e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String adjustMessageForKnownExceptions(String message) {
        String tempMessage = message.substring(message.indexOf(", line") + 2, message.indexOf(", column",
                message.indexOf(", line")));
        if (message.contains("stringToH3")) {
            return format("Unable to represent %s from the file as H3", tempMessage);
        } else if (message.contains("valid_cell")) {
            return format("Incorrect H3index found in the file: %s", message.substring(message.indexOf(", line")
                    + 2, message.indexOf(": \"", message.indexOf(", line"))));
        } else if (message.contains("double precision")) {
            return format("Incorrect value found in the file: %s", tempMessage);
        } else {
            return message;
        }
    }

    public void deleteIndicator(String uuid) {
        jdbcTemplate.update(format("DELETE FROM %s WHERE param_uuid = '%s'::uuid",
                bivariateIndicatorsMetadataTableName, uuid));
    }

    public BivariateIndicatorDto getIndicatorByIdAndOwner(String id, String owner)
            throws BivariateIndicatorsPRViolationException {
        List<BivariateIndicatorDto> bivariateIndicatorDtos = jdbcTemplate.query(
                format("SELECT * FROM %s WHERE param_id = '%s' AND owner = '%s'",
                        bivariateIndicatorsMetadataTableName,
                        id,
                        owner),
                bivariateIndicatorRowMapper);

        return switch (bivariateIndicatorDtos.size()) {
            case 0 -> null;
            case 1 -> bivariateIndicatorDtos.get(0);
            default -> throw new BivariateIndicatorsPRViolationException(format("More then one indicator " +
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
        return jdbcTemplate.query(format("SELECT * FROM %s", bivariateIndicatorsMetadataTableName),
                bivariateIndicatorRowMapper);
    }

    // TODO: use owner here as param_id alone is no longer considered to be unique
    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getSelectedBivariateIndicators(List<String> indicatorIds) {
        return jdbcTemplate.query(format("SELECT * FROM %s WHERE param_id in ('%s')",
                        bivariateIndicatorsMetadataTableName, String.join("','", indicatorIds)),
                bivariateIndicatorRowMapper);
    }

    public BivariateIndicatorDto getIndicatorByUuid(String uuid) {
        return jdbcTemplate.queryForObject(format("SELECT * FROM %s where param_uuid = '%s'::uuid",
                bivariateIndicatorsMetadataTableName, uuid), bivariateIndicatorRowMapper);
    }

    //TODO: remove after transition from param_id to uuid as an identifier for indicator. Use 'getIndicatorByUuid' method in future instead
    @Deprecated
    public String getLabelByParamId(String paramId) {
        String bivariateIndicatorsTable = useStatSeparateTables ? bivariateIndicatorsMetadataTableName
                : bivariateIndicatorsTableName;
        return jdbcTemplate.queryForObject(format("SELECT param_label FROM %s where param_id = '%s'",
                bivariateIndicatorsTable, paramId), String.class);
    }

    public void updateIndicatorsLastUpdateDate(Instant lastUpdated) {
        jdbcTemplate.update(format("UPDATE %s SET last_updated = '%s'", bivariateIndicatorsMetadataTableName,
                Timestamp.from(lastUpdated)));
    }

    public Instant getIndicatorsLastUpdateDate() {
        Timestamp lastUpdated = jdbcTemplate.queryForObject(format("SELECT MAX(last_updated) FROM %s",
                bivariateIndicatorsMetadataTableName), Timestamp.class);
        return lastUpdated != null ? lastUpdated.toInstant() : null;
    }

    public void updateIndicatorState(String uuid, IndicatorState state) {
        jdbcTemplate.update(format("UPDATE %s SET state = '%s' WHERE param_uuid = '%s'::uuid",
                bivariateIndicatorsMetadataTableName, state.name(), uuid));
    }

    public void updateStatH3Geom() {
        jdbcTemplate.execute("SET enable_hashjoin = off");
        jdbcTemplate.execute(queryFactory.getSql(updateStatH3Geom));
        jdbcTemplate.execute("RESET enable_hashjoin");
    }
}
