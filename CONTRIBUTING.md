# Contributing to StewardMesh

## Branch model

The repository has two long-lived branches:

- `main` — completed, demonstrable project stages only;
- `dev` — integration branch for the current stage.

Every atomic change starts from an up-to-date `dev` branch:

```bash
git switch dev
git pull --ff-only
git switch -c feature/<short-kebab-name>
```

Examples: `feature/maven-reactor`, `feature/postgres-flyway`, `feature/xlsx-intake`.

Rules:

1. Keep one coherent feature or refactoring concern per branch.
2. Do not commit directly to `main` or `dev` after repository bootstrap.
3. Rebase or merge the current `dev` before final verification if the branch has diverged.
4. Open a pull request from `feature/*` to `dev` only after the feature definition of done is satisfied.
5. Prefer squash merge for `feature/* -> dev`, giving `dev` one reviewable commit per atomic feature.
6. After a complete project stage, open a GitHub pull request from `dev` to `main`.
7. Use a merge commit for `dev -> main` so the stage boundary remains visible.
8. Delete merged feature branches. Never force-push shared `dev` or `main`.

Emergency fixes are not part of the MVP workflow. If one becomes necessary, document the exception and merge the same fix back to `dev`.

## Pull request requirements

Every PR describes:

- the problem and deliberately bounded scope;
- affected domain or architecture invariants;
- observable behavior and externally visible contracts;
- tests executed and their results;
- migrations, security, compatibility and operational impact;
- documentation, ADR or repo-skill changes when applicable.

A PR is mergeable when:

- the Maven verification required by the changed boundary passes;
- unit, integration, contract and architecture tests are proportionate to the risk;
- formatting/static checks pass;
- no real company, supplier, personal, banking or secret data is present;
- migrations are forward-only and tested against PostgreSQL;
- APIs/events/MCP schemas remain versioned and documented;
- logs and metrics do not expose sensitive values;
- repository README files, contracts, skill references and implementation do not contradict one another.

Configure GitHub branch protection for `main` and `dev`: require pull requests, successful checks, resolved conversations, and prohibit force pushes and branch deletion. Require at least one approval when a second reviewer is available.

## Commit conventions

Use imperative Conventional Commit subjects:

```text
feat(intake): persist immutable intake artifact metadata
fix(messaging): deduplicate relayed source events
test(domain): cover conflicting supplier identifiers
docs(architecture): record NSI distribution boundary
chore(build): add Maven quality plugins
```

Commits must not mix unrelated formatting or generated-file churn with behavior changes.

Commit each significant completed logical block after running its relevant verification. Prefer a small reviewable series of coherent commits over accumulating the entire feature in one commit. Do not manufacture micro-commits or separate changes that only compile, test or make sense together.

## Baseline verification

Use Java 25 and the repository Maven Wrapper. Before opening a feature pull request, run:

```bash
./mvnw --batch-mode verify
docker compose -f deploy/local/compose.yaml config --quiet
git diff --check
```

## Repository documentation

Multiple README files are intentional but bounded:

- root `README.md` is the project entry point;
- a top-level subsystem may have one README explaining its responsibility and how to use it before implementation exists;
- externally visible machine contracts belong in `contracts`.

The local `docs/` directory contains private planning and research notes and is intentionally ignored. Never rely on it in CI, Maven builds, tests or links committed to the repository. Stable reusable engineering constraints belong in the repo-skill references; user-facing setup belongs in the nearest top-level README; machine-readable interfaces belong in `contracts`. Summarize consequential decisions and trade-offs in the PR description and commit history.

Do not add README files to every Java package or Maven module. Prefer package documentation, tests and clear code when there is no separate operator/developer workflow to explain.

## Safe repository data

- Use only synthetic fixtures.
- Keep `.env`, credentials, tokens and local volumes untracked.
- Do not copy internal schemas, queue names, hostnames, business identifiers or production examples into the repository.
- Model enterprise integration patterns generically and document any assumptions.
