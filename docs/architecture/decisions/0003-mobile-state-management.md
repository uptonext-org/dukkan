# ADR 0003: Mobile state with TanStack Query and React context

- **Status:** Accepted, 2026-10-18

## Context
The server is authoritative for all prices, carts and slots. Client-only
state is small: the auth token and form inputs. Public sources report that
Picnic uses RxJS (V-04). We are not trying to copy Picnic's internals.

## Decision
- **Server state:** TanStack Query. Cart mutations write the returned `Cart`
  into the cache.
- **Session:** an `AuthProvider` context. The token is kept in secure storage
  behind a `TokenStorage` interface (react-native-keychain on devices,
  in-memory in tests).
- No global store library.

## Consequences
Little boilerplate, built-in caching and retries, and testable through an
injected `ApiClient`. If client-side state grows (offline basket, for
example), we revisit this decision.
