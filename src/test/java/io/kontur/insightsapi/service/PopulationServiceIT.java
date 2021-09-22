package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.model.InputGeometryType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PopulationServiceIT {

    private static final String POPULATION_QUERY = "POLYGON((0 0,0 5,5 5,5 0,0 0))";

    @Autowired
    private PopulationService populationService;

    @Test
    void calculatePopulationAndGdp() {
        Optional<Map<String, CalculatePopulationDto>> population = populationService.calculatePopulationAndGdp(POPULATION_QUERY, InputGeometryType.WKT);
        Assertions.assertTrue(population.isPresent(), "Population is not received");
        Assertions.assertTrue(population.get().size() >= 1);
        Assertions.assertEquals(BigDecimal.valueOf(700), population.get().values().iterator().next().getPopulation());
        Assertions.assertEquals(BigDecimal.valueOf(400), population.get().values().iterator().next().getUrban());
        Assertions.assertEquals(BigDecimal.valueOf(900000), population.get().values().iterator().next().getGdp());
        Assertions.assertEquals("population", population.get().values().iterator().next().getType());
    }

    @Test
    void calculateArea() {
        BigDecimal area = populationService.calculateArea(POPULATION_QUERY, InputGeometryType.WKT);
        Assertions.assertNotNull(area, "Area is not received");
        Assertions.assertTrue(area.compareTo(BigDecimal.ZERO) > 0);
    }
}