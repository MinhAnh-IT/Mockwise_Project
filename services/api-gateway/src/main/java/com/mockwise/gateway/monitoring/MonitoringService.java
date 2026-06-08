package com.mockwise.gateway.monitoring;

import com.mockwise.gateway.monitoring.MonitoringOverview.ComponentStatus;
import com.mockwise.gateway.monitoring.MonitoringOverview.HostMetrics;
import com.mockwise.gateway.monitoring.MonitoringOverview.RuntimeInfo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Assembles the admin monitoring snapshot. Probes are best-effort and isolated:
 * one component being down never fails the whole call. Blocking by design — the
 * controller runs it on a bounded-elastic scheduler so the WebFlux event loop
 * is never blocked. All probes run concurrently so the call resolves in roughly
 * one timeout.
 */
@Slf4j
@Service
public class MonitoringService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";
    private static final String DEGRADED = "DEGRADED";

    private final MonitoringProperties props;
    private final HttpClient httpClient;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    public MonitoringService(MonitoringProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public MonitoringOverview overview() {
        List<CompletableFuture<ComponentStatus>> serviceChecks = props.getServices().stream()
                .map(s -> CompletableFuture.supplyAsync(() -> checkService(s.getName(), s.getUrl()), pool))
                .toList();

        List<CompletableFuture<ComponentStatus>> infraChecks = new ArrayList<>();
        props.getInfra().forEach(i ->
                infraChecks.add(CompletableFuture.supplyAsync(() -> checkTcp(i.getName(), i.getTarget()), pool)));

        List<ComponentStatus> services = serviceChecks.stream().map(CompletableFuture::join).toList();
        List<ComponentStatus> infra = infraChecks.stream().map(CompletableFuture::join).toList();

        return new MonitoringOverview(
                services, infra, hostMetrics(), runtimeInfo(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    // ── Service liveness ─────────────────────────────────────────────────────

    /** Any HTTP response means the process is up; 5xx = degraded; no response = down. */
    private ComponentStatus checkService(String name, String url) {
        long start = System.nanoTime();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            long ms = elapsedMs(start);
            int sc = resp.statusCode();
            return new ComponentStatus(name, sc >= 500 ? DEGRADED : UP, ms, "HTTP " + sc);
        } catch (Exception e) {
            return new ComponentStatus(name, DOWN, null, reason(e));
        }
    }

    // ── Infra reachability (TCP connect) ─────────────────────────────────────

    private ComponentStatus checkTcp(String name, String target) {
        HostPort hp = parseHostPort(target);
        if (hp == null) {
            return new ComponentStatus(name, DOWN, null, "unparseable target");
        }
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(hp.host(), hp.port()), props.getTimeoutMs());
            return new ComponentStatus(name, UP, elapsedMs(start), hp.host() + ":" + hp.port());
        } catch (Exception e) {
            return new ComponentStatus(name, DOWN, null, reason(e));
        }
    }

    // ── Host + runtime ───────────────────────────────────────────────────────

    private HostMetrics hostMetrics() {
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double cpu = os.getCpuLoad();
        Double cpuPercent = cpu < 0 ? null : round1(cpu * 100.0);
        double load = os.getSystemLoadAverage();
        Double loadAvg = load < 0 ? null : round1(load);

        long memTotal = os.getTotalMemorySize();
        long memFree = os.getFreeMemorySize();

        File root = new File("/");
        long diskTotal = root.getTotalSpace();
        long diskFree = root.getUsableSpace();

        return new HostMetrics(cpuPercent, loadAvg, memTotal, memTotal - memFree, diskTotal, diskFree);
    }

    private RuntimeInfo runtimeInfo() {
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        OffsetDateTime startedAt = Instant.ofEpochMilli(rt.getStartTime()).atOffset(ZoneOffset.UTC);
        return new RuntimeInfo(
                "api-gateway",
                System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(),
                rt.getUptime(),
                startedAt);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private record HostPort(String host, int port) {}

    private static HostPort parseHostPort(String target) {
        if (target == null || target.isBlank()) return null;
        String t = target.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            try {
                URI uri = URI.create(t);
                int port = uri.getPort() > 0 ? uri.getPort() : (t.startsWith("https") ? 443 : 80);
                return new HostPort(uri.getHost(), port);
            } catch (Exception e) {
                return null;
            }
        }
        // Bootstrap list — take the first entry
        String first = t.split(",")[0].trim();
        int colon = first.lastIndexOf(':');
        if (colon <= 0 || colon == first.length() - 1) return null;
        try {
            return new HostPort(first.substring(0, colon), Integer.parseInt(first.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String reason(Exception e) {
        String msg = e.getMessage();
        return msg != null && !msg.isBlank() ? msg : e.getClass().getSimpleName();
    }

    @PreDestroy
    void shutdown() {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
