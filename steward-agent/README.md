# Steward Agent

Thin reference agent for goal-driven supplier onboarding and stewardship. It accesses the master-data service only through secured MCP capabilities and never receives database or broker credentials.

The active reference implementation is a model-independent supervisor with four ordered phases: `PROFILE`, `IDENTIFY`, `PLAN`, and `VERIFY`. A reasoner adapter returns typed directives; the supervisor enforces phase transitions, requires a fixed evidence checklist before advancing, and stops after at most 16 tool calls. Profiling must include artifact structure, deterministic mapping suggestion and a mapped-record readiness preview. Identification must inspect import status plus party and site candidates; planning must create and simulate the proposal; verification must reload the sealed plan. Every phase has a fixed capability allowlist.

The agent can inspect evidence, simulate a sealed plan, and create a proposal for human review. It cannot approve, reject, or execute a plan. Those capabilities remain separate server-side roles and are absent from every agent allowlist. The retained trace contains stable decision codes and tool outcomes, not hidden reasoning or credentials.

Every MCP result crosses an explicit untrusted-content boundary before a reasoner can observe it. Trusted policy, goal, phase and capability allowlist remain in a control-only structure; tool content is separately labelled `UNTRUSTED_TOOL_EVIDENCE`, encoded as canonical JSON and bound to a SHA-256 digest. Supplier text is therefore data rather than an instruction source. The boundary rejects unsupported, excessively nested or oversized content before it enters the reasoning context. Model adapters must preserve this separation and must not concatenate tool evidence into trusted instructions.

`StreamableHttpMcpCapabilityClient` is the only master-data access adapter. It performs the authenticated MCP handshake, sends a bearer token on every request, validates the session and response envelope, and returns bounded structured tool content. It has no dependency on any master-data-service module. The versioned workflow contract is [`contracts/agent/reference-supervisor-v1.json`](../contracts/agent/reference-supervisor-v1.json).

The first vertical slice deliberately leaves model selection outside the workflow core: a local or hosted model implements `StewardReasoner` without changing phase policy or MCP transport. Multiple autonomous agents are a later optimization and require eval evidence.
