package io.kontur.insightsapi.repository;

import com.google.common.collect.Lists;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TileRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final JdbcTemplate jdbcTemplate;

    private final QueryFactory queryFactory;

    @Value("classpath:/sql.queries/get_tile_mvt.sql")
    private Resource getTileMvtResource;

    @Value("classpath:/sql.queries/get_tile_mvt_indicators_list_v2.sql")
    private Resource getTileMvtIndicatorsListResourceV2;

    @Value("classpath:/sql.queries/get_tile_mvt_generate_on_the_fly.sql")
    private Resource getTileMvtGenerateOnTheFly;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsTestTableName;

    @Value("${calculations.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;

    private final IndicatorRepository indicatorRepository;

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    @Value("${calculations.tiles.tile-size}")
    private Integer tileSize;

    @Value("${calculations.tiles.hex-edge-pixels}")
    private Integer hexEdgePixels;

    @Value("${calculations.tiles.max-h3-resolution}")
    private Integer maxH3Resolutions;

    @Value("${calculations.tiles.min-h3-resolution}")
    private Integer minH3Resolutions;

    public byte[] getBivariateTileMvt(Integer z, Integer x, Integer y, List<String> bivariateIndicators) {

        String query = generateSqlQuery(bivariateIndicators);

        var paramSource = new MapSqlParameterSource("z", z);
        paramSource.addValue("x", x);
        paramSource.addValue("y", y);
        paramSource.addValue("tile_size", tileSize);
        paramSource.addValue("hex_edge_pixels", hexEdgePixels);
        paramSource.addValue("max_h3_resolution", maxH3Resolutions);
        paramSource.addValue("min_h3_resolution", minH3Resolutions);


        return namedParameterJdbcTemplate.queryForObject(query, paramSource,
                (rs, rowNum) -> rs.getBytes("tile"));
    }

    public byte[] getBivariateTileMvtIndicatorsListV2(Integer z, Integer x, Integer y, List<String> bivariateIndicators) {
        var paramSource = new MapSqlParameterSource("z", z);
        paramSource.addValue("x", x);
        paramSource.addValue("y", y);
        paramSource.addValue("ind0", bivariateIndicators.get(0));
        paramSource.addValue("ind1", bivariateIndicators.get(1));
        paramSource.addValue("ind2", bivariateIndicators.get(2));
        paramSource.addValue("ind3", bivariateIndicators.get(3));
        paramSource.addValue("tile_size", tileSize);
        paramSource.addValue("hex_edge_pixels", hexEdgePixels);
        paramSource.addValue("max_h3_resolution", maxH3Resolutions);
        paramSource.addValue("min_h3_resolution", minH3Resolutions);
        var query = String.format(queryFactory.getSql(getTileMvtIndicatorsListResourceV2),
                bivariateIndicatorsTestTableName, bivariateIndicatorsTestTableName, bivariateIndicatorsTestTableName,
                bivariateIndicatorsTestTableName, bivariateIndicatorsTestTableName);
        return namedParameterJdbcTemplate.queryForObject(query, paramSource,
                (rs, rowNum) -> rs.getBytes("tile"));
    }

    public List<String> getAllBivariateIndicators() {
        String bivariateIndicatorsTable = useStatSeparateTables ? bivariateIndicatorsTestTableName : bivariateIndicatorsTableName;
        var query = String.format("select param_id from %s", bivariateIndicatorsTable);
        return jdbcTemplate.query(query, (rs, rowNum) -> rs.getString("param_id"));
    }

    public List<String> getGeneralBivariateIndicators() {
        Set<String> result = new HashSet<>();
        var query = "select x_numerator, x_denominator, y_numerator, y_denominator from bivariate_overlays";
        jdbcTemplate.query(query, (rs, rowNum) ->
                result.addAll(List.of(rs.getString("x_numerator"),
                        rs.getString("x_denominator"),
                        rs.getString("y_numerator"),
                        rs.getString("y_denominator"))));
        return result.stream().toList();
    }

    private String generateSqlQuery(List<String> bivariateIndicators) {
        if (useStatSeparateTables) {
            List<BivariateIndicatorDto> bivariateIndicatorDtos = indicatorRepository.getAllBivariateIndicators();

            List<String> outerFilter = Lists.newArrayList();
            List<String> columns = Lists.newArrayList();

            for (BivariateIndicatorDto indicator : bivariateIndicatorDtos) {
                outerFilter.add(String.format("'%s'", indicator.getUuid()));
                columns.add(String.format("coalesce(avg(indicator_value) filter (where indicator_uuid = '%s'), 0) as %s", indicator.getUuid(), indicator.getId()));
            }

            return String.format(queryFactory.getSql(getTileMvtGenerateOnTheFly),
                    StringUtils.join(outerFilter, ", "),
                    StringUtils.join(columns, ", "));

        } else {
            return String.format(queryFactory.getSql(getTileMvtResource),
                    StringUtils.join(bivariateIndicators.stream().map(current -> String.format("coalesce(%s, 0) as %s", current, current)).toList(), ", "));
        }
    }
}
