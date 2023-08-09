package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.FunctionArgs;
import io.kontur.insightsapi.model.FunctionResult;
import io.kontur.insightsapi.model.Unit;
import io.kontur.insightsapi.service.cacheable.FunctionsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Transactional(readOnly = true)
public class FunctionsRepository implements FunctionsService {

    @Value("classpath:/sql.queries/function_intersect.sql")
    private Resource functionIntersect;

    @Value("classpath:/sql.queries/function_intersect_v2.sql")
    private Resource functionIntersectV2;

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    @Value("${calculations.bivariate.indicators.test.table}")
    private String bivariateIndicatorsMetadataTableName;

    @Value("${calculations.bivariate.indicators.table}")
    private String bivariateIndicatorsTableName;


    private static final Pattern VALID_STRING_PATTERN = Pattern.compile("(\\d|\\w){1,255}");

    private final Logger logger = LoggerFactory.getLogger(FunctionsRepository.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final JdbcTemplate jdbcTemplate;

    private final QueryFactory queryFactory;

    private final IndicatorRepository indicatorRepository;

    public List<FunctionResult> calculateFunctionsResult(String geojson, List<FunctionArgs> args) {
        List<String> params = args.stream()
                .map(this::createFunctionsForSelect)
                .toList();
        List<String> paramIds = new ArrayList<>();
        for (FunctionArgs arg : args) {
            if (!paramIds.contains(arg.getX())) {
                paramIds.add(arg.getX());
            }
            if (arg.getY() != null && !paramIds.contains(arg.getY())) {
                paramIds.add(arg.getY());
            }
        }
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = StringUtils.EMPTY;
        if (useStatSeparateTables) {
            List<String> columns = new ArrayList<>();
            List<String> fromRes = new ArrayList<>();
            List<String> fromBivariateIndicators = new ArrayList<>();
            List<String> whereH3 = new ArrayList<>();
            List<String> whereUuid = new ArrayList<>();
            List<String> whereParamId = new ArrayList<>();
            for (int i = 0; i < paramIds.size(); i++) {
                columns.add(String.format("res_%s.indicator_value as %s", i, paramIds.get(i)));
                fromRes.add(String.format("res_%s", i));
                fromBivariateIndicators.add(String.format("%s bi_%s", bivariateIndicatorsMetadataTableName, i));
                whereUuid.add(String.format("res_%s.indicator_uuid = bi_%s.param_uuid", i, i));
                whereParamId.add(String.format("bi_%s.param_id = '%s'", i, paramIds.get(i)));
            }
            if (paramIds.size() > 1) {
                for (int i = 1; i < paramIds.size(); i++) {
                    whereH3.add(String.format("res_%s.h3 = res_%s.h3 and ", 0, i));
                }
            }
            query = String.format(queryFactory.getSql(functionIntersectV2), bivariateIndicatorsMetadataTableName, StringUtils.join(paramIds, "', '"),
                    StringUtils.join(columns, ", "), StringUtils.join(fromRes, ", res "), StringUtils.join(fromBivariateIndicators, ", "), StringUtils.join(whereH3, ""),
                    StringUtils.join(whereUuid, " and "), StringUtils.join(whereParamId, " and "), StringUtils.join(params, ", "));
        } else {
            query = String.format(queryFactory.getSql(functionIntersect),
                    StringUtils.join(paramIds, ", "),
                    StringUtils.join(params, ", "));
        }
        List<FunctionResult> result = new ArrayList<>();
        try {
            namedParameterJdbcTemplate.query(query, paramSource, (rs -> {
                result.addAll(createFunctionResultList(args, rs));
            }));
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s", geojson);
            logger.error(error, e);
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
                //TODO: in future getLabel should consume UUID
                return new FunctionResult(arg.getId(), rs.getBigDecimal("result" + arg.getId()), getUnit(arg), getLabel(arg.getX()), getLabel(arg.getY()));
            } catch (SQLException e) {
                logger.error("Can't get BigDecimal value from result set", e);
                return null;
            }
        }).toList();
    }

    //TODO: change deprecated method to 'getIndicatorByUuid' after transition from param_ir to param_uuid as indicator identifier
    private String getLabel(String paramId) {
        return Strings.isEmpty(paramId) ? null : indicatorRepository.getLabelByParamId(paramId);
    }

    private Unit getUnit(FunctionArgs arg) {
        String bivariateIndicatorsTable = useStatSeparateTables ? bivariateIndicatorsMetadataTableName : bivariateIndicatorsTableName;
        String query;
        //TODO: localization for units can be added to this request in future
        try {
            if ("percentageXWhereNoY".equals(arg.getName())) {
                query = """
                        select unit_id, short_name, long_name
                        from bivariate_unit_localization
                        where unit_id = 'perc'
                        """;
            } else {
                query = String.format("""
                        select bivariate_unit_localization.unit_id, short_name, long_name
                        from %s bi
                        left join bivariate_unit_localization on bi.unit_id = bivariate_unit_localization.unit_id
                        where bi.param_id = '%s';
                        """, bivariateIndicatorsTable, arg.getX());
            }
            return jdbcTemplate.queryForObject(query, (rs, rowNum) ->
                    Unit.builder()
                            .id(rs.getString("unit_id"))
                            .shortName(rs.getString("short_name"))
                            .longName(rs.getString("long_name"))
                            .build());
        } catch (EmptyResultDataAccessException e) {
            logger.error("No such unit", e);
            throw new EmptyResultDataAccessException("No such unit", 1);
        }
    }

    private String checkString(String string) {
        if (string != null && !VALID_STRING_PATTERN.matcher(string).matches()) {
            logger.error("Illegal argument for request creation was found");
            throw new IllegalArgumentException("Illegal argument for request creation was found");
        }
        return string;
    }
}