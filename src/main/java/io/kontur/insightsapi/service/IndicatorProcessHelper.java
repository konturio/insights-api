package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class IndicatorProcessHelper {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorProcessHelper.class);

    private final IndicatorService indicatorService;

    private final AxisService axisService;

    private static final int UUID_STRING_LENGTH = 36;

    private static final int CORE_POOL_SIZE = 10;

    private static final int MAX_POOL_SIZE = 20;

    private static final int MAX_QUEUE_SIZE = 200;

    private static final ThreadPoolExecutor executor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
            60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE));

    public ResponseEntity<String> processIndicator(HttpServletRequest request) {

        final ResponseEntity<String> response = indicatorService.uploadIndicatorData(request);

        executor.submit(() -> {
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                    && response.getBody().length() == UUID_STRING_LENGTH) {
                String uuid = response.getBody();
                List<BivariateIndicatorDto> incomingBivariateIndicatorDtoAsList =
                        List.of(indicatorService.getIndicatorByUuid(uuid));

                logger.info("Start calculations for indicator with uuid {}", uuid);
                long calculationStartTime = System.currentTimeMillis();

                axisService.createAxis(incomingBivariateIndicatorDtoAsList);

                long calculationEndTime = System.currentTimeMillis();
                long calculationTimeInSeconds = (calculationEndTime - calculationStartTime) / 1000;
                logger.info("Calculations for indicator with uuid {} have been done successfully and took {}", uuid,
                        String.format("%02d hours %02d minutes %02d seconds", calculationTimeInSeconds / 3600,
                                (calculationTimeInSeconds % 3600) / 60, (calculationTimeInSeconds % 60)));
                indicatorService.updateIndicatorState(uuid, IndicatorState.READY);
            }
        });
        logger.info("Current queue size with indicators to process: {}", executor.getQueue().size());

        return response;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
