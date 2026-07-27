package com.hhclicker.config;

import com.hhclicker.integration.hhadapter.HhAdapterProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(HhAdapterProperties.class)
public class RestClientConfig {
    @Bean
    RestClient hhAdapterRestClient(HhAdapterProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()));
        return RestClient.builder()
            .baseUrl(properties.getUrl())
            .requestFactory(factory)
            .build();
    }
}
