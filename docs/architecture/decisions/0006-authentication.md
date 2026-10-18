# ADR 0006: Self-issued JWT for the MVP

- **Status:** Accepted, 2026-10-18

## Decision
We use e-mail and password, with passwords hashed by BCrypt. The backend
issues a short-lived (1 hour) HS256 JWT and validates it through Spring
Security's OAuth2 resource-server support. The secret comes from
`DUKKAN_JWT_SECRET`, and the app refuses to start if the secret is shorter
than 32 bytes. On mobile, the token lives in the Keychain or Keystore.

## Consequences
- Sessions are stateless and simple. A logout cannot revoke a token before it
  expires, which is acceptable for a 1-hour MVP token.
- Backlog: refresh tokens with rotation, login rate limiting, password reset,
  and moving to an OIDC provider (asymmetric keys, JWKS) before production.
