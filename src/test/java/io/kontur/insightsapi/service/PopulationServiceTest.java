package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.CalculatePopulationDto;
import io.kontur.insightsapi.dto.StatisticDto;
import io.kontur.insightsapi.model.InputGeometryType;
import io.kontur.insightsapi.repository.PopulationRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PopulationServiceTest {

    private static final String POPULATION_QUERY = "POLYGON((0 0,0 5,5 5,5 0,0 0))";

    @Test
    void calculatePopulation() {
        PopulationRepository populationRepository = mock(PopulationRepository.class);
        PopulationService populationService = new PopulationService(populationRepository);
        when(populationRepository.getPopulationAndGdp(POPULATION_QUERY, InputGeometryType.WKT)).thenReturn(getPopulation());
        when(populationRepository.getArea(POPULATION_QUERY, InputGeometryType.WKT)).thenReturn(BigDecimal.ONE);

        StatisticDto statistic = populationService.calculatePopulation(POPULATION_QUERY, InputGeometryType.WKT);
        Assertions.assertNotNull(statistic, "Population statistic is not received");
        Map<String, CalculatePopulationDto> population = getPopulation();

        Assertions.assertEquals(population.values().iterator().next().getPopulation(), statistic.getPopulation());
        Assertions.assertEquals(population.values().iterator().next().getUrban(), statistic.getUrban());
        Assertions.assertEquals(population.values().iterator().next().getGdp(), statistic.getGdp());

    }

    private Map<String, CalculatePopulationDto> getPopulation() {
        Map<String, CalculatePopulationDto> populationMap = new HashMap<>();
        CalculatePopulationDto dto = new CalculatePopulationDto();
        dto.setPopulation(BigDecimal.valueOf(1000));
        dto.setUrban(BigDecimal.valueOf(500));
        dto.setGdp(BigDecimal.valueOf(10000));
        dto.setType("population");
        populationMap.put(dto.getType(), dto);
        return populationMap;
    }
}