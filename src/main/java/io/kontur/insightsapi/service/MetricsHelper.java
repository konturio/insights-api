package io.kontur.insightsapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.Lists;
import io.kontur.insightsapi.dto.ForAvgCorrelationDto;
import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.dto.PureNumeratorDenominatorDto;
import io.kontur.insightsapi.model.PolygonMetrics;
import io.kontur.insightsapi.service.cacheable.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetricsHelper {

    private final GeometryTransformer geometryTransformer;

    private final MetricsService metricsService;

    public List<NumeratorsDenominatorsDto> getNumeratorsDenominatorsForNotEmptyLayers(Map<String, Object> arguments, String transformedGeometry){
        List<NumeratorsDenominatorsDto> numeratorsDenominatorsDtos = metricsService.getNumeratorsDenominatorsForMetrics()
                .stream()
                .sorted(Comparator.comparing(NumeratorsDenominatorsDto::getXLabel)
                        .thenComparing(NumeratorsDenominatorsDto::getYLabel)
                        .thenComparing(NumeratorsDenominatorsDto::getXNumerator)
                        .thenComparing(NumeratorsDenominatorsDto::getXDenominator)
                        .thenComparing(NumeratorsDenominatorsDto::getYNumerator)
                        .thenComparing(NumeratorsDenominatorsDto::getYDenominator))
                .collect(Collectors.toList());

        String finalTransformedGeometry = transformedGeometry;

        //find numerators for not empty layers
        return Lists.partition(numeratorsDenominatorsDtos, 500).parallelStream()
                        .map(sourceDtoList -> findNumeratorsDenominatorsForNotEmptyLayers(sourceDtoList, finalTransformedGeometry))
                        .flatMap(Collection::stream)
                        .sorted(Comparator.comparing(NumeratorsDenominatorsDto::getXLabel)
                                .thenComparing(NumeratorsDenominatorsDto::getYLabel)
                                .thenComparing(NumeratorsDenominatorsDto::getXNumerator)
                                .thenComparing(NumeratorsDenominatorsDto::getXDenominator)
                                .thenComparing(NumeratorsDenominatorsDto::getYNumerator)
                                .thenComparing(NumeratorsDenominatorsDto::getYDenominator))
                        .collect(Collectors.toList());

    }

    public String getPolygon(Map<String, Object> arguments) throws JsonProcessingException {
        if (arguments.containsKey("polygon")) {
            return geometryTransformer.transform(arguments.get("polygon").toString(), false);
        }
        if (arguments.containsKey("polygonV2")) {
            return geometryTransformer.transform(arguments.get("polygonV2").toString(), false);
        }
        return null;
    }

    public List<Map<PureNumeratorDenominatorDto, Double>> calculateAvgMetricsXY(List<PolygonMetrics> correlationRateList) {
        Map<PureNumeratorDenominatorDto, ForAvgCorrelationDto> xMap = new HashMap<>();
        Map<PureNumeratorDenominatorDto, ForAvgCorrelationDto> yMap = new HashMap<>();
        for (PolygonMetrics currentRate : correlationRateList) {
            var xNum = currentRate.getX().getQuotient().get(0);
            var xDen = currentRate.getX().getQuotient().get(1);
            var yNum = currentRate.getY().getQuotient().get(0);
            var yDen = currentRate.getY().getQuotient().get(1);
            var numeratorDenominatorX = new PureNumeratorDenominatorDto(xNum, xDen);
            var numeratorDenominatorY = new PureNumeratorDenominatorDto(yNum, yDen);

            calculateDataForAvgCorrelation(numeratorDenominatorX, currentRate, xMap);
            calculateDataForAvgCorrelation(numeratorDenominatorY, currentRate, yMap);
        }

        Map<PureNumeratorDenominatorDto, Double> xMapResult = xMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getSum() / entry.getValue().getNumber()));

        Map<PureNumeratorDenominatorDto, Double> yMapResult = yMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().getSum() / entry.getValue().getNumber()));

        return List.of(xMapResult, yMapResult);
    }

    private void calculateDataForAvgCorrelation(PureNumeratorDenominatorDto numeratorDenominator,
                                                PolygonMetrics currentRate,
                                                Map<PureNumeratorDenominatorDto, ForAvgCorrelationDto> result) {
        if (!result.containsKey(numeratorDenominator)) {
            result.put(numeratorDenominator, new ForAvgCorrelationDto(0.0, 0));
        }
        var forAvgCorrelationDto = result.get(numeratorDenominator);
        forAvgCorrelationDto.setSum(forAvgCorrelationDto.getSum() + Math.abs(currentRate.getMetrics()));
        forAvgCorrelationDto.setNumber(forAvgCorrelationDto.getNumber() + 1);
    }

    private List<NumeratorsDenominatorsDto> findNumeratorsDenominatorsForNotEmptyLayers(List<NumeratorsDenominatorsDto> numeratorsDenominatorsDtos,
                                                                                        String transformedGeometry) {
        Map<String, Boolean> numeratorsForNotEmptyLayers =
                metricsService.getNumeratorsForNotEmptyLayersBatch(transformedGeometry, numeratorsDenominatorsDtos);
        return numeratorsDenominatorsDtos.stream()
                .filter(dto -> (numeratorsForNotEmptyLayers.containsKey(dto.getXNumerator())
                        && numeratorsForNotEmptyLayers.get(dto.getXNumerator()))
                        && (numeratorsForNotEmptyLayers.containsKey(dto.getYNumerator())
                        && numeratorsForNotEmptyLayers.get(dto.getYNumerator())))
                .collect(Collectors.toList());
    }
}
