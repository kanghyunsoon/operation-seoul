# Production Readiness Baseline

This project is moving beyond MVP. The following items are now part of the production baseline.

## Implemented Production-Oriented Changes

- Minigames are fixed Vue components, not generated UI.
- Minigame completion is verified server-side through `MG|TYPE|VALUE` proof validation.
- Puzzle rewards remain server-controlled through `rewardPayload.rewards`.
- AI draft generation enriches site data before Gemini generation when needed.
- AI image generation is not called from the app. Admins receive per-card `imagePrompt` values and manually paste generated image URLs.
- Suspect and evidence image prompts are stored in DB through `case_suspects.image_prompt` and `case_evidences.image_prompt`.
- Publishing is blocked until every spot has field verification metadata.
- Publishing is blocked until every suspect/evidence card has a reviewed image prompt and an external HTTP(S) image URL.
- Puzzle submissions use a MySQL-backed wrong-answer guard shared across application instances.
- Expired puzzle attempt records are removed by a fault-tolerant scheduled cleanup job.
- The production profile enables graceful shutdown, bounded request sizes, response compression, and explicit DB pool timeouts.
- Deployment liveness/readiness probes are available through restricted Spring Boot health endpoints.
- JWT validation enforces a configured issuer and bounded production token lifetime.
- Core play requests use Bean Validation for coordinates, puzzle answers, deduction questions, and final answers.
- API responses and logs include `X-Request-Id` correlation IDs.
- The 10 minigame proof formats have automated validator coverage.
- The `prod` profile rejects weak JWT secrets, wildcard/local CORS origins, and dev arrival bypass.
- Runtime secrets are loaded from environment variables instead of committed property values.
- Admin episode create/edit/publish/archive/delete operations are stored in a persistent audit timeline with actor and request ID.
- Built-in sample seed data is kept as `DRAFT` and marked as requiring field verification.

## Image Workflow

1. Gemini or fallback draft creates one `imagePrompt` per suspect/evidence card.
2. Admin reviews each prompt in the mission generation screen.
3. Admin copies one card prompt at a time into an external image tool.
4. Admin pastes the generated image URL into that same card.
5. Saved episode keeps both `imageUrl` and `imagePrompt` for later regeneration.

## Remaining Production Gaps

- Enforce backend tests and frontend production builds in CI.
- Add browser-level QA for all 10 minigames on mobile viewport.
- Add stricter image URL policy: allowed domains, file size guidance, moderation checks, and explicit fallback approval workflow.
- Add external metrics/alerting for AI draft failures, puzzle failure rates, and completion rates.
- Add secret-manager integration in the deployment platform and rotate previously exposed development keys.

## Verified In This Workspace

- Backend Gradle tests pass on the configured Java toolchain.
- Frontend Vite production build passes.
- All 10 minigame proof formats have automated validation coverage.
- Admin audit service and puzzle cleanup job have focused tests.
- Known plaintext development API keys were removed from committed configuration.

## Final Manual Release Checklist

- Rotate every API key and database password that previously appeared in local files.
- Configure the `prod` profile and all variables in `PRODUCTION_ENVIRONMENT.md`.
- Run the complete route on a real mobile device with GPS and mobile data.
- Test all 10 minigames on both Android and iOS browser viewports.
- Verify every spot's coordinates, access hours, signage, and arrival radius on site.
- Confirm every suspect/evidence image URL is durable, licensed, and appropriate.
- Configure deployment liveness/readiness probes and preserve `X-Request-Id` in proxy logs.
- Confirm database backups and restore procedures before accepting real users.

## Non-Negotiable Release Gate

Do not publish a route unless:

- All spots have verified coordinates and arrival radius.
- All puzzle answers pass server validation.
- All reward payloads validate.
- All suspect/evidence cards have reviewed text, a card-specific `imagePrompt`, and a real external HTTP(S) `imageUrl`.
- Final place is not exposed publicly.
- The route has been played end-to-end on a mobile device.
