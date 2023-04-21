package io.kontur.insightsapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResultDto {

    private String tempTableName;

    private long numberOfUploadedRows;

    private String errorMessage;
}
