# Domain invariants

Read this reference for domain modeling, matching, mastering, onboarding, or stewardship changes.

## Canonical distinctions

- `SupplierParty` is the global legal or economic entity.
- `SupplierAddress` is an address asserted by one or more sources. It is not automatically a supplier site.
- `SupplierSite` is the buying organization's operational procure-to-pay context for a supplier at an address and within a procurement business unit. Its purposes can include purchasing, sourcing, pay, or ship-from.
- `BusinessUnit` is an internal reference used to establish procurement and client context. The MVP imports it; it does not master the complete corporate hierarchy.
- `SiteAssignment` authorizes a client business unit to transact through a supplier site for a validity interval.
- A role or assignment is temporal and contextual. Do not encode it as a permanent boolean on the supplier.

## Source and master data

- Source records are immutable facts about what a source asserted at an ingestion time.
- Record `originSystem`, source record identity, producer, and transport separately. A reference-data distribution service is not the origin merely because it delivered an event.
- Corrections arrive as new versions or explicit supersession, not silent overwrites.
- A master entity groups source records; it is not one of those records.
- Each golden attribute records the winning source value, source record, rule, ruleset version, and decision time.
- Recalculation with the same inputs and ruleset must be deterministic.
- Merge/unmerge must retain enough lineage to explain and reverse the association.
- A replay or relay of the same business event must not create a second assertion or master-data effect.

## Identity resolution

Use a staged pipeline:

1. Normalize without destroying the original value.
2. Generate a bounded candidate set using exact or coarse blocking keys.
3. Compute independently explainable features.
4. Apply versioned thresholds for auto-link, stewardship review, or no-match.
5. Persist the feature breakdown and ruleset version used for the decision.

For the Russian synthetic MVP, identifiers may include INN, KPP, and OGRN. Do not assume that equal INN makes every source row the same operational object: different KPP/address combinations may represent distinct supplier sites or subdivisions of the same party. Conflicting authoritative identifiers must block automatic merging.

## Onboarding and change control

Model onboarding as an explicit state machine. A useful baseline is:

`RECEIVED -> PARSED -> VALIDATED -> MATCHED -> PLAN_READY -> AWAITING_APPROVAL -> APPROVED -> EXECUTING -> COMPLETED`

Terminal alternatives include `REJECTED`, `FAILED`, and `CANCELLED`. Define allowed transitions in the domain; do not scatter status assignments through controllers or tools.

Changes to identity, activation/spend authorization, bank details, party linking, and supplier-site assignments are high impact. They require explicit evidence, authorization, and audit. A low-confidence interpretation must route to review rather than be converted into a deterministic fact.

## Time and hierarchy

- Store `validFrom`/`validTo` for roles and assignments where business history matters.
- Prevent overlapping mutually exclusive assignments and invalid intervals.
- Use UTC instants for system events and explicit business dates for effective dating.
- Never infer current truth by deleting historical rows.
