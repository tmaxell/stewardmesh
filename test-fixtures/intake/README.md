# Supplier workbook v1 fixtures

- `supplier-workbook-v1-valid.xlsx` contains three valid synthetic assertions. Two rows deliberately share an INN while using different KPP, address, site and business-unit context.
- `supplier-workbook-v1-mixed-invalid.xlsx` contains one valid row plus an unknown header, a row with multiple deterministic field errors and a formula cell that must be rejected without evaluation.

These workbooks contain fictional names, locations and identifiers only. The corresponding machine-readable layout and safety limits live under `contracts/intake/`.
