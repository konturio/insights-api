package io.kontur.insightsapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BivariateIndicatorDto {

    @JsonProperty(value = "id", required = true)
    @NotEmpty(message = "Id cannot be null or empty")
    @Deprecated
    private String id;

    @JsonProperty(value = "label", required = true)
    @NotEmpty(message = "Label cannot be null or empty")
    private String label;

    @JsonProperty(value = "copyrights")
    private List<String> copyrights;

    @JsonProperty(value = "direction", required = true)
    @NotEmpty(message = "List of directions cannot be null or empty")
    private List<List<String>> direction;

    @JsonProperty(value = "isBase", required = true, defaultValue = "false")
    @NotNull(message = "Incorrect type of is_base field, true/false expected.")
    private Boolean isBase = false;

    @JsonProperty(value = "uuid")
    private String externalId;

    @JsonIgnore
    private String internalId;

    @JsonProperty(value = "owner")
    private String owner;

    @JsonProperty(value = "state")
    private IndicatorState state;

    @JsonProperty(value = "isPublic", required = true, defaultValue = "false")
    @NotNull(message = "Incorrect type of is_public field, true/false expected.")
    private Boolean isPublic = false;

    @JsonProperty(value = "allowedUsers")
    private List<String> allowedUsers;

    @JsonProperty(value = "date")
    private OffsetDateTime date;

    @JsonProperty(value = "description")
    private String description;

    @JsonProperty(value = "coverage")
    private String coverage;

    @JsonProperty(value = "updateFrequency")
    private String updateFrequency;

    @JsonProperty(value = "application")
    private List<String> application;

    @JsonProperty(value = "unitId")
    private String unitId;

    @JsonProperty(value = "emoji")
    private String emoji;

    @JsonProperty(value = "lastUpdated")
    private OffsetDateTime lastUpdated;

    @JsonProperty(value = "downscale")
    @Pattern(regexp = "equal|proportional", message = "Downscale value must be 'equal' or 'proportional'")
    private String downscale;

    @JsonProperty(value = "hash")
    private String hash;

    @JsonIgnore
    private String uploadId;
}
