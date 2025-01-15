package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.service.AxisService;
import io.kontur.insightsapi.dto.AxisOverridesRequest;
import io.kontur.insightsapi.dto.PresetDto;
import io.kontur.insightsapi.service.IndicatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.BindingResult;
import org.springframework.dao.DataIntegrityViolationException;

import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;

@Tag(name = "Indicators", description = "Indicators API")
@RestController
@RequestMapping("/indicators")
@MultipartConfig
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('uploadIndicators')")
public class IndicatorController {

    private final IndicatorService indicatorService;

    private final AxisService axisService;

    @Operation(
            summary = "Create indicator.",
            tags = {"Indicators"},
            description = "Upload data representing h3Index - indicatorValue pairs in the form of CSV file " +
                    "(no header) alongside indicator metadata. After data has been successfully uploaded, " +
                    "the response with indicator unique identifier (uuid) is returned and calculations for " +
                    "indicator start in the background." +

                    "<br><br>Currently files can't be uploaded via Swagger due to endpoint " +
                    "implementation specifics." +

                    "<br><br>Curl general example: curl -w \":::\"%{http_code} --location --request POST " +
                    "https://apps.kontur.io/insights-api/indicators/upload " +
                    "--header 'Authorization: <ACCESS_TOKEN> " +
                    "--form 'parameters=\"{\\\"id\\\": ${layer_id}, \\\"label\\\": ${layer_label}, " +
                    "\\\"direction\\\": ${layer_direction}, \\\"isBase\\\": ${layer_isbase}, \\\"isPublic\\\": " +
                    "${layer_ispublic}, \\\"copyrights\\\": ${layer_copyrights}, \\\"description\\\": " +
                    "${layer_description}, \\\"coverage\\\": ${layer_coverage}, \\\"updateFrequency\\\": " +
                    "${layer_update_freq}, \\\"unitId\\\": ${layer_unit_id}, \\\"emoji\\\": ${emoji}, " +
                    "\\\"lastUpdated\\\": ${layer_last_updated}, \\\"downscale\\\": ${downscaleMethod}}\" " +
                    "--form 'file=@\"/path/to/file/indicator.csv\"'" +

                    "<br><br>Curl example with parameters: curl -w \":::\"%{http_code} --location --request POST " +
                    "https://apps.kontur.io/insights-api/indicators/upload --header " +
                    "'Authorization: Bearer <ACCESS_TOKEN>' --form 'parameters={\"id\": \"area_km2\", \"label\": " +
                    "\"Area\", \"direction\": [[\"neutral\"], [\"neutral\"]], \"isBase\": true, \"isPublic\": false, " +
                    "\"copyrights\": [\"Concept of areas © Brahmagupta, René Descartes\"], \"description\": \"\", " +
                    "\"coverage\": \"World\", \"updateFrequency\": \"static\", \"unitId\": \"km2\", \"emoji\": \"📐\", "+
                    "\"lastUpdated\": \"\", \"downscale\": \"equal\"}' " +
                    "--form 'file=@\"data/area_km2.csv\"'",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful upload"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<String> createIndicator(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Service is temporarily unavailable due to maintenance");
        //return indicatorService.uploadIndicatorData(request, false);
    }

    @Operation(
            summary = "Update data about specific indicator.",
            tags = {"Indicators"},
            description = "Upload data representing h3Index - indicatorValue pairs in the form of CSV file " +
                    "(no header) alongside indicator metadata. After data has been successfully uploaded, " +
                    "the response with indicator unique identifier (uuid) is returned and calculations for " +
                    "indicator start in the background." +

                    "<br><br>Currently files can't be uploaded via Swagger due to endpoint " +
                    "implementation specifics." +

                    "<br><br>Curl general example: curl -w \":::\"%{http_code} --location --request PUT " +
                    "https://apps.kontur.io/insights-api/indicators/upload " +
                    "--header 'Authorization: <ACCESS_TOKEN> " +
                    "--form 'parameters=\"{\\\"id\\\": ${layer_id}, \\\"label\\\": ${layer_label}, \\\"uuid\\\": ${uuid}, " +
                    "\\\"direction\\\": ${layer_direction}, \\\"isBase\\\": ${layer_isbase}, \\\"isPublic\\\": " +
                    "${layer_ispublic}, \\\"copyrights\\\": ${layer_copyrights}, \\\"description\\\": " +
                    "${layer_description}, \\\"coverage\\\": ${layer_coverage}, \\\"updateFrequency\\\": " +
                    "${layer_update_freq}, \\\"unitId\\\": ${layer_unit_id}, \\\"emoji\\\": ${emoji}, " +
                    "\\\"lastUpdated\\\": ${layer_last_updated}, \\\"downscale\\\": ${downscaleMethod}}\" " +
                    "--form 'file=@\"/path/to/file/indicator.csv\"'" +

                    "<br><br>Curl example with parameters: curl -w \":::\"%{http_code} --location --request PUT " +
                    "https://apps.kontur.io/insights-api/indicators/upload --header " +
                    "'Authorization: Bearer <ACCESS_TOKEN>' --form 'parameters={\"id\": \"area_km2\", \"label\": " +
                    "\"Area\", \"uuid\": \"7efd9ba2-e7de-44b9-8140-26c89e8170d7\", \"direction\": [[\"neutral\"], [\"neutral\"]], \"isBase\": true, \"isPublic\": false, " +
                    "\"copyrights\": [\"Concept of areas © Brahmagupta, René Descartes\"], \"description\": \"\", " +
                    "\"coverage\": \"World\", \"updateFrequency\": \"static\", \"unitId\": \"km2\", \"emoji\": \"📐\", "+
                    "\"lastUpdated\": \"\", \"downscale\": \"equal\"}' " +
                    "--form 'file=@\"data/area_km2.csv\"'",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful upload"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "404", description = "Not Found"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PutMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<String> updateIndicator(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body("Service is temporarily unavailable due to maintenance");
        //return indicatorService.uploadIndicatorData(request, true);
    }

    @Operation(
            summary = "Get indicator upload status",
            tags = {"Indicators"},
            description = "Get indicator upload status. Owner is obtained from the token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping("/upload/status/{uploadId}")
    public ResponseEntity<String> getIndicatorUploadStatus(@PathVariable String uploadId) {
        return indicatorService.getIndicatorUploadStatus(uploadId);
    }

    @Operation(
            summary = "Get indicators metadata by owner.",
            tags = {"Indicators"},
            description = "Get indicators metadata by owner. Owner is obtained from the token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping()
    public ResponseEntity<List<BivariateIndicatorDto>> getIndicatorsByOwner() {
        return indicatorService.getIndicatorsByOwnerAndParamId(null);
    }

    // TODO: later ID will be expected as external_id (UUID) not param_id
    @Operation(
            summary = "Get indicators metadata by owner and ID.",
            tags = {"Indicators"},
            description = "Get indicators metadata by owner and ID. Owner is obtained from the token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping("/{id}")
    public ResponseEntity<List<BivariateIndicatorDto>> getIndicatorsByOwner(@PathVariable String id) {
        return indicatorService.getIndicatorsByOwnerAndParamId(id);
    }

    @Operation(
            summary = "Create or update custom labels and stops for bivariate axis.",
            tags = {"Indicators"},
            description = "Provided numerator and denominator UUIDs should exist as bivariate indicators for current user. " +
                     "Accepts overrides for the following params:<br>" +
                     "label, min, p25, p75, max, min_label, p25_label, p75_label, max_label",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping(value = "/axis/custom")
    public ResponseEntity<String> uploadLabels(@Valid @RequestBody AxisOverridesRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body("Validation error: " + bindingResult.getFieldError().getDefaultMessage());
        }
        try {
            axisService.insertOverrides(request);
        } catch (DataIntegrityViolationException e) {
            // catch 'invalid syntax for type...' to hide plain SQL in error response
            return ResponseEntity.badRequest().body("Invalid input data format");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok().body("");
    }

    @Operation(
            summary = "Create or update bivariate presets.",
            tags = {"Indicators"},
            description = """
                Create bivariate presets by providing UUIDs for numerator/denominator pairs. Indicator UUIDs should exist for current user. x_numerator_id/x_denominator_id is vertical axis, y_numerator/y_denominator is horizontal one.
                The "colors" field accepts escaped-json string for bivariate legend with 9 cells, example value:
                "[{\\"id\\":\\"A1\\",\\"color\\":\\"rgb(232,232,157)\\"},{\\"id\\":\\"A2\\",\\"color\\":\\"rgb(239,163,127)\\"},{\\"id\\":\\"A3\\",\\"color\\":\\"rgb(228,26,28)\\"},{\\"id\\":\\"B1\\",\\"color\\":\\"rgb(186,226,153)\\"},{\\"id\\":\\"B2\\",\\"color\\":\\"rgb(161,173,88)\\"},{\\"id\\":\\"B3\\",\\"color\\":\\"rgb(191,108,63)\\"},{\\"id\\":\\"C1\\",\\"color\\":\\"rgb(90,200,127)\\"},{\\"id\\":\\"C2\\",\\"color\\":\\"rgb(112,186,128)\\"},{\\"id\\":\\"C3\\",\\"color\\":\\"rgb(83,152,106)\\"}]"
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Success"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping(value = "/axis/preset")
    public ResponseEntity<String> uploadPreset(@Valid @RequestBody PresetDto request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body("Validation error: " + bindingResult.getFieldError().getDefaultMessage());
        }
        try {
            axisService.insertPreset(request);
        } catch (DataIntegrityViolationException e) {
            // catch 'invalid syntax for type...' to hide plain SQL in error response
            return ResponseEntity.badRequest().body("Invalid input data format");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok().body("");
    }
}
