# CustomerService

Customer profile, KYC lifecycle, and customer existence checks for the MockBank platform.

- **Port:** `8083`
- **Database:** PostgreSQL `customerdb`
- **Auth:** JWT from AuthUser (`issuer-uri` + `auth.jwt.audience`)

## Related modules

- `commons-dto` — shared DTOs and exception handling
- `commons-security` — JWT resource server and Feign M2M client config
- `commons-observability` — correlation ID and access logging

See [docs/system-overview-and-runbook.md](../docs/system-overview-and-runbook.md) for local run instructions.
