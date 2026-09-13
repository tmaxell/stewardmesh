package io.stewardmesh.agent;

import java.util.Map;

/** Sole master-data access boundary available to the reference agent. */
@FunctionalInterface
public interface McpCapabilityClient {

    Map<String, Object> call(String toolName, Map<String, Object> arguments);
}
