package io.kontur.insightsapi.repository;

import io.kontur.insightsapi.dto.FunctionArgs;
import io.kontur.insightsapi.exception.EmptySqlQueryAnswer;
import io.kontur.insightsapi.model.FunctionResult;
import io.kontur.insightsapi.service.cacheable.FunctionsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FunctionsRepository implements FunctionsService {

    @Value("classpath:function_intersect.sql")
    Resource functionIntersect;

    private static final Pattern VALID_STRING_PATTERN = Pattern.compile("(\\d|\\w){1,255}");

    private final Logger logger = LoggerFactory.getLogger(FunctionsRepository.class);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private final QueryFactory queryFactory;

    @Retryable(value = EmptySqlQueryAnswer.class, backoff = @Backoff(delayExpression = "${retry.functionRequest.delay}",
            multiplierExpression = "${retry.functionRequest.multiplier}"))
    public List<FunctionResult> calculateFunctionsResult(String geojson, List<FunctionArgs> args) {
        logger.info("GET DATA FROM DB");
        List<String> params = args.stream()
                .map(this::createFunctionsForSelect)
                .toList();
        var paramSource = new MapSqlParameterSource("polygon", geojson);
        var query = String.format(queryFactory.getSql(functionIntersect), StringUtils.join(params, ", "));
        List<FunctionResult> result = new ArrayList<>();
        try {
            namedParameterJdbcTemplate.query(query, paramSource, (rs -> {
                result.addAll(createFunctionResultList(args, rs));
            }));
            checkResultForNull(result);
        } catch (EmptySqlQueryAnswer e) {
            throw e;
        } catch (Exception e) {
            String error = String.format("Sql exception for geometry %s", geojson);
            logger.error(error, e);
            throw new IllegalArgumentException(error, e);
        }
        return result;
    }

    @Recover
    public List<FunctionResult> calculateFunctionsResultFallback(Exception exception, String geojson, List<FunctionArgs> args) {
        return args.stream()
                .map(arg -> new FunctionResult(arg.getId(), null))
                .collect(Collectors.toList());
    }

    private void checkResultForNull(List<FunctionResult> result) {
        boolean isResultNull = result.stream().allMatch(r -> r.getResult() == null);
        if (isResultNull) {
            logger.warn("Sql query answer is empty");
            throw new EmptySqlQueryAnswer();
        }
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