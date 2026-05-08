package com.datalabeling.service;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 大模型全局并发控制（按模型配置维度）
 */
@Slf4j
@Service
public class ModelConcurrencyService {

    private static final int DEFAULT_MAX_CONCURRENCY = 10;
    private static final int MIN_MAX_CONCURRENCY = 1;
    private static final int MAX_MAX_CONCURRENCY = 100;

    /**
     * 当 configId 为空（如 application.yml 回退配置）时使用的 Key，避免与数据库自增ID冲突。
     */
    private static final Integer FALLBACK_CONFIG_KEY = 0;

    private final Map<Integer, Limiter> limiters = new ConcurrentHashMap<>();

    public Permit acquire(Integer configId, Integer maxConcurrency) {
        int key = normalizeConfigKey(configId);
        int normalizedMax = normalizeMaxConcurrency(maxConcurrency);

        Limiter limiter = limiters.computeIfAbsent(key, ignored -> new Limiter(normalizedMax));
        limiter.updateMaxConcurrency(normalizedMax);

        try {
            limiter.acquire();
            return new Permit(limiter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "等待模型并发许可被中断");
        }
    }

    public int getCurrentConcurrency(Integer configId) {
        Limiter limiter = limiters.get(normalizeConfigKey(configId));
        return limiter == null ? 0 : limiter.getInUse();
    }

    public Map<Integer, Integer> getCurrentConcurrencyByConfigIds(Collection<Integer> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return configIds.stream()
                .distinct()
                .filter(id -> id != null)
                .collect(Collectors.toMap(id -> id, this::getCurrentConcurrency));
    }

    private int normalizeConfigKey(Integer configId) {
        return configId == null ? FALLBACK_CONFIG_KEY : configId;
    }

    private int normalizeMaxConcurrency(Integer maxConcurrency) {
        if (maxConcurrency == null) {
            return DEFAULT_MAX_CONCURRENCY;
        }
        return Math.max(MIN_MAX_CONCURRENCY, Math.min(MAX_MAX_CONCURRENCY, maxConcurrency));
    }

    public static final class Permit implements AutoCloseable {
        private final Limiter limiter;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private Permit(Limiter limiter) {
            this.limiter = limiter;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                limiter.release();
            }
        }
    }

    private static final class Limiter {
        private final AdjustableSemaphore semaphore;
        private final AtomicInteger maxConcurrency;

        private Limiter(int maxConcurrency) {
            int normalized = Math.max(MIN_MAX_CONCURRENCY, maxConcurrency);
            this.semaphore = new AdjustableSemaphore(normalized, true);
            this.maxConcurrency = new AtomicInteger(normalized);
        }

        void updateMaxConcurrency(int newMaxConcurrency) {
            int normalized = Math.max(MIN_MAX_CONCURRENCY, newMaxConcurrency);
            int oldMax = maxConcurrency.get();
            if (oldMax == normalized) {
                return;
            }
            synchronized (this) {
                oldMax = maxConcurrency.get();
                if (oldMax == normalized) {
                    return;
                }

                int delta = normalized - oldMax;
                if (delta > 0) {
                    semaphore.release(delta);
                } else {
                    semaphore.reducePermitsPublic(-delta);
                }
                maxConcurrency.set(normalized);
            }
        }

        void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        void release() {
            semaphore.release();
        }

        int getInUse() {
            return maxConcurrency.get() - semaphore.availablePermits();
        }
    }

    private static final class AdjustableSemaphore extends Semaphore {
        private AdjustableSemaphore(int permits, boolean fair) {
            super(permits, fair);
        }

        private void reducePermitsPublic(int reduction) {
            super.reducePermits(reduction);
        }
    }
}
