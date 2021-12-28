package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.FunctionArgs;
import io.kontur.insightsapi.model.FunctionResult;
import io.kontur.insightsapi.service.SlowQueryDetection;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Repository
@RequiredArgsConstructor
public class FunctionsRepository {

    private static final Pattern VALID_STRING_PATTERN = Pattern.compile("(\\d|\\w){1,255}");

    private final Logger logger = LoggerFactory.getLogger(FunctionsRepository.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @SlowQueryDetection(paramWithGeometry = "geojson")
    @Transactional(readOnly = true)
    public List<FunctionResult> calculateFunctionsResult(String geojson, List<FunctionArgs> args) {
        List<String> params = args.stream()
                .map(this::createFunctionsForSelect)
                .toList();
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
                                   population,
                                   populated_area_km2,
                                   count,
                                   building_count,
                                   highway_length,
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
                """.trim(), StringUtils.join(params, ", "));
        List<FunctionResult> result = new ArrayList<>();
        try {
            namedParameterJdbcTemplate.query(query, paramSource, (rs -> {
                result.addAll(createFunctionResultList(args, rs));
            }));
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s. Exception: %s", geojson, e.getMessage());
            logger.error(error);
            throw new IllegalArgumentException(error, e);
        }
        return result;
    }

    private String createFunctionsForSelect(FunctionArgs functionArgs) {
        String validId = checkString(functionArgs.getId());
        String validX = checkString(functionArgs.getX());
        String validY = checkString(functionArgs.getY());
        return switch (functionArgs.getName()) {
            case "sumX" -> "sum(" + validX + ") as result" + validId;
            case "sumXWhereNoY" -> "sum(" + validX + " * (1 - sign(" + validY + "))) " +
                    "as result" + validId;
            case "percentageXWhereNoY" -> "(sum(" + validX + " * (1 - sign(" + validY + ")))/sum(" +
                    validX + ") filter (where " + validX + " != 0)) * 100 as result" + validId;
            case "maxX" -> "max(" + validX + ") as result" + validId;
            default -> null;
        };
    }

    private List<FunctionResult> createFunctionResultList(List<FunctionArgs> args, ResultSet rs) {
        return args.stream().map(arg -> {
            try {
                return new FunctionResult(arg.getId(), rs.getBigDecimal("result" + arg.getId()));
            } catch (SQLException e) {
                logger.error("Can't get BigDecimal value from result set", e);
                return null;
            }
        }).toList();
    }

    private String checkString(String string) {
        if (string != null && !VALID_STRING_PATTERN.matcher(string).matches()) {
            logger.error("Illegal argument for request creation was found");
            throw new IllegalArgumentException("Illegal argument for request creation was found");
        }
        return string;
    }
}
