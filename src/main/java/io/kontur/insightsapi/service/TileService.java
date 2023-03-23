package io.kontur.insightsapi.service;

import io.kontur.insightsapi.repository.TileRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TileService {

    @Value("${calculations.useStatSeparateTables:false}")
    private Boolean useStatSeparateTables;

    private final Logger logger = LoggerFactory.getLogger(TileService.class);

    private final TileRepository tileRepository;

    public byte[] getBivariateTileMvt(Integer z, Integer x, Integer y, String indicatorsClass) {
        List<String> bivariateIndicators;
        switch (indicatorsClass) {
            case ("all"):
                bivariateIndicators = tileRepository.getAllBivariateIndicators();
                break;
            case ("general"):
                bivariateIndicators = tileRepository.getGeneralBivariateIndicators();
                break;
            default:
                String error = String.format("Tile indicator class is not defined. Class: %s", indicatorsClass);
                logger.error(error);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
        return tileRepository.getBivariateTileMvt(z, x, y, bivariateIndicators);
    }

    public byte[] getBivariateTileMvtIndicatorsList(Integer z, Integer x, Integer y, List<String> indicatorsList) {
        var indicators = indicatorsList;
        if (CollectionUtils.isEmpty(indicators)) {
            indicators = tileRepository.getAllBivariateIndicators();
        } else {
            if (!checkIndicatorsList(indicators)) {
                String error = "Wrong indicator name. " +
                        "All indicators should be from bivariate_indicators table";
                logger.error(error);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
            }
        }
        if (useStatSeparateTables) {
            return tileRepository.getBivariateTileMvtIndicatorsListV2(z, x, y, indicators);
        }
        return tileRepository.getBivariateTileMvt(z, x, y, indicators);
    }

    private boolean checkIndicatorsList(List<String> indicatorsList) {
        var bivariateIndicators = tileRepository.getAllBivariateIndicators();
        return bivariateIndicators.containsAll(indicatorsList);
    }
}
