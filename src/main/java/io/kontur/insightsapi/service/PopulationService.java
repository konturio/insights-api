package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.dto.HumanitarianImpactDto;
import io.kontur.insightsapi.dto.StatisticDto;
import io.kontur.insightsapi.model.OsmQuality;
import io.kontur.insightsapi.model.UrbanCore;
import io.kontur.insightsapi.repository.PopulationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.wololo.geojson.Feature;
import org.wololo.geojson.FeatureCollection;
import org.wololo.geojson.Geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PopulationService {

    private final PopulationRepository populationRepository;

    private final Logger logger = LoggerFactory.getLogger(PopulationService.class);

    public Optional<Map<String, CalculatePopulationDto>> calculatePopulationAndGdp(String geometry) {
        Map<String, CalculatePopulationDto> population = populationRepository.getPopulationAndGdp(geometry);
        return Optional.ofNullable(population);
    }

    public BigDecimal calculateArea(String geometry) {
        return populationRepository.getArea(geometry);
    }

    public List<HumanitarianImpactDto> calculateHumanitarianImpact(String wkt) {
        return populationRepository.calculateHumanitarianImpact(wkt);
    }

    public StatisticDto calculatePopulation(String geometry) {
        if (StringUtils.isBlank(geometry)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty input geometry");
        }
        StatisticDto statistic = new StatisticDto();
        if (BigDecimal.ZERO.compareTo(calculateArea(geometry)) <= 0) {
            Optional<Map<String, CalculatePopulationDto>> populationStatistic = calculatePopulationAndGdp(geometry);
            if (populationStatistic.isEmpty()) {
                logger.warn("Population statistic was not found for geometry: {}", geometry);
                statistic.setPopulation(new BigDecimal(0));
                statistic.setUrban(new BigDecimal(0));
                statistic.setGdp(new BigDecimal(0));
            } else {
                BigDecimal population = populationStatistic.get().get("population").getPopulation();
                BigDecimal urban = populationStatistic.get().get("population").getUrban();
                BigDecimal gdp = populationStatistic.get().get("population").getGdp();
                statistic.setPopulation(population == null ? new BigDecimal(0) : population.setScale(0, RoundingMode.HALF_UP));
                statistic.setUrban(urban == null ? new BigDecimal(0) : urban.setScale(0, RoundingMode.HALF_UP));
                statistic.setGdp(gdp == null ? new BigDecimal(0) : gdp.setScale(0, RoundingMode.HALF_UP));
            }
        } else {
            statistic.setPopulation(new BigDecimal(0));
            statistic.setUrban(new BigDecimal(0));
            statistic.setGdp(new BigDecimal(0));
        }
        return statistic;
    }

    public FeatureCollection convertImpactIntoFeatureCollection(String wkt, List<HumanitarianImpactDto> impactDtos) {
        Feature[] features = impactDtos
                .stream()
                .map(impact -> {
                    if (impact.getGeometry() instanceof Geometry) {
                        final String id = impact.getName().toLowerCase().replace(" ", "_");
                        Map<String, Object> properties = Map.of("population", impact.getPopulation(),
                                "percentage", impact.getPercentage(),
                                "name", impact.getName(),
                                "id", id,
                                "totalAreaKm2", impact.getTotalAreaKm2(),
                                "totalPopulation", impact.getTotalPopulation(),
                                "areaKm2", impact.getAreaKm2());
                        return Optional.of(new Feature(id, (Geometry) impact.getGeometry(), properties));
                    } else {
                        logger.warn("Unexpected GeoJson object type {} for input {}", impact.getGeometry(), wkt);
                        return Optional.<Feature>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(Feature[]::new);
        return new FeatureCollection(features);
    }

    public OsmQuality calculateOsmQuality(String geojson, List<String> osmRequestFields){
        return populationRepository.calculateOsmQuality(geojson, osmRequestFields);
    }

    public UrbanCore calculateUrbanCore(String wkt, List<String> requestFields){
        return populationRepository.calculateUrbanCore(wkt, requestFields);
    }
}
