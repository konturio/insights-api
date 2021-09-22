package io.kontur.insightsapi.service.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.kickstart.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import io.kontur.insightsapi.model.Analytics;
import io.kontur.insightsapi.model.InputGeometryType;
import io.kontur.insightsapi.service.GeometryTransformer;
import io.kontur.insightsapi.service.Helper;
import io.kontur.insightsapi.service.PopulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HumanitarianImpactResolver implements GraphQLResolver<Analytics> {

    private final PopulationService populationService;

    private final GeometryTransformer geometryTransformer;

    private final ObjectMapper objectMapper;

    private final Helper helper;

    public String getHumanitarianImpact(Analytics analytics, DataFetchingEnvironment environment) throws JsonProcessingException {
        var polygon = helper.getPolygonFromRequest(environment);
        var transformedGeometry = geometryTransformer.transform(polygon);
        var impactDtos = populationService.calculateHumanitarianImpact(transformedGeometry, InputGeometryType.GEOJSON);
        var collection = populationService.convertImpactIntoFeatureCollection(transformedGeometry, impactDtos);
        return objectMapper.writeValueAsString(collection);
    }
}
