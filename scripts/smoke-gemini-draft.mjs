import crypto from 'node:crypto';
import { readFile, rm, writeFile } from 'node:fs/promises';
import { buildValidationSummary } from './validate-ai-draft.mjs';

// This script intentionally makes one draft-generation request.
// If local validation fails, fix prompt/code/guardrails before running it again.
const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:18080/api';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-seoul-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-seoul-local';
const inputPath = process.env.INPUT_PATH || 'tmp-enrich-response-18080.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-gemini-draft-response.json';
const errorOutputPath = process.env.ERROR_OUTPUT_PATH || 'tmp-gemini-draft-error.json';
const debugCluePath = process.env.DEBUG_CLUE_PATH || 'backend/build/ai-draft-debug/latest-pre-guardrail-investigation-clues.json';

function base64Url(input) {
  return Buffer.from(input)
    .toString('base64')
    .replaceAll('=', '')
    .replaceAll('+', '-')
    .replaceAll('/', '_');
}

function signJwt(email) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = base64Url(JSON.stringify({
    sub: email,
    iss: jwtIssuer,
    iat: now,
    exp: now + 3600
  }));
  const signature = crypto
    .createHmac('sha256', jwtSecret)
    .update(`${header}.${payload}`)
    .digest('base64')
    .replaceAll('=', '')
    .replaceAll('+', '-')
    .replaceAll('/', '_');
  return `${header}.${payload}.${signature}`;
}

const enriched = JSON.parse(await readFile(inputPath, 'utf8'));
const payload = enriched.data || enriched;
await rm(debugCluePath, { force: true });

const response = await fetch(`${apiBaseUrl}/v1/admin/episodes/ai-draft/gemini`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${signJwt(adminEmail)}`,
    'Content-Type': 'application/json; charset=utf-8'
  },
  body: JSON.stringify(payload),
  signal: AbortSignal.timeout(180_000)
});

const text = await response.text();

if (!response.ok) {
  await writeFile(errorOutputPath, text, 'utf8');
  console.error(`HTTP ${response.status}`);
  console.error(text);
  console.error(`Saved error response to ${errorOutputPath}`);
  process.exitCode = 1;
} else {
  await writeFile(outputPath, text, 'utf8');

  const parsed = JSON.parse(text);
  const body = parsed.data || parsed;
  const draft = body.draft || {};
  const missions = draft.missions || [];
  const investigation = missions.filter((mission) =>
    mission.markerType === 'ANSWER_HINT' && mission.finalPlace !== true
  );

  console.log(JSON.stringify({
    publishable: body.publishable,
    warningCount: body.validationWarnings?.length || 0,
    warnings: body.validationWarnings || [],
    title: draft.episodeTitle || null,
    genre: draft.genre || null,
    finalAnswerKeywordItemCount: draft.finalAnswerKeywordItems?.length || 0,
    suspectCount: draft.suspects?.length || 0,
    missionCount: missions.length,
    investigationClueCount: investigation.length,
    finalMission: missions.find((mission) => mission.finalPlace === true || mission.markerType === 'FINAL') || null,
    clueSlots: investigation.map((mission) => ({
      order: mission.order,
      targetKeywordType: mission.targetKeywordType,
      rewardClue: mission.rewardClue
    }))
  }, null, 2));

  const validationSummary = buildValidationSummary(parsed, outputPath);
  console.log('\nLocal draft validation:');
  console.log(JSON.stringify(validationSummary, null, 2));
  if (!validationSummary.valid) {
    console.error('Saved response, but local draft validation failed. Fix prompt/code/guardrails before calling Gemini again.');
    process.exitCode = 1;
  }
}
