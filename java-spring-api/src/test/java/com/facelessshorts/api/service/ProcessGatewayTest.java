package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessGatewayTest {
    @Test
    void timeoutIsReportedWithoutWaitingForProcessOutput() {
        ApiException ex = assertThrows(ApiException.class, () -> new ProcessGateway().execute(List.of("cmd", "/c", "ping 127.0.0.1 -n 4 > nul"), Path.of("."), 1));
        assertEquals(504, ex.status());
    }
}