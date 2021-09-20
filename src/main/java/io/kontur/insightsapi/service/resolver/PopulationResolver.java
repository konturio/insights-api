package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.kontur.insightsapi.dto.StatisticDto;
import io.kontur.insightsapi.model.Analytics;
import io.kontur.insightsapi.model.Population;
import io.kontur.insightsapi.service.GeometryTransformer;
import io.kontur.insightsapi.service.Helper;
import io.kontur.insightsapi.service.PopulationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class PopulationResolver implements GraphQLResolver<Analytics> {

    private final Logger logger = LoggerFactory.getLogger(PopulationResolver.class);

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final Helper helper;

    public CompletableFuture<Population> getPopulation(Analytics analytics, DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> {
            var polygon = helper.getPolygonFromRequest(environment);
            String transformedGeometry = null;
            try {
                transformedGeometry = geometryTransformer.transformToWkt(polygon);
            } catch (JsonProcessingException e) {
                logger.error("Exception in geojson transformation, population statistic calculation", e);
            }
            StatisticDto populationStatistic = populationService.calculatePopulation(transformedGeometry);
            return Population.builder()
                    .population(populationStatistic.getPopulation())
                    .gdp(populationStatistic.getGdp())
                    .urban(populationStatistic.getUrban())
                    .build();
        });
    }
}
