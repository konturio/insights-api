package io.kontur.insightsapi.configuration;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.kontur.insightsapi.dto.BivariateIndicatorDto;
import io.kontur.insightsapi.dto.BivariativeAxisDto;
import io.kontur.insightsapi.dto.FunctionArgs;
import io.kontur.insightsapi.dto.NumeratorsDenominatorsDto;
import io.kontur.insightsapi.service.IndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig extends CachingConfigurerSupport {

    private final HashFunction hashFunction = Hashing.murmur3_32_fixed();

    private final IndicatorService indicatorService;

    private String version() {
        Instant lastUpdated = indicatorService.getIndicatorsLastUpdateDate();
        return lastUpdated == null ? "none" : String.valueOf(lastUpdated.toEpochMilli());
    }

    /**
     * Extract indicator identifiers from known DTO types. Strings are ignored
     * because callers may pass geometry or other values that are not indicator IDs.
     * If a method needs per-indicator caching it should supply the indicator ID
     * wrapped in one of the supported DTOs.
     */
    private List<String> extractIds(Object param) {
        List<String> ids = new ArrayList<>();
        if (param == null) {
            return ids;
        }

        if (param instanceof List<?> list) {
            for (Object obj : list) {
                ids.addAll(extractIds(obj));
            }
        } else if (param instanceof BivariateIndicatorDto dto) {
            ids.add(dto.getId());
        } else if (param instanceof FunctionArgs fArgs) {
            ids.add(fArgs.getId());
        } else if (param instanceof BivariativeAxisDto axis) {
            if (axis.getNumerator_uuid() != null) {
                ids.add(axis.getNumerator_uuid());
            }
            if (axis.getDenominator_uuid() != null) {
                ids.add(axis.getDenominator_uuid());
            }
        } else if (param instanceof NumeratorsDenominatorsDto ndDto) {
            if (ndDto.getXNumUuid() != null) {
                ids.add(ndDto.getXNumUuid().toString());
            }
            if (ndDto.getXDenUuid() != null) {
                ids.add(ndDto.getXDenUuid().toString());
            }
            if (ndDto.getYNumUuid() != null) {
                ids.add(ndDto.getYNumUuid().toString());
            }
            if (ndDto.getYDenUuid() != null) {
                ids.add(ndDto.getYDenUuid().toString());
            }
        }

        return ids;
    }

    private String indicatorsVersion(Object... params) {
        List<String> ids = new ArrayList<>();
        for (Object param : params) {
            ids.addAll(extractIds(param));
        }
        if (ids.isEmpty()) {
            return version();
        }
        Map<String, Instant> updates = indicatorService.getIndicatorsLastUpdateDates(ids);
        ids.sort(String::compareTo);
        List<String> parts = new ArrayList<>();
        for (String id : ids) {
            Instant ts = updates.get(id);
            parts.add(id + ":" + (ts == null ? "none" : ts.toEpochMilli()));
        }
        return String.join("-", parts);
    }

    @Bean("stringKeyGenerator")
    public KeyGenerator customStringKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 1 && params[0] instanceof String) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8)
                        + "_" + indicatorsVersion(params[0]);
            }
            throw new IllegalArgumentException("Wrong params for StringKeyGenerator");
        };
    }

    @Bean("stringListKeyGenerator")
    public KeyGenerator customStringListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 2 && params[0] instanceof String && params[1] instanceof List) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[1].toString(), StandardCharsets.UTF_8)
                        + "_" + indicatorsVersion(params[0], params[1]);
            }
            throw new IllegalArgumentException("Wrong params for StringListKeyGenerator");
        };
    }

    @Bean("stringStringKeyGenerator")
    public KeyGenerator customStringStringKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 2 && params[0] instanceof String && params[1] instanceof String) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString((String) params[1], StandardCharsets.UTF_8)
                        + "_" + indicatorsVersion(params[0], params[1]);
            }
            throw new IllegalArgumentException("Wrong params for StringStringKeyGenerator");
        };
    }

    @Bean("listKeyGenerator")
    public KeyGenerator customListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 1 && params[0] instanceof List) {
                return hashFunction.hashString(params[0].toString(), StandardCharsets.UTF_8)
                        + "_" + indicatorsVersion(params[0]);
            }
            throw new IllegalArgumentException("Wrong params for ListKeyGenerator");
        };
    }

    @Bean("threeParametersAsStringOrListKeyGenerator")
    public KeyGenerator customThreeParametersAsStringOrListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 3 && (params[0] instanceof String || params[0] instanceof List)
                    && (params[1] instanceof String || params[1] instanceof List)
                    && (params[2] instanceof String || params[2] instanceof List)) {
                return hashFunction.hashString(params[0].toString(), StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[1].toString(), StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[2].toString(), StandardCharsets.UTF_8)
                        + "_" + indicatorsVersion(params[0], params[1], params[2]);
            }
            throw new IllegalArgumentException("Wrong params for StringStringListKeyGenerator");
        };
    }
}
