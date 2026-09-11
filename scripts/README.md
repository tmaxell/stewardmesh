# Scripts

Small deterministic developer utilities belong here when they eliminate repeated manual work. Product behavior does not belong in scripts.

Run the complete Phase 1 supplier-intake proof from the repository root:

```bash
./scripts/verify-phase-1.sh
```

The command requires Java 25 and Docker. It exercises the public HTTP flow against disposable PostgreSQL and LocalStack containers, checks all module tests and architecture rules, validates the local Compose model, and checks patch whitespace.

Start the complete local dependency stack and service with `./scripts/run-local.sh`. While it is running, `./scripts/smoke-local.sh` obtains a scoped local token and verifies both the first synthetic workbook upload and its idempotent replay.
