# Production Environment

Run the backend with:

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
```

Required environment variables:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_ISSUER
CORS_ALLOWED_ORIGINS
GEMINI_API_KEY
GOOGLE_VISION_KEY
TOURAPI_SERVICE_KEY
KAKAO_REST_API_KEY
```

Optional environment variables:

```text
TMAP_APP_KEY
GEMINI_MODEL
PUZZLE_ATTEMPT_LIMIT
PUZZLE_ATTEMPT_WINDOW_SECONDS
PUZZLE_CLEANUP_INITIAL_DELAY_MS
PUZZLE_CLEANUP_INTERVAL_MS
DB_POOL_MAX_SIZE
DB_POOL_MIN_IDLE
DB_CONNECTION_TIMEOUT_MS
DB_VALIDATION_TIMEOUT_MS
DB_IDLE_TIMEOUT_MS
DB_MAX_LIFETIME_MS
JWT_VALIDITY_MS
```

Frontend:

```text
VITE_API_BASE_URL
VITE_API_TIMEOUT_MS
```

General API requests use a 20-second timeout by default. AI draft requests retain their separate extended timeout.

Production rules:

- `JWT_SECRET` must be a random value of at least 32 characters.
- Production JWTs default to a 2-hour lifetime and are rejected when the issuer does not match `JWT_ISSUER`.
- `CORS_ALLOWED_ORIGINS` must contain explicit deployed origins only.
- `DEV_ARRIVAL_ENABLED` must never be enabled.
- Secrets must be injected by the deployment platform, not committed to property files.
- Rotate any API key or database password that has previously appeared in a local property file, terminal output, screenshot, or shared archive.
- Preserve `X-Request-Id` through the reverse proxy and log collector.
- MySQL is required for the shared puzzle attempt guard.
- Expired puzzle attempt rows are removed hourly by default. Cleanup is idempotent and safe across multiple instances.
- The production profile uses graceful shutdown so in-flight requests have up to 30 seconds to finish.
- Request and multipart sizes are bounded to reduce accidental memory exhaustion.
- Database pool size and timeouts are configurable through environment variables.
- Use `/actuator/health/liveness` for process health and `/actuator/health/readiness` for traffic routing.
- Health details are hidden from unauthenticated callers; non-health actuator endpoints remain unexposed.
