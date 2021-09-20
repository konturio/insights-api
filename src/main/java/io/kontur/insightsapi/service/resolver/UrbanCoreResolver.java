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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UrbanCoreResolver implements GraphQLResolver<Analytics> {

    private final Logger logger = LoggerFactory.getLogger(UrbanCoreResolver.class);

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final Helper helper;

    public CompletableFuture<UrbanCore> getUrbanCore(Analytics analytics, DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> {
            var polygon = helper.getPolygonFromRequest(environment);
            String transformedGeometry = null;
            try {
                transformedGeometry = geometryTransformer.transformToWkt(polygon);
            } catch (JsonProcessingException e) {
                logger.error("Exception in geojson transformation, urban core statistic calculation", e);
            }
            var fieldList = environment.getSelectionSet().getFields().stream()
                    .map(SelectedField::getQualifiedName)
                    .collect(Collectors.toList());
            return populationService.calculateUrbanCore(transformedGeometry, fieldList);
        });
    }
}
