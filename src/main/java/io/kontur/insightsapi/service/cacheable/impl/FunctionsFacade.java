package io.kontur.insightsapi.service.cacheable.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import io.kontur.insightsapi.dto.FunctionArgs;
import io.kontur.insightsapi.model.FunctionResult;
import io.kontur.insightsapi.repository.FunctionsRepository;
import io.kontur.insightsapi.service.cacheable.CacheEvictable;
import io.kontur.insightsapi.service.cacheable.FunctionsService;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Primary
@ConditionalOnProperty(prefix = "cache", name = "functions")
public class FunctionsFacade implements FunctionsService, CacheEvictable {

    private final FunctionsRepository repository;

    private final Cache<String, List<FunctionResult>> cache;

    private final HashFunction hashFunction;

    private final Logger logger = LoggerFactory.getLogger(FunctionsFacade.class);

    public FunctionsFacade(FunctionsRepository repository, @Value("${cache.maximumSize}") Integer maximumSize) {
        this.repository = repository;
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(1, TimeUnit.DAYS)
                .maximumSize(maximumSize)
                .build();
        this.hashFunction = Hashing.murmur3_32_fixed();
    }

    @SneakyThrows
    @Override
    public List<FunctionResult> calculateFunctionsResult(String geojson, List<FunctionArgs> args) {
        logger.info("GET DATA FROM DB");
        return cache.get(keyGen(geojson, args),
                () -> repository.calculateFunctionsResult(geojson, args));
    }

    private String keyGen(String geojson, List<FunctionArgs> args) {
        return hashFunction.hashString(geojson, Charset.defaultCharset()) + "_"
                + hashFunction.hashString(args.toString(), Charset.defaultCharset());
    }

    @Override
    public void evict() {
        cache.invalidateAll();
    }
}
