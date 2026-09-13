# Steward Agent

Thin reference agent for goal-driven supplier onboarding and stewardship. It accesses the master-data service only through secured MCP capabilities and never receives database or broker credentials.

The active reference implementation is a model-independent supervisor with four ordered phases: `PROFILE`, `IDENTIFY`, `PLAN`, and `VERIFY`. A reasoner adapter returns typed directives; the supervisor enforces phase transitions, requires tool evidence before advancing, and stops after at most 16 tool calls. Every phase has a fixed capability allowlist.

The agent can inspect evidence, simulate a sealed plan, and create a proposal for human review. It cannot approve, reject, or execute a plan. Those capabilities remain separate server-side roles and are absent from every agent allowlist. The retained trace contains stable decision codes and tool outcomes, not hidden reasoning or credentials.

`StreamableHttpMcpCapabilityClient` is the only master-data access adapter. It performs the authenticated MCP handshake, sends a bearer token on every request, validates the session and response envelope, and returns bounded structured tool content. It has no dependency on any master-data-service module. The versioned workflow contract is [`contracts/agent/reference-supervisor-v1.json`](../contracts/agent/reference-supervisor-v1.json).

The first vertical slice deliberately leaves model selection outside the workflow core: a local or hosted model implements `StewardReasoner` without changing phase policy or MCP transport. Multiple autonomous agents are a later optimization and require eval evidence.
