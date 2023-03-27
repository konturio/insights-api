package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.service.IndicatorService;
import io.kontur.insightsapi.service.cacheable.CacheEvictable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Cache", description = "Cache API")
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {

    private final List<CacheEvictable> cacheEvictables;

    private final IndicatorService indicatorService;

    @Operation(summary = "Clean all caches.",
            tags = {"Cache"},
            description = "Clean all caches.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successful operation",
                            content = @Content(mediaType = "application/json")),
                    @ApiResponse(responseCode = "400", description = "Bad Request"),
                    @ApiResponse(responseCode = "500", description = "Internal error")})
    @GetMapping("/cleanUp")
    public void cleanCaches() {
        indicatorService.updateIndicatorsLastUpdateDate(Instant.now());

        cacheEvictables.forEach(CacheEvictable::evict);
    }
}
