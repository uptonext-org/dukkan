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

## Update 2026-10-21 (B-39)
- Access tokens now live 15 minutes. Opaque refresh tokens (32 random bytes, stored as SHA-256)
  live 30 days and rotate on every use; a reused token revokes its whole sign-in (token family),
  which limits the damage of a copied token. Logout, password reset and password change revoke
  refresh tokens; JWTs are not revoked and simply expire.
- Password reset uses single-use 30-minute tokens mailed over SMTP; the request endpoint never
  reveals whether an account exists (always 202, mail sent in the background).
- Sign in with Apple and Google: the server verifies the provider's ID token (JWKS, issuer,
  audience, expiry, nonce) and links accounts by verified email. No OAuth flow runs on the server.
