# Local database reset scripts

Use the canonical shell entry point from the repo root:

```bash
./scripts/reset-local-data.sh
```

That script drops and recreates service databases and preserves the demo `SUPER_ADMIN` in `authdb`.

`reset-except-admin.sql` is an optional manual alternative for Postgres-only resets when you prefer `psql` directly.
