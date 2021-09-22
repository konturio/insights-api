package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.kontur.insightsapi.dto.StatisticDto;
import io.kontur.insightsapi.model.Analytics;
import io.kontur.insightsapi.model.InputGeometryType;
import io.kontur.insightsapi.model.Population;
import io.kontur.insightsapi.service.GeometryTransformer;
import io.kontur.insightsapi.service.Helper;
import io.kontur.insightsapi.service.PopulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PopulationResolver implements GraphQLResolver<Analytics> {

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final Helper helper;

    public Population getPopulation(Analytics analytics, DataFetchingEnvironment environment) throws JsonProcessingException {
        var polygon = helper.getPolygonFromRequest(environment);
        var transformedGeometry = geometryTransformer.transform(polygon);
        StatisticDto populationStatistic = populationService.calculatePopulation(transformedGeometry, InputGeometryType.GEOJSON);
        return Population.builder()
                .population(populationStatistic.getPopulation())
                .gdp(populationStatistic.getGdp())
                .urban(populationStatistic.getUrban())
                .build();
    }
}
