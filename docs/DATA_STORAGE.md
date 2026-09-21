# Data storage model

The application separates durable business and identity data from shared,
short-lived coordination data. This keeps restarts safe while retaining the
cross-dyno caching needed to stay within Netatmo rate limits.

## PostgreSQL

PostgreSQL is the source of truth for data that must survive Redis eviction or
an application restart:

- application users and passkey credentials;
- per-user Netatmo OAuth connections used by the dashboard;
- the distinct system Netatmo OAuth connection used by the scheduled Salesforce
  Data Cloud forwarding job;
- operational messages.

Netatmo access and refresh tokens are encrypted with AES-256-GCM before they are
written. `TOKEN_ENCRYPTION_KEY` must be a stable, base64-encoded 32-byte key. A
new key can be generated with `openssl rand -base64 32`; changing or losing it
makes existing stored tokens unreadable and requires Netatmo authorization again.

`ADMIN_EMAIL` identifies the passkey user allowed to authorize the system
Netatmo connection. Ordinary users can authorize only their own dashboard
connection.

## Redis

Redis contains data that is shared across web dynos but safe to expire:

- Spring Session authentication state and short-lived WebAuthn/OAuth challenges;
- global Netatmo response caches (`homesdata`, `homestatus`, `measure`, and the
  system metrics feed);
- expiring Netatmo request counters;
- expiring scheduler locks that prevent duplicate work across dynos.

The dashboard caches are deliberately resource-scoped rather than user-scoped.
For example, `homesdata` has one global key, while home status and measurements
are keyed by the requested home/device/module parameters. This is intentional:
the application trades per-user isolation of cached responses for the minimum
possible number of calls to the rate-limited Netatmo API.

## Process memory

Only reconstructable, process-local state stays in memory: REST clients,
deserialized configuration, the currently loaded system Netatmo token set, and
short-lived Salesforce access tokens. Their durable inputs remain in environment
configuration or PostgreSQL, so a dyno restart can rebuild them.

## Legacy migration

At startup, the application copies legacy `user:*` hashes and the old global
`netatmo:*` token keys from Redis into PostgreSQL when no corresponding row
exists. It leaves the Redis records intact for rollback safety. After a verified
production migration, set `LEGACY_REDIS_MIGRATION_ENABLED=false`; the old keys
can be removed separately after the rollback window closes.
