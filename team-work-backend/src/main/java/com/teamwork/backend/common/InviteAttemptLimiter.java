package com.teamwork.backend.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InviteAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<Long, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public void check(Long accountId) {
        Deque<Instant> attempts = failures.computeIfAbsent(accountId, id -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= MAX_FAILURES) {
                throw new ApiException(429, "邀请码错误次数过多，请稍后再试");
            }
        }
    }

    public void fail(Long accountId) {
        Deque<Instant> attempts = failures.computeIfAbsent(accountId, id -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void succeed(Long accountId) {
        failures.remove(accountId);
    }

    private void prune(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }
}
