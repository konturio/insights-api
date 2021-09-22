package io.kontur.insightsapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wololo.geojson.*;

import java.util.Arrays;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
public class GeometryTransformer {

    private final ObjectMapper objectMapper;

    public String transform(String geoJsonString) throws JsonProcessingException {
        var geoJSON = GeoJSONFactory.create(geoJsonString);
        var type = geoJSON.getType();
        switch (type) {
            case ("FeatureCollection"):
                return transformToGeometryCollection(geoJSON);
            case ("Feature"):
                return transformToGeometry(geoJSON);
            default:
                return geoJsonString;
        }
    }

    private String transformToGeometryCollection(GeoJSON geoJSON) throws JsonProcessingException {
        var featureCollection = (FeatureCollection) geoJSON;
        var geometries = Arrays.stream(featureCollection.getFeatures())
                .map(Feature::getGeometry)
                .collect(toList());
        var resultCollection = new GeometryCollection(geometries.toArray(Geometry[]::new));
        return objectMapper.writeValueAsString(resultCollection);
    }

    private String transformToGeometry(GeoJSON geoJSON) throws JsonProcessingException {
        var geometry = ((Feature) geoJSON).getGeometry();
        return objectMapper.writeValueAsString(geometry);
    }
}
