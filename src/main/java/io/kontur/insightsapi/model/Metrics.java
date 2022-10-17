package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
public class Metrics implements Serializable {

    @Serial
    private static final long serialVersionUID = 3963476990861283822L;

    private Axis x;

    private Axis y;

    private Double rate;

    private Double quality;

    @Deprecated
    private Double correlation;

    @Deprecated
    private Double avgCorrelationX;

    @Deprecated
    private Double avgCorrelationY;

    private Double metrics;

    private Double avgMetricsX;

    private Double avgMetricsY;
}
