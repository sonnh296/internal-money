package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRewardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/api/benchmark")
@RequiredArgsConstructor
public class BenchmarkController {

    private final LockingRewardService rewardService;

    @Qualifier("platformExecutor")
    private final TaskExecutor platformExecutor;

    @Qualifier("virtualExecutor")
    private final TaskExecutor virtualExecutor;

    @PostMapping("/internal-test")
    public Map<String, Object> runInternalBenchmark(@RequestParam(defaultValue = "5000") int count) {
        log.info("Starting internal benchmark for {} requests", count);

        // 1. Test với Platform Threads
        BenchmarkResult platformResult = runTest(platformExecutor, count, "PLATFORM");

        // 2. Test với Virtual Threads
        BenchmarkResult virtualResult = runTest(virtualExecutor, count, "VIRTUAL");

        return Map.of(
                "totalRequests", count,
                "platform", platformResult,
                "virtual", virtualResult,
                "note", "Test nội bộ để loại bỏ độ trễ mạng và overhead của JMeter/HTTP stack."
        );
    }

    @GetMapping("/run")
    public Map<String, Object> run(
            @RequestParam(defaultValue = "100") int concurrency,
            @RequestParam(defaultValue = "platform") String mode) {
        TaskExecutor executor = "virtual".equalsIgnoreCase(mode) ? virtualExecutor : platformExecutor;
        long start = System.currentTimeMillis();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>(concurrency);

        for (int i = 0; i < concurrency; i++) {
            String customerId = "bench-" + mode + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            TransactionRequest request = new TransactionRequest(customerId, "txn-" + UUID.randomUUID(),
                    java.math.BigDecimal.valueOf(100));
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    rewardService.processReward(request);
                    success.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long durationMs = Math.max(1L, System.currentTimeMillis() - start);
        double throughput = concurrency / (durationMs / 1000.0);
        double avgLatency = (double) durationMs / concurrency;

        return Map.of(
                "mode", mode.toLowerCase(),
                "concurrency", concurrency,
                "totalRequests", concurrency,
                "successCount", success.get(),
                "failCount", fail.get(),
                "durationMs", durationMs,
                "throughput", throughput,
                "avgLatencyMs", avgLatency
        );
    }

    private BenchmarkResult runTest(TaskExecutor executor, int count, String label) {
        long start = System.currentTimeMillis();
        List<CompletableFuture<Void>> futures = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String customerId = "bench-" + label + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            TransactionRequest request = new TransactionRequest(customerId, "txn-" + UUID.randomUUID(),
                    java.math.BigDecimal.valueOf(100));
            
            futures.add(CompletableFuture.runAsync(() -> {
                rewardService.processReward(request);
            }, executor));
        }

        // Đợi tất cả hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long durationMs = System.currentTimeMillis() - start;
        double rps = (double) count / (durationMs / 1000.0);

        return new BenchmarkResult(durationMs, Math.round(rps * 100.0) / 100.0);
    }

    public record BenchmarkResult(long durationMs, double rps) {}
}
