package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.model.Functions;
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
public class FunctionsRepository {

    private static final Map<String, String> queryMap = Map.ofEntries(
            Map.entry("population", "sum(population) as population "),
            Map.entry("settledArea", "sum(populated_area_km2) as settledArea "),
            Map.entry("osmGaps", "sum(populated_area_km2*(1-sign(count)))/sum(populated_area_km2)*100 as osmGaps "),
            Map.entry("peopleWithoutOsmObjects", "sum(population*(1-sign(count))) as peopleWithoutOsmObjects "),
            Map.entry("settledAreaWithoutOsmObjects", "sum(populated_area_km2*(1-sign(count))) as settledAreaWithoutOsmObjects "),
            Map.entry("settledAreaWithoutOsmBuildingsPercent",
                    "sum(populated_area_km2*(1-sign(building_count)))/sum(populated_area_km2)*100 as settledAreaWithoutOsmBuildingsPercent "),
            Map.entry("peopleWithoutOsmBuildings", "sum(population*(1-sign(building_count))) as peopleWithoutOsmBuildings "),
            Map.entry("settledAreaWithoutOsmBuildings", "sum(populated_area_km2*(1-sign(building_count))) as settledAreaWithoutOsmBuildings "),
            Map.entry("settledAreaWithoutOsmRoadsPercent", "sum(populated_area_km2*(1-sign(highway_length)))/sum(populated_area_km2)*100 " +
                    "as settledAreaWithoutOsmRoadsPercent "),
            Map.entry("peopleWithoutOsmRoads", "sum(population*(1-sign(highway_length))) as peopleWithoutOsmRoads "),
            Map.entry("settledAreaWithoutOsmRoads", "sum(populated_area_km2*(1-sign(highway_length))) as settledAreaWithoutOsmRoads")
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Helper helper;

    @Transactional(readOnly = true)
    public Functions calculateFunctions(String geojson, List<String> fieldList){
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
                                         select distinct on (sh3.h3) sh3.h3, sh3.population, sh3.populated_area_km2, sh3.count, 
                sh3.building_count, sh3.highway_length from stat_h3 sh3, subdivided_polygons sp 
                                         where st_dwithin(sh3.geom, sp.geom, 0) and resolution = 8
                                    ) 
                select %s from stat_area st
                """.trim(), StringUtils.join(queryList, ", "));
        return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                Functions.builder()
                        .population(rs.getLong("population"))
                        .settledArea(rs.getBigDecimal("settledArea"))
                        .osmGaps(rs.getBigDecimal("osmGaps"))
                        .peopleWithoutOsmObjects(rs.getLong("peopleWithoutOsmObjects"))
                        .settledAreaWithoutOsmObjects(rs.getBigDecimal("settledAreaWithoutOsmObjects"))
                        .settledAreaWithoutOsmBuildingsPercent(rs.getBigDecimal("settledAreaWithoutOsmBuildingsPercent"))
                        .peopleWithoutOsmBuildings(rs.getLong("peopleWithoutOsmBuildings"))
                        .settledAreaWithoutOsmBuildings(rs.getBigDecimal("settledAreaWithoutOsmBuildings"))
                        .settledAreaWithoutOsmRoadsPercent(rs.getBigDecimal("settledAreaWithoutOsmRoadsPercent"))
                        .peopleWithoutOsmRoads(rs.getLong("peopleWithoutOsmRoads"))
                        .settledAreaWithoutOsmRoads(rs.getBigDecimal("settledAreaWithoutOsmRoads"))
                        .build());
    }
}
