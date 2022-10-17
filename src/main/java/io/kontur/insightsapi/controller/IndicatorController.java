package io.kontur.insightsapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.service.IndicatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.annotation.MultipartConfig;
import javax.validation.Valid;

@Tag(name = "Indicators", description = "Indicators API")
@RestController
@RequestMapping("/indicators")
@MultipartConfig
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE})
    public String createIndicator(@RequestBody @Valid BivariateIndicatorDto bivariateIndicatorDto) throws JsonProcessingException{
        return indicatorService.createIndicator(bivariateIndicatorDto);
    }
}
