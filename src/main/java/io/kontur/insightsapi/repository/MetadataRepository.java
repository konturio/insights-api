package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.MetadataDto;
import io.kontur.insightsapi.mapper.MetadataRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class MetadataRepository {

    private final JdbcTemplate jdbcTemplate;

    private final MetadataRowMapper metadataRowMapper;

    public void setDataLastUpdateTime(Instant dataLastUpdateTime) {
        jdbcTemplate.update(String.format("update insights_metadata set data_last_update_time = %s",
                Timestamp.from(dataLastUpdateTime)));
    }

    public Instant getDataLastUpdateTime() {
        MetadataDto metadataDto =
                jdbcTemplate.query("select data_last_update_time from insights_metadata", metadataRowMapper).get(0);
        return metadataDto.getDataLastUpdateTime();
    }
}
