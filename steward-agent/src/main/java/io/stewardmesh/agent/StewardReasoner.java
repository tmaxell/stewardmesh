package io.stewardmesh.agent;

/** Model-independent decision boundary for the reference supervisor. */
@FunctionalInterface
public interface StewardReasoner {

    AgentDirective next(AgentReasoningContext context);

    /** Provider adapters override this when the preceding decision exposed token accounting. */
    default ModelTokenUsage lastTokenUsage() {
        return ModelTokenUsage.unavailable();
    }
}
