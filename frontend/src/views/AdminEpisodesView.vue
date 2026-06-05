<template>
  <main class="admin-episode-page">
    <header class="admin-hero">
      <div>
        <p>ADMIN CASE OPS</p>
        <h1>에피소드 관리</h1>
        <span>사건파일, 장소, 퍼즐, 최종 장소, 리워드 placeholder를 점검합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" class="ghost-btn" @click="router.push({ name: 'EpisodeList' })">전역 미션 선택</button>
        <button type="button" @click="router.push({ name: 'AdminUsers' })">회원 관리</button>
        <button type="button" @click="router.push({ name: 'AdminReviews' })">리뷰 관리</button>
      </div>
    </header>

    <section class="layout">
      <aside class="episode-list">
        <div class="section-title">
          <h2>사건파일 목록</h2>
          <div class="payload-actions compact">
            <button type="button" @click="createEpisode">새 사건 생성</button>
            <button type="button" class="ghost-btn" @click="loadEpisodes">새로고침</button>
          </div>
        </div>
        <p v-if="loading" class="empty">에피소드를 불러오는 중입니다.</p>
        <article
          v-for="episode in episodes"
          :key="episode.id"
          class="episode-card"
          :class="{ active: selectedEpisodeId === episode.id }"
          @click="selectEpisode(episode.id)"
        >
          <strong>{{ episode.title }}</strong>
          <span>{{ episode.genre }} · {{ episode.difficulty }} · {{ episode.status }}</span>
          <div class="metrics">
            <em>장소 {{ episode.spotCount }}</em>
            <em>퍼즐 {{ episode.puzzleCount }}</em>
            <em>자료 {{ episode.evidenceCount }}</em>
            <em>클리어 {{ episode.clearedPlayers }}/{{ episode.totalPlayers }}</em>
          </div>
        </article>
      </aside>

      <section class="detail-panel">
        <p v-if="message" class="message" :class="messageType">{{ message }}</p>
        <article v-if="selected" class="detail-card">
          <div class="detail-head">
            <div>
              <p>INTERNAL CASE FILE</p>
              <h2>{{ selected.title }}</h2>
              <span>{{ selected.subtitle }}</span>
            </div>
            <div class="detail-status">
              <strong>{{ selected.status }}</strong>
              <button type="button" class="danger-btn" :disabled="selected.status === 'PUBLISHED'" @click="deleteEpisode">
                사건 삭제
              </button>
              <small v-if="selected.status === 'PUBLISHED'">공개 중인 사건은 ARCHIVED 전환 후 삭제</small>
            </div>
          </div>

          <div class="secret-box">
            <h3>관리자 전용 최종 추리 설정</h3>
            <p>최종 질문: {{ selected.finalQuestion }}</p>
            <p>정답 유형: {{ selected.finalAnswerType }}</p>
            <p>정답: {{ selected.finalAnswer }}</p>
            <p>Alias: {{ selected.finalAnswerAliases || '없음' }}</p>
            <p>질문 제한: {{ selected.maxDeductionQuestions || 20 }}회</p>
          </div>

          <section class="admin-preview-panel">
            <div class="section-title">
              <h3>사용자 플레이 미리보기</h3>
              <div class="payload-actions">
                <button type="button" class="ghost-btn" @click="previewOpen = !previewOpen">
                  {{ previewOpen ? '미리보기 접기' : '관리자 내부 미리보기' }}
                </button>
                <button type="button" class="ghost-btn" :disabled="selected.status !== 'PUBLISHED'" @click="goUserMap">
                  사용자 지도 열기
                </button>
                <button type="button" class="ghost-btn" :disabled="selected.status !== 'PUBLISHED'" @click="goUserCaseFile">
                  사용자 사건파일 열기
                </button>
              </div>
            </div>
            <p>
              DRAFT는 사용자 API에서 접근할 수 없으므로 이 관리자 미리보기로만 확인합니다.
              PUBLISHED 이후에는 실제 사용자 지도/사건파일 화면으로 이동할 수 있습니다.
            </p>
            <div v-if="previewOpen" class="preview-grid">
              <article>
                <strong>공개 지도 마커</strong>
                <ul>
                  <li v-for="spot in selected.spots || []" :key="`preview-spot-${spot.spotId}`">
                    <span :class="spot.publicMarkerType">{{ markerPreviewLabel(spot.publicMarkerType) }}</span>
                    {{ spot.placeName }}
                    <em v-if="spot.finalPlace">관리자 내부 최종 장소</em>
                  </li>
                </ul>
              </article>
              <article>
                <strong>사건자료 카드</strong>
                <ul>
                  <li v-for="evidence in selected.evidences || []" :key="`preview-evidence-${evidence.evidenceId}`">
                    <span>{{ evidence.type }}</span>
                    {{ evidence.title }}
                    <em>{{ evidence.unlockedByDefault ? '기본 공개' : '퍼즐 보상 해금' }}</em>
                  </li>
                </ul>
              </article>
              <article>
                <strong>용의자 카드</strong>
                <ul>
                  <li v-for="suspect in selected.suspects || []" :key="`preview-suspect-${suspect.suspectId}`">
                    <span>{{ suspect.alias }}</span>
                    {{ suspect.displayName }}
                    <em>{{ suspect.unlockedByDefault ? '기본 공개' : '퍼즐 보상 해금' }}</em>
                  </li>
                </ul>
              </article>
            </div>
          </section>

            <details class="edit-section">
              <summary>에피소드 핵심 정보 수정</summary>
              <div class="publish-rules">
                <strong>공개 전 필수 조건</strong>
                <p>장소 8~9개 권장, START 1개, ANSWER_HINT 4개 이상, DESTINATION_HINT 2개 이상, 실제 최종 장소 1개, FINAL_CANDIDATE 공개 마커 2개 이상, 모든 퍼즐/힌트/reward_payload가 필요합니다.</p>
                <div class="publish-actions">
                  <button type="button" class="ghost-btn" @click="checkPublishReadiness">공개 준비도 점검</button>
                  <button type="button" class="publish-btn" :disabled="!publishReadiness?.ready || selected?.status === 'PUBLISHED'" @click="publishEpisode">
                    PUBLISHED 전환
                  </button>
                </div>
              </div>
              <section v-if="publishReadiness" class="readiness-panel" :class="{ ready: publishReadiness.ready }">
                <div class="section-title">
                  <h3>{{ publishReadiness.ready ? '공개 가능' : '공개 차단' }}</h3>
                  <strong>{{ publishReadiness.message }}</strong>
                </div>
                <div class="readiness-metrics">
                  <span>장소 {{ publishReadiness.summary?.spotCount || 0 }}</span>
                  <span>START {{ publishReadiness.summary?.startCount || 0 }}</span>
                  <span>ANSWER {{ publishReadiness.summary?.answerHintCount || 0 }}</span>
                  <span>DEST {{ publishReadiness.summary?.destinationHintCount || 0 }}</span>
                  <span>FINAL {{ publishReadiness.summary?.finalPlaceCount || 0 }}</span>
                  <span>후보 {{ publishReadiness.summary?.finalCandidateCount || 0 }}</span>
                  <span>퍼즐 {{ publishReadiness.summary?.puzzleCount || 0 }}</span>
                  <span>용의자 {{ publishReadiness.summary?.suspectCount || 0 }}</span>
                  <span>증거 {{ publishReadiness.summary?.evidenceCount || 0 }}</span>
                </div>
                <ul v-if="publishReadiness.blockingIssues?.length">
                  <li v-for="issue in publishReadiness.blockingIssues" :key="issue">{{ issue }}</li>
                </ul>
                <details>
                  <summary>운영 체크리스트</summary>
                  <ul>
                    <li v-for="item in publishReadiness.checklist || []" :key="item">{{ item }}</li>
                  </ul>
                </details>
                <p v-if="publishReadiness.ready" class="publish-ready-note">
                  준비도 점검을 통과했습니다. 현장 검수까지 완료했다면 PUBLISHED 전환 버튼으로 사용자 플레이 목록에 노출할 수 있습니다.
                </p>
              </section>
              <div class="edit-grid">
              <label>제목<input v-model.trim="episodeForm.title" type="text" /></label>
              <label>부제<input v-model.trim="episodeForm.subtitle" type="text" /></label>
              <label>장르<input v-model.trim="episodeForm.genre" type="text" /></label>
              <label>시대<input v-model.trim="episodeForm.era" type="text" /></label>
              <label>난이도<input v-model.trim="episodeForm.difficulty" type="text" /></label>
              <label>상태
                <select v-model="episodeForm.status">
                  <option value="DRAFT">DRAFT</option>
                  <option value="PUBLISHED">PUBLISHED</option>
                  <option value="ARCHIVED">ARCHIVED</option>
                </select>
              </label>
              <label>최종 정답 유형<input v-model.trim="episodeForm.finalAnswerType" type="text" /></label>
              <label>최종 정답<input v-model.trim="episodeForm.finalAnswer" type="text" /></label>
              <label class="wide">정답 alias<input v-model.trim="episodeForm.finalAnswerAliases" type="text" /></label>
              <label class="wide">최종 질문<input v-model.trim="episodeForm.finalQuestion" type="text" /></label>
              <label class="wide">픽션 시놉시스<textarea v-model="episodeForm.fictionSynopsis" rows="3"></textarea></label>
              <label class="wide">진실 파일<textarea v-model="episodeForm.finalTruthSummary" rows="3"></textarea></label>
              <label class="wide">실제 역사 해설<textarea v-model="episodeForm.actualHistorySummary" rows="3"></textarea></label>
              <label class="wide">추리 secret facts<textarea v-model="episodeForm.deductionSecretFacts" rows="3"></textarea></label>
              <label class="wide">정답 노출 금지어<textarea v-model="episodeForm.deductionForbiddenReveals" rows="2"></textarea></label>
              <label>질문 제한<input v-model.number="episodeForm.maxDeductionQuestions" type="number" min="1" /></label>
              <label>권장 인원<input v-model.trim="episodeForm.recommendedPlayers" type="text" /></label>
              <label class="wide">팀 역할 안내<textarea v-model="episodeForm.teamRoleGuide" rows="2"></textarea></label>
              <label class="wide">운영 주의사항<textarea v-model="episodeForm.noticeText" rows="3"></textarea></label>
            </div>
            <button type="button" @click="saveEpisode">에피소드 저장</button>
          </details>

          <div class="stat-grid">
            <article><strong>{{ selected.progressStats?.totalPlayers || 0 }}</strong><span>플레이어</span></article>
            <article><strong>{{ selected.progressStats?.inProgressPlayers || 0 }}</strong><span>진행 중</span></article>
            <article><strong>{{ selected.progressStats?.clearedPlayers || 0 }}</strong><span>클리어</span></article>
          </div>

          <div class="section-title">
            <h3>장소/퍼즐 검수</h3>
            <button type="button" class="ghost-btn" @click="addSpot">장소 추가</button>
          </div>
          <div class="spot-list">
            <article v-for="spot in selected.spots || []" :key="spot.spotId" class="spot-card" :class="{ final: spot.finalPlace }">
              <div class="spot-head">
                <strong>{{ spot.placeName }}</strong>
                <span>{{ spot.publicMarkerType }} / {{ spot.clueRole }}</span>
              </div>
              <p>{{ spot.storyText }}</p>
              <p class="internal" v-if="spot.finalPlace">내부 실제 최종 장소입니다. 공개 API에는 노출되지 않습니다.</p>
              <details v-if="spot.puzzle">
                <summary>퍼즐/정답/reward_payload</summary>
                <div class="edit-grid">
                  <label>장소명<input v-model.trim="spot.placeName" type="text" /></label>
                  <label>공개 마커
                    <select v-model="spot.publicMarkerType">
                      <option value="START">START</option>
                      <option value="ANSWER_HINT">ANSWER_HINT</option>
                      <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                      <option value="STORY">STORY</option>
                      <option value="FINAL_CANDIDATE">FINAL_CANDIDATE</option>
                    </select>
                  </label>
                  <label>내부 마커
                    <select v-model="spot.markerType">
                      <option value="START">START</option>
                      <option value="ANSWER_HINT">ANSWER_HINT</option>
                      <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                      <option value="STORY">STORY</option>
                      <option value="FINAL_CANDIDATE">FINAL_CANDIDATE</option>
                      <option value="FINAL">FINAL</option>
                    </select>
                  </label>
                  <label>단서 역할
                    <select v-model="spot.clueRole">
                      <option value="START">START</option>
                      <option value="ANSWER_HINT">ANSWER_HINT</option>
                      <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                      <option value="STORY_CONTEXT">STORY_CONTEXT</option>
                      <option value="FINAL_PLACE">FINAL_PLACE</option>
                    </select>
                  </label>
                  <label>도착 반경<input v-model.number="spot.arrivalRadius" type="number" min="10" /></label>
                  <label class="check"><input v-model="spot.finalPlace" type="checkbox" /> 실제 최종 장소</label>
                  <label class="wide">주소<input v-model.trim="spot.address" type="text" /></label>
                  <label>위도<input v-model.number="spot.latitude" type="number" step="0.000001" /></label>
                  <label>경도<input v-model.number="spot.longitude" type="number" step="0.000001" /></label>
                  <label class="wide">사건 문구<textarea v-model="spot.storyText" rows="2"></textarea></label>
                </div>
                <button type="button" @click="saveSpot(spot)">장소 저장</button>
                <button type="button" class="danger-btn" @click="removeSpot(spot)">장소 삭제</button>
                <div class="edit-grid puzzle-edit">
                  <label>퍼즐 유형
                    <select v-model="spot.puzzle.puzzleType">
                      <option value="OBSERVATION">OBSERVATION</option>
                      <option value="NUMBER_LOCK">NUMBER_LOCK</option>
                      <option value="INITIAL_SOUND">INITIAL_SOUND</option>
                      <option value="PATTERN">PATTERN</option>
                      <option value="STORY_COMBINATION">STORY_COMBINATION</option>
                    </select>
                  </label>
                  <label>정답 형식
                    <select v-model="spot.puzzle.answerFormat">
                      <option value="TEXT">TEXT</option>
                      <option value="NUMBER">NUMBER</option>
                      <option value="CHOICE">CHOICE</option>
                      <option value="CODE">CODE</option>
                    </select>
                  </label>
                  <label>정답<input v-model.trim="spot.puzzle.answer" type="text" /></label>
                  <label>보상 단서<input v-model.trim="spot.puzzle.rewardClue" type="text" /></label>
                  <label class="wide">문제<textarea v-model="spot.puzzle.questionText" rows="3"></textarea></label>
                  <label class="wide">reward_payload JSON<textarea v-model="spot.puzzle.rewardPayload" rows="5"></textarea></label>
                  <label v-for="hint in spot.puzzle.hints || []" :key="hint.hintLevel">힌트 {{ hint.hintLevel }}<input v-model.trim="hint.hintText" type="text" /></label>
                </div>
                <div class="payload-actions">
                  <button type="button" class="ghost-btn" @click="validatePayload(spot)">reward_payload 검증</button>
                  <span v-if="payloadValidation[spot.puzzle.puzzleId]" :class="payloadValidation[spot.puzzle.puzzleId].valid ? 'valid' : 'invalid'">
                    {{ payloadValidation[spot.puzzle.puzzleId].valid ? '유효함' : '오류 있음' }}
                  </span>
                </div>
                <div v-if="payloadValidation[spot.puzzle.puzzleId]" class="validation-box">
                  <p v-for="error in payloadValidation[spot.puzzle.puzzleId].errors || []" :key="`err-${error}`" class="invalid">{{ error }}</p>
                  <p v-for="warning in payloadValidation[spot.puzzle.puzzleId].warnings || []" :key="`warn-${warning}`" class="warning">{{ warning }}</p>
                  <ul>
                    <li v-for="reward in payloadValidation[spot.puzzle.puzzleId].rewards || []" :key="`${reward.type}-${reward.targetId}-${reward.value}`">
                      {{ reward.type }} · {{ reward.value || reward.targetLabel || reward.targetId }}
                    </li>
                  </ul>
                </div>
                <button type="button" @click="savePuzzle(spot)">퍼즐 저장</button>
                <ul>
                  <li v-for="hint in spot.puzzle.hints || []" :key="hint.hintLevel">H{{ hint.hintLevel }}. {{ hint.hintText }}</li>
                </ul>
              </details>
            </article>
          </div>

          <div class="section-title">
            <h3>용의자/증거 자료</h3>
            <div class="payload-actions">
              <button type="button" class="ghost-btn" @click="addSuspect">용의자 추가</button>
              <button type="button" class="ghost-btn" @click="addEvidence">증거 추가</button>
            </div>
          </div>
          <div class="mini-grid">
            <article v-for="suspect in selected.suspects || []" :key="suspect.suspectId">
              <strong>{{ suspect.alias }} · {{ suspect.displayName }}</strong>
              <p>{{ suspect.suspiciousPoint }}</p>
              <span>{{ suspect.unlockedByDefault ? '기본 해금' : '조건 해금' }}</span>
              <details class="card-editor">
                <summary>용의자 수정</summary>
                <label>별칭<input v-model.trim="suspect.alias" type="text" /></label>
                <label>표시 이름<input v-model.trim="suspect.displayName" type="text" /></label>
                <label>짧은 설명<input v-model.trim="suspect.shortDescription" type="text" /></label>
                <label>관계<input v-model.trim="suspect.relationToVictim" type="text" /></label>
                <label>의심 포인트<textarea v-model="suspect.suspiciousPoint" rows="2"></textarea></label>
                <label>알리바이<textarea v-model="suspect.alibiSummary" rows="2"></textarea></label>
                <label>초상 이미지 URL<input v-model.trim="suspect.portraitImageUrl" type="url" /></label>
                <label>표시 순서<input v-model.number="suspect.displayOrder" type="number" /></label>
                <label class="check"><input v-model="suspect.unlockedByDefault" type="checkbox" /> 기본 해금</label>
                <button type="button" @click="saveSuspect(suspect)">용의자 저장</button>
                <button type="button" class="danger-btn" @click="removeSuspect(suspect)">용의자 삭제</button>
              </details>
            </article>
            <article v-for="evidence in selected.evidences || []" :key="`e-${evidence.evidenceId}`">
              <strong>{{ evidence.title }}</strong>
              <p>{{ evidence.type }} · spot {{ evidence.sourceSpotId || '-' }}</p>
              <span>{{ evidence.unlockedByDefault ? '기본 해금' : 'reward_payload 해금' }}</span>
              <details class="card-editor">
                <summary>증거/메모/사진 수정</summary>
                <label>제목<input v-model.trim="evidence.title" type="text" /></label>
                <label>타입
                  <select v-model="evidence.type">
                    <option value="PHOTO">PHOTO</option>
                    <option value="MEMO">MEMO</option>
                    <option value="NOTE">NOTE</option>
                    <option value="DOCUMENT">DOCUMENT</option>
                    <option value="EVIDENCE">EVIDENCE</option>
                    <option value="SUSPECT_CLUE">SUSPECT_CLUE</option>
                    <option value="POST_IT">POST_IT</option>
                    <option value="ANSWER_CLUE">ANSWER_CLUE</option>
                    <option value="DESTINATION_CLUE">DESTINATION_CLUE</option>
                    <option value="STORY_CLUE">STORY_CLUE</option>
                  </select>
                </label>
                <label>이미지 URL<input v-model.trim="evidence.imageUrl" type="url" /></label>
                <label>출처 장소 ID<input v-model.number="evidence.sourceSpotId" type="number" /></label>
                <label>관련 용의자 ID<input v-model.number="evidence.relatedSuspectId" type="number" /></label>
                <label>관련 단서 타입<input v-model.trim="evidence.relatedClueType" type="text" /></label>
                <label>표시 순서<input v-model.number="evidence.displayOrder" type="number" /></label>
                <label>요약<textarea v-model="evidence.textSummary" rows="3"></textarea></label>
                <label class="check"><input v-model="evidence.unlockedByDefault" type="checkbox" /> 기본 해금</label>
                <button type="button" @click="saveEvidence(evidence)">증거 저장</button>
                <button type="button" class="danger-btn" @click="removeEvidence(evidence)">증거 삭제</button>
              </details>
            </article>
          </div>

          <h3>리워드 placeholder</h3>
          <div class="mini-grid">
            <article v-for="reward in selected.partnerRewards || []" :key="reward.rewardId" class="reward">
              <strong>{{ reward.title }}</strong>
              <p>{{ reward.description }}</p>
              <span>{{ reward.rewardType }} · {{ reward.status }}</span>
              <details class="card-editor">
                <summary>리워드 placeholder 수정</summary>
                <label>제목<input v-model.trim="reward.title" type="text" /></label>
                <label>설명<textarea v-model="reward.description" rows="2"></textarea></label>
                <label>타입
                  <select v-model="reward.rewardType">
                    <option value="COUPON">COUPON</option>
                    <option value="GIFT_CARD">GIFT_CARD</option>
                    <option value="LOCAL_CURRENCY">LOCAL_CURRENCY</option>
                    <option value="CAFE_DISCOUNT">CAFE_DISCOUNT</option>
                    <option value="STAMP">STAMP</option>
                  </select>
                </label>
                <label>상태
                  <select v-model="reward.status">
                    <option value="DISABLED">DISABLED</option>
                    <option value="PLANNED">PLANNED</option>
                    <option value="ACTIVE">ACTIVE</option>
                    <option value="ENDED">ENDED</option>
                  </select>
                </label>
                <label>파트너명<input v-model.trim="reward.partnerName" type="text" /></label>
                <label>장소명<input v-model.trim="reward.locationName" type="text" /></label>
                <label>위도<input v-model.number="reward.latitude" type="number" step="0.000001" /></label>
                <label>경도<input v-model.number="reward.longitude" type="number" step="0.000001" /></label>
                <button type="button" @click="savePartnerReward(reward)">리워드 저장</button>
              </details>
            </article>
            <p v-if="!(selected.partnerRewards || []).length" class="empty">등록된 리워드 placeholder가 없습니다.</p>
          </div>
        </article>


      </section>

      <article class="draft-panel full-width">
          <div class="section-title">
            <div>
              <p class="eyebrow">CASE BUILDER</p>
              <h2>AI 사건파일 자동 작성</h2>
            </div>
            <div class="payload-actions action-bar">
              <button type="button" class="primary-action" :disabled="draftBusy || !canGenerateDraftFromSelection" :class="{ busy: activeAction === 'gemini' }" @click="generateGeminiDraft">
                {{ activeAction === 'gemini' ? 'Gemini 작성 중...' : 'Gemini로 전체 초안 작성' }}
              </button>
              <button type="button" class="ghost-btn" :disabled="draftBusy || !canGenerateDraftFromSelection" :class="{ busy: activeAction === 'rule' }" @click="generateDraft">
                {{ activeAction === 'rule' ? '예비 초안 작성 중...' : '예비 초안 만들기' }}
              </button>
              <button v-if="draftResult?.draft" type="button" class="ghost-btn" :disabled="draftBusy" :class="{ busy: activeAction === 'validate' }" @click="validateDraft(false)">
                {{ activeAction === 'validate' ? '검증 중...' : '기본 검증' }}
              </button>
              <button v-if="draftResult?.draft" type="button" class="ghost-btn" :disabled="draftBusy" :class="{ busy: activeAction === 'geminiValidate' }" @click="validateDraft(true)">
                {{ activeAction === 'geminiValidate' ? 'Gemini 검증 중...' : 'Gemini 검증' }}
              </button>
              <button v-if="draftResult?.draft" type="button" class="save-draft-btn" :disabled="draftBusy" :class="{ busy: activeAction === 'save' }" @click="saveDraft">
                {{ activeAction === 'save' ? 'DB 저장 중...' : 'DRAFT로 저장' }}
              </button>
            </div>
          </div>
          <div class="ai-mode-grid">
            <article>
              <strong>Gemini 전체 작성</strong>
              <span>관리자가 선택한 장소와 메모를 기반으로 스토리, 퍼즐, 단서, 용의자, 증거 카드 초안을 생성합니다.</span>
            </article>
            <article>
              <strong>예비 초안</strong>
              <span>Gemini 키가 없거나 호출 실패 시 쓰는 안전 fallback입니다. AI가 아니라 입력값 기반 템플릿입니다.</span>
            </article>
            <article>
              <strong>이미지 카드</strong>
              <span>실제 현장 사진을 상상하지 않고, 가상 사건자료 카드 이미지를 자동 생성해 저장합니다.</span>
            </article>
          </div>
          <div v-if="draftBusy || draftStatus || draftError" class="draft-status-box" :class="{ error: Boolean(draftError) }">
            <div class="draft-status-head">
              <strong>{{ draftBusy ? '작업 진행 중' : draftError ? '작업 실패' : '최근 작업' }}</strong>
              <span v-if="draftBusy">{{ draftElapsedSeconds }}초 경과</span>
            </div>
            <p>{{ draftError || draftStatus }}</p>
            <div v-if="draftBusy" class="draft-progress-bar">
              <i :style="{ width: `${draftProgressPercent}%` }"></i>
            </div>
            <ol v-if="draftBusy" class="draft-step-list">
              <li v-for="step in draftProgressSteps" :key="step.key" :class="{ active: step.key === draftProgressStep, done: step.done }">
                <b>{{ step.label }}</b>
                <span>{{ step.description }}</span>
              </li>
            </ol>
          </div>
          <div v-if="!canGenerateDraftFromSelection" class="draft-status-box error">
            <strong>초안 작성 전 필요 조건</strong>
            <p>{{ draftSelectionBlockReason }}</p>
          </div>
          <p class="warning">저장된 초안은 항상 DRAFT로 시작합니다. 현장 좌표/숫자/표지판 검수 후 PUBLISHED로 변경하세요.</p>

          <section class="creation-flow">
            <article :class="{ done: candidateLoaded }">
              <b>1</b>
              <strong>TourAPI 기준 장소</strong>
              <span>사건의 중심 관광지를 선택합니다.</span>
            </article>
            <article :class="{ done: nearbyLoaded }">
              <b>2</b>
              <strong>Kakao 주변 후보</strong>
              <span>골목상권/문화 후보지를 불러옵니다.</span>
            </article>
            <article :class="{ done: selectedCandidates.length >= 8 }">
              <b>3</b>
              <strong>8~9개 장소 선택</strong>
              <span>TourAPI 기준 장소가 내부 최종 장소입니다.</span>
            </article>
            <article :class="{ done: Boolean(draftResult?.draft) }">
              <b>4</b>
              <strong>초안 생성/검증</strong>
              <span>스토리, 퍼즐, 자료를 생성하고 검수합니다.</span>
            </article>
          </section>

          <section class="candidate-panel">
            <div class="section-title">
              <h3>1. TourAPI 기준 장소</h3>
              <div class="payload-actions">
                <select v-model="candidateAreaCode">
                  <option value="seoul">서울</option>
                  <option value="gangwon">강원권</option>
                  <option value="chungnam">충남권</option>
                  <option value="chungbuk">충북권</option>
                  <option value="jeonbuk">전북권</option>
                  <option value="jeonnam">전남권</option>
                  <option value="gyeongbuk">경북권</option>
                  <option value="gyeongnam">경남권</option>
                  <option value="jeju">제주</option>
                </select>
                <button type="button" @click="loadPlaceCandidates">TourAPI 기준 장소 불러오기</button>
              </div>
            </div>
            <p class="candidate-help">TourAPI 장소는 사건의 기준 지점이자 서버 내부 최종 장소입니다. 기준 장소를 선택하면 Kakao Local로 주변 골목상권/문화 후보지를 불러옵니다.</p>
            <div class="ops-notice">
              <strong>키/도메인 설정 확인</strong>
              <p>TourAPI와 Kakao Local 후보 조회는 백엔드 API 키가 필요합니다. 지도 표시는 프론트 Kakao JavaScript 키와 허용 도메인, 길찾기는 Tmap 앱 키/도메인 설정을 확인해야 합니다.</p>
            </div>
            <p v-if="candidateLoading" class="empty">TourAPI 후보를 불러오는 중입니다.</p>
            <p v-else-if="candidateLoaded && !placeCandidates.length" class="empty">TourAPI 후보가 없습니다. API 키와 지역 설정을 확인하세요.</p>
            <div class="candidate-grid">
              <article v-for="candidate in placeCandidates" :key="candidateKey(candidate)" class="candidate-card" :class="{ selected: anchorCandidate && candidateKey(anchorCandidate) === candidateKey(candidate) }">
                <strong>{{ candidate.title }}</strong>
                <p>{{ candidate.address || '주소 없음' }}</p>
                <span>{{ candidate.latitude }}, {{ candidate.longitude }}</span>
                <button type="button" class="ghost-btn" :disabled="!hasCandidateCoordinate(candidate)" @click="loadNearbyCandidates(candidate)">이 장소를 기준으로 주변 후보 찾기</button>
                <em v-if="!hasCandidateCoordinate(candidate)" class="coordinate-warning">좌표 없음</em>
              </article>
            </div>
            <div v-if="anchorCandidate" class="anchor-box">
              <strong>선택한 기준 장소: {{ anchorCandidate.title }}</strong>
              <p>{{ anchorCandidate.address }}</p>
            </div>
          </section>

          <section class="candidate-panel">
            <div class="section-title">
              <h3>2. Kakao Local 주변 후보</h3>
              <div class="payload-actions">
                <label class="inline-field">반경(m)<input v-model.number="nearbyRadius" type="number" min="100" max="20000" /></label>
                <button type="button" class="ghost-btn" :disabled="!anchorCandidate" @click="loadNearbyCandidates(anchorCandidate)">주변 후보 다시 불러오기</button>
                <button type="button" class="ghost-btn" :disabled="!canGenerateDraftFromSelection" @click="applyCandidatesToDraft">선택 장소를 초안 입력에 적용</button>
              </div>
            </div>
            <p class="candidate-help">기준 장소 포함 8~9개를 선택하세요. TourAPI 기준 장소가 내부 최종 장소가 되고, 공개 화면에는 일반 조사 후보처럼만 표시됩니다. Kakao 후보가 부족하면 아래 수동 후보로 보강하세요.</p>
            <div class="selection-summary">
              <strong>선택 {{ selectedCandidates.length }}개 / 권장 8~9개</strong>
              <span :class="{ ready: canGenerateDraftFromSelection }">
                {{ selectedCandidateStatus }}
              </span>
            </div>
            <p v-if="nearbyLoading" class="empty">Kakao Local 주변 후보를 불러오는 중입니다.</p>
            <p v-else-if="nearbyLoaded && !nearbyCandidates.length" class="empty">주변 후보가 없습니다. Kakao REST API 키와 반경을 확인하거나 수동 후보를 추가하세요.</p>
            <div class="manual-candidate-form">
              <strong>수동 후보 추가</strong>
              <div class="manual-grid">
                <label>장소명<input v-model.trim="manualCandidate.title" type="text" placeholder="예: 골목 카페 앞" /></label>
                <label>주소<input v-model.trim="manualCandidate.address" type="text" placeholder="도로명 또는 지번 주소" /></label>
                <label>위도<input v-model="manualCandidate.latitude" type="number" step="0.000001" placeholder="37.5665" /></label>
                <label>경도<input v-model="manualCandidate.longitude" type="number" step="0.000001" placeholder="126.9780" /></label>
              </div>
              <label class="manual-note">현장 메모<textarea v-model.trim="manualCandidate.description" rows="2" placeholder="관리자 입력 현장 관찰 요소와 운영 전 검수 메모를 적으세요."></textarea></label>
              <button type="button" class="ghost-btn" @click="addManualCandidate">수동 후보 추가</button>
              <p>수동 후보는 운영 공개 전 GPS 좌표, 접근 가능 여부, 현장 관찰 요소를 반드시 검수해야 합니다.</p>
            </div>
            <div class="candidate-grid">
              <article v-for="candidate in nearbyCandidates" :key="candidateKey(candidate)" class="candidate-card" :class="{ selected: isCandidateSelected(candidate) }">
                <label class="check">
                  <input type="checkbox" :checked="isCandidateSelected(candidate)" @change="toggleCandidate(candidate)" />
                  <strong>{{ candidate.title }}</strong>
                </label>
                <p>{{ candidate.address || '주소 없음' }}</p>
                <span>{{ candidate.source }} · {{ candidate.latitude }}, {{ candidate.longitude }}</span>
                <em v-if="!hasCandidateCoordinate(candidate)" class="coordinate-warning">좌표 없음: 선택해도 초안 생성 불가</em>
              </article>
            </div>
            <div v-if="selectedCandidates.length" class="selected-route">
              <h4>선택 장소 역할 미리보기</h4>
              <ol>
                <li v-for="(candidate, index) in orderedSelectedCandidates" :key="candidateKey(candidate)">
                  <b>{{ index + 1 }}</b>
                  <strong>{{ candidate.title }}</strong>
                  <span :class="roleForCandidate(index)">{{ roleLabel(roleForCandidate(index)) }}</span>
                  <em v-if="isAnchorCandidate(candidate)">TourAPI 기준 장소 · 내부 최종 장소</em>
                </li>
              </ol>
              <p>사용자 지도에는 내부 최종 장소 여부가 노출되지 않고, 공개 마커는 조사 후보로 표시됩니다.</p>
            </div>
          </section>
          <div class="draft-actions-helper">
            <strong>3. 초안 입력 준비 완료</strong>
            <span>선택 장소, 좌표, 관리자 메모는 내부 payload로 자동 전달됩니다. 화면에는 JSON을 노출하지 않습니다.</span>
          </div>
          <section v-if="draftResult?.draft" class="draft-editor">
            <div class="section-title">
              <h3>초안 폼 편집</h3>
              <span>아래 수정 내용은 검증과 DRAFT 저장에 바로 반영됩니다.</span>
            </div>

            <details open class="draft-edit-block">
              <summary>사건파일 기본 정보</summary>
              <div class="edit-grid">
                <label>제목<input v-model.trim="draftResult.draft.episodeTitle" type="text" /></label>
                <label>장르<input v-model.trim="draftResult.draft.genre" type="text" /></label>
                <label>시대<input v-model.trim="draftResult.draft.era" type="text" /></label>
                <label>정답 유형
                  <select v-model="draftResult.draft.finalAnswerType">
                    <option value="CULPRIT">CULPRIT</option>
                    <option value="WEAPON">WEAPON</option>
                    <option value="EVIDENCE">EVIDENCE</option>
                    <option value="HIDDEN_DOCUMENT">HIDDEN_DOCUMENT</option>
                    <option value="SECRET_KEYWORD">SECRET_KEYWORD</option>
                    <option value="HIDDEN_TRUTH">HIDDEN_TRUTH</option>
                  </select>
                </label>
                <label>최종 정답<input v-model.trim="draftResult.draft.finalAnswer" type="text" /></label>
                <label>질문 제한<input v-model.number="draftResult.draft.maxDeductionQuestions" type="number" min="1" /></label>
                <label class="wide">정답 alias, 쉼표 구분<input :value="listToCsv(draftResult.draft.finalAnswerAliases)" type="text" @input="draftResult.draft.finalAnswerAliases = csvToList($event.target.value)" /></label>
                <label class="wide">최종 질문<input v-model.trim="draftResult.draft.finalQuestion" type="text" /></label>
                <label class="wide">픽션 시놉시스<textarea v-model="draftResult.draft.fictionSynopsis" rows="3"></textarea></label>
                <label class="wide">진실 파일<textarea v-model="draftResult.draft.finalTruthSummary" rows="3"></textarea></label>
                <label class="wide">실제 역사 해설<textarea v-model="draftResult.draft.actualHistorySummary" rows="3"></textarea></label>
                <label class="wide">추리 secret facts, 줄바꿈 구분<textarea :value="listToLines(draftResult.draft.deductionSecretFacts)" rows="3" @input="draftResult.draft.deductionSecretFacts = linesToList($event.target.value)"></textarea></label>
                <label class="wide">정답 노출 금지어, 줄바꿈 구분<textarea :value="listToLines(draftResult.draft.deductionForbiddenReveals)" rows="3" @input="draftResult.draft.deductionForbiddenReveals = linesToList($event.target.value)"></textarea></label>
              </div>
            </details>

            <details open class="draft-edit-block">
              <summary>장소/퍼즐 초안</summary>
              <div class="draft-mission-list">
                <article v-for="mission in draftResult.draft.missions || []" :key="`draft-mission-${mission.order}`" class="draft-mission-card" :class="{ final: mission.finalPlace }">
                  <div class="spot-head">
                    <strong>{{ mission.order }}. {{ mission.placeName }}</strong>
                    <span>{{ mission.publicMarkerType }} / {{ mission.clueRole }}</span>
                  </div>
                  <div class="edit-grid">
                    <label>장소명<input v-model.trim="mission.placeName" type="text" /></label>
                    <label>주소<input v-model.trim="mission.address" type="text" /></label>
                    <label>위도<input v-model.number="mission.latitude" type="number" step="0.000001" /></label>
                    <label>경도<input v-model.number="mission.longitude" type="number" step="0.000001" /></label>
                    <label>내부 마커
                      <select v-model="mission.markerType" @change="mission.finalPlace = mission.markerType === 'FINAL'; syncDraftMissionRole(mission)">
                        <option value="START">START</option>
                        <option value="ANSWER_HINT">ANSWER_HINT</option>
                        <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                        <option value="STORY">STORY</option>
                        <option value="FINAL_CANDIDATE">FINAL_CANDIDATE</option>
                        <option value="FINAL">FINAL</option>
                      </select>
                    </label>
                    <label>공개 마커
                      <select v-model="mission.publicMarkerType">
                        <option value="START">START</option>
                        <option value="ANSWER_HINT">ANSWER_HINT</option>
                        <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                        <option value="STORY">STORY</option>
                        <option value="FINAL_CANDIDATE">FINAL_CANDIDATE</option>
                      </select>
                    </label>
                    <label>단서 역할
                      <select v-model="mission.clueRole">
                        <option value="START">START</option>
                        <option value="ANSWER_HINT">ANSWER_HINT</option>
                        <option value="DESTINATION_HINT">DESTINATION_HINT</option>
                        <option value="STORY_CONTEXT">STORY_CONTEXT</option>
                        <option value="FINAL_PLACE">FINAL_PLACE</option>
                      </select>
                    </label>
                    <label>도착 반경<input v-model.number="mission.arrivalRadius" type="number" min="10" /></label>
                    <label class="check"><input v-model="mission.finalPlace" type="checkbox" @change="mission.markerType = mission.finalPlace ? 'FINAL' : 'FINAL_CANDIDATE'; syncDraftMissionRole(mission)" /> 실제 최종 장소</label>
                    <label class="wide">사건 문구<textarea v-model="mission.storyText" rows="2"></textarea></label>
                    <label>퍼즐 유형
                      <select v-model="mission.puzzleType">
                        <option value="OBSERVATION">OBSERVATION</option>
                        <option value="NUMBER_LOCK">NUMBER_LOCK</option>
                        <option value="INITIAL_SOUND">INITIAL_SOUND</option>
                        <option value="PATTERN">PATTERN</option>
                        <option value="STORY_COMBINATION">STORY_COMBINATION</option>
                      </select>
                    </label>
                    <label>정답 형식
                      <select v-model="mission.answerFormat">
                        <option value="TEXT">TEXT</option>
                        <option value="NUMBER">NUMBER</option>
                        <option value="CHOICE">CHOICE</option>
                        <option value="CODE">CODE</option>
                      </select>
                    </label>
                    <label>퍼즐 정답<input v-model.trim="mission.answer" type="text" /></label>
                    <label>보상 단서<input v-model.trim="mission.rewardClue" type="text" /></label>
                    <label class="wide">퍼즐 질문<textarea v-model="mission.questionText" rows="2"></textarea></label>
                    <label class="wide">생성 근거<textarea v-model="mission.groundRule" rows="2"></textarea></label>
                  </div>
                  <div class="hint-edit-list">
                    <label v-for="(_, hintIndex) in mission.hints" :key="`hint-${mission.order}-${hintIndex}`">
                      H{{ hintIndex + 1 }}
                      <input v-model.trim="mission.hints[hintIndex]" type="text" />
                    </label>
                  </div>
                </article>
              </div>
            </details>

            <details class="draft-edit-block">
              <summary>용의자/증거 카드 초안</summary>
              <h4>용의자</h4>
              <div class="mini-grid">
                <article v-for="(suspect, index) in draftResult.draft.suspects || []" :key="`draft-suspect-${index}`">
                  <label>별칭<input v-model.trim="suspect.alias" type="text" /></label>
                  <label>표시 이름<input v-model.trim="suspect.displayName" type="text" /></label>
                  <label>의심 포인트<textarea v-model="suspect.suspiciousPoint" rows="2"></textarea></label>
                </article>
              </div>
              <h4>증거/메모/사진</h4>
              <div class="mini-grid">
                <article v-for="(evidence, index) in draftResult.draft.evidences || []" :key="`draft-evidence-${index}`">
                  <label>제목<input v-model.trim="evidence.title" type="text" /></label>
                  <label>타입
                    <select v-model="evidence.type">
                      <option value="PHOTO">PHOTO</option>
                      <option value="MEMO">MEMO</option>
                      <option value="NOTE">NOTE</option>
                      <option value="DOCUMENT">DOCUMENT</option>
                      <option value="EVIDENCE">EVIDENCE</option>
                      <option value="SUSPECT_CLUE">SUSPECT_CLUE</option>
                      <option value="POST_IT">POST_IT</option>
                      <option value="ANSWER_CLUE">ANSWER_CLUE</option>
                      <option value="DESTINATION_CLUE">DESTINATION_CLUE</option>
                      <option value="STORY_CLUE">STORY_CLUE</option>
                    </select>
                  </label>
                  <label>출처 순서<input v-model.number="evidence.sourceMissionOrder" type="number" min="1" /></label>
                  <label class="wide">이미지 URL<input v-model.trim="evidence.imageUrl" type="text" placeholder="비워두면 저장 시 자동 카드 이미지 생성" /></label>
                  <img v-if="evidence.imageUrl" class="draft-evidence-image" :src="evidence.imageUrl" alt="사건자료 이미지 미리보기" />
                  <label>요약<textarea v-model="evidence.textSummary" rows="2"></textarea></label>
                </article>
              </div>
            </details>
          </section>
          <section v-if="draftValidation" class="validation-panel" :class="{ invalid: !draftValidation.valid }">
            <div class="section-title">
              <h3>초안 검증 결과</h3>
              <strong>{{ draftValidation.valid ? '저장 가능' : '수정 필요' }} · 위험도 {{ draftValidation.riskScore }}</strong>
            </div>
            <p>{{ draftValidation.summary }}</p>
            <ul v-if="draftValidation.findings?.length">
              <li v-for="finding in draftValidation.findings" :key="`${finding.code}-${finding.missionOrder}-${finding.message}`">
                <b>{{ finding.severity }}</b>
                <span>{{ finding.code }}</span>
                <em v-if="finding.missionOrder">spot {{ finding.missionOrder }}</em>
                {{ finding.message }}
              </li>
            </ul>
            <p v-else class="empty">검증 이슈가 없습니다. 그래도 현장 검수는 필요합니다.</p>
          </section>
        </article>
    </section>
  </main>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminEpisodeApi } from '@/api/adminEpisodeApi';

const router = useRouter();
const episodes = ref([]);
const selected = ref(null);
const selectedEpisodeId = ref(null);
const loading = ref(false);
const message = ref('');
const messageType = ref('success');
const episodeForm = ref({});
const payloadValidation = ref({});
const publishReadiness = ref(null);
const previewOpen = ref(false);
const draftResult = ref(null);
const draftValidation = ref(null);
const activeAction = ref('');
const draftStatus = ref('');
const draftError = ref('');
const draftElapsedSeconds = ref(0);
const draftProgressStep = ref('');
let draftTimerId = null;
const candidateAreaCode = ref('seoul');
const candidateLoading = ref(false);
const candidateLoaded = ref(false);
const placeCandidates = ref([]);
const anchorCandidate = ref(null);
const nearbyCandidates = ref([]);
const nearbyLoading = ref(false);
const nearbyLoaded = ref(false);
const nearbyRadius = ref(1500);
const selectedCandidates = ref([]);
const manualCandidate = ref({
  title: '',
  address: '',
  latitude: '',
  longitude: '',
  description: ''
});
const draftInput = ref(JSON.stringify({
  area: '서울 정동길',
  era: '대한제국 말기',
  theme: '역사 미스터리',
  targetAudience: '20대 친구/데이트 코스',
  playTime: '90~120분',
  places: [
    { name: '대한문', description: '사건 진입 장소', visibleElements: ['문', '현판', '광장'], numbers: [], keywords: ['시작', '기록'], adminMemo: '현장 검수 필요', role: 'START' },
    { name: '덕수궁 돌담길', description: '돌담과 기록 단서', visibleElements: ['돌담', '가로등', '표지판'], numbers: ['1897'], keywords: ['황제', '사진'], adminMemo: '안내판 숫자 검수 필요', role: 'ANSWER_HINT' },
    { name: '정동제일교회', description: '붉은 벽과 목격 단서', visibleElements: ['붉은 벽', '건물명'], numbers: [], keywords: ['목격', '벽'], adminMemo: '건물명 확인', role: 'ANSWER_HINT' },
    { name: '배재학당 역사박물관', description: '기록 자료 단서', visibleElements: ['건물명', '안내판'], numbers: [], keywords: ['기록', '조수'], adminMemo: '운영시간 확인', role: 'ANSWER_HINT' },
    { name: '정동극장', description: '방향 단서', visibleElements: ['간판', '포스터'], numbers: [], keywords: ['방향', '문'], adminMemo: '포스터 내용은 고정 아님', role: 'DESTINATION_HINT' },
    { name: '서울시립미술관 앞마당', description: '최종 후보 장소', visibleElements: ['광장', '조형물'], numbers: [], keywords: ['후보', '그림자'], adminMemo: '후보 장소', role: 'FINAL_CANDIDATE' },
    { name: '중명전', description: '실제 최종 후보', visibleElements: ['붉은 벽', '건물명'], numbers: ['1905'], keywords: ['밀서', '문'], adminMemo: '실제 최종 장소 후보', role: 'FINAL' }
  ]
}, null, 2));
const draftStepOrder = [
  { key: 'prepare', label: '입력 정리', description: '선택 장소와 좌표를 초안 JSON으로 반영합니다.' },
  { key: 'request', label: 'AI 요청', description: '백엔드가 Gemini에 구조화 초안을 요청합니다.' },
  { key: 'parse', label: '응답 대기', description: '스토리, 퍼즐, 단서, 자료 카드 JSON을 기다립니다.' },
  { key: 'hydrate', label: '화면 반영', description: '받은 초안을 편집 가능한 폼과 이미지 카드로 표시합니다.' }
];

const draftBusy = computed(() => Boolean(activeAction.value));
const draftProgressPercent = computed(() => {
  if (!draftBusy.value) return 0;
  return Math.min(92, 12 + draftElapsedSeconds.value * 0.8);
});
const draftProgressSteps = computed(() => {
  const currentIndex = draftStepOrder.findIndex((step) => step.key === draftProgressStep.value);
  return draftStepOrder.map((step, index) => ({
    ...step,
    done: currentIndex > index
  }));
});
const canGenerateDraftFromSelection = computed(() => {
  const count = selectedCandidates.value.length;
  return count >= 8 && count <= 9 && selectedCandidates.value.every(hasCandidateCoordinate);
});
const draftSelectionBlockReason = computed(() => {
  const count = selectedCandidates.value.length;
  if (count < 8) return '기준 장소를 포함해 최소 8개 장소를 선택해야 Gemini 전체 초안 작성이 가능합니다.';
  if (count > 9) return '장소는 최대 9개까지만 사용할 수 있습니다.';
  const missing = selectedCandidates.value.filter((candidate) => !hasCandidateCoordinate(candidate));
  if (missing.length) {
    return `위도/경도가 없는 장소가 있습니다: ${missing.map((candidate) => candidate.title || '이름 없는 장소').join(', ')}`;
  }
  return '';
});
const selectedCandidateStatus = computed(() => {
  const count = selectedCandidates.value.length;
  if (count < 8) return '장소가 부족합니다. 공개 조건을 맞추려면 최소 8개를 선택하세요.';
  if (count > 9) return '장소가 너무 많습니다. 최대 9개까지만 사용하세요.';
  const missing = selectedCandidates.value.filter((candidate) => !hasCandidateCoordinate(candidate));
  if (missing.length) return `좌표가 없는 장소 ${missing.length}개가 있습니다. 좌표가 있는 후보로 교체하거나 수동 후보를 추가하세요.`;
  return '초안 생성에 사용할 수 있는 장소 구성입니다.';
});
const orderedSelectedCandidates = computed(() => {
  if (!anchorCandidate.value) return selectedCandidates.value;
  const anchorKey = candidateKey(anchorCandidate.value);
  return [
    ...selectedCandidates.value.filter((candidate) => candidateKey(candidate) !== anchorKey),
    anchorCandidate.value
  ];
});

onMounted(loadEpisodes);
onUnmounted(stopDraftTimer);

async function loadEpisodes() {
  loading.value = true;
  try {
    episodes.value = await adminEpisodeApi.getEpisodes();
    if (!selected.value && episodes.value.length) {
      await selectEpisode(episodes.value[0].id);
    }
  } catch (error) {
    setMessage(error.userMessage || '관리자 에피소드 목록을 불러올 수 없습니다.', 'error');
  } finally {
    loading.value = false;
  }
}

async function selectEpisode(episodeId) {
  selectedEpisodeId.value = episodeId;
  publishReadiness.value = null;
  previewOpen.value = false;
  try {
    selected.value = await adminEpisodeApi.getEpisode(episodeId);
    hydrateEpisodeForm(selected.value);
  } catch (error) {
    setMessage(error.userMessage || '에피소드 상세를 불러올 수 없습니다.', 'error');
  }
}

function goUserMap() {
  if (selected.value?.status !== 'PUBLISHED') {
    setMessage('DRAFT 사건파일은 사용자 지도 API에서 접근할 수 없습니다. PUBLISHED 전환 후 열어 주세요.', 'error');
    return;
  }
  router.push({ name: 'EpisodeMap', params: { episodeId: selectedEpisodeId.value } });
}

function goUserCaseFile() {
  if (selected.value?.status !== 'PUBLISHED') {
    setMessage('DRAFT 사건파일은 사용자 사건파일 API에서 접근할 수 없습니다. PUBLISHED 전환 후 열어 주세요.', 'error');
    return;
  }
  router.push({ name: 'EpisodeCaseFile', params: { episodeId: selectedEpisodeId.value } });
}

function markerPreviewLabel(type) {
  return {
    START: '시작',
    ANSWER_HINT: '정답 힌트',
    DESTINATION_HINT: '목적지 힌트',
    STORY: '스토리',
    FINAL_CANDIDATE: '조사 후보'
  }[type] || type;
}

function hydrateEpisodeForm(episode) {
  episodeForm.value = {
    title: episode.title || '',
    subtitle: episode.subtitle || '',
    era: episode.era || '',
    genre: episode.genre || '',
    difficulty: episode.difficulty || '',
    estimatedTime: episode.estimatedTime || '',
    estimatedDistance: episode.estimatedDistance || '',
    fictionSynopsis: episode.fictionSynopsis || '',
    finalAnswerType: episode.finalAnswerType || '',
    finalAnswer: episode.finalAnswer || '',
    finalAnswerAliases: episode.finalAnswerAliases || '',
    finalQuestion: episode.finalQuestion || '',
    finalTruthSummary: episode.finalTruthSummary || '',
    actualHistorySummary: episode.actualHistorySummary || '',
    deductionSecretFacts: episode.deductionSecretFacts || '',
    deductionForbiddenReveals: episode.deductionForbiddenReveals || '',
    maxDeductionQuestions: episode.maxDeductionQuestions || 20,
    recommendedPlayers: episode.recommendedPlayers || '',
    teamRoleGuide: episode.teamRoleGuide || '',
    noticeText: episode.noticeText || '',
    status: episode.status || 'DRAFT'
  };
}

async function refreshEpisodeList() {
  episodes.value = await adminEpisodeApi.getEpisodes();
}

async function createEpisode() {
  const createdAt = new Date().toLocaleString('ko-KR', { hour12: false });
  try {
    const created = await adminEpisodeApi.createEpisode({
      title: `새 사건파일 초안 ${createdAt}`
    });
    selected.value = created;
    selectedEpisodeId.value = created.id;
    hydrateEpisodeForm(created);
    publishReadiness.value = null;
    previewOpen.value = false;
    await refreshEpisodeList();
    setMessage('새 사건파일 DRAFT가 생성되었습니다. 핵심 정보를 수정한 뒤 장소/퍼즐/사건자료를 추가하세요.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '사건파일을 생성할 수 없습니다.', 'error');
  }
}

async function deleteEpisode() {
  if (!selected.value || !selectedEpisodeId.value) return;
  if (selected.value.status === 'PUBLISHED') {
    setMessage('PUBLISHED 사건파일은 먼저 ARCHIVED로 변경한 뒤 삭제하세요.', 'error');
    return;
  }
  const confirmed = window.confirm(`${selected.value.title} 사건파일을 삭제할까요? 장소, 퍼즐, 사건자료, 진행 기록, 리뷰가 함께 삭제됩니다.`);
  if (!confirmed) return;
  try {
    await adminEpisodeApi.deleteEpisode(selectedEpisodeId.value);
    selected.value = null;
    selectedEpisodeId.value = null;
    publishReadiness.value = null;
    previewOpen.value = false;
    await refreshEpisodeList();
    if (episodes.value.length) {
      await selectEpisode(episodes.value[0].id);
    }
    setMessage('사건파일이 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '사건파일을 삭제할 수 없습니다.', 'error');
  }
}

async function saveEpisode() {
  if (episodeForm.value.status === 'PUBLISHED') {
    setMessage('공개 전환은 공개 준비도 점검 통과 후 PUBLISHED 전환 버튼으로 진행해 주세요.', 'error');
    episodeForm.value.status = selected.value?.status || 'DRAFT';
    return;
  }
  try {
    selected.value = await adminEpisodeApi.updateEpisode(selectedEpisodeId.value, episodeForm.value);
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    await refreshEpisodeList();
    setMessage('에피소드 정보가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '에피소드를 수정할 수 없습니다.', 'error');
  }
}

async function checkPublishReadiness() {
  if (!selectedEpisodeId.value) return;
  try {
    publishReadiness.value = await adminEpisodeApi.getPublishReadiness(selectedEpisodeId.value);
    setMessage(
      publishReadiness.value.ready ? '공개 준비도 점검을 통과했습니다.' : '공개 전 수정해야 할 항목이 있습니다.',
      publishReadiness.value.ready ? 'success' : 'error'
    );
  } catch (error) {
    setMessage(error.userMessage || '공개 준비도를 점검할 수 없습니다.', 'error');
  }
}

async function publishEpisode() {
  if (!selectedEpisodeId.value) return;
  const readiness = publishReadiness.value?.ready
    ? publishReadiness.value
    : await adminEpisodeApi.getPublishReadiness(selectedEpisodeId.value);
  publishReadiness.value = readiness;
  if (!readiness.ready) {
    setMessage('공개 조건을 먼저 수정해 주세요.', 'error');
    return;
  }
  const confirmed = window.confirm('PUBLISHED로 공개하면 사용자 에피소드 목록과 플레이 API에 노출됩니다. 현장 검수와 스포일러 검수를 완료했습니까?');
  if (!confirmed) return;
  try {
    selected.value = await adminEpisodeApi.updateEpisode(selectedEpisodeId.value, {
      ...episodeForm.value,
      status: 'PUBLISHED'
    });
    hydrateEpisodeForm(selected.value);
    await refreshEpisodeList();
    publishReadiness.value = await adminEpisodeApi.getPublishReadiness(selectedEpisodeId.value);
    setMessage('사건파일이 PUBLISHED로 공개되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '사건파일을 공개할 수 없습니다.', 'error');
  }
}

async function saveSpot(spot) {
  try {
    selected.value = await adminEpisodeApi.updateSpot(selectedEpisodeId.value, spot.spotId, {
      placeName: spot.placeName,
      address: spot.address,
      latitude: spot.latitude,
      longitude: spot.longitude,
      markerType: spot.markerType,
      clueRole: spot.clueRole,
      publicMarkerType: spot.publicMarkerType,
      storyText: spot.storyText,
      arrivalRadius: spot.arrivalRadius,
      finalPlace: spot.finalPlace
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    await refreshEpisodeList();
    setMessage('장소 정보가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '장소를 수정할 수 없습니다.', 'error');
  }
}

async function addSpot() {
  try {
    selected.value = await adminEpisodeApi.createSpot(selectedEpisodeId.value, {
      placeName: '새 조사 장소',
      markerType: 'STORY',
      publicMarkerType: 'STORY',
      clueRole: 'STORY_CONTEXT',
      storyText: '관리자 검수용 새 조사 장소입니다.',
      arrivalRadius: 50,
      finalPlace: false
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    await refreshEpisodeList();
    setMessage('장소와 기본 퍼즐이 추가되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '장소를 추가할 수 없습니다.', 'error');
  }
}

async function removeSpot(spot) {
  if (!window.confirm(`${spot.placeName} 장소와 연결된 퍼즐을 삭제할까요?`)) return;
  try {
    selected.value = await adminEpisodeApi.deleteSpot(selectedEpisodeId.value, spot.spotId);
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    await refreshEpisodeList();
    setMessage('장소가 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '장소를 삭제할 수 없습니다.', 'error');
  }
}

async function savePuzzle(spot) {
  if (!spot.puzzle) return;
  try {
    const validation = await adminEpisodeApi.validateRewardPayload(selectedEpisodeId.value, spot.puzzle.rewardPayload);
    payloadValidation.value = { ...payloadValidation.value, [spot.puzzle.puzzleId]: validation };
    if (!validation.valid) {
      setMessage('reward_payload 오류를 먼저 수정해 주세요.', 'error');
      return;
    }
    selected.value = await adminEpisodeApi.updatePuzzle(selectedEpisodeId.value, spot.puzzle.puzzleId, {
      puzzleType: spot.puzzle.puzzleType,
      questionText: spot.puzzle.questionText,
      answer: spot.puzzle.answer,
      answerFormat: spot.puzzle.answerFormat,
      rewardClue: spot.puzzle.rewardClue,
      rewardPayload: spot.puzzle.rewardPayload,
      difficulty: spot.puzzle.difficulty,
      hints: (spot.puzzle.hints || []).map((hint) => hint.hintText)
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('퍼즐 정보가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '퍼즐을 수정할 수 없습니다.', 'error');
  }
}

async function saveSuspect(suspect) {
  try {
    selected.value = await adminEpisodeApi.updateSuspect(selectedEpisodeId.value, suspect.suspectId, {
      alias: suspect.alias,
      displayName: suspect.displayName,
      shortDescription: suspect.shortDescription,
      portraitImageUrl: suspect.portraitImageUrl,
      relationToVictim: suspect.relationToVictim,
      suspiciousPoint: suspect.suspiciousPoint,
      alibiSummary: suspect.alibiSummary,
      unlockedByDefault: suspect.unlockedByDefault,
      displayOrder: suspect.displayOrder
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('용의자 카드가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '용의자 카드를 수정할 수 없습니다.', 'error');
  }
}

async function addSuspect() {
  try {
    selected.value = await adminEpisodeApi.createSuspect(selectedEpisodeId.value, {
      alias: `용의자 ${(selected.value?.suspects || []).length + 1}`,
      displayName: '새 용의자',
      suspiciousPoint: '의심 포인트를 입력하세요.',
      unlockedByDefault: false
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('용의자 카드가 추가되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '용의자 카드를 추가할 수 없습니다.', 'error');
  }
}

async function removeSuspect(suspect) {
  if (!window.confirm(`${suspect.displayName} 용의자 카드를 삭제할까요? 관련 증거 연결은 해제됩니다.`)) return;
  try {
    selected.value = await adminEpisodeApi.deleteSuspect(selectedEpisodeId.value, suspect.suspectId);
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('용의자 카드가 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '용의자 카드를 삭제할 수 없습니다.', 'error');
  }
}

async function saveEvidence(evidence) {
  try {
    selected.value = await adminEpisodeApi.updateEvidence(selectedEpisodeId.value, evidence.evidenceId, {
      title: evidence.title,
      type: evidence.type,
      imageUrl: evidence.imageUrl,
      textSummary: evidence.textSummary,
      sourceSpotId: evidence.sourceSpotId || null,
      relatedSuspectId: evidence.relatedSuspectId || null,
      relatedClueType: evidence.relatedClueType,
      unlockedByDefault: evidence.unlockedByDefault,
      displayOrder: evidence.displayOrder
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('증거 카드가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '증거 카드를 수정할 수 없습니다.', 'error');
  }
}

async function addEvidence() {
  try {
    selected.value = await adminEpisodeApi.createEvidence(selectedEpisodeId.value, {
      title: '새 사건 자료',
      type: 'NOTE',
      textSummary: '관리자 검수용 사건 자료입니다.',
      unlockedByDefault: false
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('증거 카드가 추가되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '증거 카드를 추가할 수 없습니다.', 'error');
  }
}

async function removeEvidence(evidence) {
  if (!window.confirm(`${evidence.title} 자료 카드를 삭제할까요?`)) return;
  try {
    selected.value = await adminEpisodeApi.deleteEvidence(selectedEpisodeId.value, evidence.evidenceId);
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('증거 카드가 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '증거 카드를 삭제할 수 없습니다.', 'error');
  }
}

async function savePartnerReward(reward) {
  try {
    selected.value = await adminEpisodeApi.updatePartnerReward(selectedEpisodeId.value, reward.rewardId, {
      title: reward.title,
      description: reward.description,
      rewardType: reward.rewardType,
      partnerName: reward.partnerName,
      locationName: reward.locationName,
      latitude: reward.latitude,
      longitude: reward.longitude,
      status: reward.status
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('리워드 placeholder가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '리워드 placeholder를 수정할 수 없습니다.', 'error');
  }
}

async function validatePayload(spot) {
  if (!spot.puzzle) return;
  try {
    const validation = await adminEpisodeApi.validateRewardPayload(selectedEpisodeId.value, spot.puzzle.rewardPayload);
    payloadValidation.value = { ...payloadValidation.value, [spot.puzzle.puzzleId]: validation };
    setMessage(validation.valid ? 'reward_payload가 유효합니다.' : 'reward_payload에 오류가 있습니다.', validation.valid ? 'success' : 'error');
  } catch (error) {
    setMessage(error.userMessage || 'reward_payload를 검증할 수 없습니다.', 'error');
  }
}

function startDraftProgress(action, status, step = 'prepare') {
  activeAction.value = action;
  draftError.value = '';
  draftStatus.value = status;
  draftElapsedSeconds.value = 0;
  draftProgressStep.value = step;
  stopDraftTimer();
  draftTimerId = window.setInterval(() => {
    draftElapsedSeconds.value += 1;
    if (draftElapsedSeconds.value >= 2 && draftProgressStep.value === 'prepare') {
      draftProgressStep.value = 'request';
    }
    if (draftElapsedSeconds.value >= 8 && draftProgressStep.value === 'request') {
      draftProgressStep.value = 'parse';
    }
  }, 1000);
}

function finishDraftProgress(status = '') {
  draftProgressStep.value = 'hydrate';
  if (status) draftStatus.value = status;
  stopDraftTimer();
  activeAction.value = '';
}

function failDraftProgress(errorMessage) {
  draftError.value = errorMessage;
  stopDraftTimer();
  activeAction.value = '';
}

function stopDraftTimer() {
  if (draftTimerId) {
    window.clearInterval(draftTimerId);
    draftTimerId = null;
  }
}

async function generateDraft() {
  if (!prepareDraftInputFromSelection()) return;
  startDraftProgress('rule', 'API 키 없이 입력값 기반 예비 초안을 작성하고 있습니다.');
  try {
    const payload = JSON.parse(draftInput.value);
    draftProgressStep.value = 'request';
    draftResult.value = await adminEpisodeApi.createAiDraft(payload);
    draftProgressStep.value = 'hydrate';
    hydrateDraftForEditing();
    draftValidation.value = null;
    finishDraftProgress('예비 초안이 생성되었습니다. Gemini가 아니라 템플릿 기반 결과이므로 문장 품질 검수가 필요합니다.');
    setMessage('예비 사건파일 초안이 생성되었습니다. 아직 DB에는 저장되지 않았습니다.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || '초안을 생성할 수 없습니다.');
    setMessage(draftError.value, 'error');
  }
}

async function generateGeminiDraft() {
  if (!prepareDraftInputFromSelection()) return;
  startDraftProgress('gemini', 'Gemini가 사건 개요, 퍼즐, 단서, 용의자, 증거 카드 초안을 작성하고 있습니다. 최대 180초까지 기다립니다.');
  try {
    const payload = JSON.parse(draftInput.value);
    draftProgressStep.value = 'request';
    draftResult.value = await adminEpisodeApi.createGeminiDraft(payload);
    draftProgressStep.value = 'hydrate';
    hydrateDraftForEditing();
    draftValidation.value = null;
    finishDraftProgress('Gemini 초안이 생성되었습니다. 저장 전 최종 장소 은닉, 퍼즐 근거, 이미지 카드를 검수하세요.');
    setMessage('Gemini 사건파일 초안이 생성되었습니다. 아직 DB에는 저장되지 않았습니다.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || 'Gemini 초안을 생성할 수 없습니다. gemini.api.key와 gemini.model 설정을 확인하세요.');
    setMessage(draftError.value, 'error');
  }
}

async function validateDraft(useGemini) {
  if (!draftResult.value?.draft) return;
  startDraftProgress(useGemini ? 'geminiValidate' : 'validate', useGemini ? 'Gemini로 초안 위험 요소를 검토하고 있습니다.' : '기본 규칙으로 초안을 검증하고 있습니다.');
  try {
    const sourceInput = JSON.parse(draftInput.value);
    draftProgressStep.value = 'request';
    draftValidation.value = await adminEpisodeApi.validateAiDraft({
      draft: draftResult.value.draft,
      sourceInput,
      useGemini
    });
    draftProgressStep.value = 'hydrate';
    setMessage(
      draftValidation.value.valid ? '초안 검증을 통과했습니다. 저장 전 현장 검수는 계속 필요합니다.' : '초안에 수정이 필요한 항목이 있습니다.',
      draftValidation.value.valid ? 'success' : 'error'
    );
    finishDraftProgress(draftValidation.value.valid
      ? '검증을 통과했습니다. 그래도 현장 관찰 요소와 정답 노출 여부는 사람이 확인해야 합니다.'
      : '검증 이슈가 있습니다. 아래 검증 결과에서 ERROR 항목을 먼저 수정하세요.');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || '초안을 검증할 수 없습니다.');
    setMessage(draftError.value, 'error');
  }
}

async function saveDraft() {
  if (!draftResult.value?.draft) return;
  if (draftValidation.value && !draftValidation.value.valid) {
    setMessage('검증에서 차단 이슈가 발견된 초안은 저장하지 않는 것을 권장합니다. 수정 후 다시 검증해 주세요.', 'error');
    return;
  }
  startDraftProgress('save', '초안과 자동 생성 사건자료 이미지를 DRAFT로 저장하고 있습니다.');
  try {
    draftProgressStep.value = 'request';
    const saved = await adminEpisodeApi.saveAiDraft({ draft: buildDraftSavePayload(), status: 'DRAFT' });
    draftProgressStep.value = 'hydrate';
    selected.value = saved;
    selectedEpisodeId.value = saved.id;
    hydrateEpisodeForm(saved);
    await refreshEpisodeList();
    publishReadiness.value = await adminEpisodeApi.getPublishReadiness(saved.id);
    finishDraftProgress('DRAFT 저장이 완료되었습니다. 왼쪽 목록과 상세 검수 영역에 반영되었습니다.');
    setMessage('AI 사건파일 초안이 DRAFT로 저장되었습니다. 공개 준비도 결과를 확인하고 부족한 항목을 수정해 주세요.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || 'AI 초안을 저장할 수 없습니다.');
    setMessage(draftError.value, 'error');
  }
}

function hydrateDraftForEditing() {
  const draft = draftResult.value?.draft;
  if (!draft) return;
  draft.finalAnswerAliases = Array.isArray(draft.finalAnswerAliases) ? draft.finalAnswerAliases : [];
  draft.deductionSecretFacts = Array.isArray(draft.deductionSecretFacts) ? draft.deductionSecretFacts : [];
  draft.deductionForbiddenReveals = Array.isArray(draft.deductionForbiddenReveals) ? draft.deductionForbiddenReveals : [];
  draft.missions = Array.isArray(draft.missions) ? draft.missions : [];
  draft.suspects = Array.isArray(draft.suspects) ? draft.suspects : [];
  draft.evidences = Array.isArray(draft.evidences) ? draft.evidences : [];
  if (isGenericDraftTitle(draft.episodeTitle)) {
    draft.episodeTitle = suggestedDraftTitle(draft);
  }
  draft.evidences.forEach((evidence) => {
    evidence.imageUrl = evidence.imageUrl || generatedEvidenceCardDataUrl(evidence.title, evidence.type);
  });
  draft.missions.forEach((mission, index) => {
    mission.order = mission.order || index + 1;
    mission.hints = Array.isArray(mission.hints) ? mission.hints : [];
    while (mission.hints.length < 3) mission.hints.push('');
    mission.arrivalRadius = mission.arrivalRadius || 50;
    syncDraftMissionRole(mission);
  });
}

function buildDraftSavePayload() {
  const draft = JSON.parse(JSON.stringify(draftResult.value.draft));
  if (isGenericDraftTitle(draft.episodeTitle)) {
    draft.episodeTitle = suggestedDraftTitle(draft);
  }
  draft.evidences = (draft.evidences || []).map((evidence) => {
    const imageUrl = String(evidence.imageUrl || '').trim();
    return {
      ...evidence,
      imageUrl: imageUrl.startsWith('data:') || imageUrl.length > 900 ? '' : imageUrl
    };
  });
  return draft;
}

function isGenericDraftTitle(title) {
  const normalized = String(title || '').toLowerCase().replaceAll(' ', '');
  return !normalized || normalized.includes('ep.new') || normalized.includes('draft') || normalized.includes('episode');
}

function suggestedDraftTitle(draft) {
  const missions = Array.isArray(draft?.missions) ? draft.missions : [];
  const anchor = missions.find((mission) => mission.finalPlace || mission.markerType === 'FINAL') || missions[0];
  return `EP.NEW ${anchor?.placeName || 'Operation KOREA'} 사건`;
}

function syncDraftMissionRole(mission) {
  if (!mission) return;
  const isFinal = mission.finalPlace === true || mission.markerType === 'FINAL';
  if (isFinal) {
    mission.finalPlace = true;
    mission.markerType = 'FINAL';
    mission.clueRole = 'FINAL_PLACE';
    mission.publicMarkerType = 'FINAL_CANDIDATE';
    return;
  }
  if (mission.markerType === 'START') {
    mission.clueRole = 'START';
    mission.publicMarkerType = 'START';
  } else if (mission.markerType === 'ANSWER_HINT') {
    mission.clueRole = 'ANSWER_HINT';
    mission.publicMarkerType = 'ANSWER_HINT';
  } else if (mission.markerType === 'DESTINATION_HINT') {
    mission.clueRole = 'DESTINATION_HINT';
    mission.publicMarkerType = 'DESTINATION_HINT';
  } else if (mission.markerType === 'STORY') {
    mission.clueRole = 'STORY_CONTEXT';
    mission.publicMarkerType = 'STORY';
  } else if (mission.markerType === 'FINAL_CANDIDATE') {
    mission.clueRole = mission.clueRole === 'FINAL_PLACE' ? 'DESTINATION_HINT' : mission.clueRole;
    mission.publicMarkerType = 'FINAL_CANDIDATE';
  }
}

function generatedEvidenceCardDataUrl(title = 'CASE FILE', type = 'EVIDENCE') {
  const safeTitle = escapeXml(title || 'CASE FILE');
  const safeType = escapeXml(type || 'EVIDENCE');
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
      <defs>
        <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stop-color="#1f2937"/>
          <stop offset="0.55" stop-color="#111827"/>
          <stop offset="1" stop-color="#78350f"/>
        </linearGradient>
      </defs>
      <rect width="960" height="640" fill="url(#bg)"/>
      <rect x="70" y="56" width="820" height="528" rx="28" fill="#f5e8cc" opacity="0.95"/>
      <rect x="108" y="96" width="744" height="124" rx="16" fill="#111827" opacity="0.92"/>
      <text x="132" y="148" fill="#fbbf24" font-family="Georgia, serif" font-size="32" font-weight="700">OPERATION KOREA</text>
      <text x="132" y="190" fill="#e5e7eb" font-family="Arial, sans-serif" font-size="22">GENERATED CASE MATERIAL</text>
      <path d="M132 292 C240 238, 354 350, 462 292 S690 236, 818 292" fill="none" stroke="#92400e" stroke-width="18" opacity="0.25"/>
      <circle cx="250" cy="392" r="72" fill="#111827" opacity="0.88"/>
      <rect x="372" y="330" width="390" height="34" rx="17" fill="#78350f" opacity="0.75"/>
      <rect x="372" y="386" width="310" height="28" rx="14" fill="#92400e" opacity="0.55"/>
      <rect x="372" y="438" width="360" height="28" rx="14" fill="#92400e" opacity="0.38"/>
      <text x="132" y="544" fill="#111827" font-family="Arial, sans-serif" font-size="28" font-weight="700">${safeTitle}</text>
      <text x="132" y="580" fill="#78350f" font-family="Arial, sans-serif" font-size="20">${safeType} · fictional evidence card</text>
    </svg>
  `;
  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`;
}

function escapeXml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function listToCsv(values) {
  return Array.isArray(values) ? values.join(', ') : '';
}

function csvToList(value) {
  return String(value || '').split(',').map((item) => item.trim()).filter(Boolean);
}

function listToLines(values) {
  return Array.isArray(values) ? values.join('\n') : '';
}

function linesToList(value) {
  return String(value || '').split('\n').map((item) => item.trim()).filter(Boolean);
}

function normalizeCandidate(candidate = {}) {
  const latitude = coordinateValue(candidate.latitude, candidate.lat, candidate.y, candidate.mapY);
  const longitude = coordinateValue(candidate.longitude, candidate.lng, candidate.lon, candidate.x, candidate.mapX);
  return {
    ...candidate,
    title: candidate.title || candidate.name || candidate.placeName || candidate.place_name || '',
    address: candidate.address || candidate.roadAddress || candidate.road_address_name || candidate.address_name || '',
    latitude,
    longitude,
    description: candidate.description || candidate.overview || candidate.adminMemo || '',
    source: candidate.source || candidate.provider || '장소 후보'
  };
}

function coordinateValue(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === '') continue;
    const numberValue = Number(value);
    if (!Number.isNaN(numberValue)) return numberValue;
  }
  return null;
}

function hasCandidateCoordinate(candidate) {
  const normalized = normalizeCandidate(candidate);
  return typeof normalized.latitude === 'number'
    && typeof normalized.longitude === 'number'
    && normalized.latitude >= -90
    && normalized.latitude <= 90
    && normalized.longitude >= -180
    && normalized.longitude <= 180;
}

function prepareDraftInputFromSelection() {
  if (!canGenerateDraftFromSelection.value) {
    draftError.value = draftSelectionBlockReason.value || '선택 장소를 확인해 주세요.';
    setMessage(draftError.value, 'error');
    return false;
  }
  applyCandidatesToDraft(false);
  draftError.value = '';
  return true;
}

async function loadPlaceCandidates() {
  candidateLoading.value = true;
  candidateLoaded.value = false;
  anchorCandidate.value = null;
  nearbyCandidates.value = [];
  nearbyLoaded.value = false;
  selectedCandidates.value = [];
  try {
    const candidates = await adminEpisodeApi.getPlaceCandidates(candidateAreaCode.value);
    placeCandidates.value = candidates.map(normalizeCandidate);
    candidateLoaded.value = true;
    setMessage(placeCandidates.value.length ? 'TourAPI 장소 후보를 불러왔습니다.' : 'TourAPI 장소 후보가 없습니다.', placeCandidates.value.length ? 'success' : 'error');
  } catch (error) {
    placeCandidates.value = [];
    candidateLoaded.value = true;
    setMessage(error.userMessage || 'TourAPI 장소 후보를 불러올 수 없습니다.', 'error');
  } finally {
    candidateLoading.value = false;
  }
}

async function loadNearbyCandidates(candidate) {
  if (!candidate) return;
  const normalizedAnchor = normalizeCandidate(candidate);
  if (!hasCandidateCoordinate(normalizedAnchor)) {
    setMessage('이 기준 장소에는 위도/경도가 없습니다. 좌표가 있는 장소를 선택하거나 수동 후보로 추가하세요.', 'error');
    return;
  }
  anchorCandidate.value = normalizedAnchor;
  nearbyLoading.value = true;
  nearbyLoaded.value = false;
  selectedCandidates.value = [normalizedAnchor];
  try {
    const nearby = await adminEpisodeApi.getNearbyPlaceCandidates({
      lat: normalizedAnchor.latitude,
      lng: normalizedAnchor.longitude,
      radius: nearbyRadius.value
    });
    const normalizedNearby = nearby.map(normalizeCandidate).filter(hasCandidateCoordinate);
    const anchorKey = candidateKey(normalizedAnchor);
    nearbyCandidates.value = [
      { ...normalizedAnchor, source: 'TourAPI 기준 장소', description: normalizedAnchor.description || 'TourAPI 기준 장소입니다.' },
      ...normalizedNearby.filter((item) => candidateKey(item) !== anchorKey)
    ];
    nearbyLoaded.value = true;
    setMessage(nearbyCandidates.value.length > 1 ? 'Kakao Local 주변 후보를 불러왔습니다.' : '주변 후보가 부족합니다. 반경을 넓히거나 수동 후보를 추가하세요.', nearbyCandidates.value.length > 1 ? 'success' : 'error');
  } catch (error) {
    nearbyCandidates.value = [];
    nearbyLoaded.value = true;
    selectedCandidates.value = [normalizedAnchor];
    setMessage(error.userMessage || 'Kakao Local 주변 후보를 불러올 수 없습니다. API 키/도메인을 확인하거나 수동 후보를 추가하세요.', 'error');
  } finally {
    nearbyLoading.value = false;
  }
}

function addManualCandidate() {
  const latitude = Number(manualCandidate.value.latitude);
  const longitude = Number(manualCandidate.value.longitude);
  if (!manualCandidate.value.title || Number.isNaN(latitude) || Number.isNaN(longitude)) {
    setMessage('수동 후보는 장소명, 위도, 경도를 입력해야 합니다.', 'error');
    return;
  }
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
    setMessage('위도/경도 범위를 확인해 주세요.', 'error');
    return;
  }
  const candidate = {
    title: manualCandidate.value.title,
    address: manualCandidate.value.address,
    latitude,
    longitude,
    areaCode: candidateAreaCode.value,
    source: '관리자 수동 후보',
    description: manualCandidate.value.description || '관리자가 수동으로 추가한 주변 후보입니다. 운영 공개 전 현장 검수가 필요합니다.'
  };
  const normalizedCandidate = normalizeCandidate(candidate);
  if (nearbyCandidates.value.some((item) => candidateKey(item) === candidateKey(normalizedCandidate))) {
    setMessage('이미 추가된 후보입니다.', 'error');
    return;
  }
  nearbyCandidates.value = [...nearbyCandidates.value, normalizedCandidate];
  nearbyLoaded.value = true;
  if (selectedCandidates.value.length < 9) {
    selectedCandidates.value = [...selectedCandidates.value, normalizedCandidate];
  }
  manualCandidate.value = { title: '', address: '', latitude: '', longitude: '', description: '' };
  setMessage('수동 후보가 추가되었습니다. 운영 공개 전 현장 검수를 진행하세요.');
}

function candidateKey(candidate) {
  const normalized = normalizeCandidate(candidate);
  return `${normalized.title}|${normalized.address}|${normalized.latitude ?? ''}|${normalized.longitude ?? ''}`;
}

function isCandidateSelected(candidate) {
  const key = candidateKey(candidate);
  return selectedCandidates.value.some((item) => candidateKey(item) === key);
}

function isAnchorCandidate(candidate) {
  return Boolean(anchorCandidate.value) && candidateKey(anchorCandidate.value) === candidateKey(candidate);
}

function toggleCandidate(candidate) {
  const normalizedCandidate = normalizeCandidate(candidate);
  const key = candidateKey(normalizedCandidate);
  if (anchorCandidate.value && candidateKey(anchorCandidate.value) === key) {
    setMessage('TourAPI 기준 장소는 항상 포함됩니다. 기준 장소를 바꾸려면 1단계에서 다른 장소를 선택하세요.', 'error');
    return;
  }
  if (isCandidateSelected(candidate)) {
    selectedCandidates.value = selectedCandidates.value.filter((item) => candidateKey(item) !== key);
    return;
  }
  if (!hasCandidateCoordinate(normalizedCandidate)) {
    setMessage('위도/경도가 없는 장소는 초안 생성에 사용할 수 없습니다. 좌표가 있는 후보로 교체하거나 수동 후보를 추가하세요.', 'error');
    return;
  }
  if (selectedCandidates.value.length >= 9) {
    setMessage('장소는 최대 9개까지 선택할 수 있습니다.', 'error');
    return;
  }
  selectedCandidates.value = [...selectedCandidates.value, normalizedCandidate];
}

function applyCandidatesToDraft(showMessage = true) {
  if (selectedCandidates.value.length < 8) {
    setMessage('기준 장소를 포함해 최소 8개 이상의 장소를 선택해 주세요.', 'error');
    return;
  }
  if (!canGenerateDraftFromSelection.value) {
    setMessage(draftSelectionBlockReason.value || '선택 장소의 좌표를 확인해 주세요.', 'error');
    return;
  }
  const orderedCandidates = orderedSelectedCandidates.value.map(normalizeCandidate);
  const roles = buildRoles(orderedCandidates.length);
  const payload = {
    area: areaLabel(candidateAreaCode.value),
    era: '관리자 검수 필요',
    theme: '역사 미스터리',
    targetAudience: '야외 방탈출 플레이어',
    playTime: '90~120분',
    places: orderedCandidates.map((candidate, index) => ({
      name: candidate.title,
      address: candidate.address,
      latitude: candidate.latitude,
      longitude: candidate.longitude,
      description: candidate.description || (isAnchorCandidate(candidate)
        ? 'TourAPI 기준 장소입니다. 사건의 실제 내부 최종 장소로 사용되며 운영 공개 전 현장 검수가 필요합니다.'
        : 'Kakao Local 주변 후보입니다. 실제 역사/현장 정보는 관리자 검수 후 사용하세요.'),
      visibleElements: ['관리자 현장 메모 필요'],
      numbers: [],
      keywords: [candidate.title, areaLabel(candidateAreaCode.value), candidate.source || '장소 후보'],
      adminMemo: `${candidate.source || '장소 후보'} 기반입니다. ${isAnchorCandidate(candidate) ? '이 장소는 내부 최종 장소입니다. ' : ''}실제 현장 간판, 숫자, 조형물은 운영 공개 전 검수하세요.`,
      role: roles[index],
      publicMarkerType: publicMarkerForCandidate(index, roles[index], orderedCandidates.length),
      arrivalRadius: 50
    }))
  };
  draftInput.value = JSON.stringify(payload, null, 2);
  draftResult.value = null;
  draftValidation.value = null;
  if (showMessage) {
    setMessage('선택한 후보가 초안 입력에 반영되었습니다. TourAPI 기준 장소는 내부 최종 장소로 저장됩니다.', 'success');
  }
}

function buildRoles(count) {
  const roles = [];
  for (let index = 0; index < count; index += 1) {
    if (index === 0) roles.push('START');
    else if (index === count - 1) roles.push('FINAL');
    else if (index >= count - 3) roles.push('DESTINATION_HINT');
    else if (index <= 4) roles.push('ANSWER_HINT');
    else roles.push('STORY');
  }
  return roles;
}

function publicMarkerForCandidate(index, role, count) {
  if (role === 'FINAL' || index === count - 2) return 'FINAL_CANDIDATE';
  return role;
}

function roleForCandidate(index) {
  return buildRoles(orderedSelectedCandidates.value.length)[index] || 'STORY';
}

function roleLabel(role) {
  return {
    START: '시작 장소',
    ANSWER_HINT: '정답 힌트',
    DESTINATION_HINT: '목적지 힌트',
    STORY: '스토리 단서',
    FINAL_CANDIDATE: '조사 후보',
    FINAL: '내부 최종 장소'
  }[role] || role;
}

function areaLabel(areaCode) {
  const labels = {
    seoul: '서울',
    gangwon: '강원권',
    chungnam: '충남권',
    chungbuk: '충북권',
    jeonbuk: '전북권',
    jeonnam: '전남권',
    gyeongbuk: '경북권',
    gyeongnam: '경남권',
    jeju: '제주'
  };
  return labels[areaCode] || '서울';
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}
</script>

<style scoped>
.admin-episode-page { min-height: 100vh; box-sizing: border-box; padding: 24px 16px 70px; background: radial-gradient(circle at 8% 8%, rgba(245,158,11,.18), transparent 30%), linear-gradient(155deg, #111827, #0f172a 58%, #050505); color: #f8fafc; font-family: 'Noto Sans KR', Georgia, serif; }
.admin-hero { width: min(100%, 1180px); margin: 0 auto 16px; display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; padding: 20px; border: 1px solid rgba(148,163,184,.2); border-radius: 20px; background: rgba(15,23,42,.72); }
.admin-hero p { margin: 0 0 6px; color: #f59e0b; font-size: .75rem; font-weight: 900; letter-spacing: .14em; }
.admin-hero h1 { margin: 0; font-size: clamp(1.8rem, 8vw, 3.2rem); }
.admin-hero span { display: block; margin-top: 8px; color: #cbd5e1; }
.hero-actions { display: flex; flex-wrap: wrap; gap: 8px; }
button { min-height: 40px; border: 0; border-radius: 10px; background: #f59e0b; color: #111827; font-weight: 900; padding: 0 13px; cursor: pointer; transition: transform .12s ease, box-shadow .12s ease, filter .12s ease, border-color .12s ease; }
button:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 10px 22px rgba(245,158,11,.2); filter: brightness(1.05); }
button:active:not(:disabled), button.busy { transform: translateY(1px) scale(.98); box-shadow: 0 0 0 3px rgba(245,158,11,.22); }
button:focus-visible { outline: 3px solid rgba(251,191,36,.55); outline-offset: 2px; }
button:disabled { opacity: .45; cursor: not-allowed; }
.layout { width: min(100%, 1180px); margin: 0 auto; display: grid; grid-template-columns: 330px 1fr; gap: 14px; }
.episode-list, .detail-card, .draft-panel { border: 1px solid rgba(148,163,184,.2); border-radius: 18px; background: rgba(15,23,42,.68); padding: 16px; }
.section-title { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
h2, h3 { margin: 0 0 10px; }
.episode-card { padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(2,6,23,.38); margin-top: 10px; cursor: pointer; }
.episode-card.active { border-color: #f59e0b; box-shadow: 0 0 0 1px rgba(245,158,11,.38) inset; }
.episode-card strong, .spot-card strong, .mini-grid strong { display: block; }
.episode-card span, .spot-card span, .mini-grid span { color: #cbd5e1; font-size: .84rem; }
.metrics { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.metrics em { border-radius: 999px; background: rgba(245,158,11,.14); color: #fde68a; padding: 5px 8px; font-style: normal; font-size: .76rem; }
.message { padding: 10px; border-radius: 12px; margin: 0 0 10px; }
.message.success { background: rgba(22,101,52,.22); color: #bbf7d0; }
.message.error { background: rgba(127,29,29,.34); color: #fecaca; }
.eyebrow { margin: 0 0 4px; color: #f59e0b !important; font-weight: 900; letter-spacing: .14em; font-size: .72rem; }
.detail-head { display: flex; justify-content: space-between; gap: 12px; }
.detail-head p { margin: 0 0 5px; color: #f59e0b; font-weight: 900; letter-spacing: .12em; font-size: .72rem; }
.detail-head strong { color: #fde68a; }
.detail-status { display: grid; justify-items: end; gap: 7px; min-width: 128px; }
.detail-status small { max-width: 170px; color: #94a3b8; font-size: .72rem; line-height: 1.35; text-align: right; }
.detail-status .danger-btn { margin-top: 0; }
.secret-box { margin: 14px 0; padding: 14px; border: 1px solid rgba(248,113,113,.38); border-radius: 14px; background: rgba(127,29,29,.18); }
.secret-box p { margin: 6px 0; color: #fee2e2; }
.admin-preview-panel { margin: 14px 0; padding: 14px; border: 1px solid rgba(59,130,246,.24); border-radius: 16px; background: rgba(30,64,175,.12); }
.admin-preview-panel p { margin: 8px 0 0; color: #bfdbfe; font-size: .86rem; line-height: 1.55; }
.preview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; margin-top: 12px; }
.preview-grid article { border: 1px solid rgba(148,163,184,.16); border-radius: 14px; background: rgba(2,6,23,.32); padding: 12px; }
.preview-grid strong { display: block; margin-bottom: 8px; color: #fde68a; }
.preview-grid ul { display: grid; gap: 7px; list-style: none; padding: 0; margin: 0; }
.preview-grid li { display: grid; gap: 4px; color: #e2e8f0; font-size: .84rem; line-height: 1.4; }
.preview-grid span { width: fit-content; border-radius: 999px; padding: 3px 7px; background: rgba(148,163,184,.14); color: #cbd5e1; font-size: .72rem; font-weight: 900; }
.preview-grid span.START { color: #93c5fd; background: rgba(37,99,235,.16); }
.preview-grid span.ANSWER_HINT { color: #fdba74; background: rgba(234,88,12,.16); }
.preview-grid span.DESTINATION_HINT { color: #d8b4fe; background: rgba(126,34,206,.16); }
.preview-grid span.STORY { color: #86efac; background: rgba(21,128,61,.16); }
.preview-grid span.FINAL_CANDIDATE { color: #cbd5e1; background: rgba(31,41,55,.7); }
.preview-grid em { color: #94a3b8; font-size: .72rem; font-style: normal; }
.stat-grid, .mini-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; margin: 12px 0 18px; }
.stat-grid article, .mini-grid article, .spot-card { border: 1px solid rgba(148,163,184,.16); border-radius: 14px; background: rgba(2,6,23,.32); padding: 12px; }
.stat-grid strong { font-size: 1.5rem; color: #fde68a; }
.stat-grid span, .empty { color: #94a3b8; }
.spot-list { display: grid; gap: 10px; }
.spot-card.final { border-color: rgba(248,113,113,.62); }
.spot-head { display: flex; justify-content: space-between; gap: 8px; }
.spot-card p, .mini-grid p, .draft-panel p { color: #cbd5e1; line-height: 1.55; }
.internal { color: #fecaca !important; font-weight: 900; }
summary { cursor: pointer; color: #fde68a; font-weight: 900; }
pre, textarea { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.2); border-radius: 12px; background: rgba(2,6,23,.72); color: #e2e8f0; padding: 12px; overflow: auto; }
textarea { margin-top: 10px; font: 12px ui-monospace, SFMono-Regular, Consolas, monospace; resize: vertical; }
.edit-section { margin: 14px 0; padding: 12px; border: 1px solid rgba(245,158,11,.28); border-radius: 14px; background: rgba(2,6,23,.26); }
.publish-rules { margin: 10px 0 12px; padding: 10px; border: 1px solid rgba(245,158,11,.22); border-radius: 12px; background: rgba(245,158,11,.08); }
.publish-rules strong { color: #fde68a; }
.publish-rules p { margin: 6px 0 0; color: #cbd5e1; line-height: 1.5; font-size: .86rem; }
.publish-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.publish-btn { background: #16a34a; color: #f0fdf4; }
.publish-btn:disabled { opacity: .42; cursor: not-allowed; }
.readiness-panel { margin: 10px 0 14px; padding: 12px; border: 1px solid rgba(248,113,113,.4); border-radius: 14px; background: rgba(127,29,29,.16); }
.readiness-panel.ready { border-color: rgba(34,197,94,.4); background: rgba(22,101,52,.16); }
.readiness-panel .section-title { align-items: flex-start; }
.readiness-panel .section-title strong { color: #cbd5e1; font-size: .84rem; }
.readiness-metrics { display: flex; flex-wrap: wrap; gap: 7px; margin: 10px 0; }
.readiness-metrics span { border: 1px solid rgba(148,163,184,.2); border-radius: 999px; padding: 5px 8px; color: #e2e8f0; font-size: .78rem; }
.readiness-panel ul { margin: 8px 0 0; padding-left: 18px; color: #fecaca; line-height: 1.55; }
.readiness-panel.ready ul { color: #bbf7d0; }
.publish-ready-note { margin: 10px 0 0; padding: 10px; border-radius: 10px; background: rgba(22,163,74,.14); color: #bbf7d0 !important; font-weight: 800; }
.edit-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 12px 0; }
.edit-grid label { display: grid; gap: 6px; color: #cbd5e1; font-size: .82rem; font-weight: 800; }
.edit-grid label.wide { grid-column: 1 / -1; }
.edit-grid label.check { display: flex; align-items: center; gap: 8px; }
input, select { width: 100%; box-sizing: border-box; border: 1px solid rgba(148,163,184,.22); border-radius: 10px; background: rgba(2,6,23,.72); color: #e2e8f0; padding: 10px; font: inherit; }
.puzzle-edit { margin-top: 16px; border-top: 1px solid rgba(148,163,184,.16); padding-top: 12px; }
.payload-actions { display: flex; align-items: center; gap: 10px; margin: 8px 0 12px; }
.payload-actions.compact { margin: 0; flex-wrap: wrap; justify-content: flex-end; }
.payload-actions.action-bar { flex-wrap: wrap; justify-content: flex-end; }
.ghost-btn { border: 1px solid rgba(148,163,184,.28); background: transparent; color: #fde68a; }
.primary-action { background: linear-gradient(135deg, #f59e0b, #f97316); color: #111827; }
.save-draft-btn { border: 1px solid rgba(34,197,94,.42); background: #16a34a; color: #f0fdf4; }
.danger-btn { margin-top: 8px; border: 1px solid rgba(248,113,113,.42); background: rgba(127,29,29,.34); color: #fecaca; }
.valid { color: #86efac; font-weight: 900; }
.invalid { color: #fecaca; font-weight: 900; }
.warning { color: #fde68a; font-weight: 800; }
.validation-box { margin: 10px 0; padding: 10px; border: 1px solid rgba(148,163,184,.18); border-radius: 12px; background: rgba(2,6,23,.42); }
.validation-box p { margin: 4px 0; }
.validation-box ul { margin: 8px 0 0; padding-left: 18px; color: #cbd5e1; }
.draft-editor { margin: 14px 0; padding: 14px; border: 1px solid rgba(59,130,246,.24); border-radius: 16px; background: rgba(30,64,175,.12); }
.draft-editor .section-title span { color: #bfdbfe; font-size: .82rem; }
.ai-mode-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin: 12px 0; }
.ai-mode-grid article { padding: 14px; border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(2,6,23,.34); }
.ai-mode-grid strong { display: block; color: #fde68a; margin-bottom: 7px; }
.ai-mode-grid span { color: #cbd5e1; font-size: .84rem; line-height: 1.5; }
.draft-status-box { margin: 12px 0; padding: 12px 14px; border: 1px solid rgba(59,130,246,.34); border-radius: 14px; background: rgba(30,64,175,.16); }
.draft-status-box.error { border-color: rgba(248,113,113,.52); background: rgba(127,29,29,.2); }
.draft-status-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.draft-status-box strong { color: #bfdbfe; }
.draft-status-box.error strong { color: #fecaca; }
.draft-status-head span { color: #fde68a; font-weight: 900; font-size: .82rem; }
.draft-status-box p { margin: 6px 0 0; color: #e2e8f0; }
.draft-progress-bar { position: relative; height: 9px; overflow: hidden; margin-top: 12px; border-radius: 999px; background: rgba(15,23,42,.8); border: 1px solid rgba(148,163,184,.18); }
.draft-progress-bar i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, #f59e0b, #38bdf8, #22c55e); transition: width .4s ease; }
.draft-step-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 12px 0 0; padding: 0; list-style: none; }
.draft-step-list li { padding: 10px; border: 1px solid rgba(148,163,184,.18); border-radius: 12px; background: rgba(2,6,23,.28); opacity: .58; }
.draft-step-list li.active { opacity: 1; border-color: rgba(56,189,248,.55); box-shadow: 0 0 0 1px rgba(56,189,248,.18) inset; }
.draft-step-list li.done { opacity: .82; border-color: rgba(34,197,94,.38); background: rgba(20,83,45,.16); }
.draft-step-list b { display: block; color: #f8fafc; font-size: .82rem; }
.draft-step-list span { display: block; margin-top: 5px; color: #cbd5e1; font-size: .74rem; line-height: 1.4; }
.creation-flow { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 14px 0; }
.creation-flow article { display: grid; gap: 5px; min-height: 112px; box-sizing: border-box; padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(2,6,23,.32); }
.creation-flow article.done { border-color: rgba(34,197,94,.38); background: rgba(20,83,45,.2); }
.creation-flow b { display: grid; place-content: center; width: 28px; height: 28px; border-radius: 999px; background: #f59e0b; color: #111827; }
.creation-flow strong { color: #f8fafc; }
.creation-flow span { color: #cbd5e1; font-size: .78rem; line-height: 1.4; }
.draft-edit-block { margin-top: 12px; padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(2,6,23,.28); }
.draft-edit-block h4 { margin: 12px 0 8px; color: #fde68a; }
.draft-mission-list { display: grid; gap: 10px; margin-top: 10px; }
.draft-mission-card { border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(15,23,42,.5); padding: 12px; }
.draft-mission-card.final { border-color: rgba(248,113,113,.55); }
.hint-edit-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.hint-edit-list label, .draft-edit-block .mini-grid label { display: grid; gap: 6px; color: #cbd5e1; font-size: .8rem; font-weight: 800; }
.draft-evidence-image { width: 100%; aspect-ratio: 3 / 2; object-fit: cover; border-radius: 12px; border: 1px solid rgba(245,158,11,.25); background: rgba(2,6,23,.5); }
.validation-panel { margin: 12px 0; padding: 14px; border: 1px solid rgba(34,197,94,.35); border-radius: 14px; background: rgba(22,101,52,.16); }
.validation-panel.invalid { border-color: rgba(248,113,113,.5); background: rgba(127,29,29,.18); color: inherit; font-weight: inherit; }
.validation-panel ul { margin: 10px 0 0; padding: 0; list-style: none; display: grid; gap: 7px; }
.validation-panel li { padding: 8px; border-radius: 10px; background: rgba(2,6,23,.28); color: #e5e7eb; }
.validation-panel b { margin-right: 7px; color: #fde68a; }
.validation-panel span { margin-right: 7px; color: #93c5fd; font-size: .78rem; }
.validation-panel em { margin-right: 7px; color: #fca5a5; font-style: normal; font-size: .78rem; }
.card-editor { margin-top: 10px; padding-top: 9px; border-top: 1px solid rgba(148,163,184,.16); }
.card-editor label { display: grid; gap: 6px; margin-top: 8px; color: #cbd5e1; font-size: .8rem; font-weight: 800; }
.card-editor label.check { display: flex; align-items: center; gap: 8px; }
.card-editor button { margin-top: 10px; width: 100%; }
.candidate-panel { margin: 14px 0; padding: 12px; border: 1px solid rgba(245,158,11,.22); border-radius: 14px; background: rgba(2,6,23,.28); }
.candidate-help { color: #fde68a !important; font-size: .86rem; }
.ops-notice, .manual-candidate-form { margin: 10px 0; padding: 12px; border: 1px solid rgba(125,211,252,.24); border-radius: 14px; background: rgba(8,47,73,.22); }
.ops-notice strong, .manual-candidate-form strong { display: block; color: #bae6fd; margin-bottom: 6px; }
.ops-notice p, .manual-candidate-form p { margin: 0; color: #cbd5e1; font-size: .82rem; line-height: 1.55; }
.manual-candidate-form { border-color: rgba(245,158,11,.28); background: rgba(120,53,15,.16); }
.manual-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-bottom: 8px; }
.manual-grid label, .manual-note { display: grid; gap: 5px; color: #fed7aa; font-size: .8rem; font-weight: 800; }
.manual-note { margin-bottom: 8px; }
.manual-note textarea { min-height: 64px; resize: vertical; }
.candidate-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; max-height: 360px; overflow: auto; }
.candidate-card { border: 1px solid rgba(148,163,184,.16); border-radius: 12px; background: rgba(15,23,42,.58); padding: 10px; }
.candidate-card.selected { border-color: #f59e0b; box-shadow: 0 0 0 1px rgba(245,158,11,.34) inset; }
.candidate-card p { margin: 6px 0; color: #cbd5e1; font-size: .82rem; line-height: 1.45; }
.candidate-card span { color: #94a3b8; font-size: .75rem; }
.selection-summary { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 10px 0; padding: 10px 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 12px; background: rgba(15,23,42,.55); }
.selection-summary strong { color: #fff7ed; }
.selection-summary span { color: #fecaca; font-size: .82rem; font-weight: 900; }
.selection-summary span.ready { color: #86efac; }
.selected-route { margin-top: 12px; padding: 12px; border: 1px solid rgba(59,130,246,.26); border-radius: 14px; background: rgba(30,64,175,.13); }
.selected-route h4 { margin: 0 0 8px; color: #bfdbfe; }
.selected-route ol { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
.selected-route li { display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 8px; padding: 8px; border-radius: 10px; background: rgba(2,6,23,.34); }
.selected-route b { display: grid; place-content: center; width: 24px; height: 24px; border-radius: 999px; background: rgba(148,163,184,.2); color: #e2e8f0; }
.selected-route li > span { border-radius: 999px; padding: 4px 7px; font-size: .72rem; font-weight: 900; }
.selected-route .START { color: #93c5fd; background: rgba(37,99,235,.16); }
.selected-route .ANSWER_HINT { color: #fdba74; background: rgba(234,88,12,.16); }
.selected-route .DESTINATION_HINT { color: #d8b4fe; background: rgba(126,34,206,.16); }
.selected-route .STORY { color: #86efac; background: rgba(21,128,61,.16); }
.selected-route .FINAL { color: #fecaca; background: rgba(127,29,29,.22); }
.selected-route em { grid-column: 2 / -1; color: #fecaca; font-size: .75rem; font-style: normal; font-weight: 900; }
.selected-route p { margin: 10px 0 0; color: #cbd5e1; font-size: .82rem; }
.draft-actions-helper { display: flex; justify-content: space-between; gap: 10px; margin: 14px 0 8px; padding: 10px 12px; border-radius: 12px; background: rgba(245,158,11,.1); border: 1px solid rgba(245,158,11,.22); }
.draft-actions-helper strong { color: #fde68a; }
.draft-actions-helper span { color: #cbd5e1; font-size: .84rem; line-height: 1.45; }
.reward { opacity: .82; }
@media (max-width: 860px) { .admin-hero, .layout { display: block; } .hero-actions { margin-top: 12px; } .detail-panel { margin-top: 14px; } .stat-grid, .mini-grid, .edit-grid, .candidate-grid, .manual-grid, .hint-edit-list, .creation-flow, .preview-grid, .ai-mode-grid, .draft-step-list { grid-template-columns: 1fr; } .selection-summary, .draft-actions-helper { display: grid; } .selected-route li { grid-template-columns: 24px 1fr; } .selected-route li > span { grid-column: 2; justify-self: start; } }
</style>
