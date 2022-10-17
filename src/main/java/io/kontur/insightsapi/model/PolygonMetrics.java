package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
public class PolygonMetrics extends Metrics implements Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 6508982706284997962L;

    @Override
    public PolygonMetrics clone() {
        PolygonMetrics result = new PolygonMetrics();
        try {
            result = (PolygonMetrics) super.clone();
        } catch (CloneNotSupportedException e) {
            result.setCorrelation(getCorrelation());
            result.setAvgCorrelationX(getAvgCorrelationX());
            result.setAvgCorrelationY(getAvgCorrelationY());
            result.setMetrics(getMetrics());
            result.setAvgMetricsX(getAvgMetricsX());
            result.setAvgMetricsY(getAvgMetricsY());
            result.setRate(getRate());
            result.setQuality(getQuality());
        }
        result.setX(getX().clone());
        result.setY(getY().clone());
        return result;
    }
}
