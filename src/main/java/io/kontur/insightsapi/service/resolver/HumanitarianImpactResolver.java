package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.kontur.insightsapi.model.Analytics;
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
public class HumanitarianImpactResolver implements GraphQLResolver<Analytics> {

    private final Logger logger = LoggerFactory.getLogger(HumanitarianImpactResolver.class);

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final ObjectMapper objectMapper;

    private final Helper helper;

    public CompletableFuture<String> getHumanitarianImpact(Analytics analytics, DataFetchingEnvironment environment) {
        return CompletableFuture.supplyAsync(() -> {
            var polygon = helper.getPolygonFromRequest(environment);
            String transformedGeometry = null;
            try {
                transformedGeometry = geometryTransformer.transformToWkt(polygon);
                var impactDtos = populationService.calculateHumanitarianImpact(transformedGeometry);
                var collection = populationService.convertImpactIntoFeatureCollection(transformedGeometry, impactDtos);
                return objectMapper.writeValueAsString(collection);
            } catch (JsonProcessingException e) {
                logger.error("Exception in geojson transformation, humanitarian impact calculation", e);
                return null;
            }
        });
    }
}
