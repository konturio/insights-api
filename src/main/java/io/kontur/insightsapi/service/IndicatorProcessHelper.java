package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.IndicatorState;
import io.kontur.insightsapi.exception.BivariateIndicatorsPRViolationException;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class IndicatorProcessHelper {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorProcessHelper.class);

    public static final String PARAM_NAME_UUID = "uuid";
    public static final String PARAM_NAME_PARAM_ID = "paramId";


    private final IndicatorService indicatorService;

    private final AxisService axisService;

    private static final int UUID_STRING_LENGTH = 36;

    private static final int CORE_POOL_SIZE = 10;

    private static final int MAX_POOL_SIZE = 20;

    private static final int MAX_QUEUE_SIZE = 200;

    private static final ThreadPoolExecutor calculationExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE,
            60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUE_SIZE));

    public ResponseEntity<String> processIndicator(HttpServletRequest request) {

        long uploadStartTime = System.currentTimeMillis();

        final ResponseEntity<String> response = indicatorService.uploadIndicatorData(request);

        long uploadEndTime = System.currentTimeMillis();
        long uploadTimeInSeconds = (uploadEndTime - uploadStartTime) / 1000;

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                && response.getBody().length() == UUID_STRING_LENGTH) {
            String uuid = response.getBody();

            logger.info("Upload of csv file for indicator with uuid {} has been done successfully and took {}", uuid,
                    String.format("%02d hours %02d minutes %02d seconds", uploadTimeInSeconds / 3600,
                            (uploadTimeInSeconds % 3600) / 60, (uploadTimeInSeconds % 60)));

            // TODO commented for test purpose
            // submitStopsAndQualityCalculation(PARAM_NAME_UUID, uuid);

        } else {
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Error while uploading indicator. Http status: {}", response.getStatusCode().value());
            } else if (response.getBody() == null || response.getBody().length() != UUID_STRING_LENGTH) {
                logger.error("Error while uploading indicator: {}",
                        response.getBody() != null
                                ? "wrong response format (should be uuid): " + response.getBody()
                                : "empty response (should be uuid)");
            }
        }
        logger.info("Current queue size with indicators to process: {}", calculationExecutor.getQueue().size());

        return response;
    }

    public ResponseEntity<String> submitStopsAndQualityCalculation(String id, String paramName) {
        calculationExecutor.submit(() -> {
            try {
                BivariateIndicatorDto incomingBivariateIndicatorDto = null;

                switch (paramName) {
                    case PARAM_NAME_UUID :
                        incomingBivariateIndicatorDto = indicatorService.getIndicatorByUuid(id);
                        break;
                    case PARAM_NAME_PARAM_ID:
                        incomingBivariateIndicatorDto = indicatorService.getIndicatorByParamIdAndOwner(id);
                        break;
                    default: logger.error("Unsupported parameter name for indicators");
                }

                if (incomingBivariateIndicatorDto != null) {
                    logger.info("Start calculations for indicator with {} {}", paramName, id);
                    OffsetDateTime calculationStartTime = OffsetDateTime.now();

                    axisService.createAxis(List.of(incomingBivariateIndicatorDto));

                    logger.info("Calculations for indicator with {} {} have been done successfully and took {}",
                            paramName, id, Duration.between(calculationStartTime, OffsetDateTime.now()));

                    if (StringUtils.isNoneBlank(incomingBivariateIndicatorDto.getUuid())) {
                        indicatorService.updateIndicatorState(incomingBivariateIndicatorDto.getUuid(),
                                IndicatorState.READY);
                    }
                }
            } catch (BivariateIndicatorsPRViolationException ie) {
                logger.error("Error while retrieving indicator metadata", ie);
            } catch (Exception e) {
                logger.error("Error while calculating stops and quality for indicator with {} {}", paramName, id);
            }
        });
        logger.info("Current queue size with indicators to calculate: {}", calculationExecutor.getQueue().size());
        return ResponseEntity.ok().build();
    }

    @PreDestroy
    public void shutdown() {
        calculationExecutor.shutdown();
    }
}
