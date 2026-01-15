package com.templatemanagement.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Value("${cache.template.ttl-minutes:30}")
    private long templateTtlMinutes;

    @Value("${cache.template.max-size:1000}")
    private long templateMaxSize;

    @Value("${cache.vendor.ttl-minutes:30}")
    private long vendorTtlMinutes;

    @Value("${cache.vendor.max-size:500}")
    private long vendorMaxSize;

    @Bean
    public Caffeine<Object, Object> templateCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(templateTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(templateMaxSize)
                .recordStats();
    }

    @Bean
    public Caffeine<Object, Object> vendorCacheBuilder() {
        return Caffeine.newBuilder()
                .expireAfterWrite(vendorTtlMinutes, TimeUnit.MINUTES)
                .maximumSize(vendorMaxSize)
                .recordStats();
    }
}
