# LedgerX Lite — Security Guide

## Authentication Flow

```
1. Client → POST /auth/login { email, password }
2. Server validates credentials with BCryptPasswordEncoder.matches()
3. Server validates User.status == ACTIVE
4. Server generates signed JWT: { sub: email, userId: id, exp: now+24h }
5. Client stores token (secure storage only — never localStorage in browser)
6. Client sends: Authorization: Bearer <token> on all subsequent requests
7. JwtAuthenticationFilter validates signature + expiry on every request
8. If ACTIVE user found → sets SecurityContext → request proceeds
9. If invalid/expired → SecurityContext empty → Spring returns 401
```

## JWT Configuration

| Property | Default | Production |
|----------|---------|-----------|
| `ledgerx.jwt.secret` | ❌ Must set | Min 32 chars, from secrets manager |
| `ledgerx.jwt.expiry-ms` | 86400000 (24h) | 3600000 (1h) recommended |

**Never commit the JWT secret to version control.**

In production, inject via environment variable:
```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

## Password Security

- BCrypt with cost factor 12
- Plaintext password never stored, never logged
- `User.getPasswordHash()` returns the BCrypt hash only
- Password change invalidates no tokens (stateless — implement token revocation list if needed)

## Endpoint Access Control

| Endpoint | Auth Required | Notes |
|----------|--------------|-------|
| POST /auth/login | ❌ | Public |
| POST /users/register | ❌ | Public |
| GET /actuator/health | ❌ | Health probes |
| GET /swagger-ui/** | ❌ | Restrict in prod if needed |
| Everything else | ✅ Valid JWT | 401 if missing/invalid |

## Account Status Enforcement

`JwtAuthenticationFilter` checks `user.status == ACTIVE` before setting the SecurityContext. A SUSPENDED or CLOSED user whose token has not yet expired will receive 401.

`TransactionService` checks status again after acquiring the wallet lock, so a user suspended mid-request cannot complete their transaction.

## Known Limitations (Production Hardening Checklist)

- [ ] **Token revocation:** No blacklist. Logout = client discards token. Add Redis-based revocation list for SUSPENDED users.
- [ ] **HTTPS:** Deploy behind TLS terminator (nginx/ALB). Never run HTTP in production.
- [ ] **Rate limiting:** Add Spring Cloud Gateway or nginx rate limiting on /auth/login.
- [ ] **Wallet ownership:** API currently does not verify the authenticated user owns the wallet they're transacting on. Add ownership check in WalletController before delegating to service.
- [ ] **Admin role:** suspend/activate endpoints should require an ADMIN role, not just any authenticated user.
- [ ] **Secrets rotation:** Rotate JWT secret periodically. All existing tokens will be invalidated on rotation (acceptable for 1h expiry).
