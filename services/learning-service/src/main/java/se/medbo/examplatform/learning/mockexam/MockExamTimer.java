package se.medbo.examplatform.learning.mockexam;

import java.time.Duration;
import java.time.Instant;

public final class MockExamTimer {
    private MockExamTimer() {}

    public static TimerState state(Instant startedAt, Integer durationMinutes, Instant now) {
        long elapsed = Math.max(0, Duration.between(startedAt, now).getSeconds());
        if (durationMinutes == null) {
            return new TimerState(0, false, Math.toIntExact(Math.min(elapsed, Integer.MAX_VALUE)), null);
        }
        if (durationMinutes < 1) throw new IllegalArgumentException("Duration must be positive");
        long allowed = Math.multiplyExact((long) durationMinutes, 60L);
        return new TimerState((int) Math.max(0, allowed - elapsed), elapsed >= allowed,
                (int) Math.min(elapsed, allowed), startedAt.plusSeconds(allowed));
    }

    public record TimerState(int remainingSeconds, boolean expired, int elapsedSeconds, Instant deadline) {}
}
