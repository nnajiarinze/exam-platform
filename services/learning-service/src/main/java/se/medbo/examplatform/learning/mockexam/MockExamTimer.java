package se.medbo.examplatform.learning.mockexam;

import java.time.Duration;
import java.time.Instant;

public final class MockExamTimer {
    private MockExamTimer() {}

    public static TimerState state(Instant startedAt, int durationMinutes, Instant now) {
        if (durationMinutes < 1) throw new IllegalArgumentException("Duration must be positive");
        long allowed = Math.multiplyExact((long) durationMinutes, 60L);
        long elapsed = Math.max(0, Duration.between(startedAt, now).getSeconds());
        return new TimerState((int) Math.max(0, allowed - elapsed), elapsed >= allowed,
                (int) Math.min(elapsed, allowed), startedAt.plusSeconds(allowed));
    }

    public record TimerState(int remainingSeconds, boolean expired, int elapsedSeconds, Instant deadline) {}
}
