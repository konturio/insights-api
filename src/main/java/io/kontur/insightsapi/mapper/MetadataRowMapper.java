package io.kontur.insightsapi.mapper;

import io.kontur.insightsapi.dto.MetadataDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;

@Service
public class MetadataRowMapper implements RowMapper<MetadataDto> {

    @Override
    public MetadataDto mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        MetadataDto metadataDto = new MetadataDto();
        metadataDto.setDataLastUpdateTime(resultSet.getTimestamp(MetadataColumns.data_last_update_time.name())
                .toInstant());
        return metadataDto;
    }

    private enum MetadataColumns {
        data_last_update_time
    }
}
