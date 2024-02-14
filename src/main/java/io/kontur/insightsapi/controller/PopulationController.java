package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.dto.*;
import io.kontur.insightsapi.service.PopulationTransformer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.wololo.geojson.FeatureCollection;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Tag(name = "Population", description = "Population API")
@RestController
@RequestMapping("/population")
@Deprecated
public class PopulationController {

    private final Logger logger;

    private final PopulationTransformer populationTransformer;

    private final WKTReader reader;

    public PopulationController(PopulationTransformer populationTransformer) {
        this.populationTransformer = populationTransformer;
        this.logger = LoggerFactory.getLogger(PopulationController.class);
        this.reader = new WKTReader();
    }

    @Operation(summary = "Calculate population statistic in specified polygon.",
            tags = {"Population"},
            description = "Calculate population statistic in specified polygon.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping
    public StatisticDto calculatePopulation(@Parameter(description = "Polygon in WKT format to calculate population statistic.") @RequestBody RequestInfo info) {
        if (info == null || StringUtils.isBlank(info.getPolygon())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty input geometry");
        }
        try {
            reader.read(info.getPolygon());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad input geometry");
        }
        try {
            ZonedDateTime start = ZonedDateTime.now(ZoneId.of("UTC"));
            logger.debug("Start time: {}", start.toString());
            StatisticDto statistic = populationTransformer.calculatePopulation(info.getPolygon());
            ZonedDateTime end = ZonedDateTime.now(ZoneId.of("UTC"));
            logger.debug("End time: {}. Duration: {} ms", end.toString(), (end.toInstant().toEpochMilli() - start.toInstant().toEpochMilli()));
            return statistic;
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }
    }

    @Operation(summary = "Focus on humanitarian impact of disaster.",
            tags = {"Population"},
            description = "Focus on humanitarian impact of disaster.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping("/humanitarian_impact")
    public FeatureCollection focusOnHumanitarianImpact(@Parameter(description = "Geometries in WKT format.") @RequestBody String wkt) {
        if (StringUtils.isBlank(wkt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty input geometry");
        }
        try {
            reader.read(wkt);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad input geometry");
        }
        List<HumanitarianImpactDto> impactDtos = populationTransformer.calculateHumanitarianImpact(wkt);
        return populationTransformer.convertImpactIntoFeatureCollection(wkt, impactDtos);
    }

    @Operation(summary = "Calculate population statistic in several geometries.",
            tags = {"Population"},
            description = "Calculate population statistic in several geometries.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping("/several")
    @Async
    public CompletableFuture<List<SeveralPolygonsCalculationOutputDto>> calculateSeveralPopulation(@Parameter(description = "The polygons in WKT format to calculate population statistic.")
                                                                                                   @RequestBody List<SeveralPolygonsCalculationInputDto> data) {
        logger.info("Received data for processing: {}", data.stream()
                .map(dto -> String.format("Id: %s, Geometry: %s", dto.getId(), dto.getGeometry()))
                .collect(Collectors.joining("; ")));
        if (CollectionUtils.isEmpty(data)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty input geometry");
        }
        Date start = new Date();
        logger.debug("Start time: {}", start.toString());

        List<SeveralPolygonsCalculationOutputDto> result = new ArrayList<>();
        data.forEach(dto -> {
            try {
                reader.read(dto.getGeometry());
            } catch (ParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty input geometry");
            }
            SeveralPolygonsCalculationOutputDto outputDTO = new SeveralPolygonsCalculationOutputDto();
            outputDTO.setId(dto.getId());
            outputDTO.setStatistic(populationTransformer.calculatePopulation(dto.getGeometry()));
            result.add(outputDTO);
        });
        Date end = new Date();
        logger.debug("End time: {}. Duration: {} ms", end.toString(), (end.getTime() - start.getTime()));
        logger.info("Result after processing: {}", result.stream()
                .map(r -> String.format("Id: %s, Population: %s, Urban: %s, GDP: %s",
                        r.getId(),
                        r.getStatistic().getPopulation(),
                        r.getStatistic().getUrban(),
                        r.getStatistic().getGdp()))
                .collect(Collectors.joining("; ")));
        return CompletableFuture.completedFuture(result);
    }

}
