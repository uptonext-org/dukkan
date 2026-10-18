# ADR 0005: Contract-first OpenAPI in `packages/shared-contracts`

- **Status:** Accepted, 2026-10-18

## Decision
`packages/shared-contracts/openapi.yaml` is the source of truth. The backend
has a contract test that fails when an operation in the file has no handler,
or when an `/api/v1` handler is not documented. The mobile app's types mirror
the contract.

## Consequences
API changes are reviewed as contract diffs first. Generating server stubs was
rejected: a generated layer adds a lot of code and fits poorly with Error Prone.
