import crypto from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:18080/api';
const savedResponsePath = process.env.SAVED_RESPONSE_PATH || 'tmp-gemini-draft-save-response.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-player-flow-smoke-response.json';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-korea-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-korea-local';

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

async function request(path, { method = 'GET', token, body, expectedStatus } = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json; charset=utf-8' } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(60_000)
  });
  const text = await response.text();
  let parsed = null;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = { raw: text };
  }
  if (expectedStatus && response.status !== expectedStatus) {
    throw new Error(`Expected HTTP ${expectedStatus} for ${path}, got ${response.status}: ${text}`);
  }
  if (!expectedStatus && !response.ok) {
    throw new Error(`HTTP ${response.status} for ${path}: ${text}`);
  }
  return { status: response.status, body: parsed };
}

async function registerAndLogin() {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const email = `player-smoke-${suffix}@example.com`;
  const password = 'SmokeTest123!';
  const nickname = `player-smoke-${suffix}`;
  await request('/v1/auth/register', {
    method: 'POST',
    body: { email, password, nickname }
  });
  const login = await request('/v1/auth/login', {
    method: 'POST',
    body: { email, password, nickname }
  });
  const data = unwrap(login.body);
  return {
    token: data.token || data.accessToken,
    email
  };
}

function parsePayload(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  return JSON.parse(value);
}

function proofForInteraction(interaction) {
  const type = interaction?.type || '';
  const config = interaction?.config || {};
  switch (type) {
    case 'NUMBER_LOCK':
      return config.solutionDigits || '';
    case 'WORD_COMPOSE':
      return interaction.localSolution || '';
    case 'MEMORY_CARD':
      return 'MATCHED';
    case 'PATTERN_LOCK':
      return (config.nodes || []).join(',');
    case 'RAPID_TAP':
      return String(config.target ?? 7);
    case 'DIRECTION_SEQUENCE':
      return (config.sequence || []).join(',');
    case 'UP_DOWN_TIMER':
      return String(config.solution ?? '');
    case 'NUMBER_BASEBALL':
      return String(config.solution ?? '');
    case 'NUMBER_SEQUENCE_TAP':
      return expectedNumberTapProof(config);
    case 'COLOR_STROOP':
    case 'LEFT_RIGHT_SORT':
      return JSON.stringify({
        correctCount: Number(config.passCorrectCount || 1),
        totalRounds: Number(config.rounds || config.passCorrectCount || 1),
        wrongCount: 0,
        elapsedMillis: 1000
      });
    default:
      throw new Error(`Unsupported interaction type: ${type}`);
  }
}

function expectedNumberTapProof(config) {
  const sequence = Array.isArray(config.sequence) ? config.sequence : [];
  const skipNumbers = uniqueNumbers(config.skipNumbers, config.skipNumber);
  const doubleNumbers = uniqueNumbers(config.doubleNumbers, config.doubleNumber);
  const values = [];
  for (const value of sequence) {
    if (skipNumbers.includes(value)) continue;
    values.push(String(value));
    if (doubleNumbers.includes(value)) values.push(String(value));
  }
  return values.join(',');
}

function uniqueNumbers(arrayValue, fallback) {
  const values = Array.isArray(arrayValue) ? arrayValue : [];
  if (!Array.isArray(arrayValue) && Number.isInteger(fallback)) values.push(fallback);
  return [...new Set(values.filter(Number.isInteger))].slice(0, 2);
}

function minigameAnswer(puzzle) {
  const payload = parsePayload(puzzle?.rewardPayload || '{}');
  const interaction = payload.interaction;
  return `MG|${interaction.type}|${proofForInteraction(interaction)}`;
}

function arriveBody(spot) {
  return {
    userLat: spot.latitude,
    userLng: spot.longitude
  };
}

function publishPayload(saved) {
  return {
    title: saved.title,
    subtitle: saved.subtitle,
    era: saved.era,
    genre: saved.genre,
    difficulty: saved.difficulty,
    estimatedTime: saved.estimatedTime,
    estimatedDistance: saved.estimatedDistance,
    fictionSynopsis: saved.fictionSynopsis,
    missionDescription: saved.missionDescription,
    finalAnswerType: saved.finalAnswerType,
    finalAnswer: saved.finalAnswer,
    finalAnswerAliases: saved.finalAnswerAliases,
    finalAnswerKeywordItems: saved.finalAnswerKeywordItems,
    finalQuestion: saved.finalQuestion,
    finalTruthSummary: saved.finalTruthSummary,
    actualHistorySummary: saved.actualHistorySummary,
    deductionSecretFacts: saved.deductionSecretFacts,
    deductionForbiddenReveals: saved.deductionForbiddenReveals,
    maxDeductionQuestions: saved.maxDeductionQuestions,
    recommendedPlayers: saved.recommendedPlayers,
    teamRoleGuide: saved.teamRoleGuide,
    noticeText: saved.noticeText,
    status: 'PUBLISHED'
  };
}

async function main() {
  const raw = await readFile(savedResponsePath, 'utf8');
  const saved = unwrap(JSON.parse(raw));
  const episodeId = saved.id;
  const spots = saved.spots || [];
  const investigation = spots.filter((spot) => spot.markerType === 'ANSWER_HINT' && spot.finalPlace !== true);
  const finalSpot = spots.find((spot) => spot.finalPlace === true || spot.markerType === 'FINAL');
  if (!episodeId || investigation.length !== 8 || !finalSpot) {
    throw new Error('Saved response does not contain the expected episode flow.');
  }

  const adminToken = signJwt(adminEmail);
  const published = unwrap((await request(`/v1/admin/episodes/${episodeId}`, {
    method: 'PUT',
    token: adminToken,
    body: publishPayload(saved)
  })).body);
  if (published.status !== 'PUBLISHED') {
    throw new Error(`Episode was not published. status=${published.status}`);
  }

  const { token, email } = await registerAndLogin();
  await request(`/v1/episodes/${episodeId}/start`, { method: 'POST', token });

  const initialMap = unwrap((await request(`/v1/episodes/${episodeId}/map`, { token })).body);
  const lockedFinalArrive = await request(`/v1/episodes/${episodeId}/final-arrive`, {
    method: 'POST',
    token,
    body: arriveBody(finalSpot),
    expectedStatus: 403
  });
  const lockedDirectArrive = await request(`/v1/episodes/${episodeId}/spots/${finalSpot.spotId}/arrive`, {
    method: 'POST',
    token,
    body: arriveBody(finalSpot),
    expectedStatus: 403
  });

  const solved = [];
  for (const spot of investigation) {
    await request(`/v1/episodes/${episodeId}/spots/${spot.spotId}/arrive`, {
      method: 'POST',
      token,
      body: arriveBody(spot)
    });
    const puzzle = unwrap((await request(`/v1/spots/${spot.spotId}/puzzle`, { token })).body);
    const submit = unwrap((await request(`/v1/puzzles/${puzzle.puzzleId}/submit`, {
      method: 'POST',
      token,
      body: { answer: minigameAnswer(spot.puzzle) }
    })).body);
    if (submit.correct !== true) {
      throw new Error(`Puzzle submit failed for spot ${spot.spotId}`);
    }
    solved.push({ spotId: spot.spotId, puzzleId: puzzle.puzzleId });
  }

  const unlockedMap = unwrap((await request(`/v1/episodes/${episodeId}/map`, { token })).body);
  const finalArrive = unwrap((await request(`/v1/episodes/${episodeId}/final-arrive`, {
    method: 'POST',
    token,
    body: arriveBody(finalSpot)
  })).body);
  const deductionStart = unwrap((await request(`/v1/episodes/${episodeId}/deduction/start`, {
    method: 'POST',
    token
  })).body);
  const finalAnswer = unwrap((await request(`/v1/episodes/${episodeId}/final-answer`, {
    method: 'POST',
    token,
    body: {
      sessionId: deductionStart.sessionId,
      finalAnswer: saved.finalAnswer
    }
  })).body);
  const clearReport = unwrap((await request(`/v1/episodes/${episodeId}/clear-report`, { token })).body);

  const summary = {
    valid: true,
    email,
    episodeId,
    initialFinalDestinationUnlocked: initialMap.finalDestinationUnlocked,
    initialVisibleSpotCount: initialMap.spots?.length || 0,
    lockedFinalArriveCode: lockedFinalArrive.body?.code,
    lockedDirectArriveCode: lockedDirectArrive.body?.code,
    solvedCount: solved.length,
    unlockedFinalDestinationUnlocked: unlockedMap.finalDestinationUnlocked,
    unlockedVisibleSpotCount: unlockedMap.spots?.length || 0,
    finalArrived: finalArrive.arrived,
    canStartDeduction: finalArrive.canStartDeduction,
    deductionSessionId: deductionStart.sessionId,
    finalAnswerCorrect: finalAnswer.correct,
    finalAnswerStatus: finalAnswer.status,
    clearReportStatus: clearReport.status,
    clearReportCompletedSpotCount: clearReport.completedSpotCount,
    clearReportAnswerClueCount: clearReport.answerClueCount,
    clearReportTypedClueCounts: {
      CULPRIT: clearReport.culpritClues?.length || 0,
      WEAPON: clearReport.weaponClues?.length || 0,
      MOTIVE: clearReport.motiveClues?.length || 0,
      METHOD: clearReport.methodClues?.length || 0
    },
    issues: []
  };
  if (summary.initialFinalDestinationUnlocked !== false) summary.issues.push('final destination should be locked initially');
  if (summary.initialVisibleSpotCount !== 9) summary.issues.push(`expected 9 visible spots initially, got ${summary.initialVisibleSpotCount}`);
  if (summary.lockedFinalArriveCode !== 'FINAL_DESTINATION_LOCKED') summary.issues.push('final-arrive should be locked initially');
  if (summary.lockedDirectArriveCode !== 'FINAL_DESTINATION_LOCKED') summary.issues.push('direct final spot arrive should be locked initially');
  if (summary.solvedCount !== 8) summary.issues.push(`expected 8 solved investigation spots, got ${summary.solvedCount}`);
  if (summary.unlockedFinalDestinationUnlocked !== true) summary.issues.push('final destination should unlock after 8 investigation spots');
  if (summary.unlockedVisibleSpotCount !== 10) summary.issues.push(`expected 10 visible spots after unlock, got ${summary.unlockedVisibleSpotCount}`);
  if (summary.finalArrived !== true || summary.canStartDeduction !== true) summary.issues.push('final arrive should enable deduction');
  if (!summary.deductionSessionId) summary.issues.push('deduction session should be created');
  if (summary.finalAnswerCorrect !== true || summary.finalAnswerStatus !== 'CLEARED') summary.issues.push('final answer should clear the episode');
  if (summary.clearReportStatus !== 'CLEARED') summary.issues.push('clear report should be available after final answer');
  if (summary.clearReportCompletedSpotCount !== 8) summary.issues.push(`expected 8 completed spots in clear report, got ${summary.clearReportCompletedSpotCount}`);
  if (summary.clearReportAnswerClueCount !== 8) summary.issues.push(`expected 8 answer clues in clear report, got ${summary.clearReportAnswerClueCount}`);
  for (const slot of ['CULPRIT', 'WEAPON', 'MOTIVE', 'METHOD']) {
    if (summary.clearReportTypedClueCounts[slot] !== 2) {
      summary.issues.push(`expected 2 ${slot} clues in clear report, got ${summary.clearReportTypedClueCounts[slot]}`);
    }
  }
  summary.valid = summary.issues.length === 0;

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
