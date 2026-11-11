package dev.codesentinel.jobs;

import dev.codesentinel.common.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

class JobWorkerTest {

    private AppProperties createProps() {
        return new AppProperties(
                new AppProperties.Jwt("secret-key-that-is-at-least-32-bytes-long", 480),
                "http://127.0.0.1:5173",
                true,
                false,
                "",
                30,
                new AppProperties.Storage("local", "/tmp/caliper-storage"),
                new AppProperties.Bootstrap("", ""),
                new AppProperties.Worker(true, 1500L, 10, 3),
                new AppProperties.Plag(10, 5, 0.30, 0.80),
                new AppProperties.Judge("docker", "/tmp/caliper-judge", "docker")
        );
    }

    @Test
    @DisplayName("JobWorker processOne returns false when queue is empty")
    void processOneReturnsFalseOnEmptyQueue() {
        TransactionTemplate noopTx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                return null;
            }
        };

        JobWorker worker = new JobWorker(null, null, null, createProps(), noopTx);
        boolean processed = worker.processOne();
        assertFalse(processed, "Process one must return false when no jobs are available");
    }

    @Test
    @DisplayName("JobWorker poll catches RuntimeException safely without blowing up thread")
    void pollCatchesRuntimeException() {
        TransactionTemplate failingTx = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                throw new RuntimeException("Simulated DB connection timeout");
            }
        };

        JobWorker worker = new JobWorker(null, null, null, createProps(), failingTx);
        assertDoesNotThrow(worker::poll, "Worker poll must swallow runtime exceptions safely");
    }
}
