package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Statistic extends BaseStatistic {

    private List<Metrics> correlationRates;

    private List<Metrics> covarianceRates;
}
