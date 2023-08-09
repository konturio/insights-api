package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.dto.HumanitarianImpactDto;
import io.kontur.insightsapi.model.OsmQuality;
import io.kontur.insightsapi.model.UrbanCore;
import io.kontur.insightsapi.service.Helper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
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

    @Value("classpath:/sql.queries/calculate_population_and_gdp.sql")
    private Resource calculatePopulationAndAGdp;

    @Value("classpath:/sql.queries/calculate_population_and_gdp_v2.sql")
    private Resource calculatePopulationAndAGdpV2;

    @Value("classpath:/sql.queries/population_humanitarian_impact.sql")
    private Resource populationHumanitarianImpact;

    @Value("classpath:/sql.queries/population_humanitarian_impact_v2.sql")
    private Resource populationHumanitarianImpactV2;

    @Value("classpath:/sql.queries/population_osm.sql")
    private Resource populationOsm;

    @Value("classpath:/sql.queries/population_osm_v2.sql")
    private Resource populationOsmV2;

    @Value("classpath:/sql.queries/population_urbancore.sql")
    private Resource populationUrbanCore;

    @Value("classpath:/sql.queries/population_urbancore_v2.sql")
    private Resource populationUrbanCoreV2;

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsMetadataTableName;

    private final QueryFactory queryFactory;

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

    @Transactional(readOnly = true)
    public Map<String, CalculatePopulationDto> getPopulationAndGdp(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var queryString = StringUtils.EMPTY;
        if (useStatSeparateTables) {
            queryString = String.format(queryFactory.getSql(calculatePopulationAndAGdpV2), bivariateIndicatorsMetadataTableName,
                    bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName);
        } else {
            queryString = queryFactory.getSql(calculatePopulationAndAGdp);
        }
        try {
            return Map.of("population", Objects.requireNonNull(namedParameterJdbcTemplate.queryForObject(queryString, paramSource, (rs, rowNum) ->
                    CalculatePopulationDto.builder()
                            .population(rs.getBigDecimal("population"))
                            .gdp(rs.getBigDecimal("gdp"))
                            .type(rs.getString("type"))
                            .urban(rs.getBigDecimal("urban")).build())));
        } catch (DataAccessResourceFailureException e) {
            String error = String.format(DatabaseUtil.ERROR_TIMEOUT, geometry);
            logger.error(error, e);
            throw new DataAccessResourceFailureException(error, e);
        } catch (EmptyResultDataAccessException e) {
            String error = String.format(DatabaseUtil.ERROR_EMPTY_RESULT, geometry);
            logger.error(error, e);
            throw new EmptyResultDataAccessException(error, 1);
        } catch (Exception e) {
            String error = String.format(DatabaseUtil.ERROR_SQL, geometry);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getArea(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var query = "select ST_Area(:geometry::geometry)";
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, BigDecimal.class);
        } catch (Exception e) {
            String error = String.format(DatabaseUtil.ERROR_SQL, geometry);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public List<HumanitarianImpactDto> calculateHumanitarianImpact(String geometry) {
        var paramSource = new MapSqlParameterSource("geometry", geometry);
        var queryString = StringUtils.EMPTY;
        if (useStatSeparateTables) {
            var transformedGeometry = helper.transformGeometryToWkt(geometry);
            paramSource.addValue("transformed_geometry", transformedGeometry);
            queryString = String.format(queryFactory.getSql(populationHumanitarianImpactV2), bivariateIndicatorsMetadataTableName);
        } else {
            queryString = queryFactory.getSql(populationHumanitarianImpact);
        }
        try {
            return namedParameterJdbcTemplate.query(queryString, paramSource, (rs, rowNum) ->
                    HumanitarianImpactDto.builder()
                            .areaKm2(rs.getBigDecimal("areaKm2"))
                            .population(rs.getBigDecimal("population"))
                            .totalPopulation(rs.getBigDecimal("totalPopulation"))
                            .geometry(GeoJSONFactory.create(rs.getString("geometry")))
                            .name(rs.getString("name"))
                            .percentage(rs.getString("percentage"))
                            .totalAreaKm2(rs.getBigDecimal("totalAreaKm2")).build());
        } catch (DataAccessResourceFailureException e) {
            String error = String.format(DatabaseUtil.ERROR_TIMEOUT, geometry);
            logger.error(error, e);
            throw new DataAccessResourceFailureException(error, e);
        } catch (EmptyResultDataAccessException e) {
            String error = String.format(DatabaseUtil.ERROR_EMPTY_RESULT, geometry);
            logger.error(error, e);
            throw new EmptyResultDataAccessException(error, 1);
        } catch (Exception e) {
            String error = String.format(DatabaseUtil.ERROR_SQL, geometry);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public OsmQuality calculateOsmQuality(String geojson, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, queryMap);
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = StringUtils.EMPTY;
        if (useStatSeparateTables) {
            query = String.format(queryFactory.getSql(populationOsmV2), bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName,
                    bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName, bivariateIndicatorsMetadataTableName,
                    bivariateIndicatorsMetadataTableName, StringUtils.join(queryList, ", "));
        } else {
            query = String.format(queryFactory.getSql(populationOsm), StringUtils.join(queryList, ", "));
        }
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
        } catch (DataAccessResourceFailureException e) {
            String error = String.format(DatabaseUtil.ERROR_TIMEOUT, geojson);
            logger.error(error, e);
            throw new DataAccessResourceFailureException(error, e);
        } catch (EmptyResultDataAccessException e) {
            String error = String.format(DatabaseUtil.ERROR_EMPTY_RESULT, geojson);
            logger.error(error, e);
            throw new EmptyResultDataAccessException(error, 1);
        } catch (Exception e) {
            String error = String.format(DatabaseUtil.ERROR_SQL, geojson);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }

    @Transactional(readOnly = true)
    public UrbanCore calculateUrbanCore(String geojson, List<String> fieldList) {
        var queryList = helper.transformFieldList(fieldList, urbanCoreQueryMap);
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = StringUtils.EMPTY;
        if (useStatSeparateTables) {
            var transformedGeometry = helper.transformGeometryToWkt(geojson);
            paramSource.addValue("transformed_polygon", transformedGeometry);
            query = String.format(queryFactory.getSql(populationUrbanCoreV2), bivariateIndicatorsMetadataTableName);
        } else {
            query = String.format(queryFactory.getSql(populationUrbanCore), StringUtils.join(queryList, ", "));
        }
        try {
            return namedParameterJdbcTemplate.queryForObject(query, paramSource, (rs, rowNum) ->
                    UrbanCore.builder()
                            .urbanCorePopulation(rs.getBigDecimal("urbanCorePopulation"))
                            .urbanCoreAreaKm2(rs.getBigDecimal("urbanCoreAreaKm2"))
                            .totalPopulatedAreaKm2(rs.getBigDecimal("totalPopulatedAreaKm2")).build());
        } catch (DataAccessResourceFailureException e) {
            String error = String.format(DatabaseUtil.ERROR_TIMEOUT, geojson);
            logger.error(error, e);
            throw new DataAccessResourceFailureException(error, e);
        } catch (EmptyResultDataAccessException e) {
            //result may be empty here
            logger.error("empty result for geometry -> " + geojson);
            return UrbanCore.builder()
                    .urbanCoreAreaKm2(new BigDecimal(0))
                    .urbanCorePopulation(new BigDecimal(0))
                    .totalPopulatedAreaKm2(new BigDecimal(0)).build();
        } catch (Exception e) {
            String error = String.format(DatabaseUtil.ERROR_SQL, geojson);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
    }
}
