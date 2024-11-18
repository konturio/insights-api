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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.CannotAcquireLockException;
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
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

@Repository
@RequiredArgsConstructor
public class IndicatorRepository {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorRepository.class);

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    private final DataSource dataSource;

    @Value("classpath:/sql.queries/insert_bivariate_indicators.sql")
    private Resource insertBivariateIndicators;

    private final QueryFactory queryFactory;

    private final BivariateIndicatorRowMapper bivariateIndicatorRowMapper;

    @Value("${calculations.bivariate.transposed.table}")
    private String transposedTableName;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsMetadataTableName;

    @Value("${calculations.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    private final ThreadPoolExecutor uploadExecutor;

    private String getUploadAppName(String uploadId) {
        return "upload " + uploadId;
    }

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

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("select from bivariate_indicators_metadata where internal_id = '" + internalId + "' for no key update nowait");
            }
            // row-level lock will be released when COPY fails or succeeds

            Future<?> uploadTask = submitUploadTask(file, internalId, pipedOutputStream);

            copyFile(connection, pipedInputStream);

            try {
                uploadTask.get();
            } catch (Exception e) {
                logger.error("failed to stream file to db", e);
                throw new IndicatorDataProcessingException(e.getMessage(), e);
            }

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("update bivariate_indicators_metadata set state = 'NEW' where internal_id = '" + internalId + "'");
            }
            connection.commit();
            // now new indicator is visible for other transactions with the state "NEW"
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
        String insertQuery = String.format(queryFactory.getSql(insertBivariateIndicators), bivariateIndicatorsMetadataTableName);

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

    private void copyFile(Connection connection, InputStream inputStream) throws SQLException, IOException {
        if (connection.isWrapperFor(Connection.class)) {
            CopyManager copyManager = new CopyManager((BaseConnection) connection.unwrap(Connection.class));
            copyManager.copyIn(String.format("COPY %s FROM STDIN DELIMITER ',' null 'NULL'", transposedTableName), inputStream);
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

    public List<BivariateIndicatorDto> getIndicatorsByExternalId(String externalId) {
        return jdbcTemplate.query(
                String.format("SELECT * FROM %s WHERE external_id = '%s'::uuid",
                        bivariateIndicatorsMetadataTableName,
                        externalId),
                bivariateIndicatorRowMapper);
    }

    public List<BivariateIndicatorDto> getIndicatorsByOwner(String owner) {
        return jdbcTemplate.query(
                "SELECT * FROM " + bivariateIndicatorsMetadataTableName + " WHERE owner = ?",
                bivariateIndicatorRowMapper, owner);
    }

    public String getIndicatorIdByUploadId(String owner, String uploadId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT external_id || '/' || state FROM " + bivariateIndicatorsMetadataTableName +
                " WHERE owner = ? AND upload_id = ?::uuid",
                String.class, owner, uploadId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void checkActiveUpload(BivariateIndicatorDto indicator) throws Exception {
        // if the indicator with the same param_id and owner is currently uploading, return its upload id
        try {
            // lookup by partial match: first part of upload_id is always a hash of param_id & owner
            jdbcTemplate.queryForObject(
                "select 1 from bivariate_indicators_metadata where param_id = ? and owner = ? and state = 'COPY IN PROGRESS' for no key update nowait",
                String.class, indicator.getId(), indicator.getOwner());
        } catch (EmptyResultDataAccessException e) {
            ;
        } catch (CannotAcquireLockException e) {
            // COPY process is holding a lock on some row with this param_id
            throw new IndicatorDataProcessingException("indicator upload already in progress");
        }
    }

    public List<BivariateIndicatorDto> getIndicatorsByOwnerAndParamId(String owner, String paramId) {
        return jdbcTemplate.query(
                "SELECT * FROM " + bivariateIndicatorsMetadataTableName + " WHERE owner = ? AND param_id = ?",
                bivariateIndicatorRowMapper, owner, paramId);
    }

    public BivariateIndicatorDto getIndicatorByOwnerAndExternalId(String owner, String externalId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT * FROM " + bivariateIndicatorsMetadataTableName + " WHERE owner = ? AND external_id = ?::uuid limit 1",
                    bivariateIndicatorRowMapper, owner, externalId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // TODO: possibly will be added something about owner field here
    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getAllBivariateIndicators() {
        return jdbcTemplate.query(String.format("SELECT * FROM %s", bivariateIndicatorsMetadataTableName),
                bivariateIndicatorRowMapper);
    }

    @Transactional(readOnly = true)
    public List<BivariateIndicatorDto> getSelectedBivariateIndicators(List<String> indicatorIds) {
        return jdbcTemplate.query(String.format("SELECT distinct on (param_id) * FROM %s WHERE param_id in ('%s') and is_public order by param_id, date desc",
                        bivariateIndicatorsMetadataTableName, String.join("','", indicatorIds)),
                bivariateIndicatorRowMapper);
    }

    //TODO: remove after transition from param_id to uuid as an identifier for indicator. Use 'getIndicatorByUuid' method in future instead
    @Deprecated
    public String getLabelByParamId(String paramId) {
        String bivariateIndicatorsTable = useStatSeparateTables ? bivariateIndicatorsMetadataTableName
                : bivariateIndicatorsTableName;
        String condition = useStatSeparateTables ? "and is_public order by date desc" : "";
        try {
            return jdbcTemplate.queryForObject(String.format("SELECT param_label FROM %s where param_id = '%s' %s limit 1",
                    bivariateIndicatorsTable, paramId, condition), String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Instant getIndicatorsLastUpdateDate() {
        Timestamp lastUpdated = jdbcTemplate.queryForObject(String.format("SELECT MAX(last_updated) FROM %s",
                bivariateIndicatorsMetadataTableName), Timestamp.class);
        return lastUpdated != null ? lastUpdated.toInstant() : null;
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
    }
}
