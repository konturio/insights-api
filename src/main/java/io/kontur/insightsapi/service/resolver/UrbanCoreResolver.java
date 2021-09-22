package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.SelectedField;
import io.kontur.insightsapi.model.Analytics;
import io.kontur.insightsapi.model.UrbanCore;
import io.kontur.insightsapi.service.GeometryTransformer;
import io.kontur.insightsapi.service.Helper;
import io.kontur.insightsapi.service.PopulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UrbanCoreResolver implements GraphQLResolver<Analytics> {

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final Helper helper;

    public UrbanCore getUrbanCore(Analytics analytics, DataFetchingEnvironment environment) throws JsonProcessingException {
        var polygon = helper.getPolygonFromRequest(environment);
        var transformedGeometry = geometryTransformer.transform(polygon);
        var fieldList = environment.getSelectionSet().getFields().stream()
                .map(SelectedField::getQualifiedName)
                .collect(Collectors.toList());
        return populationService.calculateUrbanCore(transformedGeometry, fieldList);
    }
}
