package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.service.TileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Tag(name = "Tiles", description = "Tiles API")
@RestController
@RequestMapping("/tiles")
@RequiredArgsConstructor
public class TileController {

    private final Logger log = LoggerFactory.getLogger(TileController.class);

    private final TileService tileService;

    @Operation(summary = "Get bivariate mvt tile using z, x, y and indicator class.",
            tags = {"Tiles"},
            description = "Get bivariate mvt tile using z, x, y and indicator class.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/vnd.mapbox-vector-tile")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping(value = "/bivariate/v1/{z}/{x}/{y}.mvt", produces = "application/vnd.mapbox-vector-tile")
    public ResponseEntity<byte[]> getBivariateTileMvt(@PathVariable Integer z, @PathVariable Integer x,
                                                      @PathVariable Integer y,
                                                      @RequestParam(defaultValue = "all") String indicatorsClass,
                                                      WebRequest request) {
        if (z < 0 || z > 8 || x < 0 || x > (Math.pow(2, z) - 1) || y < 0 || y > (Math.pow(2, z) - 1)) {
            return ResponseEntity.ok()
                    .body(new byte[0]);
        }
//        if (request.checkNotModified(tileService.calculateEtagValue())) {
//            return null;
//        }
        log.info("Cache expired. Refreshing tiles");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                //.header("Expires", "")
                //.eTag(tileService.calculateEtagValue())
                .body(tileService.getBivariateTileMvt(z, x, y, indicatorsClass));
    }

    @Operation(summary = "Get bivariate mvt tile using z, x, y and list of indicator.",
            tags = {"Tiles"},
            description = "Get bivariate mvt tile using z, x, y and list of indicator.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/vnd.mapbox-vector-tile")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping(value = "/bivariate/v2/{z}/{x}/{y}.mvt", produces = "application/vnd.mapbox-vector-tile")
    public byte[] getBivariateTileMvtV2(@PathVariable Integer z, @PathVariable Integer x, @PathVariable Integer y,
                                        @RequestParam(required = false) List<String> indicatorsList) {
        if (z < 0 || z > 8 || x < 0 || x > (Math.pow(2, z) - 1) || y < 0 || y > (Math.pow(2, z) - 1)) {
            return new byte[0];
        }
        return tileService.getBivariateTileMvtIndicatorsList(z, x, y, indicatorsList);
    }
}
