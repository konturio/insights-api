package io.kontur.insightsapi.controller;

import io.kontur.insightsapi.service.cacheable.CacheCleanUpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Cache", description = "Cache API")
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CacheCleanUpService cacheCleanUpService;

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
        cacheCleanUpService.cleanUpIfRequired();
    }
}
