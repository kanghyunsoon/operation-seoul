import { readFile, writeFile } from 'node:fs/promises';

const sourcePath = process.env.SOURCE_PATH || 'tmp-enrich-response-18080.json';
const outputPath = process.env.OUTPUT_PATH || 'tmp-region-enrich-response.json';

const presets = {
  chungcheong: {
    area: '\uCDA9\uCCAD\uAD8C \uB300\uC804',
    era: '\uD604\uB300',
    theme: '\uCDA9\uCCAD\uAD8C \uB3C4\uC2EC \uACFC\uD559\uBB38\uD654\uAD8C \uC57C\uC678 \uBC29\uD0C8\uCD9C \uAC80\uC99D',
    places: [
      ['\uB300\uC804\uC5ED', '\uB300\uC804\uAD11\uC5ED\uC2DC \uB3D9\uAD6C \uC911\uC559\uB85C 215', 'START'],
      ['\uC131\uC2EC\uB2F9 \uBCF8\uC810 \uC77C\uB300', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC911\uAD6C \uB300\uC885\uB85C480\uBC88\uAE38 15', 'ANSWER_HINT'],
      ['\uB300\uC804\uADFC\uD604\uB300\uC0AC\uC804\uC2DC\uAD00', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC911\uAD6C \uC911\uC559\uB85C 101', 'ANSWER_HINT'],
      ['\uC6B0\uC554\uC0AC\uC801\uACF5\uC6D0', '\uB300\uC804\uAD11\uC5ED\uC2DC \uB3D9\uAD6C \uCDA9\uC815\uB85C 53', 'ANSWER_HINT'],
      ['\uD55C\uBC2D\uC218\uBAA9\uC6D0', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC11C\uAD6C \uB454\uC0B0\uB300\uB85C 169', 'ANSWER_HINT'],
      ['\uB300\uC804\uC608\uC220\uC758\uC804\uB2F9', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC11C\uAD6C \uB454\uC0B0\uB300\uB85C 135', 'ANSWER_HINT'],
      ['\uC5D1\uC2A4\uD3EC\uACFC\uD559\uACF5\uC6D0', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC720\uC131\uAD6C \uB300\uB355\uB300\uB85C 480', 'ANSWER_HINT'],
      ['\uCE74\uC774\uC2A4\uD2B8 \uC815\uBB38 \uC77C\uB300', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC720\uC131\uAD6C \uB300\uD559\uB85C 291', 'ANSWER_HINT'],
      ['\uC720\uC131\uC628\uCC9C\uC5ED \uC77C\uB300', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC720\uC131\uAD6C \uBD09\uBA85\uB3D9', 'ANSWER_HINT'],
      ['\uAD6D\uB9BD\uC911\uC559\uACFC\uD559\uAD00', '\uB300\uC804\uAD11\uC5ED\uC2DC \uC720\uC131\uAD6C \uB300\uB355\uB300\uB85C 481', 'FINAL']
    ],
    finalAnswers: {
      culprit: '\uD55C\uC9C0\uC6D0',
      weapon: '\uB3C5\uC131 \uC2DC\uC57D\uC774 \uC11E\uC778 \uC5F0\uAD6C\uC2E4 \uC74C\uB8CC',
      motive: '\uC5F0\uAD6C \uC870\uC791 \uC740\uD3D0',
      method: '\uD53C\uD574\uC790\uC758 \uB9E4\uC77C \uC2DC\uD5D8 \uC804 \uB9C8\uC2DC\uB294 \uC74C\uB8CC\uB97C \uB3C5\uC131 \uC74C\uB8CC\uB85C \uBC14\uAFD4\uCE58\uAE30'
    }
  },
  busan: {
    area: '부산 원도심',
    era: '현대',
    theme: '부산 항만 기록 미스터리 검증',
    places: [
      ['부산역', '부산광역시 동구 중앙대로 206', 'START'],
      ['초량이바구길', '부산광역시 동구 초량동 일대', 'ANSWER_HINT'],
      ['부산근현대역사관', '부산광역시 중구 대청로 104', 'ANSWER_HINT'],
      ['용두산공원', '부산광역시 중구 용두산길 37-55', 'ANSWER_HINT'],
      ['부산타워', '부산광역시 중구 용두산길 37-30', 'ANSWER_HINT'],
      ['국제시장', '부산광역시 중구 신창동4가', 'ANSWER_HINT'],
      ['보수동책방골목', '부산광역시 중구 보수동1가', 'ANSWER_HINT'],
      ['부평깡통시장', '부산광역시 중구 부평1길 48', 'ANSWER_HINT'],
      ['자갈치시장', '부산광역시 중구 자갈치해안로 52', 'ANSWER_HINT'],
      ['영도대교', '부산광역시 중구 중앙동7가', 'FINAL']
    ],
    finalAnswers: {
      culprit: '서민재',
      weapon: '독성 방부제가 묻은 항만 서류 봉투',
      motive: '밀수 장부 은폐',
      method: '피해자가 매일 확인하던 화물 인수 서류를 독성 봉투로 바꿔치기'
    }
  }
};

const presetName = process.env.REGION_PRESET || 'chungcheong';
const preset = presets[presetName];
if (!preset) {
  throw new Error(`Unknown REGION_PRESET: ${presetName}`);
}

const source = JSON.parse(await readFile(sourcePath, 'utf8'));
const payload = source.data || source;

function sourcePlace(index) {
  return payload.places?.[Math.min(index, (payload.places?.length || 1) - 1)] || {};
}

function place(row, index) {
  const [name, address, role] = row;
  return {
    ...sourcePlace(index),
    name,
    address,
    role,
    publicMarkerType: role === 'START' ? 'START' : 'ANSWER_HINT',
    latitude: null,
    longitude: null,
    description: `Field candidate for ${preset.area}.`,
    adminMemo: `Smoke-test candidate for ${preset.area}. Verify coordinates, access, opening hours, and field puzzle evidence before release.`,
    visibleElements: [
      '\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC7A5\uC18C\uBA85 \uD45C\uC2DD',
      '\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC785\uAD6C \uB610\uB294 \uC548\uB0B4\uD310',
      '\uD604\uC7A5\uC5D0\uC11C \uD655\uC778\uD560 \uC8FC\uBCC0 \uB3D9\uC120 \uB2E8\uC11C'
    ],
    externalResearchNotes: [
      `Reference: ${name} - use this ${preset.area} place background only as case atmosphere context.`
    ],
    referenceUrls: [],
    researchSourceSummary: `Manual ${preset.area} verification input for ${name}.`,
    keywords: [preset.area, '\uD604\uC7A5\uB2E8\uC11C', '\uB3D9\uC120\uD754\uC801']
  };
}

payload.area = preset.area;
payload.era = preset.era;
payload.theme = preset.theme;
payload.places = preset.places.map(place);
payload.finalSpot = place(preset.places[preset.places.length - 1], preset.places.length - 1);

payload.finalAnswerKeywordItems = [
  { slotId: 'CULPRIT', type: 'CULPRIT', displayType: '\uBC94\uC778', label: '\uBC94\uC778', keyword: preset.finalAnswers.culprit, value: preset.finalAnswers.culprit, personName: preset.finalAnswers.culprit, aliases: [] },
  { slotId: 'WEAPON', type: 'WEAPON', displayType: '\uD749\uAE30', label: '\uD749\uAE30', keyword: preset.finalAnswers.weapon, value: preset.finalAnswers.weapon, aliases: [] },
  { slotId: 'MOTIVE', type: 'MOTIVE', displayType: '\uB3D9\uAE30', label: '\uB3D9\uAE30', keyword: preset.finalAnswers.motive, value: preset.finalAnswers.motive, aliases: [] },
  { slotId: 'METHOD', type: 'METHOD', displayType: '\uBC29\uBC95', label: '\uBC29\uBC95', keyword: preset.finalAnswers.method, value: preset.finalAnswers.method, aliases: [] }
];
payload.finalAnswerKeywords = payload.finalAnswerKeywordItems.map((item) => item.value);
payload.finalAnswers = {
  culprit: preset.finalAnswers.culprit,
  weapon: preset.finalAnswers.weapon,
  motive: preset.finalAnswers.motive,
  method: preset.finalAnswers.method,
  relatedPerson: null,
  coreClue: null,
  finalLocation: null
};

await writeFile(outputPath, `${JSON.stringify({ success: true, code: 'OK', data: payload }, null, 2)}\n`, 'utf8');
console.log(JSON.stringify({
  outputPath,
  preset: presetName,
  area: payload.area,
  finalSpot: payload.finalSpot.name,
  placeCount: payload.places.length,
  finalAnswerSlots: payload.finalAnswerKeywordItems.map((item) => item.slotId)
}, null, 2));
