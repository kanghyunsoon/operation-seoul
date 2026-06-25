import crypto from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:18080/api';
const savedResponsePath = process.env.SAVED_RESPONSE_PATH || 'tmp-gemini-draft-save-response.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-player-content-quality-response.json';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-korea-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-korea-local';

const forbiddenPlayerPhrases = [
  'TourAPI',
  'RAG',
  '기록 속 인물 서명 확인',
  '사건이 시작된 장소에서 발견된 봉투와 훼손된 기록 조각입니다.',
  '범인: 강수진',
  '흉기: 독성 캡슐',
  '동기: 비밀 계약 은폐',
  '방법: 약병 바꿔치기',
  '실제 장소',
  '가상의 용의자',
  '관리자 검수',
  '관리자검수',
  '특정 용의자',
  '용의자 중 한 명',
  '용의자 중 하나',
  '해당 용의자',
  '해고 통보를 받은 용의자',
  '용의자의 재직 기록',
  '용의자의 개인 소지품',
  '용의자가 피해자에게',
  '용의자가 피해자의',
  '용의자의 동선',
  '문서에 언급된 인물 사이에',
  '문서에 언급된 인물는',
  '문서에 언급된 인물이',
  '문서에 언급된 인물의',
  '관련 인물가',
  '관련 인물는',
  '사건 기록 속 인물가',
  '사건 기록 속 인물는',
  "'기록 속 인물,",
  '기록 속 인물이',
  '기록 속 인물은',
  '이해관계가 드러난 인물이',
  '이해관계가 드러난 인물은',
  '동선이 겹친 인물이',
  '기록 속 인물가',
  '기록 속 인물는',
  '문서에 언급된 인물가',
  '물증과 연결된 인물가',
  '물증과 연결된 인물는',
  '이해관계가 드러난 인물가',
  '이해관계가 드러난 인물는',
  '동선이 겹친 인물가',
  '동선이 겹친 인물는',
  '장소명 글자',
  'DESTINATION_HINT',
  'DESTINATION_CLUE',
  'PLACE_HINT',
  'FINAL_DESTINATION'
];

const forbiddenPreClearPhrases = [
  '정답은',
  '범인:',
  '흉기:',
  '동기:',
  '방법:'
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
  const email = `player-quality-${suffix}@example.com`;
  const password = 'SmokeTest123!';
  const nickname = `player-quality-${suffix}`;
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

function collectTextValues(value, path = '$', result = []) {
  if (typeof value === 'string') {
    result.push({ path, value });
    return result;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => collectTextValues(item, `${path}[${index}]`, result));
    return result;
  }
  if (value && typeof value === 'object') {
    for (const [key, child] of Object.entries(value)) {
      collectTextValues(child, `${path}.${key}`, result);
    }
  }
  return result;
}

function scanForbidden(textEntries, phrases, scope) {
  const issues = [];
  for (const entry of textEntries) {
    if (entry.value.includes(String.fromCharCode(0xFFFD))) {
      issues.push({ scope, path: entry.path, phrase: 'replacement-character', value: entry.value });
    }
    if (/\\u[0-9a-fA-F]{4}/.test(entry.value)) {
      issues.push({ scope, path: entry.path, phrase: 'literal-unicode-escape', value: entry.value });
    }
    for (const phrase of phrases) {
      if (entry.value.includes(phrase)) {
        issues.push({ scope, path: entry.path, phrase, value: entry.value });
      }
    }
  }
  return issues;
}

function finalAnswerValues(saved) {
  const items = Array.isArray(saved.finalAnswerKeywordItems) ? saved.finalAnswerKeywordItems : [];
  return items
    .map((item) => String(item.value || item.keyword || '').trim())
    .filter(Boolean);
}

function scanPreClearAnswerLeaks(textEntries, saved) {
    const answers = finalAnswerValues(saved);
    const leaks = [];
    for (const entry of textEntries) {
    if (!isPreClearClueTextPath(entry.path)) continue;
        for (const answer of answers) {
            if (answer.length >= 2 && entry.value.includes(answer)) {
                leaks.push({ scope: 'preClear', path: entry.path, phrase: `answer:${answer}`, value: entry.value });
            }
        }
    }
    return leaks;
}

function scanStaleAnswerTuple(saved, textEntries) {
  const values = finalAnswerValues(saved).join('|');
  const staleTuple = ['강수진', '독성 캡슐', '비밀 계약 은폐', '약병 바꿔치기'];
  const savedUsesStaleTuple = staleTuple.every((value) => values.includes(value));
  const textUsesStaleTuple = staleTuple.every((value) => textEntries.some((entry) => entry.value.includes(value)));
  return savedUsesStaleTuple || textUsesStaleTuple
    ? [{ scope: 'playerText', path: '$', phrase: 'stale-default-answer-tuple', value: values }]
    : [];
}

function scanDuplicateDeductionClues(snapshots) {
  const issues = [];
  for (const snapshot of snapshots) {
    const summary = unwrap(snapshot.body)?.clueSummary;
    if (!summary) continue;
    const bySlot = [
      ['culpritClues', summary.culpritClues],
      ['weaponClues', summary.weaponClues],
      ['motiveClues', summary.motiveClues],
      ['methodClues', summary.methodClues]
    ];
    const seen = new Map();
    for (const [slot, clues] of bySlot) {
      for (const clue of uniqueStrings(clues)) {
        const key = normalizeText(clue);
        if (!key) continue;
        if (seen.has(key) && seen.get(key) !== slot) {
          issues.push({ scope: 'deductionClues', path: `${snapshot.scope}.clueSummary.${slot}`, phrase: 'duplicate-slot-clue', value: clue });
        } else {
          seen.set(key, slot);
        }
      }
    }
  }
  return issues;
}

function uniqueStrings(values = []) {
  return [...new Set((Array.isArray(values) ? values : []).map((value) => String(value || '').trim()).filter(Boolean))];
}

function normalizeText(value) {
  return String(value || '').replace(/\s+/g, '').toLowerCase();
}

function isPreClearClueTextPath(path) {
  return path.includes('.rewardClue')
    || path.includes('.clueBoard')
    || path.includes('.newlyUnlockedItems')
    || path.includes('.collectedClues')
    || path.includes('.answerClues')
    || path.includes('.culpritClues')
    || path.includes('.weaponClues')
    || path.includes('.motiveClues')
    || path.includes('.methodClues')
    || path.includes('.coreClues')
    || path.includes('.relatedPersonClues')
    || path.includes('.storyClues')
    || path.includes('.destinationClues');
}

async function main() {
  const saved = unwrap(JSON.parse(await readFile(savedResponsePath, 'utf8')));
  const episodeId = saved.id;
  const spots = saved.spots || [];
  const investigation = spots.filter((spot) => spot.markerType === 'ANSWER_HINT' && spot.finalPlace !== true);
  const finalSpot = spots.find((spot) => spot.finalPlace === true || spot.markerType === 'FINAL');
  if (!episodeId || investigation.length !== 8 || !finalSpot) {
    throw new Error('Saved response does not contain the expected episode flow.');
  }

  const adminToken = signJwt(adminEmail);
  await request(`/v1/admin/episodes/${episodeId}`, {
    method: 'PUT',
    token: adminToken,
    body: publishPayload(saved)
  });

  const { token, email } = await registerAndLogin();
  const snapshots = [];
  const addSnapshot = (scope, body) => snapshots.push({ scope, body: unwrap(body) });

  addSnapshot('episodeDetailBeforeStart', (await request(`/v1/episodes/${episodeId}`, { token })).body);
  addSnapshot('startEpisode', (await request(`/v1/episodes/${episodeId}/start`, { method: 'POST', token })).body);
  addSnapshot('initialMap', (await request(`/v1/episodes/${episodeId}/map`, { token })).body);
  addSnapshot('initialClueBoard', (await request(`/v1/episodes/${episodeId}/clue-board`, { token })).body);

  const preClearSnapshots = [...snapshots];
  for (const spot of investigation) {
    addSnapshot(`arrive:${spot.spotId}`, (await request(`/v1/episodes/${episodeId}/spots/${spot.spotId}/arrive`, {
      method: 'POST',
      token,
      body: arriveBody(spot)
    })).body);
    const puzzle = unwrap((await request(`/v1/spots/${spot.spotId}/puzzle`, { token })).body);
    addSnapshot(`puzzle:${spot.spotId}`, puzzle);
    addSnapshot(`submit:${spot.spotId}`, (await request(`/v1/puzzles/${puzzle.puzzleId}/submit`, {
      method: 'POST',
      token,
      body: { answer: minigameAnswer(spot.puzzle) }
    })).body);
    addSnapshot(`clueBoardAfter:${spot.spotId}`, (await request(`/v1/episodes/${episodeId}/clue-board`, { token })).body);
  }

  addSnapshot('unlockedMap', (await request(`/v1/episodes/${episodeId}/map`, { token })).body);
  addSnapshot('finalArrive', (await request(`/v1/episodes/${episodeId}/final-arrive`, {
    method: 'POST',
    token,
    body: arriveBody(finalSpot)
  })).body);
  const deductionStart = unwrap((await request(`/v1/episodes/${episodeId}/deduction/start`, {
    method: 'POST',
    token
  })).body);
  addSnapshot('deductionStart', deductionStart);

  const allPreClearEntries = snapshots.flatMap((snapshot) =>
    collectTextValues(snapshot.body).map((entry) => ({ ...entry, path: `${snapshot.scope}${entry.path.slice(1)}` }))
  );
  preClearSnapshots.length = 0;

  addSnapshot('finalAnswer', (await request(`/v1/episodes/${episodeId}/final-answer`, {
    method: 'POST',
    token,
    body: {
      sessionId: deductionStart.sessionId,
      finalAnswer: saved.finalAnswer
    }
  })).body);
  addSnapshot('clearReport', (await request(`/v1/episodes/${episodeId}/clear-report`, { token })).body);

  const allEntries = snapshots.flatMap((snapshot) =>
    collectTextValues(snapshot.body).map((entry) => ({ ...entry, path: `${snapshot.scope}${entry.path.slice(1)}` }))
  );
  const issues = [
    ...scanForbidden(allEntries, forbiddenPlayerPhrases, 'playerText'),
    ...scanForbidden(allPreClearEntries, forbiddenPreClearPhrases, 'preClearText'),
    ...scanPreClearAnswerLeaks(allPreClearEntries, saved),
    ...scanStaleAnswerTuple(saved, allEntries),
    ...scanDuplicateDeductionClues(snapshots)
  ];

  const summary = {
    valid: issues.length === 0,
    email,
    episodeId,
    snapshotCount: snapshots.length,
    preClearTextCount: allPreClearEntries.length,
    allTextCount: allEntries.length,
    issueCount: issues.length,
    issues: issues.slice(0, 50)
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
