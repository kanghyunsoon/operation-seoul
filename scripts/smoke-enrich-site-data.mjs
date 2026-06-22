import crypto from 'node:crypto';
import { writeFile } from 'node:fs/promises';

const apiBaseUrl = process.env.API_BASE_URL || 'http://localhost:8080/api';
const adminEmail = process.env.ADMIN_EMAIL || 'admin@seoul.go.kr';
const jwtSecret = process.env.JWT_SECRET || 'operation-seoul-local-development-jwt-secret';
const jwtIssuer = process.env.JWT_ISSUER || 'operation-seoul-local';
const outputPath = process.env.OUTPUT_PATH || 'tmp-enrich-response.json';

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

const places = [
  ['덕수궁 대한문', '서울특별시 중구 세종대로 99', 'START'],
  ['정동길', '서울특별시 중구 정동길', 'ANSWER_HINT'],
  ['서울시립미술관', '서울특별시 중구 덕수궁길 61', 'ANSWER_HINT'],
  ['정동제일교회', '서울특별시 중구 정동길 46', 'ANSWER_HINT'],
  ['배재학당역사박물관', '서울특별시 중구 서소문로11길 19', 'ANSWER_HINT'],
  ['이화박물관', '서울특별시 중구 정동길 26', 'ANSWER_HINT'],
  ['경교장', '서울특별시 종로구 새문안로 29', 'ANSWER_HINT'],
  ['돈의문박물관마을', '서울특별시 종로구 송월길 14-3', 'ANSWER_HINT'],
  ['독립문', '서울특별시 서대문구 현저동 941', 'ANSWER_HINT'],
  ['서대문형무소역사관', '서울특별시 서대문구 통일로 251', 'FINAL']
].map(([name, address, role]) => ({
  name,
  address,
  role,
  publicMarkerType: role === 'START' ? 'START' : 'ANSWER_HINT',
  unlockCondition: role === 'FINAL' ? 'ALL_INVESTIGATION_MISSIONS_CLEARED' : undefined
}));

const request = {
  area: '서울 정동',
  era: '근현대',
  theme: '범죄 미스터리',
  targetAudience: '야외 방탈출 플레이어',
  playTime: '90~120분',
  selectedGenreId: 'CRIME_MYSTERY',
  selectedGenreName: '범죄 미스터리',
  finalAnswerKeywords: [
    '서윤재',
    '금속성 주입 장치',
    '연구 기록 조작 은폐',
    '피해자의 장비 점검 동선에 맞춰 주입 장치를 숨김'
  ],
  finalAnswerKeywordItems: [
    { slotId: 'CULPRIT', type: 'CULPRIT', label: '범인', displayType: '범인', keyword: '서윤재' },
    { slotId: 'WEAPON', type: 'WEAPON', label: '흉기', displayType: '흉기', keyword: '금속성 주입 장치' },
    { slotId: 'MOTIVE', type: 'MOTIVE', label: '동기', displayType: '동기', keyword: '연구 기록 조작 은폐' },
    { slotId: 'METHOD', type: 'METHOD', label: '방법', displayType: '방법', keyword: '피해자의 장비 점검 동선에 맞춰 주입 장치를 숨김' }
  ],
  finalAnswers: {
    culprit: '서윤재',
    weapon: '금속성 주입 장치',
    motive: '연구 기록 조작 은폐',
    method: '피해자의 장비 점검 동선에 맞춰 주입 장치를 숨김'
  },
  places,
  finalSpot: places.at(-1)
};

const response = await fetch(`${apiBaseUrl}/v1/admin/episodes/ai-draft/enrich-site-data`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${signJwt(adminEmail)}`,
    'Content-Type': 'application/json; charset=utf-8'
  },
  body: JSON.stringify(request)
});

const text = await response.text();
await writeFile(outputPath, text, 'utf8');

if (!response.ok) {
  console.error(`HTTP ${response.status}`);
  console.error(text);
  process.exit(1);
}

const parsed = JSON.parse(text);
const data = parsed.data || parsed;
const summary = {
  placeCount: data.places?.length || 0,
  finalSpotName: data.finalSpot?.name || null,
  selectedGenreId: data.selectedGenreId || null,
  finalAnswerKeywordCount: data.finalAnswerKeywordItems?.length || 0,
  notesPerPlace: (data.places || []).map((place) => ({
    name: place.name,
    notes: place.externalResearchNotes?.length || 0,
    urls: place.referenceUrls?.length || 0,
    summary: place.researchSourceSummary || null
  }))
};

console.log(JSON.stringify(summary, null, 2));
