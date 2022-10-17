package io.kontur.insightsapi.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.model.PolygonMetrics;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;

@Service
@RequiredArgsConstructor
public class PolygonMetricsRowMapper implements RowMapper<PolygonMetrics> {

    private final ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public PolygonMetrics mapRow(ResultSet resultSet, int rowNum) {
        return objectMapper.readValue(resultSet.getString(1), PolygonMetrics.class);
    }
}
