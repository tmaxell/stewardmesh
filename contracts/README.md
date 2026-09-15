# Contracts

Versioned machine-readable contracts shared with clients and integration systems:

- `intake/` — supplier workbook layouts, safety limits, validation codes and versioned source-normalization policy.
- `events/` — canonical master-data and reference-data event schemas, currently envelope v1.
- `mcp/` — stable tool/resource schemas for intake profiling, identity reads and scoped governed action plans.
- `agent/` — reference supervisor workflow, capability and untrusted-evidence boundary.
- `openapi/` — published REST specifications, currently supplier intake v1.

Generated artifacts must be derived from source contracts rather than edited independently.
