package com.facelessshorts.api.service.infrastructure;
import com.facelessshorts.api.service.integration.*;

import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class ProcessGateway {
    public ProcessGateway() {}
    public Map<String, Object> execute(List<String> command, Path cwd, long timeout) {
        try {
            Process process = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new ApiException(504, "Tool timed out after " + timeout + " seconds"); }
            String output = new String(process.getInputStream().readAllBytes());
            if (process.exitValue() != 0) throw new ApiException(500, "Tool failed: " + output.substring(Math.max(0, output.length() - 4000)));
            return Map.of("stdout", output, "exit_code", process.exitValue());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ApiException(500, "Tool execution interrupted", e); }
        catch (IOException e) { throw new ApiException(500, "Cannot execute tool", e); }
    }
}