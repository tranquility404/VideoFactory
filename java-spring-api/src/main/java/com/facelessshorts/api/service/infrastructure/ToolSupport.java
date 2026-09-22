package com.facelessshorts.api.service.infrastructure;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class ToolSupport {
    private final ApiProperties properties;
    private final ProcessGateway processes;
    private final ObjectMapper mapper;
    private final RestClient restClient;

    @Autowired
    public ToolSupport(ApiProperties properties, ProcessGateway processes, ObjectMapper mapper, RestClient restClient) {
        this.properties = properties; this.processes = processes; this.mapper = mapper; this.restClient = restClient;
    }
    /** Convenience constructor retained for unit tests; production wiring uses BeanConfig. */
    public ToolSupport(ApiProperties properties, ProcessGateway processes, ObjectMapper mapper) {
        this(properties, processes, mapper, RestClient.builder().build());
    }
    public ApiProperties properties() { return properties; }
    public ObjectMapper json() { return mapper; }
    public ProcessGateway processes() { return processes; }
    public RestClient restClient() { return restClient; }
    public Map<String,Object> getJson(String url, Map<String,String> headers, Duration timeout) {
        return requestJson("GET", url, headers, null, timeout);
    }
    public Map<String,Object> postJson(String url, Map<String,String> headers, Object body, Duration timeout) {
        try { return requestJson("POST", url, headers, mapper.writeValueAsString(body), timeout); }
        catch (IOException e) { throw new ApiException(500, "Cannot encode API request", e); }
    }
    public byte[] getBytes(String url, Map<String,String> headers, Duration timeout) {
        try {
            return restClient.get().uri(url).headers(h -> headers.forEach(h::set)).retrieve().body(byte[].class);
        } catch (org.springframework.web.client.RestClientException e) { throw new ApiException(502,"HTTP request failed",e); }
    }
    public Map<String,Object> postBytes(String url, Map<String,String> headers, byte[] body, Duration timeout) {
        try {
            return restClient.post().uri(url).headers(h -> headers.forEach(h::set)).body(body).retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {});
        } catch (org.springframework.web.client.RestClientException e) { throw new ApiException(502,"HTTP request failed",e); }
    }
    public byte[] postBytes(String url, Map<String,String> headers, Object body, Duration timeout) {
        try {
            return restClient.post().uri(url).headers(h -> headers.forEach(h::set)).body(body).retrieve().body(byte[].class);
        } catch (org.springframework.web.client.RestClientException e) { throw new ApiException(502,"HTTP request failed",e); }
    }
    public Map<String,Object> requestJson(String method, String url, Map<String,String> headers, String body, Duration timeout) {
        try {
            String response = ("POST".equals(method)
                    ? restClient.post().uri(url).headers(h -> headers.forEach(h::set)).body(body == null ? "" : body)
                    : restClient.get().uri(url).headers(h -> headers.forEach(h::set)))
                    .retrieve().body(String.class);
            return mapper.readValue(response, new TypeReference<>() {});
        } catch (org.springframework.web.client.RestClientException | IOException | IllegalArgumentException e) {
            throw new ApiException(502, "External API request failed", e);
        }
    }
    public void write(Path output, byte[] data) {
        try { Files.createDirectories(output.toAbsolutePath().getParent()); Files.write(output, data); }
        catch (IOException e) { throw new ApiException(500, "Cannot write output " + output, e); }
    }
    public static Map<String,String> headers(String name, String value) { return Map.of(name, value); }
}