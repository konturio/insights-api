package io.kontur.insightsapi.service;

import io.kontur.insightsapi.repository.IndicatorRepository;
import io.kontur.insightsapi.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorServiceTest {

    @Mock
    private IndicatorRepository indicatorRepository;

    @Mock
    private AuthService authService;

    @Test
    void getIndicatorUploadStatusReturnsNotFoundWhenResultIsNull() {
        IndicatorService service = new IndicatorService(indicatorRepository, null, null, authService);
        String uploadId = "upload-id";
        when(authService.getCurrentUsername()).thenReturn(Optional.of("owner"));
        when(indicatorRepository.getIndicatorIdByUploadId("owner", uploadId)).thenReturn(null);

        ResponseEntity<String> response = service.getIndicatorUploadStatus(uploadId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "Expected NOT_FOUND when indicator upload status is absent for uploadId=" + uploadId);
        assertEquals("upload failed or uploadId invalid", response.getBody(),
                "Unexpected response body for absent indicator upload status");
    }
}
