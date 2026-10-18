# ADR 0001: Modular monolith on PostgreSQL

- **Status:** Accepted, 2026-10-18

## Context
The MVP has eight closely coupled features (catalog, cart, promotions,
addresses, slots, orders, auth). The team is small and no domain scales
independently yet. The brief asks us to avoid premature microservices.

## Decision
We ship one Spring Boot deployable with package-level modules (`identity`,
`catalog`, `promotion`, `cart`, `address`, `delivery`, `order`, `shared`). It
uses one PostgreSQL database, and Flyway owns the schema. Modules interact only
through application-service interfaces, and an ArchUnit test enforces this.

## Consequences
- One build, one deploy and in-process transactions. A checkout runs as a
  single ACID transaction.
- Splitting out a module later means replacing its interface with a remote
  client. Strict boundaries keep that cheap.
- Every module shares one database. Each module owns its own tables, and no
  query reads another module's tables.
