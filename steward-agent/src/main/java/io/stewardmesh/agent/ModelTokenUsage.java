package io.stewardmesh.agent;

/** Optional model token counts; unavailable usage is explicit rather than reported as measured zero. */
public record ModelTokenUsage(boolean available, long inputTokens, long outputTokens) {

    private static final ModelTokenUsage UNAVAILABLE = new ModelTokenUsage(false, 0, 0);

    public ModelTokenUsage {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (!available && (inputTokens != 0 || outputTokens != 0)) {
            throw new IllegalArgumentException("unavailable token usage must not contain counts");
        }
    }

    public static ModelTokenUsage unavailable() {
        return UNAVAILABLE;
    }

    public static ModelTokenUsage measured(long inputTokens, long outputTokens) {
        return new ModelTokenUsage(true, inputTokens, outputTokens);
    }
}
