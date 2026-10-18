# ADR 0004: No server-driven UI in the MVP

- **Status:** Accepted, 2026-10-18

## Context
Public sources describe a server-driven UI platform used to avoid the 2–3
weeks it takes users to adopt app updates (V-02, B-16). It is a whole
platform: a renderer, a schema and authoring tools.

## Decision
The MVP uses native React Native screens. The home screen is composed from
ordinary endpoints (`/categories`, `/promotions`, `/products?promotedOnly=true`).

## Consequences
We ship faster now, but layout changes need an app release. Revisit once
merchandising needs weekly layout changes (backlog B-24). An OTA JavaScript
update mechanism is an alternative to evaluate.
