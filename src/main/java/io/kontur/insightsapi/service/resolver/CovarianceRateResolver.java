package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.dto.PureNumeratorDenominatorDto;
import io.kontur.insightsapi.model.Axis;
import io.kontur.insightsapi.model.BivariateStatistic;
import io.kontur.insightsapi.model.PolygonMetrics;
import io.kontur.insightsapi.service.MetricsHelper;
import io.kontur.insightsapi.service.cacheable.CovarianceRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CovarianceRateResolver implements GraphQLResolver<BivariateStatistic> {

    private final CovarianceRateService covarianceRateService;

    private final MetricsHelper metricsHelper;

    public List<PolygonMetrics> getCovarianceRates(BivariateStatistic statistic, DataFetchingEnvironment environment) throws JsonProcessingException {
        Map<String, Object> arguments = (Map<String, Object>) environment.getExecutionStepInfo()
                .getParent().getParent().getArguments().get("polygonStatisticRequest");
        if (!arguments.containsKey("polygon") && !arguments.containsKey("polygonV2")) {
            return covarianceRateService.getAllCovarianceRateStatistics().stream()
                    .map(PolygonMetrics::clone)
                    .collect(Collectors.toList());
        }
        var transformedGeometry = metricsHelper.getPolygon(arguments);

        List<NumeratorsDenominatorsDto> numeratorsDenominatorsForNotEmptyLayers =
                metricsHelper.getNumeratorsDenominatorsForNotEmptyLayers(arguments, transformedGeometry);

        var covarianceRateList = Lists.partition(numeratorsDenominatorsForNotEmptyLayers, 500).parallelStream()
                .map(sourceDtoList -> calculatePolygonCovariance(sourceDtoList, transformedGeometry))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        var avgCorrelationXY = metricsHelper.calculateAvgMetricsXY(covarianceRateList);

        fillAvgMetrics(covarianceRateList, avgCorrelationXY);

        covarianceRateList.sort(covarianceRateComparator());

        return covarianceRateList;
    }

    private List<PolygonMetrics> calculatePolygonCovariance(List<NumeratorsDenominatorsDto> sourceDtoList,
                                                              String transformedGeometry) {
        List<PolygonMetrics> result = new ArrayList<>();

        //run for every 500 bivariative_axis sourceDtoList size = 500 & get correlationList
        List<Double> covariance = covarianceRateService.getPolygonCovarianceRateStatisticsBatch(transformedGeometry, sourceDtoList);
        for (int i = 0; i < sourceDtoList.size(); i++) {
            PolygonMetrics metrics = new PolygonMetrics();

            ///combine them on Axis with quality, correlation & rate
            metrics.setX(Axis.builder()
                    .label(sourceDtoList.get(i).getXLabel())
                    .quotient(List.of(sourceDtoList.get(i).getXNumerator(), sourceDtoList.get(i).getXDenominator()))
                    .build());
            metrics.setY(Axis.builder()
                    .label(sourceDtoList.get(i).getYLabel())
                    .quotient(List.of(sourceDtoList.get(i).getYNumerator(), sourceDtoList.get(i).getYDenominator()))
                    .build());
            metrics.setQuality(sourceDtoList.get(i).getQuality());
            metrics.setMetrics(covariance.get(i));
            metrics.setRate(covariance.get(i));
            result.add(metrics);
        }
        return result;
    }

    private void fillAvgMetrics(List<PolygonMetrics> correlationRateList,
                                    List<Map<PureNumeratorDenominatorDto, Double>> avgCorrelationList) {
        var xAvgCorrelationMap = avgCorrelationList.get(0);
        var yAvgCorrelationMap = avgCorrelationList.get(1);
        for (PolygonMetrics currentRate : correlationRateList) {
            var xNum = currentRate.getX().getQuotient().get(0);
            var xDen = currentRate.getX().getQuotient().get(1);
            var yNum = currentRate.getY().getQuotient().get(0);
            var yDen = currentRate.getY().getQuotient().get(1);
            var numeratorDenominatorX = new PureNumeratorDenominatorDto(xNum, xDen);
            var numeratorDenominatorY = new PureNumeratorDenominatorDto(yNum, yDen);

            currentRate.setAvgMetricsX(xAvgCorrelationMap.get(numeratorDenominatorX));
            currentRate.setAvgMetricsY(yAvgCorrelationMap.get(numeratorDenominatorY));
        }
    }

    private Comparator<PolygonMetrics> covarianceRateComparator() {
        return Comparator.<PolygonMetrics, Double>comparing(c -> c.getAvgMetricsX() * c.getAvgMetricsY()).reversed();
    }
}
