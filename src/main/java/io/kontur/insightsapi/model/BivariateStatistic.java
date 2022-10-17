package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BivariateStatistic extends BaseStatistic {

    private List<PolygonMetrics> correlationRates;

    private List<PolygonMetrics> covarianceRates;
}
