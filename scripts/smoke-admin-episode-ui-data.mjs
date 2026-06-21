import crypto from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:18080/api';
const savedResponsePath = process.env.SAVED_RESPONSE_PATH || 'tmp-gemini-draft-save-response.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-admin-ui-data-smoke-response.json';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-seoul-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-seoul-local';
const requiredSlots = ['CULPRIT', 'WEAPON', 'MOTIVE', 'METHOD'];
const slotLabels = {
  CULPRIT: '범인',
  WEAPON: '흉기',
  MOTIVE: '동기',
  METHOD: '방법'
};

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

async function request(path) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    headers: { Authorization: `Bearer ${signJwt(adminEmail)}` },
    signal: AbortSignal.timeout(60_000)
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ${path}: ${text}`);
  }
  return unwrap(JSON.parse(text));
}

function parseRewardPayload(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  return JSON.parse(value);
}

function firstAnswerReward(spot) {
  const payload = parseRewardPayload(spot?.puzzle?.rewardPayload);
  const rewards = Array.isArray(payload.rewards) ? payload.rewards : [];
  return rewards.find((reward) => reward?.type === 'ANSWER_CLUE') || null;
}

function slotLabel(spot) {
  const reward = firstAnswerReward(spot);
  const slot = requiredSlots.includes(reward?.targetKeywordType) ? reward.targetKeywordType : '';
  if (!slot) return '';
  const supports = Array.isArray(reward.supportsKeywordSlots)
    ? reward.supportsKeywordSlots.filter((value) => requiredSlots.includes(value))
    : [];
  const supportLabels = supports
    .filter((value) => value !== slot)
    .map((value) => slotLabels[value] || value)
    .join(',');
  return supportLabels ? `${slotLabels[slot] || slot} / 보조 ${supportLabels}` : slotLabels[slot] || slot;
}

async function main() {
  const saved = unwrap(JSON.parse(await readFile(savedResponsePath, 'utf8')));
  const episode = await request(`/v1/admin/episodes/${saved.id}`);
  const readiness = await request(`/v1/admin/episodes/${saved.id}/publish-readiness`);
  const spots = episode.spots || [];
  const investigations = spots.filter((spot) => spot.markerType === 'ANSWER_HINT' && spot.finalPlace !== true);
  const finals = spots.filter((spot) => spot.finalPlace === true || spot.markerType === 'FINAL');
  const slotCounts = Object.fromEntries(requiredSlots.map((slot) => [slot, 0]));
  const labels = [];
  const issues = [];

  for (const spot of investigations) {
    const reward = firstAnswerReward(spot);
    if (!reward) {
      issues.push(`missing ANSWER_CLUE reward for spot ${spot.spotId}`);
      continue;
    }
    if (!requiredSlots.includes(reward.targetKeywordType)) {
      issues.push(`invalid targetKeywordType for spot ${spot.spotId}: ${reward.targetKeywordType}`);
      continue;
    }
    slotCounts[reward.targetKeywordType] += 1;
    if (JSON.stringify(reward.supportsKeywordSlots) !== JSON.stringify([reward.targetKeywordType])) {
      issues.push(`supportsKeywordSlots mismatch for spot ${spot.spotId}`);
    }
    labels.push({ spotId: spot.spotId, label: slotLabel(spot) });
  }

  if (spots.length !== 10) issues.push(`expected 10 spots, got ${spots.length}`);
  if (investigations.length !== 8) issues.push(`expected 8 investigation spots, got ${investigations.length}`);
  if (finals.length !== 1) issues.push(`expected 1 final spot, got ${finals.length}`);
  for (const slot of requiredSlots) {
    if (slotCounts[slot] !== 2) issues.push(`expected 2 ${slot} labels, got ${slotCounts[slot]}`);
  }
  for (const final of finals) {
    if (final.markerType !== 'FINAL') issues.push('final spot markerType must be FINAL');
    if (final.publicMarkerType !== 'ANSWER_HINT') issues.push('final spot publicMarkerType must remain ANSWER_HINT');
    if (final.clueRole !== 'FINAL_PLACE') issues.push('final spot clueRole must be FINAL_PLACE');
  }
  const serialized = JSON.stringify(episode);
  if (serialized.includes(String.fromCharCode(0xFFFD))) issues.push('admin detail contains replacement character');
  if (serialized.includes('특정 용의자')) issues.push('admin detail contains repetitive generic suspect reference: 특정 용의자');

  const summary = {
    valid: issues.length === 0,
    episodeId: episode.id,
    title: episode.title,
    status: episode.status,
    readinessReady: readiness.ready,
    spotCount: spots.length,
    investigationCount: investigations.length,
    finalCount: finals.length,
    slotCounts,
    labels,
    final: finals.map((spot) => ({
      spotId: spot.spotId,
      markerType: spot.markerType,
      publicMarkerType: spot.publicMarkerType,
      clueRole: spot.clueRole,
      finalPlace: spot.finalPlace,
      rewardClue: spot.puzzle?.rewardClue
    })),
    issues
  };

  await writeFile(outputPath, JSON.stringify(summary, null, 2), 'utf8');
  console.log(JSON.stringify(summary, null, 2));
  if (!summary.valid) process.exitCode = 1;
}

main().catch(async (error) => {
  const summary = { valid: false, error: error?.message || String(error) };
  await writeFile(outputPath, JSON.stringify(summary, null, 2), 'utf8');
  console.error(JSON.stringify(summary, null, 2));
  process.exitCode = 1;
});
