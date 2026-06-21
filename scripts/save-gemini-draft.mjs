import crypto from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:18080/api';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-seoul-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-seoul-local';
const draftResponsePath = process.env.DRAFT_RESPONSE_PATH || 'tmp-gemini-draft-response.json';
const sourceInputPath = process.env.SOURCE_INPUT_PATH || 'tmp-enrich-response-18080.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-gemini-draft-save-response.json';
const errorOutputPath = process.env.ERROR_OUTPUT_PATH || 'tmp-gemini-draft-save-error.json';
const REQUIRED_SLOTS = ['CULPRIT', 'WEAPON', 'MOTIVE', 'METHOD'];
const FORBIDDEN_ORDINAL_SUSPECT_REFS = ['첫 번째 용의자', '두 번째 용의자', '세 번째 용의자'];
const FORBIDDEN_GENERIC_SUSPECT_REFS = [
  '특정 용의자',
  '용의자 중 한 명',
  '용의자 중 하나',
  '해당 용의자',
  '해고 통보를 받은 용의자'
];

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

function unwrap(payload) {
  return payload?.data || payload;
}

function parseRewardPayload(spot) {
  try {
    return JSON.parse(spot?.puzzle?.rewardPayload || '{}');
  } catch {
    return {};
  }
}

function validateSavedEpisode(saved) {
  const issues = [];
  const spots = saved.spots || [];
  const finalSpots = spots.filter((spot) => spot.finalPlace === true || spot.markerType === 'FINAL');
  const investigation = spots.filter((spot) =>
    spot.markerType === 'ANSWER_HINT' &&
    spot.finalPlace !== true
  );
  const slotCounts = Object.fromEntries(REQUIRED_SLOTS.map((slot) => [slot, 0]));

  if ((saved.finalAnswerKeywordItems || []).length !== 4) {
    issues.push('finalAnswerKeywordItems must contain exactly 4 items.');
  }
  if (spots.length !== 10) issues.push(`expected 10 spots, got ${spots.length}`);
  if (finalSpots.length !== 1) issues.push(`expected 1 final spot, got ${finalSpots.length}`);
  if (investigation.length !== 8) issues.push(`expected 8 investigation spots, got ${investigation.length}`);

  for (const finalSpot of finalSpots) {
    if (finalSpot.markerType !== 'FINAL') issues.push('final spot markerType must be FINAL.');
    if (finalSpot.clueRole !== 'FINAL_PLACE') issues.push('final spot clueRole must be FINAL_PLACE.');
    if (finalSpot.publicMarkerType !== 'ANSWER_HINT') issues.push('final spot publicMarkerType must stay ANSWER_HINT.');
  }

  for (const spot of investigation) {
    const payload = parseRewardPayload(spot);
    const clueReward = payload.rewards?.[0] || {};
    if (clueReward.type !== 'ANSWER_CLUE') issues.push(`spot ${spot.spotId} first reward must be ANSWER_CLUE.`);
    if (clueReward.slotId !== 'ANSWER_CLUE') issues.push(`spot ${spot.spotId} slotId must be ANSWER_CLUE.`);
    if (!REQUIRED_SLOTS.includes(clueReward.targetKeywordType)) {
      issues.push(`spot ${spot.spotId} missing valid targetKeywordType.`);
    } else {
      slotCounts[clueReward.targetKeywordType] += 1;
    }
    const supports = clueReward.supportsKeywordSlots || [];
    if (JSON.stringify(supports) !== JSON.stringify([clueReward.targetKeywordType])) {
      issues.push(`spot ${spot.spotId} supportsKeywordSlots must match targetKeywordType.`);
    }
  }

  for (const slot of REQUIRED_SLOTS) {
    if (slotCounts[slot] !== 2) issues.push(`${slot} must be saved exactly twice, got ${slotCounts[slot]}`);
  }
  const serialized = JSON.stringify(saved);
  if (serialized.includes(String.fromCharCode(0xFFFD))) {
    issues.push('saved response contains Unicode replacement characters.');
  }
  for (const ref of FORBIDDEN_ORDINAL_SUSPECT_REFS) {
    if (serialized.includes(ref)) issues.push(`saved response contains ordinal suspect reference: ${ref}`);
  }
  for (const ref of FORBIDDEN_GENERIC_SUSPECT_REFS) {
    if (serialized.includes(ref)) {
      issues.push(`saved response contains weak generic suspect reference: ${ref}`);
    }
  }

  return {
    valid: issues.length === 0,
    issues,
    episodeId: saved.id,
    title: saved.title,
    status: saved.status,
    spotCount: spots.length,
    investigationCount: investigation.length,
    finalSpotCount: finalSpots.length,
    slotCounts
  };
}

const draftResponse = unwrap(JSON.parse(await readFile(draftResponsePath, 'utf8')));
const sourceInput = unwrap(JSON.parse(await readFile(sourceInputPath, 'utf8')));
const body = {
  draft: draftResponse.draft || draftResponse,
  sourceInput,
  validationResult: draftResponse.validationResult || null,
  status: 'DRAFT',
  saveReason: 'local smoke save',
  adminReviewed: true,
  adminOverrideReasons: []
};

const response = await fetch(`${apiBaseUrl}/v1/admin/episodes/ai-draft/save`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${signJwt(adminEmail)}`,
    'Content-Type': 'application/json; charset=utf-8'
  },
  body: JSON.stringify(body),
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
  const saved = unwrap(JSON.parse(text));
  const summary = validateSavedEpisode(saved);
  console.log(JSON.stringify(summary, null, 2));
  if (!summary.valid) {
    console.error('Saved response, but saved episode validation failed.');
    process.exitCode = 1;
  }
}
