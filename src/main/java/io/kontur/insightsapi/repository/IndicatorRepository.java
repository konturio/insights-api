package io.kontur.insightsapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.exception.IndicatorDataProcessingException;
import io.kontur.insightsapi.mapper.BivariateIndicatorRowMapper;
import lombok.RequiredArgsConstructor;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Repository
@RequiredArgsConstructor
public class IndicatorRepository {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorRepository.class);

    @Autowired
    @Qualifier("writeJdbcTemplate")
    private final JdbcTemplate jdbcTemplateRW;

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("writeDataSource")
    private final DataSource dataSource;

    @Value("classpath:/sql.queries/insert_bivariate_indicators.sql")
    private Resource insertBivariateIndicators;

    private final QueryFactory queryFactory;

    private final BivariateIndicatorRowMapper bivariateIndicatorRowMapper;

    private final ThreadPoolExecutor uploadExecutor;

    @Async("uploadExecutor") // maxPoolSize = 150
    public void uploadCsvFile(Path file, BivariateIndicatorDto bivariateIndicatorDto)
            throws IndicatorDataProcessingException {
        logger.info("Started upload thread {} for indicator ext.id {}", bivariateIndicatorDto.getUploadId(), bivariateIndicatorDto.getExternalId());
        Connection connection = null;

        try (PipedInputStream pipedInputStream = new PipedInputStream();
             PipedOutputStream pipedOutputStream = new PipedOutputStream(pipedInputStream)) {

            connection = DataSourceUtils.getConnection(dataSource);
            connection.setAutoCommit(false);

            String internalId = createIndicator(connection, bivariateIndicatorDto);
            connection.commit();
            // now new indicator is visible for other transactions with the state "COPY IN PROGRESS"

            String tmpTableName = "tmp_stat_h3_" + bivariateIndicatorDto.getUploadId();
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("select from bivariate_indicators_metadata where internal_id = '" + internalId + "' for no key update nowait");
                stmt.execute("create table \"" + tmpTableName + "\" (like stat_h3_transposed)");
                stmt.execute("alter table \"" + tmpTableName + "\" set (autovacuum_enabled = off)");
                stmt.execute("alter table \"" + tmpTableName + "\" set unlogged");
                logger.info("created table " + tmpTableName + " for indicator " + bivariateIndicatorDto.getExternalId());
            }
            // row-level lock will be released when COPY fails or succeeds

            Future<?> uploadTask = submitUploadTask(file, internalId, pipedOutputStream);

            copyFile(connection, pipedInputStream, tmpTableName);

            try {
                uploadTask.get();
            } catch (Exception e) {
                logger.error("failed to stream file to db", e);
                throw new IndicatorDataProcessingException(e.getMessage(), e);
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("update bivariate_indicators_metadata set state = 'TMP CREATED' where internal_id = '" + internalId + "'");
            }
            connection.commit();
            // now new indicator is visible for other transactions with the state "TMP CREATED"
            logger.info("Upload of csv file for indicator with uuid {} has been done successfully", bivariateIndicatorDto.getExternalId());
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (Exception e1) {
                    throw new IndicatorDataProcessingException("Failed to rollback indicator upload transaction", e1);
                }
            }
            throw new IndicatorDataProcessingException(String.format("Failed to copy indicator. %s", e.getMessage()), e);
        } finally {
            if (connection != null) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("SET application_name = 'PostgreSQL JDBC Driver'");
                } catch (SQLException e1) {
                    logger.error("failed to reset application_name", e1);
                }
                try {
                    connection.setAutoCommit(true);
                } catch (Exception e1) {
                    logger.error("Failed to reset auto-commit behavior after indicator upload", e1);
                }
                DataSourceUtils.releaseConnection(connection, dataSource);
                try {
                    Files.deleteIfExists(file);
                    logger.info("removed " + file.toString()); 
                } catch (Exception e1) {
                    logger.error("Failed to remove file" + file.toString(), e1);
                }
            }
        }
    }

    public String createIndicator(Connection connection, BivariateIndicatorDto bivariateIndicatorDto) throws JsonProcessingException, SQLException {
        String insertQuery = queryFactory.getSql(insertBivariateIndicators);

        try (PreparedStatement ps = connection.prepareStatement(insertQuery)) {
            initParams(ps, bivariateIndicatorDto);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("internal_id", String.class);
                } else {
                    throw new SQLException("Failed to retrieve internal_id after insert");
                }
            }
        }
    }

    private void copyFile(Connection connection, InputStream inputStream, String tmpTableName) throws SQLException, IOException {
        if (connection.isWrapperFor(Connection.class)) {
            CopyManager copyManager = new CopyManager((BaseConnection) connection.unwrap(Connection.class));
            copyManager.copyIn(String.format("COPY \"%s\" FROM STDIN DELIMITER ',' null 'NULL'", tmpTableName), inputStream);
        } else {
            logger.error("Could not connect to Copy Manager");
            throw new IndicatorDataProcessingException("Connection was closed unpredictably. Can not obtain connection for CopyManager");
        }
    }

    private Future<?> submitUploadTask(Path file, String internalId, PipedOutputStream pipedOutputStream) {
        return uploadExecutor.submit(() -> {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(pipedOutputStream, StandardCharsets.UTF_8))) {
                String row;
                while ((row = reader.readLine()) != null) {
                    String[] rowValues = row.split(",");
                    writer.write(String.join(",", rowValues[0], internalId, rowValues[1]));
                    writer.newLine();
                }
            } catch (Exception e) {
                throw new IndicatorDataProcessingException("Failed to adjust incoming csv stream with uuid", e);
            }
        });
    }

    public List<String> getIndicatorsByExternalId(String externalId) {
        return jdbcTemplate.queryForList(
                String.format("SELECT internal_id FROM bivariate_indicators_metadata WHERE external_id = '%s'::uuid",
                        externalId),
                String.class);
    }

    public List<BivariateIndicatorDto> getIndicatorsByOwner(String owner) {
        return jdbcTemplate.query("""
                SELECT
                    param_id,
                    param_label,
                    copyrights,
                    direction,
                    is_base,
                    internal_id,
                    owner,
                    state,
                    is_public,
                    allowed_users,
                    date,
                    description,
                    coverage,
                    update_frequency,
                    application,
                    unit_id,
                    last_updated,
                    external_id,
                    emoji,
                    upload_id,
                    max_res,
                    downscale,
                    hash,
                    null coverage_polygon
                FROM bivariate_indicators_metadata WHERE owner = ?""",
                bivariateIndicatorRowMapper, owner);
    }

    public String getIndicatorIdByUploadId(String owner, String uploadId) {
        try {
            return jdbcTemplateRW.queryForObject(
                "SELECT external_id || '/' || state FROM bivariate_indicators_metadata WHERE owner = ? AND upload_id = ?::uuid",
                String.class, owner, uploadId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void checkActiveUpload(BivariateIndicatorDto indicator) throws Exception {
        // first check COPY IN PROGRESS state (insights-api side of upload pipeline)
        try {
            // specifically check for lock, as no lock = failed upload
            jdbcTemplateRW.queryForObject(
                "select 1 from bivariate_indicators_metadata where param_id = ? and owner = ? and state = 'COPY IN PROGRESS' for no key update nowait",
                String.class, indicator.getId(), indicator.getOwner());
        } catch (EmptyResultDataAccessException e) {
            // indicator with this param_id and ongoing upload not found
            ;
        } catch (CannotAcquireLockException e) {
            // COPY process is holding a lock on some row with this param_id
            throw new IndicatorDataProcessingException("indicator upload already in progress");
        }

        // then check TMP CREATED state (insights-db part of uploading)
        try {
            String res = jdbcTemplateRW.queryForObject(
                "select 1 from bivariate_indicators_metadata where param_id = ? and owner = ? and state = 'TMP CREATED' limit 1",
                String.class, indicator.getId(), indicator.getOwner());
            if (res != null) {
                throw new IndicatorDataProcessingException("indicator upload already in progress");
            }
        } catch (EmptyResultDataAccessException e) {
            // indicator with this param_id and ongoing upload not found
            ;
        }
    }

    public List<BivariateIndicatorDto> getIndicatorsByOwnerAndParamId(String owner, String paramId) {
        return jdbcTemplate.query("""
                SELECT
                    param_id,
                    param_label,
                    copyrights,
                    direction,
                    is_base,
                    internal_id,
                    owner,
                    state,
                    is_public,
                    allowed_users,
                    date,
                    description,
                    coverage,
                    update_frequency,
                    application,
                    unit_id,
                    last_updated,
                    external_id,
                    emoji,
                    upload_id,
                    max_res,
                    downscale,
                    hash,
                    ST_AsGeoJSON(coverage_polygon) coverage_polygon
                FROM bivariate_indicators_metadata WHERE owner = ? AND param_id = ?""",
                bivariateIndicatorRowMapper, owner, paramId);
    }

    public String getIndicatorByOwnerAndExternalId(String owner, String externalId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT internal_id FROM bivariate_indicators_metadata WHERE owner = ? AND external_id = ?::uuid limit 1",
                    String.class, owner, externalId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public String getExternalIdByOwnerAndParamId(String owner, String paramId) {
        try {
            return jdbcTemplateRW.queryForObject(
                    "SELECT external_id FROM bivariate_indicators_metadata WHERE owner = ? AND param_id = ? ORDER BY date DESC LIMIT 1",
                    String.class, owner, paramId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // TODO: possibly will be added something about owner field here
    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getAllBivariateIndicators() {
        return getSelectedBivariateIndicators(null);
    }

    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getSelectedBivariateIndicators(List<String> indicatorIds) {
        return jdbcTemplate.query(String.format("""
                SELECT distinct on (param_id) 
                    param_id,
                    param_label,
                    copyrights,
                    direction,
                    is_base,
                    internal_id,
                    owner,
                    state,
                    is_public,
                    allowed_users,
                    date,
                    description,
                    coverage,
                    update_frequency,
                    application,
                    unit_id,
                    last_updated,
                    external_id,
                    emoji,
                    upload_id,
                    max_res,
                    downscale,
                    hash,
                    null coverage_polygon
                FROM bivariate_indicators_metadata WHERE %s is_public and state = 'READY' order by param_id, date desc""",
                        (indicatorIds == null ? "" : String.format("param_id in ('%s') and ", String.join("','", indicatorIds)))),
                bivariateIndicatorRowMapper);
    }

    //TODO: remove after transition from param_id to uuid as an identifier for indicator. Use 'getIndicatorByUuid' method in future instead
    @Deprecated
    public String getLabelByParamId(String paramId) {
        try {
            return jdbcTemplate.queryForObject(
                    String.format("SELECT param_label FROM bivariate_indicators_metadata where param_id = '%s' and is_public order by date desc limit 1", paramId), String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Instant getIndicatorsLastUpdateDate() {
        Timestamp lastUpdated = jdbcTemplate.queryForObject("SELECT MAX(last_updated) FROM bivariate_indicators_metadata", Timestamp.class);
        return lastUpdated != null ? lastUpdated.toInstant() : null;
    }

    @Transactional(readOnly = true)
    public Map<String, Instant> getIndicatorsLastUpdateDates(List<String> indicatorIds) {
        if (indicatorIds == null || indicatorIds.isEmpty()) {
            return Map.of();
        }
        String inSql = String.format("'%s'", String.join("','", indicatorIds));
        String sql = String.format("SELECT param_id, last_updated FROM bivariate_indicators_metadata WHERE param_id in (%s)", inSql);
        return jdbcTemplate.query(sql, rs -> {
            Map<String, Instant> result = new HashMap<>();
            while (rs.next()) {
                String id = rs.getString("param_id");
                Timestamp ts = rs.getTimestamp("last_updated");
                result.put(id, ts != null ? ts.toInstant() : null);
            }
            return result;
        });
    }

    private void initParams(PreparedStatement ps, BivariateIndicatorDto bivariateIndicatorDto) throws SQLException, JsonProcessingException {
        ps.setString(1, bivariateIndicatorDto.getId());
        ps.setString(2, bivariateIndicatorDto.getLabel());
        ps.setString(3, bivariateIndicatorDto.getCopyrights() == null ? null : objectMapper.writeValueAsString(bivariateIndicatorDto.getCopyrights()));
        ps.setString(4, bivariateIndicatorDto.getDirection() == null ? null : objectMapper.writeValueAsString(bivariateIndicatorDto.getDirection()));
        ps.setBoolean(5, bivariateIndicatorDto.getIsBase());
        ps.setString(6, bivariateIndicatorDto.getExternalId());
        ps.setString(7, bivariateIndicatorDto.getOwner());
        ps.setBoolean(8, bivariateIndicatorDto.getIsPublic());
        ps.setString(9, bivariateIndicatorDto.getAllowedUsers() == null ? null : objectMapper.writeValueAsString(bivariateIndicatorDto.getAllowedUsers()));
        ps.setString(10, bivariateIndicatorDto.getDescription());
        ps.setString(11, bivariateIndicatorDto.getCoverage());
        //TODO: discuss these values, should be some default values if not specified
        ps.setString(12, bivariateIndicatorDto.getUpdateFrequency());
        ps.setString(13, bivariateIndicatorDto.getApplication() == null ? null : objectMapper.writeValueAsString(bivariateIndicatorDto.getApplication()));
        ps.setString(14, bivariateIndicatorDto.getUnitId());
        ps.setString(15, bivariateIndicatorDto.getEmoji());
        ps.setString(16, bivariateIndicatorDto.getLastUpdated() == null ? null : bivariateIndicatorDto.getLastUpdated().toString());
        ps.setString(17, bivariateIndicatorDto.getUploadId());
        ps.setString(18, bivariateIndicatorDto.getDownscale());
        ps.setString(19, bivariateIndicatorDto.getHash());
    }
}
