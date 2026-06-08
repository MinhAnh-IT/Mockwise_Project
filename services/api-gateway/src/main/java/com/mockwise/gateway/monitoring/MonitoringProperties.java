package com.mockwise.gateway.monitoring;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Targets for the admin server-monitoring dashboard. The gateway is the natural
 * owner — it already knows every downstream. All entries are overridable from
 * config/env so prod can point at the real docker hostnames without a code
 * change; defaults use the compose service names.
 */
@Component
@ConfigurationProperties(prefix = "monitoring")
@Getter
@Setter
public class MonitoringProperties {

    /** Per-check connect/read timeout (ms). Checks run concurrently. */
    private int timeoutMs = 2000;

    private List<ServiceTarget> services = new ArrayList<>();
    private List<InfraTarget> infra = new ArrayList<>();

    @Getter
    @Setter
    public static class ServiceTarget {
        /** Display name, e.g. "IAM". */
        private String name;
        /** Any URL on the service; an HTTP response (even 4xx) means it's up. */
        private String url;
    }

    @Getter
    @Setter
    public static class InfraTarget {
        /** Display name, e.g. "Redis". */
        private String name;
        /**
         * A {@code host:port}, a bootstrap list {@code host:port,host2:port2},
         * or a URL ({@code http://minio:9000}). The first host:port is TCP-probed.
         */
        private String target;
    }
}
