# Evals

`frozen-agent-scenarios-v1.json` is the immutable, synthetic-only evaluation contract for the MVP reference agent. It covers all 18 outcomes required by the project brief: onboarding and duplicate classification, site and business-unit changes, authorization and stale plans, idempotency and transport recovery, prompt injection and tool loops, messaging replay and reconciliation gaps.

The `steward-agent` module loads this contract and grades observable results without hidden model reasoning. Its report includes task success, classification accuracy, duplicate precision/recall, unsafe-action rate, evidence completeness, unnecessary escalation, tool-call count, latency, cost and recovery without repeated side effects. Provider-specific runners must emit `AgentEvalActual` values against this unchanged contract; changing expected outcomes requires a new dataset version.

Run the deterministic reference baseline with Java 25:

```bash
./mvnw --batch-mode --no-transfer-progress -pl steward-agent -am test
```
