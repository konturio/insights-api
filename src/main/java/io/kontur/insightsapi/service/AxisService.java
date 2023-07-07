package io.kontur.insightsapi.service;

import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.BivariativeAxisDto;
import io.kontur.insightsapi.repository.AxisRepository;
import io.kontur.insightsapi.repository.IndicatorRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class AxisService {

    private static final Logger logger = LoggerFactory.getLogger(AxisService.class);

    private final IndicatorRepository indicatorRepository;

    private final AxisRepository axisRepository;

    @Transactional
    public ResponseEntity<String> createAxis(@NotNull List<BivariateIndicatorDto> indicatorsForAxis) {

        List<BivariativeAxisDto> axisForCurrentIndicators = new ArrayList<>();

        for (BivariateIndicatorDto indicatorForAxis : indicatorsForAxis) {

            List<BivariateIndicatorDto> allIndicatorsExceptIndicatorForAxis = indicatorRepository
                    .getAllBivariateIndicators()
                    .stream()
                    .filter(bivariateIndicatorDto -> !bivariateIndicatorDto.getUuid().equals(indicatorForAxis.getUuid()))
                    .toList();

            if (indicatorForAxis.getIsBase()) {
                axisForCurrentIndicators.addAll(allIndicatorsExceptIndicatorForAxis
                        .stream()
                        .map(bivariateIndicatorDto -> new BivariativeAxisDto(
                                bivariateIndicatorDto.getId(),
                                bivariateIndicatorDto.getUuid(),
                                indicatorForAxis.getId(),
                                indicatorForAxis.getUuid()))
                        .toList());
            }

            axisForCurrentIndicators.addAll(allIndicatorsExceptIndicatorForAxis
                    .stream()
                    .filter(BivariateIndicatorDto::getIsBase)
                    .filter(bivariateIndicatorDto -> !indicatorsForAxis.stream().map(BivariateIndicatorDto::getUuid).toList().contains(bivariateIndicatorDto.getUuid()))
                    .map(bivariateIndicatorDto -> new BivariativeAxisDto(
                            indicatorForAxis.getId(),
                            indicatorForAxis.getUuid(),
                            bivariateIndicatorDto.getId(),
                            bivariateIndicatorDto.getUuid()))
                    .toList());
        }

        try {
            axisRepository.deleteAxisIfExist(indicatorsForAxis);

            axisRepository.uploadAxis(axisForCurrentIndicators);

            logger.info("Start stops and quality calculations for indicator with uuid {}",
                    indicatorsForAxis.get(0).getUuid());
            long calculationStartTime = System.currentTimeMillis();

            calculateStopsAndQuality(axisForCurrentIndicators);

            long calculationEndTime = System.currentTimeMillis();
            long calculationTimeInSeconds = (calculationEndTime - calculationStartTime) / 1000;
            logger.info("Stops and quality calculations for indicator with uuid {} have been done successfully " +
                            "and took {}", indicatorsForAxis.get(0).getUuid(),
                    String.format("%02d hours %02d minutes %02d seconds", calculationTimeInSeconds / 3600,
                            (calculationTimeInSeconds % 3600) / 60, (calculationTimeInSeconds % 60)));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Indicator data is stored in stat_h3_transposed but error " +
                    "occurred during estimation of axis parameters: " +
                    e.getMessage() +
                    ". Indicator UUID(s) = " +
                    StringUtils.join(indicatorsForAxis.stream().map(BivariateIndicatorDto::getUuid).toList(), ", "));
        }

        return ResponseEntity.ok().body(StringUtils.join(indicatorsForAxis.stream().map(BivariateIndicatorDto::getUuid).toList(), ", "));
    }

    //TODO: think further here about parallel calculation in terms of story #13934
    private void calculateStopsAndQuality(List<BivariativeAxisDto> axisForCurrentIndicators) {
        axisForCurrentIndicators.forEach(this::calculateQuality);

    }

    private void calculateQuality(BivariativeAxisDto bivariativeAxisDto) {
        axisRepository.calculateStopsAndQuality(bivariativeAxisDto);

    }
}
