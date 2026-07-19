package se.medbo.examplatform.learning.practice;

public final class SessionCompletion {
    private SessionCompletion() {}

    public static boolean isComplete(int answered, int total) {
        if (answered < 0 || total < 1 || answered > total) {
            throw new IllegalArgumentException("Invalid session progress");
        }
        return answered == total;
    }
}
