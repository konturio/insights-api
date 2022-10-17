package io.kontur.insightsapi.repository;

import com.google.common.collect.Lists;
import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.mapper.*;
import io.kontur.insightsapi.model.Axis;
import io.kontur.insightsapi.model.BivariateStatistic;
import io.kontur.insightsapi.model.PolygonMetrics;
import io.kontur.insightsapi.model.Statistic;
import io.kontur.insightsapi.service.cacheable.CorrelationRateService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class StatisticRepository implements CorrelationRateService {

    @Value("classpath:/sql.queries/statistic_all.sql")
    private Resource statisticAll;

    @Value("classpath:/sql.queries/bivariate_statistic.sql")
    private Resource bivariateStatistic;

    @Value("classpath:/sql.queries/axis_statistic.sql")
    private Resource axisStatistic;

    @Value("classpath:/sql.queries/statistic_correlation.sql")
    private Resource statisticCorrelation;

    @Value("classpath:/sql.queries/statistic_correlation_numdenom.sql")
    private Resource statisticCorrelationNumdenom;

    @Value("classpath:/sql.queries/statistic_correlation_intersect.sql")
    private Resource statisticCorrelationIntersect;

    @Value("classpath:/sql.queries/statistic_correlation_emptylayer_intersect.sql")
    private Resource statisticCorrelationEmptylayerIntersect;

    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final StatisticRowMapper statisticRowMapper;

    private final BivariateStatisticRowMapper bivariateStatisticRowMapper;

    private final AxisRowMapper axisRowMapper;

    private final PolygonMetricsRowMapper polygonMetricsRowMapper;

    private final CorrelationRateRowMapper correlationRateRowMapper;

    private final QueryFactory queryFactory;

    private final Logger logger = LoggerFactory.getLogger(StatisticRepository.class);

    @Transactional(readOnly = true)
    public Statistic getAllStatistic() {
        return jdbcTemplate.queryForObject(queryFactory.getSql(statisticAll), statisticRowMapper);
    }

    @Transactional(readOnly = true)
    public BivariateStatistic getBivariateStatistic() {
        return jdbcTemplate.queryForObject(queryFactory.getSql(bivariateStatistic), bivariateStatisticRowMapper);
    }

    @Transactional(readOnly = true)
    public List<Axis> getAxisStatistic() {
        return jdbcTemplate.query(queryFactory.getSql(axisStatistic), axisRowMapper);
    }

    @Transactional(readOnly = true)
    public List<PolygonMetrics> getAllCorrelationRateStatistics() {
        return jdbcTemplate.query(queryFactory.getSql(statisticCorrelation), polygonMetricsRowMapper);
    }

    @Transactional(readOnly = true)
    public List<PolygonMetrics> getAllCovarianceRateStatistics() {
        //TODO: the same as correlations, just for example. Will be changed in future
        return jdbcTemplate.query(queryFactory.getSql(statisticCorrelation), polygonMetricsRowMapper);
    }

    @Transactional(readOnly = true)
    public List<NumeratorsDenominatorsDto> getNumeratorsDenominatorsForCorrelation() {
        return jdbcTemplate.query(queryFactory.getSql(statisticCorrelationNumdenom), (rs, rowNum) ->
                NumeratorsDenominatorsDto.builder()
                        .xNumerator(rs.getString("x_num"))
                        .xDenominator(rs.getString("x_den"))
                        .xLabel(rs.getString("x_param_label"))
                        .yNumerator(rs.getString("y_num"))
                        .yDenominator(rs.getString("y_den"))
                        .yLabel(rs.getString("y_param_label"))
                        .quality(rs.getDouble("quality")).build());
    }

    @Transactional(readOnly = true)
    public List<Double> getPolygonCorrelationRateStatisticsBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList) {
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", polygon);
        var requests = dtoList.stream()
                .map(dto -> createCorrelationQueryString(dto.getXNumerator(), dto.getXDenominator(),
                        dto.getYNumerator(), dto.getYDenominator())).toList();
        var distinctFieldsRequests = dtoList.stream()
                .flatMap(dto -> Stream.of(dto.getXNumerator(), dto.getYNumerator(), dto.getXDenominator(), dto.getYDenominator()))
                .distinct()
                .toList();
        var query = String.format(queryFactory.getSql(statisticCorrelationIntersect), StringUtils.join(distinctFieldsRequests, ","), StringUtils.join(requests, ","));
        //it is important to disable jit in same stream with main request
        jitDisable();
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, correlationRateRowMapper);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s", polygon);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public List<Double> getPolygonCovarianceRateStatisticsBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList) {
        //TODO: the same as correlations, just for example. Will be changed in future
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", polygon);
        var requests = dtoList.stream()
                .map(dto -> createCovarianceQueryString(dto.getXNumerator(), dto.getXDenominator(),
                        dto.getYNumerator(), dto.getYDenominator())).toList();
        var distinctFieldsRequests = dtoList.stream()
                .flatMap(dto -> Stream.of(dto.getXNumerator(), dto.getYNumerator(), dto.getXDenominator(), dto.getYDenominator()))
                .distinct()
                .toList();
        var query = String.format(queryFactory.getSql(statisticCorrelationIntersect), StringUtils.join(distinctFieldsRequests, ","), StringUtils.join(requests, ","));
        //it is important to disable jit in same stream with main request
        jitDisable();
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, correlationRateRowMapper);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s", polygon);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getNumeratorsForNotEmptyLayersBatch(String polygon, List<NumeratorsDenominatorsDto> dtoList){
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", polygon);
        var distinctFieldsRequests = dtoList.stream()
                .flatMap(dto -> Stream.of(dto.getXNumerator(), dto.getYNumerator()))
                .distinct()
                .collect(Collectors.toList());
        List<String> requests = Lists.newArrayList();
        for (String field : distinctFieldsRequests) {
            requests.add("max(" + field + ")!=min(" + field + ") as result" + field);
        }
        var query = String.format(queryFactory.getSql(statisticCorrelationEmptylayerIntersect), StringUtils.join(requests, ","), StringUtils.join(distinctFieldsRequests, ","));
        //it is important to disable jit in same stream with main request
        jitDisable();
        Map<String, Boolean> result = new HashMap<>();
        try {
            namedParameterJdbcTemplate.query(query, paramSource, (rs -> {
                result.putAll(createResultMapForNotEmptyLayers(distinctFieldsRequests, rs));
            }));
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", polygon, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
        return result;
    }

    private Map<String, Boolean> createResultMapForNotEmptyLayers(List<String> numerators, ResultSet rs) {
        Map<String, Boolean> result = new HashMap<>();
        numerators.forEach(numerator -> {
            try {
                result.put(numerator, rs.getBoolean("result" + numerator));
            } catch (SQLException e) {
                logger.error("Can't get boolean value from result set", e);
            }
        });
        return result;
    }

    private String createCorrelationQueryString(String xNum, String xDen, String yNum, String yDen) {
        return "corr(" + xNum + " / " + xDen + ", " + yNum + " / " + yDen + ") filter (where " + xDen + " != 0 and " + yDen + " != 0)";
    }

    private String createCovarianceQueryString(String xNum, String xDen, String yNum, String yDen) {
        return "covar_samp(" + xNum + " / " + xDen + ", " + yNum + " / " + yDen + ") filter (where " + xDen + " != 0 and " + yDen + " != 0)";
    }

    public void jitDisable() {
        jdbcTemplate.execute("set local jit = off");
    }
}