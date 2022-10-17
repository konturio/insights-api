package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class Quotient implements Serializable {

    @Serial
    private static final long serialVersionUID = -3246495160942760522L;

    private String name;
    private String label;
    private List<List<String>> direction;
}
