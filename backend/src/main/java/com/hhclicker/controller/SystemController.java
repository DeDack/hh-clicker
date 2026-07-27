package com.hhclicker.controller;

import com.hhclicker.integration.hhadapter.HhAdapterClient;
import com.hhclicker.integration.hhadapter.dto.response.AdapterHealthResponse;
import com.hhclicker.integration.hhadapter.dto.response.AdapterStatusResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {
    private final String adapterUrl;
    private final HhAdapterClient adapterClient;

    public SystemController(@Value("${hh.adapter.url}") String adapterUrl, HhAdapterClient adapterClient) {
        this.adapterUrl = adapterUrl;
        this.adapterClient = adapterClient;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "ok", true,
            "service", "backend",
            "adapterUrlConfigured", adapterUrl != null && !adapterUrl.isBlank()
        );
    }

    @GetMapping("/adapter/status")
    public AdapterHealthResponse adapterStatus() {
        return adapterClient.getHealth();
    }

    @GetMapping("/llm/status")
    public AdapterStatusResponse llmStatus() {
        return adapterClient.getLlmStatus();
    }
}
