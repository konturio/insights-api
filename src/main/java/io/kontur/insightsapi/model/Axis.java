package io.kontur.insightsapi.model;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Axis implements Cloneable, Serializable {

    @Serial
    private static final long serialVersionUID = 7975909078058567603L;

    private String label;

    private List<Step> steps;

    private Double quality;

    private List<String> quotient;

    private List<Quotient> quotients;

    private List<String> parent;

    @Override
    public Axis clone() {
        Axis result = new Axis();
        try {
            result = (Axis) super.clone();
        } catch (CloneNotSupportedException e) {
            result.setQuality(this.quality);
            result.setSteps(this.steps);
            result.setQuotient(this.quotient);
            result.setQuotients(this.quotients);
            result.setLabel(this.label);
            result.setParent(this.parent);
        }
        return result;
    }
}
