package dev.codesentinel.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, @DefaultValue("http://127.0.0.1:5173") String corsOrigin,
                            @DefaultValue("true") boolean registrationEnabled, @DefaultValue("false") boolean seedDemo,
                            @DefaultValue("") String staticDir, @DefaultValue("30") int rateLimitPerMinute,
                            Storage storage, Bootstrap bootstrap, Worker worker, Plag plag, Judge judge) {
    public record Jwt(@DefaultValue("") String secret, @DefaultValue("480") int ttlMinutes) {
    }

    public record Storage(@DefaultValue("local") String provider, @DefaultValue("./storage") String root) {
    }

    public record Bootstrap(@DefaultValue("") String instructorEmail, @DefaultValue("") String instructorPassword) {
    }

    public record Worker(@DefaultValue("true") boolean enabled, @DefaultValue("1500") long pollMillis,
                         @DefaultValue("10") int stuckMinutes, @DefaultValue("2") int maxAttempts) {
    }

    public record Plag(@DefaultValue("10") int k, @DefaultValue("5") int w, @DefaultValue("0.30") double candidateCutoff,
                       @DefaultValue("0.80") double highPriorityThreshold) {
    }

    public record Judge(@DefaultValue("docker") String dockerBin, @DefaultValue("") String workRoot,
                        @DefaultValue("docker") String sandboxRuntime) {
    }
}
