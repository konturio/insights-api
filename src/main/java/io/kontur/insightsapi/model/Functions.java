package io.kontur.insightsapi.model;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Functions {

    private Long population;

    private BigDecimal settledArea;

    private BigDecimal osmGaps;

    private Long peopleWithoutOsmObjects;

    private BigDecimal settledAreaWithoutOsmObjects;

    private BigDecimal settledAreaWithoutOsmBuildingsPercent;

    private Long peopleWithoutOsmBuildings;

    private BigDecimal settledAreaWithoutOsmBuildings;

    private BigDecimal settledAreaWithoutOsmRoadsPercent;

    private Long peopleWithoutOsmRoads;

    private BigDecimal settledAreaWithoutOsmRoads;
}
