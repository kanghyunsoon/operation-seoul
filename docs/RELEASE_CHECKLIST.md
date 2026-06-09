# Release Checklist

## Automated

```powershell
.\scripts\verify-release.ps1
```

Both commands must pass before deployment.

The same verification runs automatically on pull requests and pushes to `main` through
`.github/workflows/release-verification.yml`.

## Environment

- Set `SPRING_PROFILES_ACTIVE=prod`.
- Inject secrets using the deployment platform or secret manager.
- Set an explicit deployed origin in `CORS_ALLOWED_ORIGINS`.
- Keep `DEV_ARRIVAL_ENABLED=false`.
- Configure `/actuator/health/liveness` and `/actuator/health/readiness`.

## Content

- Complete on-site verification for every mission spot.
- Play the full route on a real mobile device.
- Verify all ten minigames.
- Review image prompts and generated card images individually.
- Confirm final answers and final locations are not exposed by public APIs.

## Operations

- Rotate previously exposed development credentials.
- Configure database backups and test a restore.
- Preserve `X-Request-Id` in reverse-proxy and application logs.
- Confirm alerting and rollback procedures.
