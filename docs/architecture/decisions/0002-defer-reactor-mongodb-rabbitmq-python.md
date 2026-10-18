# ADR 0002: Defer Reactor/WebFlux, MongoDB, RabbitMQ and Python services

- **Status:** Accepted, 2026-10-18

## Context
The requested stack includes Reactor, MongoDB, RabbitMQ and Python/ML. The
brief says to bring each one in only for a documented use case.

## Decision
The MVP uses blocking Spring MVC on virtual threads (`spring.threads.virtual.enabled`)
with JPA on PostgreSQL. None of the four is added yet. Each one has a trigger
for adoption:

| Technology | Adopt when |
|---|---|
| RabbitMQ + Spring AMQP | Order events must reach a separate fulfilment, notification or tracking consumer (backlog B-20, B-22) |
| MongoDB | Recipe or page-layout documents become schemaless or editorially managed (B-18, B-24) |
| Reactor / WebFlux | Streaming endpoints such as live delivery tracking over SSE, or fan-out to slow downstream calls (B-22) |
| Python + scikit-learn + Snowflake | Enough order history exists to forecast slot capacity or demand (B-25) |

## Consequences
The MVP has fewer moving parts and simpler tests. Versions stay pinned through
the Spring Boot 4.1.1 BOM (reactor-bom 2025.0.7, spring-amqp 4.1.1, mongodb 5.8.1),
so adding a technology later does not need any version overrides.
