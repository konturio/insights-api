package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.model.ThermalSpotStatistic;
import io.kontur.insightsapi.service.Helper;
import io.kontur.insightsapi.service.SlowQueryDetection;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ThermalSpotRepository {

    private static final Map<String, String> queryMap = Map.of(
            "industrialAreaKm2", "sum(industrial_area) as industrialAreaKm2 ",
            "hotspotDaysPerYearMax", "max(wildfires) as hotspotDaysPerYearMax ",
            "volcanoesCount", "sum(volcanos_count)  as volcanoesCount ",
            "forestAreaKm2", "sum(forest) as forestAreaKm2"
    );

    private final Logger logger = LoggerFactory.getLogger(ThermalSpotRepository.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Helper helper;

    @SlowQueryDetection(paramWithGeometry = "geojson")
    @Transactional(readOnly = true)
    public ThermalSpotStatistic calculateThermalSpotStatistic(String geojson, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, queryMap);
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = String.format("""
                        with validated_input as (
                            select calculate_validated_input(:polygon) geom
                        ),
                             stat_area as (
                                 select distinct on (h.h3) h.*
                                 from (
                                          select ST_Subdivide(v.geom, 30) geom
                                          from validated_input v
                                      ) p
                                          cross join
                                      lateral (
                                          select h3,
                                                 industrial_area,
                                                 wildfires,
                                                 volcanos_count,
                                                 forest
                                          from stat_h3 sh
                                          where ST_Intersects(sh.geom, p.geom)
                                            and sh.zoom = 8
                                          order by h3
                                          ) h
                             )
                        select %s from stat_area st
                """.trim(), StringUtils.join(queryList, ", "));
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                    ThermalSpotStatistic.builder()
                            .industrialAreaKm2(rs.getBigDecimal("industrialAreaKm2"))
                            .hotspotDaysPerYearMax(rs.getLong("hotspotDaysPerYearMax"))
                            .volcanoesCount(rs.getLong("volcanoesCount"))
                            .forestAreaKm2(rs.getBigDecimal("forestAreaKm2"))
                            .build());
        } catch (EmptyResultDataAccessException e) {
            return ThermalSpotStatistic.builder()
                    .industrialAreaKm2(new BigDecimal(0))
                    .hotspotDaysPerYearMax(0L)
                    .volcanoesCount(0L)
                    .forestAreaKm2(new BigDecimal(0))
                    .build();
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geojson, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }
}
