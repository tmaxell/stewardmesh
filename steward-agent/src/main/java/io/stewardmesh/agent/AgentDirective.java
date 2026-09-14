package io.stewardmesh.agent;

import java.util.Map;
import java.util.Objects;

/** Typed reasoner output; decision codes are audit labels, never hidden reasoning text. */
public sealed interface AgentDirective
        permits AgentDirective.CallTool, AgentDirective.Advance, AgentDirective.Complete {

    record CallTool(String toolName, Map<String, Object> arguments, String decisionCode)
            implements AgentDirective {

        public CallTool {
            toolName = required(toolName, "toolName", 128);
            arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments must not be null"));
            decisionCode = required(decisionCode, "decisionCode", 64);
        }
    }

    record Advance(AgentPhase nextPhase, String decisionCode) implements AgentDirective {

        public Advance {
            Objects.requireNonNull(nextPhase, "nextPhase must not be null");
            decisionCode = required(decisionCode, "decisionCode", 64);
        }
    }

    record Complete(String outcomeCode) implements AgentDirective {

        public Complete {
            outcomeCode = required(outcomeCode, "outcomeCode", 64);
        }
    }

    private static String required(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String checked = value.strip();
        if (checked.isEmpty() || checked.length() > maximum) {
            throw new IllegalArgumentException(name + " must contain 1-" + maximum + " characters");
        }
        return checked;
    }
}
