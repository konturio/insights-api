package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.repository.IndicatorRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@AllArgsConstructor
public class IndicatorProcessHelper {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorProcessHelper.class);

    private final IndicatorService indicatorService;

    private final AxisService axisService;

    private final IndicatorRepository indicatorRepository;

    private static final int UUID_STRING_LENGTH = 36;

    public ResponseEntity<String> processIndicator(HttpServletRequest request) {

        final ResponseEntity<String> response = indicatorService.uploadIndicatorData(request);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(() -> {
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().length() >= UUID_STRING_LENGTH) {
                String uuid = response.getBody().substring(response.getBody().length() - UUID_STRING_LENGTH);
                List<BivariateIndicatorDto> incomingBivariateIndicatorDtoAsList =
                        List.of(indicatorRepository.getIndicatorByUuid(uuid));

                axisService.createAxis(incomingBivariateIndicatorDtoAsList);

                logger.info("Calculations for indicator with uuid {} have been done successfully", uuid);
                indicatorRepository.updateIndicatorState(uuid, "READY");
            }
        });
        executorService.shutdown();

        return response;
    }
}
