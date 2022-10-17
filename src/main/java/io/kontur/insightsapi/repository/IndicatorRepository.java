package io.kontur.insightsapi.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.FileUploadResultDto;
import io.kontur.insightsapi.exception.ConnectionException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.sql.SQLException;

@Repository
@RequiredArgsConstructor
public class IndicatorRepository {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorRepository.class);
    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final ObjectMapper objectMapper;

    @Value("${database.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;

    @Transactional
    public String createIndicator(BivariateIndicatorDto bivariateIndicatorDto) throws JsonProcessingException {

        var paramSource = new MapSqlParameterSource()
                .addValue("id", bivariateIndicatorDto.getId())
                .addValue("label", bivariateIndicatorDto.getLabel())
                .addValue("copyrights", objectMapper.writeValueAsString(bivariateIndicatorDto.getCopyrights()))
                .addValue("direction", objectMapper.writeValueAsString(bivariateIndicatorDto.getDirection()))
                .addValue("isBase", bivariateIndicatorDto.getIsBase())
                .addValue("isPublic", bivariateIndicatorDto.getIsPublic())
                .addValue("allowedUsers", objectMapper.writeValueAsString(bivariateIndicatorDto.getAllowedUsers()));

        //TODO:add owner in future
        String bivariateIndicatorsQuery = String.format("INSERT INTO %s (param_id,param_label,copyrights,direction,is_base,param_uuid,owner,state,is_public,allowed_users,date) " +
                "VALUES (:id,:label,:copyrights::json,:direction::json,:isBase,gen_random_uuid(),null,'NEW',:isPublic,:allowedUsers,now()) RETURNING param_uuid;", bivariateIndicatorsTableName);
        return namedParameterJdbcTemplate.queryForObject(bivariateIndicatorsQuery, paramSource, String.class);
    }
}
