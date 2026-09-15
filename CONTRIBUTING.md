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
- documentation or ADR changes when applicable.

A PR is mergeable when:

- the Maven verification required by the changed boundary passes;
- unit, integration, contract and architecture tests are proportionate to the risk;
- formatting/static checks pass;
- no real company, supplier, personal, banking or secret data is present;
- migrations are forward-only and tested against PostgreSQL;
- APIs/events/MCP schemas remain versioned and documented;
- logs and metrics do not expose sensitive values;
- repository README files, contracts and implementation do not contradict one another;
- the branch history, commit trailers and PR description contain no tooling attribution, vendor footers or addresses that do not belong to the author.

Use the aggregate JaCoCo report as review evidence for production changes. The Phase 1 baseline target is at least 80% coverage of changed production lines; material branches of the domain state machine require 100% path coverage. A justified exception belongs in the PR description rather than in disabled tests or coverage exclusions.

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

## Authorship and tooling provenance

The repository records the people accountable for a change and nothing else. Authoring tools, assistants and their vendors are implementation details of a contributor's workstation and must never appear in the published history.

Never add, in commit subjects, commit bodies, commit trailers, branch names, pull request titles or descriptions, review comments, code comments, documentation or any tracked file, as authorship or tooling attribution:

- the name of an AI assistant, model or its vendor, in any spelling or casing;
- generated-by, co-authored-by, assisted-by or similar attribution to a tool;
- advertising footers, badges or links added automatically by a tool;
- email addresses that do not belong to the actual author, including tool-vendor `noreply` addresses, placeholder addresses and addresses invented for a trailer; a contributor's own GitHub-provided `users.noreply.github.com` address is their real address and remains allowed;
- accounts, handles or bot identities that are not real repository contributors.

Identifiers required for a functional external integration (for example, a provider API URI or
model ID in deploy-time configuration) are interface data, not contributor attribution. Keep them
confined to the integration code/configuration and operator documentation; never use them as an
authorship claim, generated-by footer, or promotional badge.

Rules:

1. `user.name` and `user.email` must identify the real author for every commit. Verify with `git log --format='%an <%ae>'` before opening a pull request.
2. `Co-Authored-By` is reserved for a human who actually co-authored the change and uses that person's own address.
3. Commit messages describe the change and its reasoning, never how the change was produced.
4. Remove any tool-injected trailer or footer before committing; amend or rebase the branch if one has already been committed.
5. The same rule applies to pull request bodies, issue comments and release notes.

Check a branch before review:

```bash
git log --format='%an <%ae>%n%B' origin/dev..HEAD | grep -inE 'co-authored|generated with|assisted|noreply@' || true
```

An empty result is the expected outcome.

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

The local `docs/` and `.agents/` directories contain private planning and agent guidance and are intentionally ignored. Never rely on them in CI, Maven builds, tests or links committed to the repository. Stable reusable engineering constraints belong in versioned tests, contracts, `CONTRIBUTING.md`, or the nearest top-level README; machine-readable interfaces belong in `contracts`. Summarize consequential decisions and trade-offs in the PR description and commit history.

Do not add README files to every Java package or Maven module. Prefer package documentation, tests and clear code when there is no separate operator/developer workflow to explain.

## Safe repository data

- Use only synthetic fixtures.
- Keep `.env`, credentials, tokens and local volumes untracked.
- Do not copy internal schemas, queue names, hostnames, business identifiers or production examples into the repository.
- Model enterprise integration patterns generically and document any assumptions.
