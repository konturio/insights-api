package io.kontur.insightsapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.repository.IndicatorRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class IndicatorService {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorService.class);

    private final IndicatorRepository indicatorRepository;

    public String createIndicator(BivariateIndicatorDto bivariateIndicatorDto) throws JsonProcessingException {
        return indicatorRepository.createIndicator(bivariateIndicatorDto);
    }
}
