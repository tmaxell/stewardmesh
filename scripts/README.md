# Scripts

Small deterministic developer utilities belong here when they eliminate repeated manual work. Product behavior does not belong in scripts.

Run the complete Phase 1 supplier-intake proof from the repository root:

```bash
./scripts/verify-phase-1.sh
```

The command requires Java 25 and Docker. It exercises the public HTTP flow against disposable PostgreSQL and LocalStack containers, checks all module tests and architecture rules, validates the local Compose model, and checks patch whitespace.

Run the complete Phase 3 proof with `./scripts/verify-phase-3.sh`. In addition to every earlier gate, it exercises the scoped governed MCP lifecycle and the transactional PostgreSQL inbox/outbox through real LocalStack SQS queues, including duplicate delivery and own-event loop suppression.

Start the complete local dependency stack and service with `./scripts/run-local.sh`. While it is running, `./scripts/smoke-local.sh` obtains a scoped local token and verifies both the first synthetic workbook upload and its idempotent replay.

Run the reproducible end-to-end demonstration with `./scripts/demo-local.sh` while `run-local.sh` is up. It publishes a synthetic reference business unit through SQS, uploads a synthetic workbook through the public API, then drives one governed change over MCP: the agent proposes and simulates, is refused approval because its token lacks the scope, a separate steward approves, a third principal executes, a retried execution returns the original receipt, and the mastered event is read back from the queue. It uses only synthetic identities and tolerates a database that already holds earlier runs.

For the live model path, copy `.env.example` to the ignored `.env` and add `GROQ_API_KEY` there, or export it in the terminal that runs the demo. `GROQ_MODEL` defaults to `openai/gpt-oss-20b` and `GROQ_BASE_URL` to Groq's OpenAI-compatible endpoint. With a key present, the script builds and runs the executable reference-agent jar: Groq emits strict schema-constrained directives, the supervisor enforces phase/tool policy, and the agent reaches the service only through authenticated MCP. Without a key, the same governed lifecycle remains demonstrable through the deterministic MCP fallback.
