package io.stewardmesh.agent;

/** Model-independent decision boundary for the reference supervisor. */
@FunctionalInterface
public interface StewardReasoner {

    AgentDirective next(AgentSnapshot snapshot);
}
