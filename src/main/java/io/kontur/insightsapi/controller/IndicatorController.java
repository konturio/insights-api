package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.service.IndicatorProcessHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAuthority('uploadIndicators')")
public class IndicatorController {

    private final IndicatorProcessHelper indicatorProcessHelper;

    @Operation(
            summary = "Create or update data about specific indicator.",
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
                    "${layer_update_freq}, \\\"unitId\\\": ${layer_unit_id}, \\\"lastUpdated\\\": " +
                    "${layer_last_updated}}\" " +
                    "--form 'file=@\"/path/to/file/indicator.csv\"'" +

                    "<br><br>Curl example with parameters: curl -w \":::\"%{http_code} --location --request POST " +
                    "https://apps.kontur.io/insights-api/indicators/upload --header " +
                    "'Authorization: Bearer <ACCESS_TOKEN>' --form 'parameters={\"id\": \"area_km2\", \"label\": " +
                    "\"Area\", \"direction\": [[\"neutral\"], [\"neutral\"]], \"isBase\": true, \"isPublic\": false, " +
                    "\"copyrights\": [\"Concept of areas © Brahmagupta, René Descartes\"], \"description\": \"\", " +
                    "\"coverage\": \"World\", \"updateFrequency\": \"static\", \"unitId\": \"km2\", \"lastUpdated\": " +
                    "\"\"}' --form 'file=@\"data/area_km2.csv\"'",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful upload"),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @PostMapping(value = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<String> uploadIndicatorData(HttpServletRequest request) {
        return indicatorProcessHelper.processIndicator(request);
    }
}
