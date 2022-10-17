package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.service.IndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;

@Tag(name = "Indicators", description = "Indicators API")
@RestController
@RequestMapping("/indicators")
@MultipartConfig
@RequiredArgsConstructor
public class IndicatorController {

    private final IndicatorService indicatorService;

    @Operation(summary = "Creates or updates data about specific indicator.",
            tags = {"Indicators"},
            description = "Uploads data from CSV file into stat_h3 table and calculates insights for indicator.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<String> uploadIndicatorData(HttpServletRequest request) {
        return indicatorService.uploadIndicatorData(request);
    }
}
