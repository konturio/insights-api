package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.dto.PolygonStatisticRequest;
import io.kontur.insightsapi.mapper.*;
import io.kontur.insightsapi.model.Axis;
import io.kontur.insightsapi.model.BivariateStatistic;
import io.kontur.insightsapi.model.PolygonCorrelationRate;
import io.kontur.insightsapi.model.Statistic;
import io.kontur.insightsapi.service.SlowQueryDetection;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class StatisticRepository {

    private final JdbcTemplate jdbcTemplate;

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final StatisticRowMapper statisticRowMapper;

    private final BivariateStatisticRowMapper bivariateStatisticRowMapper;

    private final AxisRowMapper axisRowMapper;

    private final PolygonCorrelationRateRowMapper polygonCorrelationRateRowMapper;

    private final CorrelationRateRowMapper correlationRateRowMapper;

    private final Logger logger = LoggerFactory.getLogger(StatisticRepository.class);

    @Transactional(readOnly = true)
    public Statistic getAllStatistic() {
        var query = """
                select
                        jsonb_build_object(
                                           'axis', ba.axis,
                                           'meta', jsonb_build_object('max_zoom', 8,
                                                                      'min_zoom', 0),
                                           'indicators', (
                                               select jsonb_agg(jsonb_build_object('name', param_id,
                                                                                   'label', param_label,
                                                                                   'direction', direction,
                                                                                   'copyrights', copyrights))
                                               from bivariate_indicators
                                           ),
                                           'colors', jsonb_build_object(
                                               'fallback', '#ccc',
                                               'combinations', (
                                                   select jsonb_agg(jsonb_build_object('color', color,
                                                                                                   'color_comment', color_comment,
                                                                                                   'corner', corner))
                                                   from bivariate_colors)
                                            ),
                                           'correlationRates', (
                                               select
                                                   jsonb_agg(jsonb_build_object(
                                                                 'x', jsonb_build_object('label', xcopy.param_label,
                                                                                         'quotient',
                                                                                         jsonb_build_array(x_num, x_den)),
                                                                 'y', jsonb_build_object('label', ycopy.param_label,
                                                                                         'quotient',
                                                                                         jsonb_build_array(y_num, y_den)),
                                                                 'rate', correlation,
                                                                 'correlation', correlation,
                                                                 'quality', quality
                                                                 )
                                                             order by abs(correlation) * quality desc nulls last , abs(correlation) desc)
                                               from
                                                   bivariate_axis_correlation, bivariate_indicators xcopy, bivariate_indicators ycopy
                                               where xcopy.param_id = x_num and ycopy.param_id = y_num
                                           ),
                                           'initAxis',
                                           jsonb_build_object('x', jsonb_build_object('label', x.label, 'quotient',
                                                                                      jsonb_build_array(x.numerator, x.denominator),
                                                                                      'steps',
                                                                                      jsonb_build_array(
                                                                                          jsonb_build_object('value', x.min, 'label', x.min_label),
                                                                                          jsonb_build_object('value', x.p25, 'label', x.p25_label),
                                                                                          jsonb_build_object('value', x.p75, 'label', x.p75_label),
                                                                                          jsonb_build_object('value', x.max, 'label', x.max_label))),
                                                              'y', jsonb_build_object('label', y.label, 'quotient',
                                                                                      jsonb_build_array(y.numerator, y.denominator),
                                                                                      'steps',
                                                                                      jsonb_build_array(
                                                                                          jsonb_build_object('value', y.min, 'label', y.min_label),
                                                                                          jsonb_build_object('value', y.p25, 'label', y.p25_label),
                                                                                          jsonb_build_object('value', y.p75, 'label', y.p75_label),
                                                                                          jsonb_build_object('value', y.max, 'label', y.max_label)))
                                               ),
                                           'overlays', ov.overlay
                            )::text
                    from
                        ( select
                              json_agg(
                                  jsonb_build_object('label', label, 'quotient', jsonb_build_array(numerator, denominator), 'quality',
                                                     quality,
                                                     'steps', jsonb_build_array(
                                                         jsonb_build_object('value', min, 'label', min_label),
                                                         jsonb_build_object('value', p25, 'label', p25_label),
                                                         jsonb_build_object('value', p75, 'label', p75_label),
                                                         jsonb_build_object('value', max, 'label', max_label)))) as axis
                          from
                              bivariate_axis )                                                                      ba,
                        ( select
                              json_agg(jsonb_build_object('name', o.name, 'active', o.active, 'description', o.description,
                                                          'colors', o.colors,
                                                          'x', jsonb_build_object('label', ax.label, 'quotient',
                                                                                  jsonb_build_array(ax.numerator, ax.denominator),
                                                                                  'steps',
                                                                                  jsonb_build_array(
                                                                                      jsonb_build_object('value', ax.min, 'label', ax.min_label),
                                                                                      jsonb_build_object('value', ax.p25, 'label', ax.p25_label),
                                                                                      jsonb_build_object('value', ax.p75, 'label', ax.p75_label),
                                                                                      jsonb_build_object('value', ax.max, 'label', ax.max_label))),
                                                          'y', jsonb_build_object('label', ay.label, 'quotient',
                                                                                  jsonb_build_array(ay.numerator, ay.denominator),
                                                                                  'steps',
                                                                                  jsonb_build_array(
                                                                                      jsonb_build_object('value', ay.min, 'label', ay.min_label),
                                                                                      jsonb_build_object('value', ay.p25, 'label', ay.p25_label),
                                                                                      jsonb_build_object('value', ay.p75, 'label', ay.p75_label),
                                                                                      jsonb_build_object('value', ay.max, 'label', ay.max_label))))
                                       order by ord) as overlay
                          from
                              bivariate_axis     ax,
                              bivariate_axis     ay,
                              bivariate_overlays o
                          where
                                ax.denominator = o.x_denominator
                            and ax.numerator = o.x_numerator
                            and ay.denominator = o.y_denominator
                            and ay.numerator = o.y_numerator )                                                      ov,
                        bivariate_axis                                                                              x,
                        bivariate_axis                                                                              y
                    where
                          x.numerator = 'count'
                      and x.denominator = 'area_km2'
                      and y.numerator = 'view_count'
                      and y.denominator = 'area_km2'
                """.trim();
        return jdbcTemplate.queryForObject(query, statisticRowMapper);
    }

    @Transactional(readOnly = true)
    public BivariateStatistic getBivariateStatistic() {
        var query = """
                select
                        jsonb_build_object(
                                           'meta', jsonb_build_object('max_zoom', 8,
                                                                      'min_zoom', 0),
                                           'indicators', (
                                               select jsonb_agg(jsonb_build_object('name', param_id,
                                                                                   'label', param_label,
                                                                                   'direction', direction,
                                                                                   'copyrights', copyrights))
                                               from bivariate_indicators
                                           ),
                                           'colors', jsonb_build_object(
                                               'fallback', '#ccc',
                                               'combinations', (
                                                   select jsonb_agg(jsonb_build_object('color', color,
                                                                                                   'color_comment', color_comment,
                                                                                                   'corner', corner))
                                                   from bivariate_colors)
                                            ),
                                           'initAxis',
                                           jsonb_build_object('x', jsonb_build_object('label', x.label, 'quotient',
                                                                                      jsonb_build_array(x.numerator, x.denominator),
                                                                                      'steps',
                                                                                      jsonb_build_array(
                                                                                          jsonb_build_object('value', x.min, 'label', x.min_label),
                                                                                          jsonb_build_object('value', x.p25, 'label', x.p25_label),
                                                                                          jsonb_build_object('value', x.p75, 'label', x.p75_label),
                                                                                          jsonb_build_object('value', x.max, 'label', x.max_label))),
                                                              'y', jsonb_build_object('label', y.label, 'quotient',
                                                                                      jsonb_build_array(y.numerator, y.denominator),
                                                                                      'steps',
                                                                                      jsonb_build_array(
                                                                                          jsonb_build_object('value', y.min, 'label', y.min_label),
                                                                                          jsonb_build_object('value', y.p25, 'label', y.p25_label),
                                                                                          jsonb_build_object('value', y.p75, 'label', y.p75_label),
                                                                                          jsonb_build_object('value', y.max, 'label', y.max_label)))
                                               ),
                                           'overlays', ov.overlay
                            )::text
                    from
                        ( select
                              json_agg(
                                  jsonb_build_object('label', label, 'quotient', jsonb_build_array(numerator, denominator), 'quality',
                                                     quality,
                                                     'steps', jsonb_build_array(
                                                         jsonb_build_object('value', min, 'label', min_label),
                                                         jsonb_build_object('value', p25, 'label', p25_label),
                                                         jsonb_build_object('value', p75, 'label', p75_label),
                                                         jsonb_build_object('value', max, 'label', max_label)))) as axis
                          from
                              bivariate_axis )                                                                      ba,
                        ( select
                              json_agg(jsonb_build_object('name', o.name, 'active', o.active, 'description', o.description,
                                                          'colors', o.colors,
                                                          'x', jsonb_build_object('label', ax.label, 'quotient',
                                                                                  jsonb_build_array(ax.numerator, ax.denominator),
                                                                                  'steps',
                                                                                  jsonb_build_array(
                                                                                      jsonb_build_object('value', ax.min, 'label', ax.min_label),
                                                                                      jsonb_build_object('value', ax.p25, 'label', ax.p25_label),
                                                                                      jsonb_build_object('value', ax.p75, 'label', ax.p75_label),
                                                                                      jsonb_build_object('value', ax.max, 'label', ax.max_label))),
                                                          'y', jsonb_build_object('label', ay.label, 'quotient',
                                                                                  jsonb_build_array(ay.numerator, ay.denominator),
                                                                                  'steps',
                                                                                  jsonb_build_array(
                                                                                      jsonb_build_object('value', ay.min, 'label', ay.min_label),
                                                                                      jsonb_build_object('value', ay.p25, 'label', ay.p25_label),
                                                                                      jsonb_build_object('value', ay.p75, 'label', ay.p75_label),
                                                                                      jsonb_build_object('value', ay.max, 'label', ay.max_label))))
                                       order by ord) as overlay
                          from
                              bivariate_axis     ax,
                              bivariate_axis     ay,
                              bivariate_overlays o
                          where
                                ax.denominator = o.x_denominator
                            and ax.numerator = o.x_numerator
                            and ay.denominator = o.y_denominator
                            and ay.numerator = o.y_numerator )                                                      ov,
                        bivariate_axis                                                                              x,
                        bivariate_axis                                                                              y
                    where
                          x.numerator = 'count'
                      and x.denominator = 'area_km2'
                      and y.numerator = 'view_count'
                      and y.denominator = 'area_km2'
                """.trim();
        return jdbcTemplate.queryForObject(query, bivariateStatisticRowMapper);
    }

    @Transactional(readOnly = true)
    public List<Axis> getAxisStatistic() {
        var query = """
                select
                            jsonb_build_object('label', label, 'quotient', jsonb_build_array(numerator, denominator), 'quality',
                                               quality,
                                               'steps', jsonb_build_array(
                                                       jsonb_build_object('value', min, 'label', min_label),
                                                       jsonb_build_object('value', p25, 'label', p25_label),
                                                       jsonb_build_object('value', p75, 'label', p75_label),
                                                       jsonb_build_object('value', max, 'label', max_label)))
                   from bivariate_axis
                """.trim();
        return jdbcTemplate.query(query, axisRowMapper);
    }

    @Transactional(readOnly = true)
    public List<PolygonCorrelationRate> getPolygonNumeratorsCorrelationRateStatistics(PolygonStatisticRequest request) {
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", request.getPolygon());
        paramSource.addValue("xNumerator", request.getXNumeratorList());
        paramSource.addValue("yNumerator", request.getYNumeratorList());
        var query = """
                with bivariate_axis_correlation_polygon as (
                    select x.numerator                             as x_num,
                           x.denominator                           as x_den,
                           y.numerator                             as y_num,
                           y.denominator                           as y_den,
                           correlate_bivariate_axes(:polygon::json, x.numerator, x.denominator, y.numerator,
                                                    y.denominator) as correlation,
                           1 - ((1 - x.quality) * (1 - y.quality)) as quality
                    from (bivariate_axis x
                             join bivariate_indicators x_den_indicator
                                  on (x.denominator = x_den_indicator.param_id)
                             join bivariate_indicators x_num_indicator
                                  on (x.numerator = x_num_indicator.param_id)),
                         (bivariate_axis y
                             join bivariate_indicators y_den_indicator
                                  on (y.denominator = y_den_indicator.param_id))
                    where (x.numerator != y.numerator)
                      and x.numerator in (:xNumerator)
                      and y.numerator in (:yNumerator)
                      and x.quality > 0.5
                      and y.quality > 0.5
                      and x_den_indicator.is_base
                      and y_den_indicator.is_base
                      and not x_num_indicator.is_base
                )
                select jsonb_build_object(
                                         'x', jsonb_build_object('label', xcopy.param_label,
                                                                  'quotient',
                                                                  jsonb_build_array(x_num, x_den)),
                                         'y', jsonb_build_object('label', ycopy.param_label,
                                                                   'quotient',
                                                                   jsonb_build_array(y_num, y_den)),
                                         'rate', correlation,
                                         'correlation', correlation,
                                         'quality', quality
                                     )::text
                from bivariate_axis_correlation_polygon,
                     bivariate_indicators xcopy,
                     bivariate_indicators ycopy
                where xcopy.param_id = x_num
                  and ycopy.param_id = y_num
                order by abs(correlation) * quality desc nulls last, abs(correlation) desc
                """.trim();
        try {
            return namedParameterJdbcTemplate.query(query, paramSource, polygonCorrelationRateRowMapper);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", request.getPolygon(), e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public List<PolygonCorrelationRate> getPolygonCorrelationRateStatistics(PolygonStatisticRequest request) {
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", request.getPolygon());
        var query = """
                with bivariate_axis_correlation_polygon as (
                    select x.numerator                             as x_num,
                           x.denominator                           as x_den,
                           y.numerator                             as y_num,
                           y.denominator                           as y_den,
                           correlate_bivariate_axes(:polygon::json, x.numerator, x.denominator, y.numerator,
                                                    y.denominator) as correlation,
                           1 - ((1 - x.quality) * (1 - y.quality)) as quality
                    from (bivariate_axis x
                             join bivariate_indicators x_den_indicator
                                  on (x.denominator = x_den_indicator.param_id)
                             join bivariate_indicators x_num_indicator
                                  on (x.numerator = x_num_indicator.param_id)),
                         (bivariate_axis y
                             join bivariate_indicators y_den_indicator
                                  on (y.denominator = y_den_indicator.param_id))
                    where (x.numerator != y.numerator)
                      and x.quality > 0.5
                      and y.quality > 0.5
                      and x_den_indicator.is_base
                      and y_den_indicator.is_base
                      and not x_num_indicator.is_base
                )
                select jsonb_build_object(
                                         'x', jsonb_build_object('label', xcopy.param_label,
                                                                  'quotient',
                                                                  jsonb_build_array(x_num, x_den)),
                                         'y', jsonb_build_object('label', ycopy.param_label,
                                                                   'quotient',
                                                                   jsonb_build_array(y_num, y_den)),
                                         'rate', correlation,
                                         'correlation', correlation,
                                         'quality', quality
                                     )::text 
                from bivariate_axis_correlation_polygon, 
                     bivariate_indicators xcopy, 
                     bivariate_indicators ycopy 
                where xcopy.param_id = x_num 
                  and ycopy.param_id = y_num 
                order by abs(correlation) * quality desc nulls last, abs(correlation) desc
                """.trim();
        try {
            return namedParameterJdbcTemplate.query(query, paramSource, polygonCorrelationRateRowMapper);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", request.getPolygon(), e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public List<PolygonCorrelationRate> getAllCorrelationRateStatistics() {
        var query = """
                select
                    jsonb_build_object(
                                      'x', jsonb_build_object('label', xcopy.param_label,
                                                              'quotient',
                                                              jsonb_build_array(x_num, x_den)),
                                      'y', jsonb_build_object('label', ycopy.param_label,
                                                              'quotient',
                                                              jsonb_build_array(y_num, y_den)),
                                      'rate', correlation,
                                      'correlation', correlation,
                                      'quality', quality
                                  ) 
                from
                    bivariate_axis_correlation, bivariate_indicators xcopy, bivariate_indicators ycopy
                    where xcopy.param_id = x_num and ycopy.param_id = y_num
                    order by abs(correlation) * quality desc nulls last, abs(correlation) desc
                """.trim();
        return jdbcTemplate.query(query, polygonCorrelationRateRowMapper);
    }

    @Transactional(readOnly = true)
    public List<NumeratorsDenominatorsDto> getNumeratorsDenominatorsForCorrelation() {
        var query = """
                select x.numerator                             as x_num,
                           x.denominator                           as x_den,
                           x_den_indicator.param_label             as x_param_label,
                           y.numerator                             as y_num,
                           y.denominator                           as y_den,
                           y_den_indicator.param_label             as y_param_label,
                           1 - ((1 - x.quality) * (1 - y.quality)) as quality
                    from (bivariate_axis x
                             join bivariate_indicators x_den_indicator
                                  on (x.denominator = x_den_indicator.param_id)
                             join bivariate_indicators x_num_indicator
                                  on (x.numerator = x_num_indicator.param_id)),
                         (bivariate_axis y
                             join bivariate_indicators y_den_indicator
                                  on (y.denominator = y_den_indicator.param_id))
                    where (x.numerator != y.numerator)
                      and x.quality > 0.5
                      and y.quality > 0.5
                      and x_den_indicator.is_base
                      and y_den_indicator.is_base
                      and not x_num_indicator.is_base
                """.trim();
        return jdbcTemplate.query(query, (rs, rowNum) ->
                NumeratorsDenominatorsDto.builder()
                        .xNumerator(rs.getString("x_num"))
                        .xDenominator(rs.getString("x_den"))
                        .xLabel(rs.getString("x_param_label"))
                        .yNumerator(rs.getString("y_num"))
                        .yDenominator(rs.getString("y_den"))
                        .yLabel(rs.getString("y_param_label"))
                        .quality(rs.getDouble("quality")).build());
    }

    @SlowQueryDetection(paramWithGeometry = "polygon")
    @Transactional(readOnly = true)
    public List<Double> getPolygonCorrelationRateStatisticsBatch(List<NumeratorsDenominatorsDto> dtoList, String polygon) {
        var paramSource = new MapSqlParameterSource();
        paramSource.addValue("polygon", polygon);
        var requests = dtoList.stream()
                .map(dto -> createCorrelationQueryString(dto.getXNumerator(), dto.getXDenominator(),
                        dto.getYNumerator(), dto.getYDenominator())).collect(Collectors.toList());
        var distinctFieldsRequests = dtoList.stream()
                .flatMap(dto -> Stream.of(dto.getXNumerator(), dto.getYNumerator(), dto.getXDenominator(), dto.getYDenominator()))
                .distinct()
                .collect(Collectors.toList());
        var query = String.format("""
                with validated_input as (
                    select calculate_validated_input(:polygon) geom
                ),
                subdivided_polygons as (
                         select ST_Subdivide(v.geom) geom
                         from validated_input v
                ),
                     stat_area as (
                         select distinct h3, %s
                         from stat_h3 sh3, subdivided_polygons sp where
                             ST_Intersects(sh3.geom, sp.geom)
                     )
                 select %s
                  from stat_area
                """.trim(), StringUtils.join(distinctFieldsRequests, ","), StringUtils.join(requests, ","));
        //it is important to disable jit in same stream with main request
        jitDisable();
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, correlationRateRowMapper);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", polygon, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    private String createCorrelationQueryString(String xNum, String xDen, String yNum, String yDen) {
        return "corr(" + xNum + " / " + xDen + ", " + yNum + " / " + yDen + ") filter (where " + xDen + " != 0 and " + yDen + " != 0)";
    }

    public void jitDisable() {
        jdbcTemplate.execute("set local jit = off");
    }
}
