package io.kontur.insightsapi.repository;

import com.google.common.collect.Lists;
import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.dto.HumanitarianImpactDto;
import io.kontur.insightsapi.model.OsmQuality;
import io.kontur.insightsapi.model.UrbanCore;
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

    private final Logger logger = LoggerFactory.getLogger(PopulationRepository.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final Helper helper;

    @SlowQueryDetection(paramWithGeometry = "geometry")
    @Transactional(readOnly = true)
    public Map<String, CalculatePopulationDto> getPopulationAndGdp(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = """
                select type, population, urban, gdp
                from calculate_population_and_gdp(:geometry)
                """.trim();
        try {
            return Map.of("population", Objects.requireNonNull(namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                    CalculatePopulationDto.builder()
                            .population(rs.getBigDecimal("population"))
                            .gdp(rs.getBigDecimal("gdp"))
                            .type(rs.getString("type"))
                            .urban(rs.getBigDecimal("urban")).build())));
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geometry, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @SlowQueryDetection(paramWithGeometry = "geometry")
    @Transactional(readOnly = true)
    public BigDecimal getArea(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = "select ST_Area(:geometry::geometry)";
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, BigDecimal.class);
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geometry, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @SlowQueryDetection(paramWithGeometry = "geometry")
    @Transactional(readOnly = true)
    public List<HumanitarianImpactDto> calculateHumanitarianImpact(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = """
                        with resolution as (
                            select calculate_area_resolution(ST_SetSRID(:geometry::geometry, 4326)) as resolution
                        ),
                             validated_input as (
                                select calculate_validated_input(:geometry) geom
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
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geometry, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @SlowQueryDetection(paramWithGeometry = "geojson")
    @Transactional(readOnly = true)
    public OsmQuality calculateOsmQuality(String geojson, List<String> fieldList) {
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
                                                 count,
                                                 building_count,
                                                 highway_length,
                                                 population,
                                                 populated_area_km2,
                                                 area_km2
                                          from stat_h3 sh
                                          where ST_Intersects(sh.geom, p.geom)
                                            and sh.zoom = 8
                                            and sh.population > 0
                                          order by h3
                                          ) h
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
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geojson, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }

    @SlowQueryDetection(paramWithGeometry = "geojson")
    @Transactional(readOnly = true)
    public UrbanCore calculateUrbanCore(String geojson, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, urbanCoreQueryMap);
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = String.format("""
                with resolution as (
                    select calculate_area_resolution(ST_SetSRID(:polygon::geometry, 4326)) as resolution
                ),
                                     validated_input as (
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
                                                         population,
                                                         area_km2
                                                  from stat_h3 sh
                                                  where ST_Intersects(sh.geom, p.geom)
                                                    and sh.zoom = (select resolution from resolution)
                                                    and sh.population > 0
                                                  order by h3
                                                  ) h
                                     ),
                                     stat_pop as (
                                         select s.*, sum(population) over (order by population desc) as sum_pop
                                         from stat_area s
                                     ),
                                     total as (
                                         select sum(population)                  as population,
                                                round(sum(area_km2)::numeric, 2) as area
                                         from stat_pop
                                     )
                                select sum(s.population)                as urbanCorePopulation,
                                       round(sum(area_km2)::numeric, 2) as urbanCoreAreaKm2,
                                       t.area                           as totalPopulatedAreaKm2
                                from stat_pop s,
                                     total t
                                where sum_pop <= t.population * 0.68
                                group by t.population, t.area;
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
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geojson, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
    }
}
