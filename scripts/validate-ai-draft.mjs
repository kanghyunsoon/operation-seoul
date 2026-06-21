import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

const inputPath = process.argv[2] || process.env.INPUT_PATH || 'tmp-gemini-draft-response.json';
const REQUIRED_SLOTS = ['CULPRIT', 'WEAPON', 'MOTIVE', 'METHOD'];
const FORBIDDEN_PLACE_HINT_TERMS = [
  'DESTINATION_HINT',
  'DESTINATION_CLUE',
  'FINAL_DESTINATION',
  'PLACE_HINT',
  '장소 힌트',
  '장소 정답',
  '최종 장소를 찾',
  '최종 목적지를 찾',
  '목적지 힌트'
];
const IMMERSION_BREAKING_TERMS = [
  'TourAPI',
  'RAG',
  '실제 장소',
  '가상의 용의자',
  '가상 용의자',
  '관리자 검수',
  '검수가 필요'
];
const WEAK_GENERIC_SUSPECT_TERMS = [
  '특정 용의자',
  '용의자 중 한 명',
  '용의자 중 하나',
  '해당 용의자',
  '해고 통보를 받은 용의자'
];

function unwrap(payload) {
  const body = payload?.data || payload;
  return {
    body,
    draft: body?.draft || body
  };
}

function textOf(value) {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  return JSON.stringify(value);
}

function normalize(value) {
  return textOf(value).trim();
}

function includesAny(text, terms) {
  const haystack = normalize(text).toLowerCase();
  return terms.some((term) => haystack.includes(term.toLowerCase()));
}

function addIssue(issues, severity, code, message, path = '') {
  issues.push({ severity, code, message, path });
}

function allText(draft) {
  return [
    draft.episodeTitle,
    draft.subtitle,
    draft.genre,
    draft.fictionSynopsis,
    draft.missionDescription,
    draft.finalQuestion,
    draft.finalTruthSummary,
    draft.actualHistorySummary,
    ...(draft.missions || []).flatMap((mission) => [
      mission.placeName,
      mission.markerType,
      mission.publicMarkerType,
      mission.clueRole,
      mission.storyText,
      mission.questionText,
      mission.rewardClue,
      mission.rewardClueSlotId,
      mission.rewardClueLabel,
      mission.targetKeywordType,
      mission.targetKeywordDisplayType
    ]),
    ...(draft.suspects || []).flatMap((suspect) => [
      suspect.displayName,
      suspect.relationToVictim,
      suspect.alibiSummary,
      suspect.suspiciousPoint
    ]),
    ...(draft.evidences || []).flatMap((evidence) => [
      evidence.title,
      evidence.type,
      evidence.textSummary
    ])
  ].join('\n');
}

function validateFinalAnswers(draft, issues) {
  const items = draft.finalAnswerKeywordItems || [];
  if (items.length !== 4) {
    addIssue(issues, 'ERROR', 'FINAL_KEYWORD_COUNT', 'finalAnswerKeywordItems must contain exactly 4 items.', 'draft.finalAnswerKeywordItems');
  }

  for (const slot of REQUIRED_SLOTS) {
    const item = items.find((candidate) => candidate.slotId === slot || candidate.type === slot);
    if (!item) {
      addIssue(issues, 'ERROR', 'FINAL_KEYWORD_SLOT_MISSING', `Missing required final answer slot ${slot}.`, 'draft.finalAnswerKeywordItems');
      continue;
    }
    if (!normalize(item.value || item.keyword)) {
      addIssue(issues, 'ERROR', 'FINAL_KEYWORD_VALUE_EMPTY', `${slot} value is empty.`, `draft.finalAnswerKeywordItems.${slot}`);
    }
  }

  const genre = normalize(draft.genre || draft.selectedGenre);
  if (!genre.includes('범죄') || !genre.includes('미스터리')) {
    addIssue(issues, 'ERROR', 'GENRE_NOT_FIXED', 'Genre must be fixed to crime mystery.', 'draft.genre');
  }
}

function validateSuspects(draft, issues) {
  const suspects = draft.suspects || [];
  if (suspects.length !== 3) {
    addIssue(issues, 'ERROR', 'SUSPECT_COUNT', 'There must be exactly 3 suspects.', 'draft.suspects');
  }
  suspects.forEach((suspect, index) => {
    if (!normalize(suspect.displayName)) {
      addIssue(issues, 'ERROR', 'SUSPECT_NAME_EMPTY', 'Suspect displayName is required.', `draft.suspects[${index}]`);
    }
    if (!normalize(suspect.alibiSummary)) {
      addIssue(issues, 'ERROR', 'SUSPECT_ALIBI_EMPTY', 'Suspect alibiSummary is required.', `draft.suspects[${index}]`);
    }
    if (!normalize(suspect.suspiciousPoint)) {
      addIssue(issues, 'ERROR', 'SUSPECT_POINT_EMPTY', 'Suspect suspiciousPoint is required.', `draft.suspects[${index}]`);
    }
  });
}

function validateMissions(draft, issues) {
  const missions = draft.missions || [];
  const start = missions.filter((mission) => mission.markerType === 'START' || mission.clueRole === 'START');
  const final = missions.filter((mission) => mission.finalPlace === true || mission.markerType === 'FINAL');
  const investigation = missions.filter((mission) =>
    mission.finalPlace !== true &&
    mission.markerType !== 'FINAL' &&
    mission.markerType !== 'START' &&
    mission.clueRole !== 'START'
  );

  if (missions.length !== 10) {
    addIssue(issues, 'ERROR', 'MISSION_COUNT', 'There must be exactly 10 missions: 1 start, 8 investigation, 1 final.', 'draft.missions');
  }
  if (start.length !== 1) {
    addIssue(issues, 'ERROR', 'START_MISSION_COUNT', 'There must be exactly 1 start mission.', 'draft.missions');
  }
  if (investigation.length !== 8) {
    addIssue(issues, 'ERROR', 'INVESTIGATION_MISSION_COUNT', 'There must be exactly 8 investigation missions.', 'draft.missions');
  }
  if (final.length !== 1) {
    addIssue(issues, 'ERROR', 'FINAL_MISSION_COUNT', 'There must be exactly 1 final mission.', 'draft.missions');
  }

  const targetSlots = investigation.map((mission) => mission.targetKeywordType).filter(Boolean);
  for (const slot of REQUIRED_SLOTS) {
    if (!targetSlots.includes(slot)) {
      addIssue(issues, 'ERROR', 'INVESTIGATION_SLOT_MISSING', `No investigation clue supports ${slot}.`, 'draft.missions');
    }
  }

  final.forEach((mission) => {
    if (mission.publicMarkerType !== 'ANSWER_HINT') {
      addIssue(issues, 'ERROR', 'FINAL_PUBLIC_MARKER_EXPOSED', 'Final mission must not expose FINAL through stored publicMarkerType.', 'draft.missions.final');
    }
    if (mission.unlockCondition !== 'ALL_INVESTIGATION_MISSIONS_CLEARED') {
      addIssue(issues, 'ERROR', 'FINAL_UNLOCK_CONDITION', 'Final mission unlockCondition must be ALL_INVESTIGATION_MISSIONS_CLEARED.', 'draft.missions.final');
    }
  });

  investigation.forEach((mission) => {
    if (mission.markerType !== 'ANSWER_HINT' || mission.clueRole !== 'ANSWER_HINT' || mission.publicMarkerType !== 'ANSWER_HINT') {
      addIssue(issues, 'ERROR', 'INVESTIGATION_MARKER_TYPE', 'Investigation mission must use ANSWER_HINT marker/clue/public type.', `draft.missions[${mission.order}]`);
    }
    if (!normalize(mission.rewardClue)) {
      addIssue(issues, 'ERROR', 'REWARD_CLUE_EMPTY', 'Investigation mission rewardClue is required.', `draft.missions[${mission.order}].rewardClue`);
    }
  });
}

function validateEvidence(draft, issues) {
  const evidences = draft.evidences || [];
  if (evidences.length !== 8) {
    addIssue(issues, 'ERROR', 'EVIDENCE_COUNT', 'There must be exactly 8 evidence cards.', 'draft.evidences');
  }
  const sourceOrders = evidences.map((evidence) => Number(evidence.sourceMissionOrder)).filter(Number.isFinite);
  for (let order = 2; order <= 9; order += 1) {
    if (!sourceOrders.includes(order)) {
      addIssue(issues, 'ERROR', 'EVIDENCE_SOURCE_MISSING', `Missing evidence for investigation mission order ${order}.`, 'draft.evidences');
    }
  }
}

function validateForbiddenText(draft, issues) {
  const combined = allText(draft);
  if (includesAny(combined, FORBIDDEN_PLACE_HINT_TERMS)) {
    addIssue(issues, 'ERROR', 'PLACE_HINT_TERM_PRESENT', 'Place hint/destination clue terminology is present.', 'draft');
  }
  if (includesAny(combined, IMMERSION_BREAKING_TERMS)) {
    addIssue(issues, 'ERROR', 'IMMERSION_BREAKING_TERM_PRESENT', 'Player-facing immersion-breaking terminology is present.', 'draft');
  }
}

function validateAnswerLeaks(draft, issues) {
  const values = (draft.finalAnswerKeywordItems || [])
    .flatMap((item) => [item.value, item.keyword, item.personName])
    .map(normalize)
    .filter((value) => value.length >= 2);

  for (const mission of draft.missions || []) {
    if (mission.finalPlace === true || mission.markerType === 'FINAL' || mission.markerType === 'START') continue;
    const clue = normalize(mission.rewardClue);
    const leaked = values.find((value) => clue.includes(value));
    if (leaked) {
      addIssue(issues, 'ERROR', 'REWARD_CLUE_ANSWER_LEAK', `Investigation rewardClue directly includes final answer value "${leaked}".`, `draft.missions[${mission.order}].rewardClue`);
    }
  }
}

function validateConcreteInvestigationClues(draft, issues) {
  for (const mission of draft.missions || []) {
    if (mission.finalPlace === true || mission.markerType === 'FINAL' || mission.markerType === 'START') continue;
    const clue = normalize(mission.rewardClue);
    const weakTerm = WEAK_GENERIC_SUSPECT_TERMS.find((term) => clue.includes(term));
    if (weakTerm) {
      addIssue(issues, 'ERROR', 'WEAK_GENERIC_SUSPECT_REFERENCE', `Investigation rewardClue contains weak generic suspect reference "${weakTerm}".`, `draft.missions[${mission.order}].rewardClue`);
    }
  }
}

export function validateDraft(payload) {
  const { body, draft } = unwrap(payload);
  const issues = [];
  if (!draft || typeof draft !== 'object') {
    addIssue(issues, 'ERROR', 'DRAFT_MISSING', 'Draft object is missing.', 'draft');
    return { body, draft: {}, issues };
  }
  validateFinalAnswers(draft, issues);
  validateSuspects(draft, issues);
  validateMissions(draft, issues);
  validateEvidence(draft, issues);
  validateForbiddenText(draft, issues);
  validateAnswerLeaks(draft, issues);
  validateConcreteInvestigationClues(draft, issues);
  return { body, draft, issues };
}

export function buildValidationSummary(payload, sourcePath = null) {
  const { body, draft, issues } = validateDraft(payload);
  const errors = issues.filter((issue) => issue.severity === 'ERROR');
  return {
    inputPath: sourcePath,
    publishable: body?.publishable ?? null,
    valid: errors.length === 0,
    errorCount: errors.length,
    issueCount: issues.length,
    title: draft.episodeTitle || null,
    genre: draft.genre || draft.selectedGenre || null,
    finalAnswerSlots: (draft.finalAnswerKeywordItems || []).map((item) => item.slotId || item.type),
    suspectCount: draft.suspects?.length || 0,
    missionCount: draft.missions?.length || 0,
    evidenceCount: draft.evidences?.length || 0,
    issues
  };
}

async function main() {
  const raw = await readFile(inputPath, 'utf8');
  const payload = JSON.parse(raw);
  const summary = buildValidationSummary(payload, inputPath);
  console.log(JSON.stringify(summary, null, 2));
  if (!summary.valid) {
    process.exitCode = 1;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await main();
}
