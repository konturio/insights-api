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

    /**
     * Version string that changes whenever any indicator in the system is updated
     * or removed. Stored as milliseconds since the epoch or {@code "none"} when
     * there are no indicators. Used for caches that do not depend on particular
     * indicator IDs.
     */
    private String globalVersion() {
        Instant lastUpdated = indicatorService.getIndicatorsLastUpdateDate();
        return lastUpdated == null ? "none" : String.valueOf(lastUpdated.toEpochMilli());
    }

    /**
     * Collect all indicator identifiers found in the given parameter. Strings
     * or generic lists are ignored because callers may pass geometry or other
     * values that are not indicator IDs. To enable per-indicator caching the ID
     * must be wrapped in one of the supported DTOs such as {@link
     * BivariateIndicatorDto} or {@link FunctionArgs}.
     */
    private List<String> collectIndicatorIds(Object param) {
        List<String> ids = new ArrayList<>();
        if (param == null) {
            return ids;
        }

        if (param instanceof List<?> list) {
            for (Object obj : list) {
                ids.addAll(collectIndicatorIds(obj));
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

    /**
     * Compose version string for a cache key based on indicator update times.
     * When no indicator IDs are found among the parameters the {@link
     * #globalVersion()} is used.
     */
    private String computeIndicatorsVersion(Object... params) {
        List<String> ids = new ArrayList<>();
        for (Object param : params) {
            ids.addAll(collectIndicatorIds(param));
        }
        if (ids.isEmpty()) {
            return globalVersion();
        }
        Map<String, Instant> updates = indicatorService.getIndicatorsLastUpdateDates(ids);
        ids.sort(String::compareTo);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Instant ts = updates.get(id);
            sb.append(id)
                    .append(':')
                    .append(ts == null ? "none" : ts.toEpochMilli());
            if (i < ids.size() - 1) {
                sb.append('-');
            }
        }
        return sb.toString();
    }

    /**
     * KeyGenerator for methods with a single String argument. The argument value
     * is hashed and combined with the indicator version derived from that
     * argument if it contains indicator references.
     */
    @Bean("stringKeyGenerator")
    public KeyGenerator customStringKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 1 && params[0] instanceof String) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8)
                        + "_" + computeIndicatorsVersion(params[0]);
            }
            throw new IllegalArgumentException("Wrong params for StringKeyGenerator");
        };
    }

    /**
     * KeyGenerator for methods taking a String and a List. Each argument is
     * hashed separately and combined with the version computed from both
     * arguments.
     */
    @Bean("stringListKeyGenerator")
    public KeyGenerator customStringListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 2 && params[0] instanceof String && params[1] instanceof List) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[1].toString(), StandardCharsets.UTF_8)
                        + "_" + computeIndicatorsVersion(params[0], params[1]);
            }
            throw new IllegalArgumentException("Wrong params for StringListKeyGenerator");
        };
    }

    /**
     * KeyGenerator for methods with two String parameters.
     */
    @Bean("stringStringKeyGenerator")
    public KeyGenerator customStringStringKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 2 && params[0] instanceof String && params[1] instanceof String) {
                return hashFunction.hashString((String) params[0], StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString((String) params[1], StandardCharsets.UTF_8)
                        + "_" + computeIndicatorsVersion(params[0], params[1]);
            }
            throw new IllegalArgumentException("Wrong params for StringStringKeyGenerator");
        };
    }

    /**
     * KeyGenerator for single List parameter.
     */
    @Bean("listKeyGenerator")
    public KeyGenerator customListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 1 && params[0] instanceof List) {
                return hashFunction.hashString(params[0].toString(), StandardCharsets.UTF_8)
                        + "_" + computeIndicatorsVersion(params[0]);
            }
            throw new IllegalArgumentException("Wrong params for ListKeyGenerator");
        };
    }

    /**
     * KeyGenerator for methods that accept three parameters which can be either
     * String or List. This is mainly used by analytics services.
     */
    @Bean("threeParametersAsStringOrListKeyGenerator")
    public KeyGenerator customThreeParametersAsStringOrListKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            if (params.length == 3 && (params[0] instanceof String || params[0] instanceof List)
                    && (params[1] instanceof String || params[1] instanceof List)
                    && (params[2] instanceof String || params[2] instanceof List)) {
                return hashFunction.hashString(params[0].toString(), StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[1].toString(), StandardCharsets.UTF_8) + "_"
                        + hashFunction.hashString(params[2].toString(), StandardCharsets.UTF_8)
                        + "_" + computeIndicatorsVersion(params[0], params[1], params[2]);
            }
            throw new IllegalArgumentException("Wrong params for StringStringListKeyGenerator");
        };
    }
}
