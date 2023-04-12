package io.kontur.insightsapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import io.kontur.insightsapi.exception.IndicatorDataProcessingException;
import io.kontur.insightsapi.mapper.BivariateIndicatorRowMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.fileupload.FileItemStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IndicatorRepository {

    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper;

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

    public void uploadCsvFileIntoStatH3Table(FileItemStream file, String uuid, boolean update) {
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.openStream()))) {
            if (update) {
                jdbcTemplate.update(String.format("DELETE FROM %s WHERE indicator_uuid = '%s'::uuid",
                        transposedTableName, uuid));
            }
            var query = String.format("INSERT INTO %s (h3, indicator_uuid, indicator_value) " +
                    "VALUES (?, '%s', ?)", transposedTableName, uuid);

            int batchSize = 1000;
            int count = 0;
            List<Object[]> batchArgs = new ArrayList<>();

            String[] row;
            while ((row = reader.readNext()) != null) {
                Object[] rowArgs = new Object[]{row[0], row[1]};
                batchArgs.add(rowArgs);
                count++;
                if (count % batchSize == 0) {
                    jdbcTemplate.batchUpdate(query, batchArgs, new int[]{Types.OTHER, Types.DOUBLE});
                    batchArgs.clear();
                }
            }
            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(query, batchArgs, new int[]{Types.OTHER, Types.DOUBLE});
            }
        } catch (Exception exception) {
            throw new IndicatorDataProcessingException(exception);
        }
    }

    public void deleteIndicator(String uuid) {
        jdbcTemplate.update(String.format("DELETE FROM %s WHERE param_uuid = '%s'::uuid",
                bivariateIndicatorsTestTableName, uuid));
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
