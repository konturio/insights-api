package io.kontur.insightsapi.repository;

import com.google.common.collect.Lists;
import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.dto.HumanitarianImpactDto;
import io.kontur.insightsapi.model.OsmQuality;
import io.kontur.insightsapi.model.UrbanCore;
import io.kontur.insightsapi.service.Helper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.wololo.geojson.GeoJSONFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class PopulationRepository {

    private static final Map<String, String> queryMap = Map.of(
            "peopleWithoutOsmBuildings", "sum(population * (1 - sign(building_count))) as peopleWithoutOsmBuildings ",
            "areaWithoutOsmBuildingsKm2", "sum(area_km2 * (1 - sign(building_count))) as areaWithoutOsmBuildingsKm2 ",
            "peopleWithoutOsmRoads", "sum(population * (1 - sign(highway_length))) as peopleWithoutOsmRoads ",
            "areaWithoutOsmRoadsKm2", "sum(area_km2 * (1 - sign(highway_length))) as areaWithoutOsmRoadsKm2 ",
            "peopleWithoutOsmObjects", "sum(population * (1 - sign(count))) as peopleWithoutOsmObjects ",
            "areaWithoutOsmObjectsKm2", "sum(area_km2 * (1 - sign(count))) as areaWithoutOsmObjectsKm2 ",
            "osmGapsPercentage", "sum(populated_area_km2 * (1 - sign(count))) / sum(populated_area_km2) * 100 as osmGapsPercentage ");

    private static final Map<String, String> urbanCoreQueryMap = Map.of(
            "urbanCorePopulation", "sum(s.population) as urbanCorePopulation ",
            "urbanCoreAreaKm2", "round(sum(area_km2)::numeric, 2) as urbanCoreAreaKm2 ",
            "totalPopulatedAreaKm2", "t.area as totalPopulatedAreaKm2 "
    );

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Helper helper;

    @Transactional(readOnly = true)
    public Map<String, CalculatePopulationDto> getPopulationAndGdp(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = """
                select type, population, urban, gdp
                from calculate_population_and_gdp_for_wkt(:geometry)
                """.trim();

        return Map.of("population", Objects.requireNonNull(namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                CalculatePopulationDto.builder()
                        .population(rs.getBigDecimal("population"))
                        .gdp(rs.getBigDecimal("gdp"))
                        .type(rs.getString("type"))
                        .urban(rs.getBigDecimal("urban")).build())));
    }

    @Transactional(readOnly = true)
    public BigDecimal getArea(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = "select ST_Area(ST_GeomFromText(:geometry))";
        return namedParameterJdbcTemplate.queryForObject(query, paramSource, BigDecimal.class);
    }

    @Transactional(readOnly = true)
    public List<HumanitarianImpactDto> calculateHumanitarianImpact(String wkt) {
        var paramSource = new MapSqlParameterSource("wkt", wkt);
        var query = """
                with resolution as (
                    select calculate_area_resolution(ST_SetSRID(:wkt::geometry, 4326)) as resolution
                ),
                     validated_input as (
                         select ST_MakeValid(ST_Transform(
                                 ST_WrapX(ST_WrapX(
                                                  ST_Union(ST_MakeValid(
                                                          d.geom
                                                      )),
                                                  180, -360), -180, 360),
                                 3857)) geom
                         from ST_Dump(ST_CollectionExtract(ST_SetSRID(
                                                                   :wkt::geometry, 4326
                                                                          ))) d
                     ),
                    subdivided_polygons as (
                              select ST_Subdivide(v.geom) geom
                              from validated_input v
                          ),
                     stat_in_area as (
                         select s.*, sum(population) over (order by population desc) as sum_pop
                         from (
                                  select distinct h3, population, area_km2, sh.geom
                                  from stat_h3 sh,
                                       subdivided_polygons p
                                  where zoom = (select resolution from resolution)
                                    and population > 0
                                    and ST_Intersects(
                                          sh.geom,
                                          p.geom
                                      )
                              ) s
                     ),
                     total as (
                         select sum(population) as population, round(sum(area_km2)::numeric, 2) as area from stat_in_area
                     ) 
                select sum(s.population)                                as population,
                       case
                           when sum_pop <= t.population * 0.68 then '0-68'
                           else '68-100'
                           end                                          as percentage,
                       case
                           when sum_pop <= t.population * 0.68 then 'Kontur Urban Core'
                           else 'Kontur Settled Periphery'
                           end                                          as name,
                       round(sum(area_km2)::numeric, 2)                 as areaKm2,
                       ST_AsGeoJSON(ST_Transform(ST_Union(geom), 4326)) as geometry,
                       t.population                                     as totalPopulation,
                       t.area                                           as totalAreaKm2 
                from stat_in_area s, 
                     total t 
                group by t.population, t.area, 2, 3
                """.trim();
        try {
            return namedParameterJdbcTemplate.query(query, paramSource, (rs, rowNum) ->
                    HumanitarianImpactDto.builder()
                            .areaKm2(rs.getBigDecimal("areaKm2"))
                            .population(rs.getBigDecimal("population"))
                            .totalPopulation(rs.getBigDecimal("totalPopulation"))
                            .geometry(GeoJSONFactory.create(rs.getString("geometry")))
                            .name(rs.getString("name"))
                            .percentage(rs.getString("percentage"))
                            .totalAreaKm2(rs.getBigDecimal("totalAreaKm2")).build());
        } catch (EmptyResultDataAccessException e) {
            return Lists.newArrayList();
        }
    }

    @Transactional(readOnly = true)
    public OsmQuality calculateOsmQuality(String geojson, List<String> fieldList) {
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
                         from validated_input v order by 1
                     ),
                                           stat_area as (
                                                         select distinct on (sh3.h3) sh3.h3, sh3.count, sh3.building_count, sh3.highway_length, 
                                sh3.population, sh3.populated_area_km2, sh3.area_km2 from stat_h3 sh3, subdivided_polygons sp 
                                                         where st_dwithin(sh3.geom, sp.geom, 0) and zoom = 8 and population > 0
                                                     ) 
                                select %s from stat_area st
                                """.trim(), StringUtils.join(queryList, ", "));
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                    OsmQuality.builder()
                            .peopleWithoutOsmBuildings(rs.getLong("peopleWithoutOsmBuildings"))
                            .areaWithoutOsmBuildingsKm2(rs.getBigDecimal("areaWithoutOsmBuildingsKm2"))
                            .peopleWithoutOsmRoads(rs.getLong("peopleWithoutOsmRoads"))
                            .areaWithoutOsmRoadsKm2(rs.getBigDecimal("areaWithoutOsmRoadsKm2"))
                            .peopleWithoutOsmObjects(rs.getLong("peopleWithoutOsmObjects"))
                            .areaWithoutOsmObjectsKm2(rs.getBigDecimal("areaWithoutOsmObjectsKm2"))
                            .osmGapsPercentage(rs.getBigDecimal("osmGapsPercentage"))
                            .build());
        } catch (EmptyResultDataAccessException e) {
            return OsmQuality.builder()
                    .peopleWithoutOsmBuildings(0L)
                    .areaWithoutOsmBuildingsKm2(new BigDecimal(0))
                    .peopleWithoutOsmRoads(0L)
                    .areaWithoutOsmRoadsKm2(new BigDecimal(0))
                    .peopleWithoutOsmObjects(0L)
                    .areaWithoutOsmObjectsKm2(new BigDecimal(0))
                    .osmGapsPercentage(new BigDecimal(0))
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public UrbanCore calculateUrbanCore(String wkt, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, urbanCoreQueryMap);
        var paramSource = new MapSqlParameterSource("wkt", wkt);
        var query = String.format("""
                with resolution as (
                            select calculate_area_resolution(ST_SetSRID(:wkt::geometry, 4326)) as resolution
                        ),
                     validated_input as (
                         select ST_MakeValid(ST_Transform(
                                 ST_WrapX(ST_WrapX(
                                                  ST_Union(ST_MakeValid(
                                                          d.geom
                                                      )),
                                                  180, -360), -180, 360),
                                 3857)) geom
                         from ST_Dump(ST_CollectionExtract(ST_SetSRID(
                                                                   :wkt::geometry, 4326
                                                                          ))) d
                     ),
                    subdivided_input as (
                              select ST_Subdivide(v.geom) geom
                              from validated_input v
                          ),
                            stat_in_area as (select s.*, sum(population) over (order by population desc) as sum_pop
                                             from (select distinct population, s.geom, area_km2, s.h3 as h3
                                                   from stat_h3 s,
                                                       subdivided_input i,
                                                       resolution r
                                                   where zoom = r.resolution
                                                     and population > 0
                                                     and ST_Intersects(
                                                           s.geom,
                                                           i.geom
                                                      )
                                             ) s),
                            total as (select sum(population) as population, round(sum(area_km2)::numeric, 2) as area from stat_in_area)
                        select %s
                        from stat_in_area s,
                            total t
                        where sum_pop <= t.population * 0.68
                        group by t.population, t.area
                        """.trim(), StringUtils.join(queryList, ", "));
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                    UrbanCore.builder()
                            .urbanCorePopulation(rs.getBigDecimal("urbanCorePopulation"))
                            .urbanCoreAreaKm2(rs.getBigDecimal("urbanCoreAreaKm2"))
                            .totalPopulatedAreaKm2(rs.getBigDecimal("totalPopulatedAreaKm2")).build());
        } catch (EmptyResultDataAccessException e) {
            return UrbanCore.builder()
                    .urbanCoreAreaKm2(new BigDecimal(0))
                    .urbanCorePopulation(new BigDecimal(0))
                    .totalPopulatedAreaKm2(new BigDecimal(0)).build();
        }
    }
}
