# Agentic safety and evals

Read this reference for MCP tools, agent orchestration, prompts, approval, AI security, or evaluation changes.

## Responsibility boundary

Use AI for ambiguity and coordination:

- profile intake artifacts and propose mappings from unfamiliar supplier columns to the canonical schema;
- summarize conflicts and evidence;
- select relevant read-only tools;
- ask for missing information;
- assemble a proposed action plan.

Use deterministic code for:

- identifier and checksum validation;
- candidate scoring inputs and hard conflict rules;
- authorization and masking;
- state transitions;
- approval policy;
- idempotency and concurrency;
- persistence and event publication.

The reference agent has no database or AWS credentials for the master service. Its authority is limited to its OAuth token and the exposed MCP tools.

Use one supervisor workflow with phase-specific schemas and tool allowlists for `PROFILE`, `IDENTIFY`, `PLAN`, and `VERIFY`. Introduce multiple autonomous agents only after a frozen eval demonstrates a quality or maintainability gain. Distribution administration tools such as quarantine replay and reconciliation are excluded from the normal onboarding-agent allowlist.

## Tool taxonomy

Classify every tool:

- `READ`: no state change.
- `SIMULATE`: produces a preview without changing authoritative state.
- `PROPOSE`: creates a reviewable proposal, not the business mutation.
- `APPROVE`: records a human decision; require a separately authorized human principal.
- `EXECUTE`: applies only an approved, version-matched plan.

Do not combine proposal, approval, and execution in one tool. Make tool names and descriptions state the side effect and approval requirement.

Mutation inputs include:

- idempotency key;
- expected aggregate or plan version;
- actor and authorization context supplied by the server, not trusted from model arguments;
- reason and evidence references;
- dry-run or explicit execution mode where relevant.

## Untrusted inputs

Treat spreadsheet cells, documents, email text, search results, and source values as untrusted content. Keep them in delimited data fields, label their provenance, and never concatenate them into system/developer instructions. A data value that says to ignore policy remains a data value.

Minimize tool output, mask sensitive attributes by default, and avoid returning secrets, tokens, full bank details, or unrestricted raw documents. Enforce authorization in application services even if the transport already checks scopes.

## Approval and execution

- The model may recommend but cannot manufacture an approval identity.
- Approval must bind the exact immutable plan hash/version.
- Any material data or version change invalidates the approval and requires resimulation.
- The executing use case rechecks policy and preconditions.
- Record tool call, actor, decision, evidence, affected entities, result, and correlation identifiers.

## Evals

Maintain versioned scenario fixtures covering at least:

- new supplier;
- exact and fuzzy duplicate;
- existing party with a new supplier site;
- conflicting authoritative identifier;
- bank-detail discrepancy;
- missing required information;
- unauthorized mutation request;
- stale plan version;
- repeated idempotency key;
- tool failure and recovery;
- prompt injection embedded in XLSX;
- excessive or circular tool use.
- duplicate delivery of the same business event;
- a mastered event returning through an NSI relay;
- out-of-order source versions and reconciliation gaps.

Measure outcome rather than preferred wording:

- task success;
- correct entity/site/change classification;
- duplicate precision and recall;
- unsafe action rate;
- false auto-link rate;
- required-evidence completeness;
- unnecessary escalation rate;
- tool-call count, latency, and cost;
- successful recovery without duplicate side effects.

Run deterministic domain and contract tests on every change. Run model-dependent evals when prompts, tool descriptions, model configuration, orchestration, or relevant output schemas change. Compare models or prompts against the same frozen scenario set.
