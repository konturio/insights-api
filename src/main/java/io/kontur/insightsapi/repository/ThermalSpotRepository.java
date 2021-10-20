package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.model.ThermalSpotStatistic;
import io.kontur.insightsapi.service.Helper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Helper helper;

    @Transactional(readOnly = true)
    public ThermalSpotStatistic calculateThermalSpotStatistic(String geojson, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, queryMap);
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = String.format("""
                with validated_input as (
                    select ST_MakeValid(ST_Transform(
                            ST_WrapX(ST_WrapX(
                                             ST_Union(ST_MakeValid(
                                                     d.geom
                                                 )),
                                             180, -360), -180, 360),
                            3857)) geom
                    from ST_Dump(ST_CollectionExtract(ST_GeomFromGeoJSON(
                                                              :polygon::jsonb
                                                                     ))) d
                ),
                subdivided_polygons as materialized (
                         select ST_Subdivide(v.geom) geom
                         from validated_input v
                ),
                           stat_area as (
                                         select distinct on (sh3.h3) sh3.h3, sh3.industrial_area, sh3.wildfires, sh3.volcanos_count, 
                sh3.forest from stat_h3 sh3, subdivided_polygons sp 
                                         where st_dwithin(sh3.geom, sp.geom, 0) and zoom = 8
                                    ) 
                select %s from stat_area st
                """.trim(), StringUtils.join(queryList, ", "));
        return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                ThermalSpotStatistic.builder()
                        .industrialAreaKm2(rs.getBigDecimal("industrialAreaKm2"))
                        .hotspotDaysPerYearMax(rs.getLong("hotspotDaysPerYearMax"))
                        .volcanoesCount(rs.getLong("volcanoesCount"))
                        .forestAreaKm2(rs.getBigDecimal("forestAreaKm2"))
                        .build());
    }
}
