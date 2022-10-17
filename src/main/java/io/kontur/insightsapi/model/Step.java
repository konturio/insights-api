package io.kontur.insightsapi.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

@Getter
@Setter
public class Step implements Serializable {

    @Serial
    private static final long serialVersionUID = -8360936587315584271L;

    private String label;

    private Double value;
}
