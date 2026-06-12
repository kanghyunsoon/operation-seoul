<template>
  <main class="admin-episode-page">
    <header class="admin-hero">
      <div>
        <p>ADMIN CASE OPS</p>
        <h1>에피소드 관리</h1>
        <span>미션 파일, 장소, 퍼즐, 내부 최종 장소, 예정 리워드를 점검합니다.</span>
      </div>
      <div class="hero-actions">
        <button type="button" class="ghost-btn" @click="router.push({ name: 'EpisodeList' })">전역 미션 선택</button>
        <button type="button" @click="router.push({ name: 'AdminUsers' })">회원 관리</button>
        <button type="button" @click="router.push({ name: 'AdminReviews' })">리뷰 관리</button>
      </div>
    </header>

    <nav class="admin-page-tabs" aria-label="관리자 사건 관리 모드">
      <button type="button" :class="{ active: activeAdminTab === 'episodes' }" @click="activeAdminTab = 'episodes'">
        사건 편집
      </button>
      <button type="button" :class="{ active: activeAdminTab === 'builder' }" @click="openBuilderTab">
        AI 자동 작성
      </button>
    </nav>
    <p v-if="message" class="message global-message" :class="messageType">{{ message }}</p>

    <section class="layout" :class="{ 'builder-layout': activeAdminTab === 'builder' }">
      <aside v-if="activeAdminTab === 'episodes'" class="episode-list">
        <div class="section-title">
          <h2>미션 파일 목록</h2>
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

      <section v-if="activeAdminTab === 'episodes'" class="detail-panel">
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
                  사용자 미션 파일 열기
                </button>
              </div>
            </div>
            <p>
              DRAFT는 사용자 API에서 접근할 수 없으므로 이 관리자 미리보기로만 확인합니다.
              PUBLISHED 이후에는 실제 사용자 지도/미션 파일 화면으로 이동할 수 있습니다.
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
                <strong>관계자 카드</strong>
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

          <nav class="admin-section-tabs" aria-label="에피소드 편집 섹션 이동">
            <button type="button" @click="scrollToAdminSection('admin-core')">핵심 정보</button>
            <button type="button" @click="scrollToAdminSection('admin-spots')">장소/퍼즐</button>
            <button type="button" @click="scrollToAdminSection('admin-assets')">관계자/해금 자료</button>
            <button type="button" @click="openBuilderTab">AI 초안</button>
          </nav>

            <details id="admin-core" class="edit-section admin-anchor" open>
              <summary>에피소드 핵심 정보 수정</summary>
              <div class="publish-rules">
                <strong>공개 전 필수 조건</strong>
                <p>장소 9개 권장, START 1개, 정답 키워드 미션 4개, 장소 키워드 미션 3개, 내부 최종 장소 1개, 모든 퍼즐/힌트/reward_payload가 필요합니다.</p>
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
                  <span>관계자 {{ publishReadiness.summary?.suspectCount || 0 }}</span>
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

          <div id="admin-spots" class="section-title admin-anchor">
            <h3>장소/퍼즐 검수</h3>
            <button type="button" class="ghost-btn" @click="addSpot">장소 추가</button>
          </div>
          <div class="spot-list">
            <article v-for="spot in selected.spots || []" :key="spot.spotId" class="spot-card" :class="{ final: spot.finalPlace, 'review-required': isReviewRequiredSpot(spot) }">
              <div class="spot-head">
                <strong>{{ spot.placeName }}</strong>
                <span>{{ spot.publicMarkerType }} / {{ spot.clueRole }}</span>
              </div>
              <div v-if="isReviewRequiredSpot(spot)" class="review-required-badge">
                검수필요 초안 · 문제/정답/힌트를 확정해야 합니다
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


                    </select>
                  </label>
                  <label>내부 마커
                    <select v-model="spot.markerType">
                      <option value="START">START</option>
                      <option value="ANSWER_HINT">ANSWER_HINT</option>
                      <option value="DESTINATION_HINT">DESTINATION_HINT</option>


                      <option value="FINAL">FINAL</option>
                    </select>
                  </label>
                  <label>단서 역할
                    <select v-model="spot.clueRole">
                      <option value="START">START</option>
                      <option value="ANSWER_HINT">ANSWER_HINT</option>
                      <option value="DESTINATION_HINT">DESTINATION_HINT</option>

                      <option value="FINAL_PLACE">FINAL_PLACE</option>
                    </select>
                  </label>
                  <label>도착 반경<input v-model.number="spot.arrivalRadius" type="number" min="10" /></label>
                  <label class="check"><input v-model="spot.finalPlace" type="checkbox" /> 실제 최종 장소</label>
                  <label class="wide">주소<input v-model.trim="spot.address" type="text" /></label>
                  <label>위도<input v-model.number="spot.latitude" type="number" step="0.000001" /></label>
                  <label>경도<input v-model.number="spot.longitude" type="number" step="0.000001" /></label>
                  <label class="wide">사건 문구<textarea v-model="spot.storyText" rows="2"></textarea></label>
                  <label class="check"><input v-model="spot.fieldVerified" type="checkbox" /> 현장 검수 완료</label>
                  <label class="wide">현장 검수 메모<textarea v-model="spot.fieldVerificationNote" rows="2" placeholder="좌표, 안내판/숫자/오브젝트, 접근 가능 여부를 확인한 내용을 기록"></textarea></label>
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

          <div id="admin-assets" class="section-title admin-anchor">
            <h3>관계자/해금 자료</h3>
            <div class="payload-actions">
              <button type="button" class="ghost-btn" @click="addSuspect">관계자 추가</button>
              <button type="button" class="ghost-btn" @click="addEvidence">증거 추가</button>
            </div>
          </div>
          <div class="mini-grid">
            <article v-for="suspect in selected.suspects || []" :key="suspect.suspectId">
              <strong>{{ suspect.alias }} · {{ suspect.displayName }}</strong>
              <p>{{ suspect.suspiciousPoint }}</p>
              <span>{{ suspect.unlockedByDefault ? '기본 해금' : '조건 해금' }}</span>
              <details class="card-editor">
                <summary>관계자 수정</summary>
                <label>별칭<input v-model.trim="suspect.alias" type="text" /></label>
                <label>표시 이름<input v-model.trim="suspect.displayName" type="text" /></label>
                <label>짧은 설명<input v-model.trim="suspect.shortDescription" type="text" /></label>
                <label>관계<input v-model.trim="suspect.relationToVictim" type="text" /></label>
                <label>의심 포인트<textarea v-model="suspect.suspiciousPoint" rows="2"></textarea></label>
                <label>알리바이<textarea v-model="suspect.alibiSummary" rows="2"></textarea></label>
                <label>초상 이미지 URL<input v-model.trim="suspect.portraitImageUrl" type="url" /></label>
                <label class="wide">초상 이미지 생성 프롬프트<textarea v-model="suspect.imagePrompt" rows="4" placeholder="외부 이미지 AI에 붙여넣을 카드별 프롬프트"></textarea></label>
                <button type="button" class="ghost-btn mini" @click="suspect.imagePrompt = buildSuspectImagePrompt(suspect)">프롬프트 재생성</button>
                <button type="button" class="ghost-btn mini" @click="copyImagePrompt(suspect.imagePrompt)">프롬프트 복사</button>
                <label>표시 순서<input v-model.number="suspect.displayOrder" type="number" /></label>
                <label class="check"><input v-model="suspect.unlockedByDefault" type="checkbox" /> 기본 해금</label>
                <button type="button" @click="saveSuspect(suspect)">관계자 저장</button>
                <button type="button" class="danger-btn" @click="removeSuspect(suspect)">관계자 삭제</button>
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
                <label class="wide">이미지 생성 프롬프트<textarea v-model="evidence.imagePrompt" rows="4" placeholder="외부 이미지 AI에 붙여넣을 카드별 프롬프트"></textarea></label>
                <button type="button" class="ghost-btn mini" @click="evidence.imagePrompt = buildEvidenceImagePrompt(evidence)">프롬프트 재생성</button>
                <button type="button" class="ghost-btn mini" @click="copyImagePrompt(evidence.imagePrompt)">프롬프트 복사</button>
                <label>출처 장소 ID<input v-model.number="evidence.sourceSpotId" type="number" /></label>
                <label>관련 관계자 ID<input v-model.number="evidence.relatedSuspectId" type="number" /></label>
                <label>관련 단서 타입<input v-model.trim="evidence.relatedClueType" type="text" /></label>
                <label>표시 순서<input v-model.number="evidence.displayOrder" type="number" /></label>
                <label>요약<textarea v-model="evidence.textSummary" rows="3"></textarea></label>
                <label class="check"><input v-model="evidence.unlockedByDefault" type="checkbox" /> 기본 해금</label>
                <button type="button" @click="saveEvidence(evidence)">증거 저장</button>
                <button type="button" class="danger-btn" @click="removeEvidence(evidence)">증거 삭제</button>
              </details>
            </article>
          </div>

          <h3>예정 리워드</h3>
          <div class="mini-grid">
            <article v-for="reward in selected.partnerRewards || []" :key="reward.rewardId" class="reward">
              <strong>{{ reward.title }}</strong>
              <p>{{ reward.description }}</p>
              <span>{{ reward.rewardType }} · {{ reward.status }}</span>
              <details class="card-editor">
                <summary>예정 리워드 수정</summary>
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
            <p v-if="!(selected.partnerRewards || []).length" class="empty">등록된 예정 리워드가 없습니다.</p>
          </div>

          <section class="audit-panel">
            <div class="section-title">
              <div>
                <p class="eyebrow">OPERATION AUDIT</p>
                <h3>관리자 변경 이력</h3>
              </div>
              <button type="button" class="ghost-btn" :disabled="auditLoading" @click="loadAuditLogs(selected.id)">
                {{ auditLoading ? '불러오는 중' : '이력 새로고침' }}
              </button>
            </div>
            <p class="audit-help">정답이나 프롬프트 원문은 기록하지 않고, 관리자·작업 대상·요청 추적 ID만 보존합니다.</p>
            <ol v-if="auditLogs.length" class="audit-list">
              <li v-for="log in auditLogs" :key="log.auditId">
                <span class="audit-marker" :class="auditActionTone(log.action)"></span>
                <div>
                  <div class="audit-title">
                    <strong>{{ auditActionLabel(log.action) }}</strong>
                    <time>{{ formatAuditDate(log.createdAt) }}</time>
                  </div>
                  <p>{{ log.summary }}</p>
                  <small>
                    {{ log.actorNickname || log.actorEmail }} · {{ log.targetType }}
                    <template v-if="log.targetId"> #{{ log.targetId }}</template>
                    <template v-if="log.requestId"> · request {{ log.requestId }}</template>
                  </small>
                </div>
              </li>
            </ol>
            <p v-else-if="!auditLoading" class="empty">아직 기록된 관리자 변경 이력이 없습니다.</p>
          </section>
        </article>


      </section>

      <article v-if="activeAdminTab === 'builder'" id="admin-draft" class="draft-panel full-width admin-anchor">
          <div class="section-title">
            <div>
              <p class="eyebrow">CASE BUILDER</p>
              <h2>AI 미션 파일 자동 작성</h2>
            </div>
            <div class="payload-actions action-bar">
              <button type="button" class="ghost-btn" :disabled="draftBusy || !canGenerateDraftFromSelection" :class="{ busy: activeAction === 'enrich' }" @click="enrichSelectedSiteData">
                {{ activeAction === 'enrich' ? '현장 근거 보강 중...' : 'RAG 현장근거 보강' }}
              </button>
              <button type="button" class="primary-action" :disabled="draftBusy || !canGenerateDraftFromSelection || draftPlan?.planReviewRequired" :class="{ busy: activeAction === 'gemini' }" @click="generateGeminiDraft">
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
              <button v-if="draftResult?.draft" type="button" class="ghost-btn" :disabled="draftBusy" @click="normalizeDraftBeforeSave(draftResult.draft, true)">
                검증 전 자동 보정
              </button>
              <button v-if="draftResult?.draft" type="button" class="save-draft-btn" :disabled="draftBusy" :class="{ busy: activeAction === 'save' }" @click="saveDraft">
                {{ activeAction === 'save' ? 'DB 저장 중...' : 'DRAFT로 저장' }}
              </button>
            </div>
          </div>
          <section class="case-builder-next">
            <div>
              <strong>{{ caseBuilderNext.title }}</strong>
              <p>{{ caseBuilderNext.description }}</p>
            </div>
            <button type="button" class="primary-action" :disabled="draftBusy || caseBuilderNext.disabled" @click="runNextCaseBuilderAction">
              {{ caseBuilderNext.button }}
            </button>
          </section>
          <section v-if="draftPlan" class="draft-feedback-panel keyword-plan-panel">
            <strong>AI 장르/최종 정답 키워드 계획</strong>
            <p>장르: {{ draftPlan.selectedGenreName }}</p>
            <p v-if="draftPlan.planReviewRequired" class="plan-review-warning">
              자동 초안 생성을 중단했습니다. {{ draftPlan.reviewReason }}
            </p>
            <div class="plan-keyword-list">
              <div v-for="item in draftPlan.finalAnswerKeywords || []" :key="`${item.label}-${item.keyword}`" class="plan-keyword-item">
                <strong>{{ item.label }}: {{ item.keyword }}</strong>
                <small>{{ item.sourceType }} · {{ item.sourcePlaceName }} · {{ item.sourceText }}</small>
              </div>
            </div>
            <p v-if="draftPlan.finalQuestionGuide">최종 질문 방향: {{ draftPlan.finalQuestionGuide }}</p>
            <p v-if="draftPlan.rationale">{{ draftPlan.rationale }}</p>
          </section>
          <div v-if="draftValidation || draftResult?.validationWarnings?.length || draftResult?.draft" class="draft-feedback-panel" :class="{ invalid: draftValidation && !draftValidation.valid }">
            <strong>{{ draftValidation ? (draftValidation.valid ? '검증 통과' : '검증 이슈 있음') : '초안 준비 완료' }}</strong>
            <p v-if="draftValidation">{{ draftValidationSummary }}</p>
            <p v-else>초안을 확인한 뒤 DRAFT 저장을 누르면 새 에피소드가 생성되고 상단 상세 패널이 해당 에피소드로 전환됩니다.</p>
            <ul v-if="draftValidation?.findings?.length">
              <li v-for="finding in draftValidation.findings.slice(0, 6)" :key="`top-${finding.code}-${finding.missionOrder}-${finding.message}`">
                <b>{{ finding.severity }}</b>
                <span>{{ finding.code }}</span>
                <em v-if="finding.missionOrder">spot {{ finding.missionOrder }}</em>
                {{ finding.message }}
              </li>
            </ul>
            <ul v-else-if="draftWarningSummary.length">
              <li v-for="warning in draftWarningSummary" :key="warning">{{ warning }}</li>
            </ul>
            <small v-if="draftValidation?.findings?.length > 6">나머지 이슈는 아래 전체 검증 결과에서 확인하세요.</small>
          </div>
          <div class="ai-mode-grid">
            <article>
              <strong>Gemini 전체 작성</strong>
              <span>관리자가 선택한 장소와 메모를 기반으로 스토리, 퍼즐, 단서, 관계자 카드, 해금 자료 카드 초안을 생성합니다. 생성 직후 최종 장소 은닉과 카드 수는 자동 보정됩니다.</span>
            </article>
            <article>
              <strong>예비 초안</strong>
              <span>Gemini 키가 없거나 호출 실패 시 쓰는 안전 fallback입니다. AI가 아니라 입력값 기반 템플릿입니다.</span>
            </article>
            <article>
              <strong>검증 정책</strong>
              <span>ERROR가 있어도 DRAFT 저장은 가능하게 두고, PUBLISHED 전 readiness에서 실제 차단합니다. AI 검증은 보조 검수로 사용합니다.</span>
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
          <p class="warning">저장된 초안은 항상 DRAFT로 시작합니다. 현장 좌표, 숫자, 표지판 검수 후 PUBLISHED로 변경하세요.</p>

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
              <strong>9개 장소 구성</strong>
              <span>TourAPI 기준 장소는 루트의 마지막 내부 목적지로 저장됩니다.</span>
            </article>
            <article :class="{ done: Boolean(draftResult?.draft) }">
              <b>4</b>
              <strong>초안 생성/검증</strong>
              <span>스토리, 퍼즐, 사건자료를 생성하고 검수합니다.</span>
            </article>
          </section>

          <section class="candidate-panel">
            <div class="section-title">
              <h3>1. TourAPI 기준 장소</h3>
              <div class="payload-actions">
                <select v-model="candidateAreaCode">
                  <option value="seoul">서울</option>
                  <option value="capital_area">서울 외 수도권(인천/경기)</option>
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
            <p class="candidate-help">TourAPI 기준 장소는 서버 내부 추리 기준 장소로 저장됩니다. 사용자 지도에는 정답 장소로 표시하지 않고, 단서를 모아 비교해야 하는 조사 지점처럼 보입니다.</p>
            <div class="ops-notice">
              <strong>운영 설정 확인</strong>
              <p>TourAPI와 Kakao Local 후보 조회에는 백엔드 API 키가 필요합니다. 키가 없으면 수동 후보를 추가해서도 초안을 만들 수 있습니다.</p>
            </div>
            <p v-if="candidateLoading" class="empty">TourAPI 후보를 불러오는 중입니다.</p>
            <p v-else-if="candidateLoadError" class="empty">{{ candidateLoadError }}</p>
            <p v-else-if="candidateLoaded && !placeCandidates.length" class="empty">선택한 지역에서 TourAPI 후보를 찾지 못했습니다.</p>
            <div class="candidate-grid">
              <article v-for="candidate in placeCandidates" :key="candidateKey(candidate)" class="candidate-card" :class="{ selected: anchorCandidate && candidateKey(anchorCandidate) === candidateKey(candidate) }">
                <strong>{{ candidate.title }}</strong>
                <p>{{ candidate.address || '주소 없음' }}</p>
                <span>{{ candidate.latitude }}, {{ candidate.longitude }}</span>
                <button type="button" class="ghost-btn" :disabled="!hasCandidateCoordinate(candidate)" @click="loadNearbyCandidates(candidate)">이 장소 기준으로 주변 후보 찾기</button>
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
                <button type="button" class="ghost-btn" :disabled="!anchorCandidate || nearbyCandidates.length < 2" @click="rerollRecommendedRoute">추천 루트 다시 구성</button>
                <button type="button" class="ghost-btn" :disabled="!canGenerateDraftFromSelection" @click="applyCandidatesToDraft">추천 루트를 초안 입력에 적용</button>
              </div>
            </div>
            <p class="candidate-help">기준 장소 포함 9개를 사용합니다. Kakao 후보가 부족하면 아래 수동 후보를 추가하세요.</p>
            <div class="selection-summary">
              <strong>선택 {{ selectedCandidates.length }}개 / 권장 9개</strong>
              <span :class="{ ready: canGenerateDraftFromSelection }">{{ selectedCandidateStatus }}</span>
            </div>
            <p v-if="nearbyLoading" class="empty">Kakao Local 주변 후보를 불러오는 중입니다.</p>
            <p v-else-if="nearbyLoaded && !nearbyCandidates.length" class="empty">주변 후보가 없습니다. Kakao REST API 키, 반경을 확인하거나 수동 후보를 추가하세요.</p>
            <div class="manual-candidate-form">
              <strong>수동 후보 추가</strong>
              <div class="manual-grid">
                <label>장소명<input v-model.trim="manualCandidate.title" type="text" placeholder="예: 골목 카페" /></label>
                <label>주소<input v-model.trim="manualCandidate.address" type="text" placeholder="도로명 또는 지번 주소" /></label>
                <label>위도<input v-model="manualCandidate.latitude" type="number" step="0.000001" placeholder="37.5665" /></label>
                <label>경도<input v-model="manualCandidate.longitude" type="number" step="0.000001" placeholder="126.9780" /></label>
              </div>
              <label class="manual-note">현장 메모<textarea v-model.trim="manualCandidate.description" rows="2" placeholder="관리자가 확인한 현장 관찰 요소나 운영 메모를 적으세요."></textarea></label>
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
                <span>{{ candidate.source }} &middot; {{ candidate.latitude }}, {{ candidate.longitude }}</span>
                <div class="candidate-actions">
                  <button v-if="isCandidateSelected(candidate) && !isAnchorCandidate(candidate)" type="button" class="ghost-btn mini" @click.stop="replaceSelectedCandidate(candidate)">다른 후보로 교체</button>
                  <button v-else-if="!isCandidateSelected(candidate)" type="button" class="ghost-btn mini" :disabled="selectedCandidates.length >= 9 || !hasCandidateCoordinate(candidate)" @click.stop="toggleCandidate(candidate)">루트에 추가</button>
                </div>
                <em v-if="!hasCandidateCoordinate(candidate)" class="coordinate-warning">좌표 없음: 초안 생성 불가</em>
              </article>
            </div>
            <div v-if="selectedCandidates.length" class="selected-route">
              <h4>추천 루트 역할 미리보기</h4>
              <p class="route-summary">{{ routeIdentitySummary }}</p>
              <ol>
                <li v-for="(candidate, index) in orderedSelectedCandidates" :key="candidateKey(candidate)">
                  <b>{{ index + 1 }}</b>
                  <strong>{{ candidate.title }}</strong>
                  <span :class="roleForCandidate(index)">{{ roleLabel(roleForCandidate(index)) }}</span>
                  <em v-if="isAnchorCandidate(candidate)">TourAPI 기준 장소 · 관리자 내부 목적지</em>
                </li>
              </ol>
              <p>사용자 지도에는 내부 최종 장소가 노출되지 않고, 관리자 테스트에서만 최종 좌표를 확인합니다.</p>
            </div>
          </section>
          <div class="draft-actions-helper">
            <strong>3. 초안 입력 준비</strong>
            <span>선택 장소, 좌표, 관리자 메모는 내부 payload로 자동 전달됩니다. 화면에는 JSON을 노출하지 않습니다.</span>
          </div>          <section v-if="draftResult?.draft" class="draft-editor">
            <div class="section-title">
              <h3>초안 폼 편집</h3>
              <span>아래 수정 내용은 검증과 DRAFT 저장에 바로 반영됩니다.</span>
            </div>

            <details open class="draft-edit-block">
              <summary>미션 파일 기본 정보</summary>
              <div class="edit-grid">
                <label>제목<input v-model.trim="draftResult.draft.episodeTitle" type="text" /></label>
                <label>부제<input v-model.trim="draftResult.draft.subtitle" type="text" /></label>
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
                <label class="wide">Fiction Mode 임무 브리핑<textarea v-model="draftResult.draft.fictionSynopsis" rows="3"></textarea></label>
                <label class="wide">Fact Mode 3. 픽션과 역사의 매칭<textarea v-model="draftResult.draft.finalTruthSummary" rows="4"></textarea></label>
                <label class="wide">Fact Mode 1~2. 모티브 공개/실제 배경 해설<textarea v-model="draftResult.draft.actualHistorySummary" rows="5"></textarea></label>
                <label class="wide">추리 secret facts, 줄바꿈 구분<textarea :value="listToLines(draftResult.draft.deductionSecretFacts)" rows="3" @input="draftResult.draft.deductionSecretFacts = linesToList($event.target.value)"></textarea></label>
                <label class="wide">정답 노출 금지어, 줄바꿈 구분<textarea :value="listToLines(draftResult.draft.deductionForbiddenReveals)" rows="3" @input="draftResult.draft.deductionForbiddenReveals = linesToList($event.target.value)"></textarea></label>
              </div>
            </details>
            <details open class="draft-edit-block">
              <summary>장소/퍼즐 초안</summary>
              <p class="draft-section-help">각 장소는 접어서 볼 수 있습니다. 최종 장소는 내부 설정으로만 쓰이고, 사용자 지도에는 공개 마커만 내려갑니다.</p>
              <div class="payload-actions compact">
                <button type="button" class="ghost-btn" @click="regenerateAllMissionsSafely">전체 미션 안전 재구성</button>
                <button type="button" class="ghost-btn" @click="normalizeDraftBeforeSave(draftResult.draft, true)">저장 전 자동 보정</button>
              </div>
              <div class="draft-mission-list">
                <details v-for="mission in draftResult.draft.missions || []" :key="`draft-mission-${mission.order}`" class="draft-mission-card" :class="{ final: mission.finalPlace }">
                  <summary class="draft-card-summary">
                    <span>
                      <strong>{{ mission.order }}. {{ mission.placeName }}</strong>
                      <small>{{ missionRoleLabel(mission) }} · {{ puzzleTypeLabel(mission.puzzleType) }} · {{ missionReadinessLabel(mission) }}</small>
                    </span>
                    <em>{{ mission.finalPlace ? '내부 최종 장소' : '펼쳐서 편집' }}</em>
                  </summary>
                  <p class="draft-card-preview">{{ mission.storyText || '스토리 문구를 입력하세요.' }}</p>
                  <div class="draft-mission-tags">
                    <span>{{ mission.publicMarkerType }}</span>
                    <span>{{ mission.clueRole }}</span>
                    <span>보상: {{ mission.rewardClue || '미정' }}</span>
                  </div>
                  <div class="payload-actions compact">
                    <button type="button" class="ghost-btn mini" @click="regenerateMissionDraft(mission)">이 미션 안전 재구성</button>
                    <button type="button" class="ghost-btn mini" @click="refreshMissionEvidenceCard(mission)">사건자료 카드 연결</button>
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


                        <option value="FINAL">FINAL</option>
                      </select>
                    </label>
                    <label>공개 마커
                      <select v-model="mission.publicMarkerType">
                        <option value="START">START</option>
                        <option value="ANSWER_HINT">ANSWER_HINT</option>
                        <option value="DESTINATION_HINT">DESTINATION_HINT</option>


                      </select>
                    </label>
                    <label>단서 역할
                      <select v-model="mission.clueRole">
                        <option value="START">START</option>
                        <option value="ANSWER_HINT">ANSWER_HINT</option>
                        <option value="DESTINATION_HINT">DESTINATION_HINT</option>

                        <option value="FINAL_PLACE">FINAL_PLACE</option>
                      </select>
                    </label>
                    <label>도착 반경<input v-model.number="mission.arrivalRadius" type="number" min="10" /></label>
                    <label class="check"><input v-model="mission.finalPlace" type="checkbox" @change="mission.markerType = mission.finalPlace ? 'FINAL' : 'DESTINATION_HINT'; syncDraftMissionRole(mission)" /> 실제 최종 장소</label>
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
                </details>
              </div>
            </details>

            <details class="draft-edit-block">
              <summary>관계자/해금 자료 카드 초안</summary>
              <h4>관계자</h4>
              <div class="mini-grid">
                <details v-for="(suspect, index) in draftResult.draft.suspects || []" :key="`draft-suspect-${index}`" class="draft-mini-card">
                  <summary class="draft-card-summary">
                    <span>
                      <strong>{{ suspect.alias || '관계자' }}</strong>
                      <small>{{ suspect.displayName || '이름 미정' }}</small>
                    </span>
                    <em>편집</em>
                  </summary>
                  <label>별칭<input v-model.trim="suspect.alias" type="text" /></label>
                  <label>표시 이름<input v-model.trim="suspect.displayName" type="text" /></label>
                  <div class="wide evidence-preview-box">
                    <img v-if="suspect.portraitImageUrl" class="draft-suspect-image" :src="suspect.portraitImageUrl" alt="관계자 초상 미리보기" />
                    <textarea v-model="suspect.imagePrompt" rows="5" placeholder="외부 이미지 AI에 넣을 관계자별 프롬프트"></textarea>
                    <button type="button" class="ghost-btn mini" @click="suspect.imagePrompt = buildSuspectImagePrompt(suspect)">프롬프트 재생성</button>
                    <button type="button" class="ghost-btn mini" @click="copyImagePrompt(suspect.imagePrompt)">프롬프트 복사</button>
                    <button type="button" class="ghost-btn mini" @click="suspect.portraitImageUrl = generatedSuspectPortraitDataUrl(suspect.displayName, suspect.alias, suspect.suspiciousPoint)">
                      임시 카드 생성
                    </button>
                    <small>이미지는 외부 이미지 AI에서 생성한 뒤 아래 URL에 붙여넣는 방식을 권장합니다.</small>
                  </div>
                  <details class="wide image-url-edit">
                    <summary>초상 이미지 URL 직접 수정</summary>
                    <label>초상 이미지 URL<input v-model.trim="suspect.portraitImageUrl" type="text" placeholder="비워두면 자동 관계자 카드 생성" /></label>
                  </details>
                  <label>의심 포인트<textarea v-model="suspect.suspiciousPoint" rows="2"></textarea></label>
                  <label>사건 관계<textarea v-model="suspect.relationToVictim" rows="2"></textarea></label>
                  <label>알리바이<textarea v-model="suspect.alibiSummary" rows="2"></textarea></label>
                  <label>카드 설명<textarea v-model="suspect.shortDescription" rows="2"></textarea></label>
                </details>
              </div>
              <h4>증거/메모/사진</h4>
              <div class="mini-grid">
                <details v-for="(evidence, index) in draftResult.draft.evidences || []" :key="`draft-evidence-${index}`" class="draft-mini-card">
                  <summary class="draft-card-summary">
                    <span>
                      <strong>{{ evidence.title || '사건자료' }}</strong>
                      <small>{{ evidence.type || 'EVIDENCE' }}</small>
                    </span>
                    <em>편집</em>
                  </summary>
                  <label>제목<input v-model.trim="evidence.title" type="text" /></label>
                  <label>유형
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
                  <label>출처 미션<input v-model.number="evidence.sourceMissionOrder" type="number" min="1" /></label>
                  <div class="wide evidence-preview-box">
                    <img v-if="evidence.imageUrl" class="draft-evidence-image" :src="evidence.imageUrl" alt="사건자료 이미지 미리보기" />
                    <textarea v-model="evidence.imagePrompt" rows="5" placeholder="외부 이미지 AI에 넣을 증거/힌트 카드별 프롬프트"></textarea>
                    <button type="button" class="ghost-btn mini" @click="evidence.imagePrompt = buildEvidenceImagePrompt(evidence)">프롬프트 재생성</button>
                    <button type="button" class="ghost-btn mini" @click="copyImagePrompt(evidence.imagePrompt)">프롬프트 복사</button>
                    <button type="button" class="ghost-btn mini" @click="evidence.imageUrl = generatedEvidenceCardDataUrl(evidence.title, evidence.type)">임시 카드 생성</button>
                    <small>이미지는 외부 이미지 AI에서 생성한 뒤 아래 URL에 붙여넣는 방식을 권장합니다.</small>
                  </div>
                  <details class="wide image-url-edit">
                    <summary>이미지 URL 직접 수정</summary>
                    <label>이미지 URL<input v-model.trim="evidence.imageUrl" type="text" placeholder="비워두면 자동 카드 이미지 생성" /></label>
                  </details>
                  <label>요약<textarea v-model="evidence.textSummary" rows="2"></textarea></label>
                </details>
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


const DEFAULT_ESCAPE_GENRE_CATALOG = [
  {
    genreId: 'MURDER_MYSTERY',
    genreName: '살인 미스터리',
    answerSlots: [
      {
        slotId: 'CULPRIT',
        label: '범인',
        description: '사건을 일으킨 픽션 인물 또는 역할명',
        minClueCount: 2
      },
      {
        slotId: 'WEAPON',
        label: '범행도구',
        description: '범행에 사용된 구체적인 물건',
        minClueCount: 2
      },
      {
        slotId: 'CASE_LOCATION',
        label: '사건장소',
        description: '사건이 발생한 구체적인 픽션 장소 또는 장소 특징',
        minClueCount: 2
      }
    ],
    recommendedPuzzleTypes: ['OBSERVATION', 'NUMBER_LOCK', 'STORY_COMBINATION'],
    forbiddenPatterns: [
      '실존 인물을 범인으로 만들기',
      '실제 장소명을 정답으로 그대로 사용하기',
      '관리자 확인 필요 문구 사용'
    ],
    finalQuestionTemplate: '{범인}이 {범행도구}로 {사건장소}에서 벌인 사건의 결론을 입력하게 한다.'
  },
  {
    genreId: 'TREASURE_HUNT',
    genreName: '보물찾기',
    answerSlots: [
      {
        slotId: 'HIDDEN_ITEM',
        label: '숨겨진 물건',
        description: '플레이어가 최종적으로 찾아야 하는 구체적인 물건',
        minClueCount: 2
      },
      {
        slotId: 'UNLOCK_CONDITION',
        label: '해금 조건',
        description: '숨겨진 물건을 열거나 확인하기 위한 짧은 조건, 숫자, 표식',
        minClueCount: 2
      },
      {
        slotId: 'STORAGE_PLACE',
        label: '보관 장소',
        description: '물건이 보관된 장소 또는 장소 특징',
        minClueCount: 2
      }
    ],
    recommendedPuzzleTypes: ['OBSERVATION', 'NUMBER_LOCK', 'PATTERN'],
    forbiddenPatterns: [
      '최종 장소명을 그대로 정답으로 사용하기',
      '비밀번호 같은 추상어만 정답으로 사용하기',
      '관리자 확인 필요 문구 사용'
    ],
    finalQuestionTemplate: '{숨겨진 물건}을 찾기 위해 {해금 조건}을 만족하고 {보관 장소}를 확인하는 결론을 입력하게 한다.'
  },
  {
    genreId: 'CODE_BREAKING',
    genreName: '암호 해독',
    answerSlots: [
      {
        slotId: 'FINAL_PHRASE',
        label: '최종 문장',
        description: '암호를 풀었을 때 나오는 짧은 최종 문장',
        minClueCount: 2
      },
      {
        slotId: 'KEY_NUMBER',
        label: '핵심 숫자',
        description: '암호 해독에 필요한 숫자 또는 숫자 조합',
        minClueCount: 2
      },
      {
        slotId: 'DECODE_LOCATION',
        label: '암호해독 장소',
        description: '암호를 대조하거나 해독하는 장소 또는 장소 특징',
        minClueCount: 2
      }
    ],
    recommendedPuzzleTypes: ['NUMBER_LOCK', 'INITIAL_SOUND', 'PATTERN'],
    forbiddenPatterns: [
      '최종 문장을 너무 길게 만들기',
      '장소명만 정답으로 사용하기',
      '관리자 확인 필요 문구 사용'
    ],
    finalQuestionTemplate: '{암호해독 장소}에서 {핵심 숫자}를 이용해 {최종 문장}을 해독하는 결론을 입력하게 한다.'
  },
  {
    genreId: 'MISSING_CASE',
    genreName: '실종 사건',
    answerSlots: [
      {
        slotId: 'MISSING_REASON',
        label: '실종 원인',
        description: '실종이 발생한 픽션 원인',
        minClueCount: 2
      },
      {
        slotId: 'LAST_LOCATION',
        label: '마지막 장소',
        description: '실종자가 마지막으로 확인된 장소 또는 장소 특징',
        minClueCount: 2
      },
      {
        slotId: 'RELATED_ITEM',
        label: '관련 물건',
        description: '실종 원인을 밝히는 구체적인 물건',
        minClueCount: 2
      }
    ],
    recommendedPuzzleTypes: ['OBSERVATION', 'STORY_COMBINATION', 'PATTERN'],
    forbiddenPatterns: [
      '실제 인물의 실종 사건처럼 쓰기',
      '실종 원인을 추상어로 쓰기',
      '관리자 확인 필요 문구 사용'
    ],
    finalQuestionTemplate: '{실종 원인} 때문에 사라졌고, {마지막 장소}에서 마지막 흔적이 확인되며, {관련 물건}이 핵심 증거라는 결론을 입력하게 한다.'
  }
];


const router = useRouter();
const episodes = ref([]);
const selected = ref(null);
const selectedEpisodeId = ref(null);
const activeAdminTab = ref('episodes');
const loading = ref(false);
const message = ref('');
const messageType = ref('success');
const episodeForm = ref({});
const payloadValidation = ref({});
const publishReadiness = ref(null);
const previewOpen = ref(false);
const auditLogs = ref([]);
const auditLoading = ref(false);
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
const candidateLoadError = ref('');
const placeCandidates = ref([]);
const anchorCandidate = ref(null);
const nearbyCandidates = ref([]);
const nearbyLoading = ref(false);
const nearbyLoaded = ref(false);
const nearbyRadius = ref(1500);
const selectedCandidates = ref([]);
const siteDataEnriched = ref(false);
const draftPlan = ref(null);
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
    { name: '서울시립미술관 앞마당', description: '목적지 혼선을 주는 조사 지점', visibleElements: ['광장', '조형물'], numbers: [], keywords: ['그림자', '동선'], adminMemo: '장소 키워드와 비교할 조사 지점', role: 'DESTINATION_HINT' },
    { name: '중명전', description: '서버 내부에서만 최종 장소로 판정할 조사 지점', visibleElements: ['붉은 벽', '건물명'], numbers: ['1905'], keywords: ['밀서', '문'], adminMemo: '사용자 화면에는 최종 장소로 직접 노출하지 않음', role: 'FINAL' }
  ]
}, null, 2));
const draftStepOrder = [
  { key: 'prepare', label: '입력 정리', description: '선택 장소와 좌표를 초안 입력값으로 정리합니다.' },
  { key: 'request', label: 'AI 요청', description: '백엔드가 Gemini 또는 규칙 기반 초안을 생성합니다.' },
  { key: 'parse', label: '응답 처리', description: '스토리, 퍼즐, 단서, 사건자료 카드 JSON을 처리합니다.' },
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
  return count === 9 && selectedCandidates.value.every(hasCandidateCoordinate);
});
const draftSelectionBlockReason = computed(() => {
  const count = selectedCandidates.value.length;
  if (count < 9) return '기준 장소를 포함해 9개 장소를 선택해야 초안 생성이 가능합니다.';
  if (count > 9) return '장소는 최대 9개까지만 사용할 수 있습니다.';
  const missing = selectedCandidates.value.filter((candidate) => !hasCandidateCoordinate(candidate));
  if (missing.length) {
    return `위도/경도가 없는 장소가 있습니다: ${missing.map((candidate) => candidate.title || '이름 없는 장소').join(', ')}`;
  }
  return '';
});
const selectedCandidateStatus = computed(() => {
  const count = selectedCandidates.value.length;
  if (count < 9) return '장소가 부족합니다. 9개 장소를 선택하세요.';
  if (count > 9) return '장소가 너무 많습니다. 최대 9개까지만 사용하세요.';
  const missing = selectedCandidates.value.filter((candidate) => !hasCandidateCoordinate(candidate));
  if (missing.length) return `좌표가 없는 장소 ${missing.length}개가 있습니다. 좌표가 있는 후보로 교체하거나 수동 후보를 추가하세요.`;
  return '시작 1개, 정답 키워드 미션 4개, 장소 키워드 미션 3개, 내부 최종 목적지 1개 구성입니다.';
});
const routeIdentitySummary = computed(() => {
  const count = selectedCandidates.value.length;
  if (!count) return 'TourAPI 기준 장소를 먼저 고르면 Kakao Local 후보로 추천 루트를 구성합니다.';
  const localCount = selectedCandidates.value.filter((candidate) => isLocalBusinessCandidate(candidate)).length;
  return `총 ${count}개 장소 · 골목상권/휴식 후보 ${localCount}개 · 기준 장소 주변 동선으로 구성`;
});
const caseBuilderNext = computed(() => {
  if (!candidateLoaded.value) {
    return {
      title: '1단계: TourAPI 기준 장소를 불러오세요.',
      description: '사건의 중심이 되는 관광지를 먼저 선택해야 주변 골목상권 후보를 추천할 수 있습니다.',
      button: 'TourAPI 기준 장소 불러오기',
      action: 'loadTourApi',
      disabled: false
    };
  }
  if (!anchorCandidate.value) {
    return {
      title: '2단계: 사건의 기준 장소를 선택하세요.',
      description: 'TourAPI 장소 카드에서 주변 후보 찾기를 누르면 그 장소가 내부 최종 장소 후보가 되고 주변 후보가 추천됩니다.',
      button: '기준 장소 선택 필요',
      action: 'selectAnchor',
      disabled: true
    };
  }
  if (!nearbyLoaded.value) {
    return {
      title: '3단계: 주변 골목상권 후보를 불러오세요.',
      description: 'Kakao Local로 기준 장소 주변의 카페, 문화공간, 골목 후보를 불러와 추천 루트를 구성합니다.',
      button: '주변 후보 불러오기',
      action: 'loadNearby',
      disabled: false
    };
  }
  if (!canGenerateDraftFromSelection.value) {
    return {
      title: '4단계: 9개 장소 구성이 필요합니다.',
      description: draftSelectionBlockReason.value || '추천 루트에는 좌표가 있는 장소 9개가 필요합니다.',
      button: '장소 구성 확인',
      action: 'showSelectionIssue',
      disabled: false
    };
  }
  if (!draftResult.value?.draft) {
    if (!siteDataEnriched.value) {
      return {
        title: '5단계: 현장근거를 먼저 보강하세요.',
        description: 'Kakao 주변 후보와 관리자 메모를 바탕으로 각 장소의 키워드와 검수 범위를 좁힙니다.',
        button: 'RAG 현장근거 보강',
        action: 'enrich',
        disabled: false
      };
    }
    if (!draftPlan.value) {
      return {
        title: '6단계: AI 장르와 최종 정답 키워드를 먼저 확정하세요.',
        description: '선택한 TourAPI 장소와 역사·문화 근거에 맞춰 장르와 정답 필수 키워드를 먼저 제안받습니다.',
        button: '장르/정답 키워드 생성',
        action: 'plan',
        disabled: false
      };
    }
    if (draftPlan.value.planReviewRequired) {
      return {
        title: '7단계: 현장 관찰 데이터를 보강하세요.',
        description: draftPlan.value.reviewReason || '근거 있는 최종 정답 키워드가 3개 미만입니다.',
        button: '현장 데이터 보강 필요',
        action: 'planReview',
        disabled: true
      };
    }
    return {
      title: '7단계: 확정한 정답 키워드로 Gemini 초안을 생성하세요.',
      description: '관리자가 확인한 장르와 정답 키워드 계약을 바탕으로 스토리 개요, 미션, 관계자 카드, 해금 자료 카드를 생성합니다.',
      button: '키워드 확정 후 전체 초안 생성',
      action: 'gemini',
      disabled: false
    };
  }
  if (!draftValidation.value) {
    return {
      title: '8단계: 초안을 검증하세요.',
      description: '검증 이슈가 있어도 DRAFT 저장은 가능하지만, PUBLISHED 전환 전에는 공개 준비도 점검을 통과해야 합니다.',
      button: '기본 검증 실행',
      action: 'validate',
      disabled: false
    };
  }
  return {
    title: '9단계: DRAFT로 저장하세요.',
    description: '저장 후 상단 상세 패널에서 공개 준비도 점검, 부족 항목 수정, PUBLISHED 전환을 진행합니다.',
    button: 'DRAFT 저장',
    action: 'save',
    disabled: false
  };
});
const draftWarningSummary = computed(() => {
  const warnings = draftResult.value?.validationWarnings || [];
  const placeholderWarnings = warnings.filter((warning) => String(warning).includes('검수용 문제') || String(warning).includes('admin-review placeholder'));
  const others = warnings.filter((warning) => !placeholderWarnings.includes(warning));
  const summary = [];
  if (placeholderWarnings.length) {
    summary.push(`현장 근거가 부족한 미션 ${placeholderWarnings.length}개는 현장 보강 필요 상태입니다. RAG 보강 또는 관리자 현장 메모로 확인 범위를 좁힌 뒤 공개 전 문제를 확정하세요.`);
  }
  return [...summary, ...others.slice(0, 4)];
});
const draftValidationSummary = computed(() => {
  if (!draftValidation.value) return '';
  if (draftValidation.value.valid) {
    return '필수 검증을 통과했습니다. 그래도 운영 공개 전 실제 현장 검수는 필요합니다.';
  }
  return '초안에 수정이 필요한 항목이 있습니다. 아래 이슈를 확인하되, DRAFT 저장은 가능하고 PUBLISHED 전환 전에 수정하면 됩니다.';
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

function openBuilderTab() {
  activeAdminTab.value = 'builder';
  requestAnimationFrame(() => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  });
}

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
    strengthenEpisodeImagePrompts(selected.value);
    hydrateEpisodeForm(selected.value);
  } catch (error) {
    setMessage(error.userMessage || '에피소드 상세를 불러올 수 없습니다.', 'error');
  }
}

function goUserMap() {
  if (selected.value?.status !== 'PUBLISHED') {
    setMessage('DRAFT 미션 파일은 사용자 지도 API에서 접근할 수 없습니다. PUBLISHED 전환 후 열어 주세요.', 'error');
    return;
  }
  router.push({ name: 'EpisodeMap', params: { episodeId: selectedEpisodeId.value } });
}

function goUserCaseFile() {
  if (selected.value?.status !== 'PUBLISHED') {
    setMessage('DRAFT 미션 파일은 사용자 미션 파일 API에서 접근할 수 없습니다. PUBLISHED 전환 후 열어 주세요.', 'error');
    return;
  }
  router.push({ name: 'EpisodeCaseFile', params: { episodeId: selectedEpisodeId.value } });
}

function markerPreviewLabel(type) {
  return {
    START: '시작',
    ANSWER_HINT: '정답 키워드',
    DESTINATION_HINT: '장소 키워드'
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
  void loadAuditLogs(episode.id, true);
}

async function loadAuditLogs(episodeId = selectedEpisodeId.value, silent = false) {
  if (!episodeId) return;
  auditLoading.value = true;
  try {
    auditLogs.value = await adminEpisodeApi.getAuditLogs(episodeId, 50);
  } catch (error) {
    if (!silent) setMessage(error.userMessage || '관리자 변경 이력을 불러올 수 없습니다.', 'error');
  } finally {
    auditLoading.value = false;
  }
}

function auditActionLabel(action) {
  return {
    CREATE_EPISODE: '사건 생성',
    UPDATE_EPISODE: '핵심 정보 수정',
    PUBLISH_EPISODE: '사건 게시',
    ARCHIVE_EPISODE: '사건 보관',
    REOPEN_EPISODE: '초안 재전환',
    DELETE_EPISODE: '사건 삭제',
    CREATE_SPOT: '장소 추가',
    UPDATE_SPOT: '장소 수정',
    DELETE_SPOT: '장소 삭제',
    UPDATE_PUZZLE: '퍼즐 수정',
    CREATE_SUSPECT: '관계자 추가',
    UPDATE_SUSPECT: '관계자 수정',
    DELETE_SUSPECT: '관계자 삭제',
    CREATE_EVIDENCE: '증거 추가',
    UPDATE_EVIDENCE: '증거 수정',
    DELETE_EVIDENCE: '증거 삭제',
    UPDATE_PARTNER_REWARD: '리워드 수정',
    SAVE_AI_DRAFT: 'AI 초안 저장'
  }[action] || action;
}

function auditActionTone(action) {
  if (action === 'PUBLISH_EPISODE') return 'publish';
  if (String(action || '').startsWith('DELETE')) return 'delete';
  if (String(action || '').startsWith('CREATE') || action === 'SAVE_AI_DRAFT') return 'create';
  return 'update';
}

function formatAuditDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short'
  }).format(new Date(value));
}

function strengthenEpisodeImagePrompts(episode) {
  (episode?.suspects || []).forEach((suspect) => {
    suspect.imagePrompt = strengthenKoreanPersonPrompt(suspect.imagePrompt || buildSuspectImagePrompt(suspect));
  });
  (episode?.evidences || []).forEach((evidence) => {
    evidence.imagePrompt = strengthenKoreanEvidencePrompt(evidence.imagePrompt || buildEvidenceImagePrompt(evidence));
  });
}

async function refreshEpisodeList() {
  episodes.value = await adminEpisodeApi.getEpisodes();
}

async function createEpisode() {
  const createdAt = new Date().toLocaleString('ko-KR', { hour12: false });
  try {
    const created = await adminEpisodeApi.createEpisode({
      title: `새 미션 파일 초안 ${createdAt}`
    });
    selected.value = created;
    selectedEpisodeId.value = created.id;
    hydrateEpisodeForm(created);
    publishReadiness.value = null;
    previewOpen.value = false;
    await refreshEpisodeList();
    setMessage('새 미션 파일 DRAFT가 생성되었습니다. 핵심 정보를 수정한 뒤 장소/퍼즐/사건자료를 추가하세요.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '미션 파일을 생성할 수 없습니다.', 'error');
  }
}

async function deleteEpisode() {
  if (!selected.value || !selectedEpisodeId.value) return;
  if (selected.value.status === 'PUBLISHED') {
    setMessage('PUBLISHED 미션 파일은 먼저 ARCHIVED로 변경한 뒤 삭제하세요.', 'error');
    return;
  }
  const confirmed = window.confirm(`${selected.value.title} 미션 파일을 삭제할까요? 장소, 퍼즐, 사건자료, 진행 기록, 리뷰가 함께 삭제됩니다.`);
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
    setMessage('미션 파일이 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '미션 파일을 삭제할 수 없습니다.', 'error');
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
    setMessage('미션 파일이 PUBLISHED로 공개되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '미션 파일을 공개할 수 없습니다.', 'error');
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
      finalPlace: spot.finalPlace,
      fieldVerified: spot.fieldVerified,
      fieldVerificationNote: spot.fieldVerificationNote
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
      markerType: 'ANSWER_HINT',
      publicMarkerType: 'ANSWER_HINT',
      clueRole: 'ANSWER_HINT',
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
      imagePrompt: suspect.imagePrompt,
      relationToVictim: suspect.relationToVictim,
      suspiciousPoint: suspect.suspiciousPoint,
      alibiSummary: suspect.alibiSummary,
      unlockedByDefault: suspect.unlockedByDefault,
      displayOrder: suspect.displayOrder
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('관계자 카드가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '관계자 카드를 수정할 수 없습니다.', 'error');
  }
}

async function addSuspect() {
  try {
    selected.value = await adminEpisodeApi.createSuspect(selectedEpisodeId.value, {
      alias: `관계자 ${(selected.value?.suspects || []).length + 1}`,
      displayName: '새 관계자',
      suspiciousPoint: '의심 포인트를 입력하세요.',
      unlockedByDefault: false
    });
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('관계자 카드가 추가되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '관계자 카드를 추가할 수 없습니다.', 'error');
  }
}

async function removeSuspect(suspect) {
  if (!window.confirm(`${suspect.displayName} 관계자 카드를 삭제할까요? 관련 해금 자료 연결은 해제됩니다.`)) return;
  try {
    selected.value = await adminEpisodeApi.deleteSuspect(selectedEpisodeId.value, suspect.suspectId);
    hydrateEpisodeForm(selected.value);
    publishReadiness.value = null;
    setMessage('관계자 카드가 삭제되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '관계자 카드를 삭제할 수 없습니다.', 'error');
  }
}

async function saveEvidence(evidence) {
  try {
    selected.value = await adminEpisodeApi.updateEvidence(selectedEpisodeId.value, evidence.evidenceId, {
      title: evidence.title,
      type: evidence.type,
      imageUrl: evidence.imageUrl,
      imagePrompt: evidence.imagePrompt,
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
      title: '새 미션 자료',
      type: 'NOTE',
      textSummary: '관리자 검수용 미션 자료입니다.',
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
    setMessage('예정 리워드가 수정되었습니다.', 'success');
  } catch (error) {
    setMessage(error.userMessage || '예정 리워드를 수정할 수 없습니다.', 'error');
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

async function enrichSelectedSiteData() {
  if (!prepareDraftInputFromSelection()) return;
  startDraftProgress('enrich', '선택한 장소별 주변 정보와 키워드를 보강하고 있습니다.', 'request');
  try {
    const payload = JSON.parse(draftInput.value);
    const enriched = await adminEpisodeApi.enrichSiteData(payload);
    draftInput.value = JSON.stringify(enriched, null, 2);
    if (Array.isArray(enriched.places)) {
      selectedCandidates.value = selectedCandidates.value.map((candidate, index) => ({
        ...candidate,
        description: enriched.places[index]?.description || candidate.description,
        visibleElements: enriched.places[index]?.visibleElements || candidate.visibleElements,
        numbers: enriched.places[index]?.numbers || candidate.numbers,
        keywords: enriched.places[index]?.keywords || candidate.keywords,
        adminMemo: enriched.places[index]?.adminMemo || candidate.adminMemo
      }));
    }
    draftResult.value = null;
    draftValidation.value = null;
    siteDataEnriched.value = true;
    finishDraftProgress('현장 근거 보강이 완료되었습니다. 보강된 관리자 메모와 키워드로 초안을 생성하세요.');
    setMessage('현장 근거 보강이 완료되었습니다. 공개 전 실제 현장 검수는 여전히 필요합니다.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || '현장 근거 보강에 실패했습니다. Kakao REST 키와 장소 좌표를 확인하세요.');
    setMessage(draftError.value, 'error');
  }
}

async function runNextCaseBuilderAction() {
  const action = caseBuilderNext.value.action;
  if (action === 'loadTourApi') {
    await loadPlaceCandidates();
    return;
  }
  if (action === 'loadNearby' && anchorCandidate.value) {
    await loadNearbyCandidates(anchorCandidate.value);
    return;
  }
  if (action === 'showSelectionIssue') {
    setMessage(draftSelectionBlockReason.value || '선택 장소를 확인해 주세요.', 'error');
    return;
  }
  if (action === 'enrich') {
    await enrichSelectedSiteData();
    return;
  }
  if (action === 'gemini') {
    await generateGeminiDraft();
    return;
  }
  if (action === 'plan') {
    await generateAnswerPlan();
    return;
  }
  if (action === 'validate') {
    await validateDraft(false);
    return;
  }
  if (action === 'save') {
    await saveDraft();
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
    setMessage('예비 미션 파일 초안이 생성되었습니다. 아직 DB에는 저장되지 않았습니다.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || '초안을 생성할 수 없습니다.');
    setMessage(draftError.value, 'error');
  }
}

async function generateGeminiDraft() {
  if (!prepareDraftInputFromSelection()) return;
  if (!draftPlan.value) {
    setMessage('먼저 AI 장르/최종 정답 키워드를 생성하고 확인하세요.', 'error');
    return;
  }
  if (draftPlan.value.planReviewRequired) {
    setMessage(draftPlan.value.reviewReason || '현장 관찰 요소를 보강한 뒤 plan을 다시 생성하세요.', 'error');
    return;
  }
  startDraftProgress('gemini', 'Gemini가 스토리 개요, 퍼즐, 단서, 관계자 카드, 해금 자료 카드 초안을 작성하고 있습니다. 최대 180초까지 기다립니다.');
  try {
    let payload = JSON.parse(draftInput.value);
    if (!siteDataEnriched.value) {
      draftProgressStep.value = 'request';
      draftStatus.value = '현장 근거를 먼저 자동 보강한 뒤 Gemini 초안을 요청합니다.';
      payload = await adminEpisodeApi.enrichSiteData(payload);
      draftInput.value = JSON.stringify(payload, null, 2);
      siteDataEnriched.value = true;
    }
    payload = applyDraftPlanToPayload(payload);
    draftInput.value = JSON.stringify(payload, null, 2);
    draftProgressStep.value = 'request';
    draftResult.value = await adminEpisodeApi.createGeminiDraft(payload);
    draftProgressStep.value = 'hydrate';
    hydrateDraftForEditing();
    normalizeDraftBeforeSave(draftResult.value.draft, false);
    draftValidation.value = null;
    finishDraftProgress('Gemini 초안이 생성되었고 저장 전 자동 보정까지 적용했습니다. 각 장소의 현장 근거만 최종 확인하세요.');
    setMessage('Gemini 미션 파일 초안이 생성되었습니다. 구조 보정은 적용됐고 아직 DB에는 저장되지 않았습니다.', 'success');
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || 'Gemini 초안을 생성할 수 없습니다. gemini.api.key와 gemini.model 설정을 확인하세요.');
    setMessage(draftError.value, 'error');
  }
}

async function validateDraft(useGemini) {
  if (!draftResult.value?.draft) return;
  normalizeDraftBeforeSave(draftResult.value.draft, false);
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
    setMessage('검증 이슈가 있어도 DRAFT로 저장합니다. PUBLISHED 전환 전에 공개 준비도와 검증 항목을 수정하세요.', 'error');
  }
  startDraftProgress('save', '초안과 자동 생성 사건자료 이미지를 DRAFT로 저장하고 있습니다.');
  try {
    draftProgressStep.value = 'request';
    const saved = await adminEpisodeApi.saveAiDraft({
      draft: buildDraftSavePayload(),
      sourceInput: JSON.parse(draftInput.value),
      validationResult: draftValidation.value,
      status: 'DRAFT'
    });
    draftProgressStep.value = 'hydrate';
    selected.value = saved;
    selectedEpisodeId.value = saved.id;
    hydrateEpisodeForm(saved);
    await refreshEpisodeList();
    publishReadiness.value = await adminEpisodeApi.getPublishReadiness(saved.id);
    finishDraftProgress('DRAFT 저장이 완료되었습니다. 왼쪽 목록과 상세 검수 영역에 반영되었습니다.');
    setMessage('AI 미션 파일 초안이 DRAFT로 저장되었습니다. 공개 준비도 결과를 확인하고 부족한 항목을 수정해 주세요.', 'success');
  } catch (error) {
    const details = [
      error.response?.status ? `HTTP ${error.response.status}` : '',
      error.errorCode || '',
      error.userMessage || error.message || 'AI 초안을 저장할 수 없습니다.'
    ].filter(Boolean).join(' · ');
    console.error('AI draft save failed', {
      status: error.response?.status,
      code: error.errorCode,
      message: error.userMessage,
      requestId: error.requestId
    });
    failDraftProgress(details);
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
  ensureDraftIllustrationCards(draft);
  if (isGenericDraftTitle(draft.episodeTitle)) {
    draft.episodeTitle = suggestedDraftTitle(draft);
  }
  draft.subtitle = draft.subtitle || suggestedDraftSubtitle(draft);
  draft.era = normalizeDraftEra(draft.era, draft);
  ensureDraftImagePrompts(draft);
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
  normalizeDraftBeforeSave(draft, false);
  if (isGenericDraftTitle(draft.episodeTitle)) {
    draft.episodeTitle = suggestedDraftTitle(draft);
  }
  draft.subtitle = draft.subtitle || suggestedDraftSubtitle(draft);
  draft.era = normalizeDraftEra(draft.era, draft);
  ensureDraftIllustrationCards(draft);
  strengthenCaseMaterials(draft);
  draft.suspects = (draft.suspects || []).map((suspect) => {
    const portraitImageUrl = String(suspect.portraitImageUrl || '').trim();
    return {
      ...suspect,
      portraitImageUrl: portraitImageUrl.startsWith('data:') || portraitImageUrl.length > 900 ? '' : portraitImageUrl
    };
  });
  draft.evidences = (draft.evidences || []).map((evidence) => {
    const imageUrl = String(evidence.imageUrl || '').trim();
    return {
      ...evidence,
      title: String(evidence.title || '').slice(0, 255),
      type: safeEvidenceType(evidence.type),
      imageUrl: imageUrl.startsWith('data:') || imageUrl.length > 900 ? '' : imageUrl
    };
  });
  return draft;
}

function normalizeDraftBeforeSave(draft = draftResult.value?.draft, showMessage = false) {
  if (!draft) return;
  draft.missions = Array.isArray(draft.missions) ? draft.missions : [];
  draft.suspects = Array.isArray(draft.suspects) ? draft.suspects : [];
  draft.evidences = Array.isArray(draft.evidences) ? draft.evidences : [];
  const motif = inferCaseMotif(draft);
  const objective = inferFinalObjective(draft, motif);
  const objectiveMismatch = hasObjectiveMismatch(draft, objective);
  const hasApprovedAnswerPlan = Array.isArray(draft.finalAnswerKeywords) && draft.finalAnswerKeywords.length > 0;
  if (!draft.finalAnswer || draft.finalAnswer === '검수필요' || draft.finalAnswer === '관리자검수' || (!hasApprovedAnswerPlan && objectiveMismatch)) {
    draft.finalAnswerType = objective.answerType;
    draft.finalAnswer = objective.finalAnswer;
    draft.finalAnswerAliases = Array.from(new Set([...(draft.finalAnswerAliases || []), ...objective.aliases]));
  }
  if (!draft.finalQuestion || isWeakFinalQuestion(draft.finalQuestion) || (!hasApprovedAnswerPlan && objectiveMismatch)) {
    draft.finalQuestion = objective.finalQuestion;
  }
  if (!draft.fictionSynopsis || isWeakText(draft.fictionSynopsis) || isRepeatedDefaultSynopsis(draft.fictionSynopsis)) {
    draft.fictionSynopsis = objective.synopsis || synopsisForMotif(draft, motif);
  }
  maskDraftKeywordLeaks(draft, draft.finalAnswerKeywords || []);
  if (!draft.finalTruthSummary || isWeakText(draft.finalTruthSummary)) {
    draft.finalTruthSummary = `3. 픽션과 역사의 매칭 (디브리핑)
스토리 속 [${motif.object}] -> 실제 역사 속 [관리자 검수 필요 역사 자료]: 최종 단서가 하나의 역사적 기억으로 수렴하도록 만든 상징 장치입니다.
스토리 속 [현장 지령] -> 실제 역사 속 [최종 목적지의 역사적 맥락]: 장소에 남은 사건의 흔적을 동선과 퍼즐로 바꾼 장치입니다.
스토리 속 [암호 카드] -> 실제 역사 속 [기록과 증언]: 플레이어가 단서를 대조하도록 실제 자료 해석 과정을 은유했습니다.
스토리 속 [조력자/관계자 진술] -> 실제 배경 속 [관련 인물과 이해관계]: 실존 인물을 부정적인 역할로 만들지 않고 역할과 갈등만 차용했습니다.`;
  }
  if (!draft.actualHistorySummary || isWeakText(draft.actualHistorySummary)) {
    draft.actualHistorySummary = `1. 모티브 공개
이 임무는 실제 [관리자 검수 필요 최종 목적지]에서 있었던 [관리자 검수 필요 역사적 사건/인물]을 모티브로 제작되었습니다.

2. 실제 배경 해설
이 에피소드는 실제 장소의 역사·문화적 분위기와 주변 동선을 상징적인 요원 임무로 각색한 픽션 스토리입니다. 운영 공개 전 관리자는 TourAPI 설명, 현장 표지, 공식 해설 자료를 확인해 실제 배경과 장소의 역사적 의의를 상세히 보강해야 합니다.`;
  }
  draft.deductionSecretFacts = Array.isArray(draft.deductionSecretFacts) && draft.deductionSecretFacts.length
    ? draft.deductionSecretFacts
    : [`최종 정답은 ${objective.finalAnswer}이다.`, '최종 정답은 시놉시스가 요구한 해결 조건을 모두 포함해야 한다.', '일부 단서 물건이나 문서 위치만 맞히는 답은 최종 정답이 아니다.'];
  draft.deductionForbiddenReveals = Array.from(new Set([...(draft.deductionForbiddenReveals || []), draft.finalAnswer, ...(draft.finalAnswerAliases || [])].filter(Boolean)));
  const finalIndex = draft.missions.findIndex((mission) => mission.finalPlace || mission.markerType === 'FINAL');
  draft.missions.forEach((mission, index) => {
    mission.order = index + 1;
    mission.arrivalRadius = Math.max(10, Number(mission.arrivalRadius || 50));
    mission.hints = Array.isArray(mission.hints) ? mission.hints.slice(0, 3) : [];
    while (mission.hints.length < 3) mission.hints.push('현장 검수 후 힌트를 보강하세요.');
    if (finalIndex >= 0 && index !== finalIndex && mission.markerType === 'FINAL') {
      mission.markerType = 'DESTINATION_HINT';
      mission.finalPlace = false;
    }
    syncDraftMissionRole(mission);
    mission.publicMarkerType = safePublicMarkerType(mission.publicMarkerType, mission);
    if (mission.finalPlace) {
      mission.publicMarkerType = 'DESTINATION_HINT';
      mission.storyText = sanitizeFinalPlaceStory(mission.storyText);
    }
    if (!mission.rewardClue) mission.rewardClue = rewardClueForRole(mission.clueRole || mission.markerType, index);
    if (!mission.questionText || !mission.answer || !mission.puzzleType) regenerateMissionDraft(mission, false);
  });
  const actualFinalCount = draft.missions.filter((mission) => mission.finalPlace || mission.markerType === 'FINAL').length;
  if (!actualFinalCount && draft.missions.length) {
    const finalMission = draft.missions[draft.missions.length - 1];
    finalMission.finalPlace = true;
    finalMission.markerType = 'FINAL';
    syncDraftMissionRole(finalMission);
    finalMission.publicMarkerType = 'DESTINATION_HINT';
  }
  ensureDraftIllustrationCards(draft);
  strengthenCaseMaterials(draft);
  if (showMessage) setMessage('저장 전 자동 보정을 적용했습니다. 내부 최종 장소는 사용자 지도에는 노출되지 않고 관리자 테스트 좌표로만 사용됩니다.', 'success');
}

function regenerateAllMissionsSafely() {
  const draft = draftResult.value?.draft;
  if (!draft?.missions?.length) return;
  draft.missions.forEach((mission) => regenerateMissionDraft(mission, false));
  normalizeDraftBeforeSave(draft, false);
  draftValidation.value = null;
  setMessage('전체 미션을 안전한 검수용 초안으로 재구성했습니다. 각 장소의 실제 현장 요소는 공개 전 확인하세요.', 'success');
}

function regenerateMissionDraft(mission, showMessage = true) {
  if (!mission) return;
  syncDraftMissionRole(mission);
  const index = Math.max(0, Number(mission.order || 1) - 1);
  const role = mission.clueRole || mission.markerType || 'ANSWER_HINT';
  const source = sourceCandidateForMission(mission);
  const keyword = primaryKeyword(source, mission);
  const rewardClue = rewardClueForRole(role, index);
  mission.storyText = storyTextForMission(mission, role, keyword);
  mission.puzzleType = puzzleTypeForSource(source, role, index);
  mission.answerFormat = mission.puzzleType === 'NUMBER_LOCK' ? 'NUMBER' : 'TEXT';
  mission.answer = answerForSource(source, mission.puzzleType, keyword);
  mission.questionText = questionForMission(mission, source, keyword);
  mission.rewardClue = rewardClue;
  mission.groundRule = groundRuleForMission(source);
  mission.hints = hintsForMission(mission, keyword);
  refreshMissionEvidenceCard(mission, false);
  draftValidation.value = null;
  if (showMessage) setMessage(`${mission.placeName} 미션을 안전한 검수용 초안으로 재구성했습니다.`, 'success');
}

function refreshMissionEvidenceCard(mission, showMessage = true) {
  const draft = draftResult.value?.draft;
  if (!draft || !mission) return;
  draft.evidences = Array.isArray(draft.evidences) ? draft.evidences : [];
  const type = evidenceTypeForRole(mission.clueRole || mission.markerType);
  const order = Number(mission.order || 1);
  let evidence = draft.evidences.find((item) => Number(item.sourceMissionOrder) === order);
  if (!evidence) {
    evidence = { sourceMissionOrder: order };
    draft.evidences.push(evidence);
  }
  evidence.title = evidenceTitleForMission(mission, type);
  evidence.type = type;
  evidence.textSummary = evidenceSummaryForMission(mission);
  evidence.imageUrl = generatedEvidenceCardDataUrl(evidence.title, type, evidence.textSummary);
  if (showMessage) setMessage(`${mission.placeName} 사건자료 카드를 다시 연결했습니다.`, 'success');
}

function isGenericDraftTitle(title) {
  const normalized = String(title || '').toLowerCase().replaceAll(' ', '');
  return !normalized || normalized.includes('ep.new') || normalized.includes('draft') || normalized.includes('episode');
}

function suggestedDraftTitle(draft) {
  const motif = inferCaseMotif(draft);
  return `EP.NEW ${motif.title} 사건`;
}

function suggestedDraftSubtitle(draft) {
  const missions = Array.isArray(draft?.missions) ? draft.missions : [];
  const startMission = missions[0];
  const motif = inferCaseMotif(draft);
  return `${startMission?.placeName || '첫 현장'}에서 시작된 ${motif.subtitle}`;
}

function inferCaseMotif(draft) {
  const source = [
    draft?.finalAnswer,
    draft?.finalAnswerType,
    draft?.episodeTitle,
    draft?.subtitle,
    draft?.fictionSynopsis,
    ...(Array.isArray(draft?.missions) ? draft.missions.flatMap((mission) => [mission.placeName, mission.storyText, mission.rewardClue, mission.groundRule]) : [])
  ].join(' ');
  if (source.includes('커피') || source.includes('카페') || source.includes('찻집') || source.includes('CE7')) {
    return {
      title: '식어 버린 찻잔',
      subtitle: '찻집 영수증과 마지막 주문을 대조하는 골목 수사',
      object: '식어 버린 찻잔 기록',
      victim: '마지막 주문자',
      caseType: '찻집 기록 변조 사건',
      setting: '찻집 골목',
      trace: '주문 시간과 영수증'
    };
  }
  if (source.includes('궁') || source.includes('의궤') || source.includes('왕') || source.includes('전각')) {
    return {
      title: '접힌 의궤 사본',
      subtitle: '궁궐 동선과 누락된 사본을 맞추는 기록 수사',
      object: '접힌 의궤 사본',
      victim: '궁궐 기록원',
      caseType: '궁궐 문서 은닉 사건',
      setting: '궁궐 담장 주변',
      trace: '봉인과 접힌 자국'
    };
  }
  if (source.includes('시장') || source.includes('식당') || source.includes('음식') || source.includes('FD6')) {
    return {
      title: '젖은 영수증',
      subtitle: '상권 동선과 지워진 계산 기록을 추적하는 수사',
      object: '젖은 영수증 조각',
      victim: '장부를 맡긴 손님',
      caseType: '거래 기록 은폐 사건',
      setting: '시장 골목',
      trace: '계산 시각과 배달 동선'
    };
  }
  if (source.includes('필름') || source.includes('사진') || source.includes('렌즈')) {
    return {
      title: '사라진 필름',
      subtitle: '사진 속 누락된 한 장면을 복원하는 기록 수사',
      object: '봉인된 필름',
      victim: '골목 사진가',
      caseType: '사진 기록 실종 사건',
      setting: '사진관 골목',
      trace: '노출된 필름과 촬영 순서'
    };
  }
  if (source.includes('인장') || source.includes('문서') || source.includes('밀서')) {
    return {
      title: '붉은 인장의 문서',
      subtitle: '봉인된 문서의 행방을 추적하는 기록 수사',
      object: '붉은 인장의 문서',
      victim: '기록 보관자',
      caseType: '문서 은닉 사건',
      setting: '문서 보관소 주변',
      trace: '봉인 자국과 서명'
    };
  }
  return {
    title: '검은 봉투',
    subtitle: '사라진 증언과 남겨진 봉투를 맞추는 골목 수사',
    object: '검은 봉투',
    victim: '익명의 제보자',
    caseType: '증언 은폐 사건',
    setting: '골목 조사 지점',
    trace: '봉투와 엇갈린 증언'
  };
}

function finalQuestionForMotif(motif) {
  return `${motif.setting}에서 모은 단서가 공통으로 가리키는 ${motif.object}의 의미는 무엇인가?`;
}

function inferFinalObjective(draft, motif) {
  const source = [
    draft?.fictionSynopsis,
    draft?.finalQuestion,
    draft?.episodeTitle,
    draft?.subtitle,
    ...(Array.isArray(draft?.missions) ? draft.missions.flatMap((mission) => [mission.storyText, mission.rewardClue, mission.groundRule]) : [])
  ].join(' ');
  if (requiresIdentityAndHideout(source)) {
    const identity = source.includes('황실') || source.includes('대한제국') || source.includes('광영회')
      ? '광영회의 위장 연락책'
      : '비공개 조직의 내부 전달자';
    const hideout = source.includes('기록') || source.includes('문서') || source.includes('설계도')
      ? '봉인된 기록고'
      : '닫힌 골목 은신처';
    const finalAnswer = `비공개 조직의 역할은 ${identity}이며 숨은 거점은 ${hideout}이다`;
    return {
      answerType: 'HIDDEN_TRUTH',
      finalAnswer,
      aliases: [finalAnswer.replaceAll(' ', ''), `${identity}와 ${hideout}`],
      finalQuestion: '비공개 조직의 역할과 숨은 거점을 단서로 종합하면 어떤 진실인가?',
      synopsis: synopsisForIdentityAndHideout(draft, motif)
    };
  }
  return {
    answerType: draft?.finalAnswerType || 'EVIDENCE',
    finalAnswer: motif.object,
    aliases: [motif.object.replaceAll(' ', '')],
    finalQuestion: finalQuestionForMotif(motif),
    synopsis: synopsisForMotif(draft, motif)
  };
}

function requiresIdentityAndHideout(text) {
  const compact = String(text || '').replaceAll(/\s+/g, '').toLowerCase();
  const identity = ['정체', '비공개조직', '비밀조직', '조직', '배후'].some((word) => compact.includes(word));
  const hideout = ['은신처', '숨어든', '숨은곳', 'hideout', '거점', '아지트'].some((word) => compact.includes(word));
  const macGuffin = ['황실비밀자금', '비밀자금', '설계도', '장부', '밀서'].some((word) => compact.includes(word));
  return (identity && hideout) || (macGuffin && (identity || hideout));
}

function hasObjectiveMismatch(draft, objective) {
  const source = String(draft?.fictionSynopsis || '');
  const finalQuestion = String(draft?.finalQuestion || '');
  const finalAnswer = String(draft?.finalAnswer || '');
  if (requiresIdentityAndHideout(source)) {
    return !containsAny(finalQuestion, ['정체', '비공개 조직', '비공개조직'])
      || !containsAny(finalQuestion, ['은신처', '숨어든', '숨은 곳', '숨은곳', '거점', '아지트'])
      || !containsAny(finalAnswer, ['비공개 조직', '비공개조직', '조직', '세력', '연락책', '전달자', '배후'])
      || !containsAny(finalAnswer, ['은신처', '거점', '아지트', '기록고', '문서고']);
  }
  return false;
}

function containsAny(value, words) {
  const text = String(value || '').replaceAll(/\s+/g, '').toLowerCase();
  return words.some((word) => text.includes(String(word).replaceAll(/\s+/g, '').toLowerCase()));
}

function maskDraftKeywordLeaks(draft, keywords) {
  const values = Array.isArray(keywords) ? keywords.map(normalizeAnswerKeywordValue).filter(Boolean) : [];
  if (!values.length) return;
  if (containsKeywordLeak(draft.finalQuestion, values)) {
    draft.finalQuestion = '흩어진 단서들이 가리키는 대상과 감춰진 행방을 밝혀내면, 이번 미션의 전말은 무엇인가?';
  }
  draft.episodeTitle = maskKeywords(draft.episodeTitle, values);
  draft.subtitle = maskKeywords(draft.subtitle, values);
  if (containsKeywordLeak(draft.fictionSynopsis, values) || containsMaskPlaceholder(draft.fictionSynopsis)) {
    draft.fictionSynopsis = safeFictionSynopsis(draft);
  }
  draft.finalQuestion = maskKeywords(draft.finalQuestion, values);
  (draft.missions || []).forEach((mission, index) => {
    if (containsKeywordLeak(mission.questionText, values)) {
      mission.questionText = '현장 근거와 미션 메모를 대조해 이 지점에서 확인할 수 있는 간접 단서를 입력하세요.';
    }
    if (containsKeywordLeak(mission.answer, values)) {
      const source = sourceCandidateForMission(mission);
      const keyword = primaryKeyword(source, mission);
      mission.answer = answerForSource(source, puzzleTypeForSource(source, mission.clueRole || mission.markerType, index), keyword);
      mission.answerFormat = 'TEXT';
      mission.puzzleAnswerRisk = 'FINAL_KEYWORD_RISK';
      mission.verificationLevel = 'ADMIN_REVIEW';
    }
    if (containsKeywordLeak(mission.rewardClue, values) || containsKeywordLeak(mission.rewardClue, [draft.finalAnswer])) {
      mission.rewardClue = rewardClueForRole(mission.clueRole || mission.markerType, index);
    }
    mission.hints = (mission.hints || []).map((hint, hintIndex) => (
      containsKeywordLeak(hint, values)
        ? ['현장 근거를 먼저 확인하세요.', '직접적인 정답 값이 아닌 특징과 역할을 비교하세요.', '해금 자료와 이전 단서를 함께 대조하세요.'][Math.min(hintIndex, 2)]
        : hint
    ));
  });
  (draft.suspects || []).forEach((card) => {
    if (containsKeywordLeak(card.displayName, values)) card.displayName = '기록 전달 관계자';
    if (containsKeywordLeak(card.shortDescription, values)) card.shortDescription = '핵심 내용을 직접 밝히지 않고 간접적인 특징만 전달하는 관계자입니다.';
    if (containsKeywordLeak(card.suspiciousPoint, values)) card.suspiciousPoint = '진술과 이동 기록 사이에 확인이 필요한 차이가 있습니다.';
    if (containsKeywordLeak(card.alibiSummary, values)) card.alibiSummary = '현장 동선과 해금 자료를 함께 대조해야 진술을 판단할 수 있습니다.';
  });
  (draft.evidences || []).forEach((card) => {
    if (containsKeywordLeak(card.title, values)) card.title = '간접 기록 자료';
    if (containsKeywordLeak(card.textSummary, values)) card.textSummary = '정답 값을 직접 밝히지 않고 형태, 재질, 용도 중 일부 특징만 남긴 해금 자료입니다.';
  });
}

function maskKeywords(text, keywords) {
  let result = String(text || '');
  keywords.forEach((keyword) => {
    const clean = String(keyword || '').trim();
    if (!clean) return;
    const mask = '핵심 단서';
    if (compactText(clean).length <= 2) {
      const escaped = clean.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      result = result.replace(new RegExp(`(^|[\\s.,!?;:()[\\]{}"'·|/])${escaped}(?=$|[\\s.,!?;:()[\\]{}"'·|/])`, 'g'), `$1${mask}`);
    } else {
      result = result.split(clean).join(mask);
      result = result.split(compactText(clean)).join(mask);
    }
  });
  return result;
}

function normalizeAnswerKeywordValue(keyword) {
  return String(keyword || '')
    .trim()
    .replaceAll(/[\[\]"'`]/g, '')
    .replaceAll(/\s+/g, ' ')
    .trim();
}

function containsMaskPlaceholder(text) {
  const compact = compactText(text);
  return compact.includes('가려진단서')
    || /가려진\d+자단서/.test(compact)
    || /\d+자단서/.test(compact)
    || compact.includes('정답키워드')
    || compact.includes('핵심키워드');
}

function safeFictionSynopsis(draft) {
  const source = [
    draft?.episodeTitle,
    draft?.subtitle,
    draft?.fictionSynopsis,
    draft?.genre
  ].join(' ');
  if (containsAny(source, ['항구', '항해', '개항', '항로', '일지', '목포'])) {
    return commandBriefing('항구 일대의 기록이 서로 다른 이동 경로를 가리키고 있네', '흔적이 지워지기 전에 암호와 기록을 대조해 숨겨진 경로의 의미를 밝혀내게');
  }
  if (containsAny(source, ['비공개 조직', '비공개조직', '은신처', '거점', '아지트', '정체'])) {
    return commandBriefing('도시 곳곳의 표식이 하나의 숨겨진 역할과 이동 경로를 가리키고 있네', '상대가 흔적을 거두기 전에 현장 기록을 대조해 관계자의 역할과 거점의 조건을 밝혀내게');
  }
  if (containsAny(source, ['보물', '상자', '봉인', '열쇠', '해금'])) {
    return commandBriefing('오래 봉인된 물건의 행방을 둘러싼 기록이 서로 어긋나고 있네', '봉인 장치가 다시 잠기기 전에 현장의 암호와 보관 흔적을 따라 정체와 확인 조건을 밝혀내게');
  }
  if (containsAny(source, ['암호', '문장', '숫자', '해독'])) {
    return commandBriefing('여러 조사 지점에서 같은 암호가 서로 다른 형태로 반복되고 있네', '암호 체계가 폐기되기 전에 숫자와 표식의 연결 규칙을 찾아 마지막 메시지의 의미를 밝혀내게');
  }
  if (containsAny(source, ['실종', '사라진', '마지막'])) {
    return commandBriefing('한 인물 또는 기록이 사라졌고 마지막 동선을 둘러싼 자료도 서로 어긋나고 있네', '남은 흔적까지 사라지기 전에 현장 단서와 물건을 대조해 실종의 이유와 행방을 밝혀내게');
  }
  const genre = draft?.genre || '이 스토리 미션';
  return commandBriefing('선택된 장소 일대에서 오래된 기록과 서로 어긋나는 단서가 발견됐네', `흔적이 훼손되기 전에 현장 단서와 암호를 차례로 대조해 ${genre}의 결론을 밝혀내게`);
}

function containsKeywordLeak(text, keywords) {
  const rawText = String(text || '');
  return keywords.some((keyword) => {
    const clean = String(keyword || '').trim();
    if (!clean) return false;
    if (compactText(clean).length <= 2) {
      const tokens = rawText.toLowerCase().split(/[\s.,!?;:()[\]{}"'`·|/]+/).filter(Boolean);
      return tokens.includes(clean.toLowerCase());
    }
    return rawText.includes(clean) || compactText(rawText).includes(compactText(clean));
  });
}

function compactText(value) {
  return String(value || '').replaceAll(/\s+/g, '').toLowerCase();
}

function synopsisForIdentityAndHideout(draft, motif) {
  const missions = Array.isArray(draft?.missions) ? draft.missions : [];
  const first = missions[0]?.placeName || motif.setting;
  return commandBriefing(
    `${first}에서 사라진 기록이 발견됐고, 관계자의 진술과 이동 흔적도 서로 어긋나고 있네`,
    '상대가 자료를 회수하기 전에 현장 단서와 미션 파일을 대조해 숨겨진 역할과 거점의 조건을 밝혀내게'
  );
}

function synopsisForMotif(draft, motif) {
  const missions = Array.isArray(draft?.missions) ? draft.missions : [];
  const first = missions[0]?.placeName || motif.setting;
  return commandBriefing(
    `${first}에서 시작된 ${motif.caseType}의 흔적이 여러 조사 지점으로 이어지고 있네`,
    `${motif.trace}이 사라지기 전에 현장 단서와 미션 파일을 대조해 목표의 정체와 이동 이유를 밝혀내게`
  );
}

function commandBriefing(situation, directive) {
  return `요원, ${situation}. 시간이 많지 않네. ${directive}. 당황할 필요는 없네. 평소 훈련한 대로 현장을 나누어 확인하면 충분히 해결할 수 있을 걸세. 내가 작전 기록을 통해 지원하겠네. 미션 파일을 확인하고 임무를 시작하도록.`;
}

function isWeakFinalQuestion(value) {
  const text = String(value || '').trim();
  if (!text) return true;
  return [
    '사건의 핵심 증거는 무엇인가?',
    '기록 보관자가 마지막까지 숨기려 한 사건의 핵심 증거는 무엇인가?',
    '최종 질문을 입력하세요.'
  ].some((word) => text.includes(word));
}

function isRepeatedDefaultSynopsis(value) {
  const text = String(value || '').trim();
  return [
    '정동의 한 사진사가 의문의 죽음을 맞았다',
    '남은 것은 마지막 사진과 흩어진 기록뿐이다',
    '사진, 메모, 동선 기록이 서로 맞지 않게 섞여 있었다'
  ].some((word) => text.includes(word));
}

function suggestedDraftEra(draft) {
  const source = [
    draft?.fictionSynopsis,
    ...(Array.isArray(draft?.missions) ? draft.missions.flatMap((mission) => [mission.storyText, mission.groundRule, mission.placeName]) : [])
  ].join(' ');
  if (source.includes('대한제국') || source.includes('정동') || source.includes('1905') || source.includes('1897')) return '대한제국 말기';
  if (source.includes('조선') || source.includes('궁') || source.includes('한양')) return '조선 후기';
  if (source.includes('근대') || source.includes('개화') || source.includes('일제')) return '근대 전환기';
  return '현대';
}

function normalizeDraftEra(value, draft) {
  const source = `${String(value || '')} ${[
    draft?.fictionSynopsis,
    ...(Array.isArray(draft?.missions) ? draft.missions.flatMap((mission) => [mission.storyText, mission.groundRule, mission.placeName]) : [])
  ].join(' ')}`;
  if (source.includes('삼국') || source.includes('고구려') || source.includes('백제') || source.includes('신라')) return '삼국시대';
  if (source.includes('고려')) return '고려시대';
  if (source.includes('대한제국') || source.includes('1897') || source.includes('1905') || source.includes('정동')) return '대한제국 말기';
  if (source.includes('일제강점기') || source.includes('일제') || source.includes('식민지')) return '일제강점기';
  if (source.includes('조선 후기') || source.includes('조선후기') || source.includes('한양') || source.includes('정조') || source.includes('영조')) return '조선 후기';
  if (source.includes('조선') || source.includes('궁궐') || source.includes('성곽')) return '조선시대';
  if (source.includes('근대') || source.includes('개화') || source.includes('한국전쟁') || source.includes('전쟁기념관') || source.includes('산업화')) return '근현대';
  return '현대';
}

function sourceCandidateForMission(mission) {
  const orderIndex = Math.max(0, Number(mission?.order || 1) - 1);
  return normalizeCandidate(orderedSelectedCandidates.value[orderIndex] || {
    title: mission?.placeName,
    address: mission?.address,
    latitude: mission?.latitude,
    longitude: mission?.longitude,
    description: mission?.groundRule || mission?.storyText
  });
}

function primaryKeyword(source, mission) {
  const keywords = [
    ...(Array.isArray(source.visibleElements) ? source.visibleElements : []),
    ...(Array.isArray(source.keywords) ? source.keywords : []),
  ].map((value) => String(value || '').trim()).filter(Boolean);
  const blocked = new Set(['동선확인', '증거확인', '최종검토', '기록확인', '단서확인', '자료확인', '현장확인', '미션확인', '검수필요', '확인필요']);
  return keywords.find((value) => value.length >= 2
    && value.length <= 12
    && !blocked.has(value.replace(/\s+/g, ''))
    && !/^[가-힣A-Za-z0-9]+의$/.test(value)
    && !['서울', '서울의', '지역', '지역의', '장소', '장소의', '현장', '현장의'].includes(value)
    && !String(source.title || '').includes(value)
    && value !== mission?.placeName) || '검수필요';
}

function puzzleTypeForSource(source, role, index) {
  const numbers = Array.isArray(source.numbers) ? source.numbers.filter(Boolean) : [];
  if (numbers.length) return 'NUMBER_LOCK';
  if (String(role).includes('ANSWER')) return index % 2 === 0 ? 'OBSERVATION' : 'INITIAL_SOUND';
  if (String(role).includes('DESTINATION')) return 'PATTERN';
  if (String(role).includes('FINAL')) return 'STORY_COMBINATION';
  return 'OBSERVATION';
}

function answerForSource(source, puzzleType, keyword) {
  const numbers = Array.isArray(source.numbers) ? source.numbers.filter(Boolean) : [];
  if (puzzleType === 'NUMBER_LOCK' && numbers.length) return String(numbers[0]);
  return keyword;
}

function rewardClueForRole(role, index) {
  const motif = inferCaseMotif(draftResult.value?.draft);
  const answerClues = motif.object.includes('필름')
    ? ['찢긴 가장자리', '빛에 탄 자국', '거꾸로 찍힌 그림자', '봉인 라벨']
    : motif.object.includes('문서')
      ? ['붉은 인장', '접힌 흔적', '사라진 서명', '봉인 끈']
      : ['검은 봉투', '젖은 모서리', '지워진 이름', '접힌 증언'];
  const destinationClues = ['낮은 담장', '조용한 문', '굽은 골목'];
  const storyClues = ['첫 목격 기록', '엇갈린 동선', '남겨진 시간표'];
  if (String(role).includes('ANSWER')) return answerClues[index % answerClues.length];
  if (String(role).includes('DESTINATION') || String(role).includes('FINAL')) return destinationClues[index % destinationClues.length];
  if (String(role).includes('START')) return '첫 기록';
  return storyClues[index % storyClues.length];
}

function storyTextForMission(mission, role, keyword) {
  const motif = inferCaseMotif(draftResult.value?.draft);
  if (mission.finalPlace || String(role).includes('FINAL')) {
    return sanitizeFinalPlaceStory(mission.storyText);
  }
  if (String(role).includes('START')) {
    return `기록수사관은 ${mission.placeName}에서 ${motif.caseType}의 첫 봉투를 연다. ${motif.victim}가 남긴 동선표에는 ${keyword}라는 표시만 짧게 남아 있다.`;
  }
  if (String(role).includes('ANSWER')) {
    return `${mission.placeName}에는 ${motif.object}의 정체를 좁히는 물성 단서가 숨어 있다. 현장 메모의 ${keyword}를 미션 파일의 증거 카드와 대조하라.`;
  }
  if (String(role).includes('DESTINATION')) {
    return `${mission.placeName}의 주변 분위기는 마지막으로 향할 장소를 직접 말하지 않고 좁혀 준다. 장소명보다 문, 벽, 길의 느낌을 장소 키워드와 비교하라.`;
  }
  return `${mission.placeName}은 관계자의 진술을 다시 확인하게 하는 배경 단서다. 이곳에서 얻은 메모는 어떤 진술이 현장 기록과 어긋나는지 판단하는 보조 자료가 된다.`;
}

function sanitizeFinalPlaceStory(storyText) {
  const value = String(storyText || '').trim();
  const forbidden = ['최종 장소', '최종장소', '최종 목적지', '최종목적지', '정답 장소', '정답장소', '최종 추리', '마지막 장소'];
  if (!value || forbidden.some((word) => value.includes(word))) {
    return '이곳에는 여러 동선이 겹친 흔적이 남아 있다. 현장에서는 주변 분위기와 미션 메모만 확인하고, 단서 보드의 장소 키워드와 조용히 비교하라.';
  }
  return value;
}

function questionForMission(mission, source, keyword) {
  const clue = mission.rewardClue || rewardClueForRole(mission.clueRole || mission.markerType, Number(mission.order || 1) - 1);
  const numbers = Array.isArray(source.numbers) ? source.numbers.filter(Boolean) : [];
  if (mission.puzzleType === 'NUMBER_LOCK' && numbers.length) {
    return `관리자 메모에 기록된 숫자 중 사건 기록과 연결된 숫자를 입력하라. 이 숫자는 '${clue}' 단서를 해금한다.`;
  }
  if (mission.puzzleType === 'INITIAL_SOUND') {
    return `미션 메모에 남은 초성 단서가 가리키는 키워드를 입력하라. 장소명 글자 추출이 아니라 관리자 검수 키워드를 기준으로 한다.`;
  }
  if (mission.puzzleType === 'PATTERN') {
    return `이 장소의 분위기와 장소 키워드를 비교해 단서 보드에 붙일 키워드를 입력하라.`;
  }
  if (mission.puzzleType === 'STORY_COMBINATION') {
    return `지금까지 모은 단서와 이 장소의 기록을 조합해 미션 파일에 붙일 핵심 단어를 입력하라.`;
  }
  return `현장에서 확인 가능한 요소 중 미션 메모의 핵심 키워드와 연결되는 단어를 입력하라.`;
}

function groundRuleForMission(source) {
  const elements = Array.isArray(source.visibleElements) ? source.visibleElements.filter(Boolean).join(', ') : '';
  const numbers = Array.isArray(source.numbers) ? source.numbers.filter(Boolean).join(', ') : '';
  const memo = source.adminMemo || source.description || '관리자 현장 메모가 필요합니다.';
  return `관리자 입력/후보 정보 기반 검수용 초안입니다. 관찰 요소: ${elements || '검수 필요'} / 숫자: ${numbers || '없음'} / 메모: ${memo}`;
}

function hintsForMission(mission, keyword) {
  if (mission.puzzleType === 'NUMBER_LOCK') {
    return ['관리자 메모에 적힌 숫자 후보를 먼저 확인하세요.', '사건 시간표나 기록 순서와 연결되는 숫자 하나만 사용합니다.', '정답은 현장 검수로 확인된 숫자 후보 중 하나입니다.'];
  }
  return [
    '장소명 글자를 뽑지 말고 현장 메모의 관찰 요소를 먼저 확인하세요.',
    `이 문제는 ${keyword}와 연결된 사건 카드의 의미를 찾는 문제입니다.`,
    '단서 보드에 붙일 단어를 고른다고 생각하면 됩니다.'
  ];
}

function evidenceTypeForRole(role) {
  if (String(role).includes('ANSWER')) return 'ANSWER_CLUE';
  if (String(role).includes('DESTINATION') || String(role).includes('FINAL')) return 'DESTINATION_CLUE';
  if (String(role).includes('START')) return 'PHOTO';
  return 'STORY_CLUE';
}

function evidenceSummaryForMission(mission) {
  const clue = mission.rewardClue || '미확인 단서';
  const place = mission.placeName || '조사 지점';
  const answer = mission.answer || '현장 표식';
  if (mission.finalPlace) return `${answer} 주변의 마모 방향과 '${clue}'에 남은 흔적이 같은 축을 가리킨다. 플레이어는 장소명이 아니라 두 흔적의 위치 관계를 대조해야 한다.`;
  const role = mission.clueRole || mission.markerType || '';
  if (String(role).includes('ANSWER')) return `${answer}의 표면에는 '${clue}'와 같은 방향으로 반복된 눌림 자국이 남아 있다. 누군가 이 특징을 미리 알고 있었다는 진술과 대조할 수 있다.`;
  if (String(role).includes('DESTINATION')) return `${answer} 옆의 흔적은 '${clue}'가 가리키는 방향과 일치하지만, 지도 가장자리의 다른 표시는 반대쪽을 가리킨다. 둘 중 반복되는 방향만 따라가야 한다.`;
  if (String(role).includes('START')) return `${place}에서 발견된 ${answer}에는 물에 젖은 자국과 같은 방향으로 이어진 작은 마모 흔적이 남아 있다. 이후 카드에서 같은 흔적을 찾아야 한다.`;
  return `${answer} 주변의 흔적과 '${clue}'의 배열이 일치한다. 관계자의 이동 시각과 비교하면 진술 하나가 실제 동선과 어긋난다.`;
}

function evidenceTitleForMission(mission, type) {
  const clue = mission.rewardClue || mission.placeName || '미확인 단서';
  if (type === 'ANSWER_CLUE') return `${clue} 증거 카드`;
  if (type === 'DESTINATION_CLUE') return `${clue} 목적지 메모`;
  if (type === 'PHOTO') return `${mission.placeName || '첫 현장'} 현장 사진`;
  if (type === 'STORY_CLUE') return `${clue} 조사 노트`;
  return `${clue} 사건자료`;
}

function missionRoleLabel(mission) {
  if (mission?.finalPlace || mission?.markerType === 'FINAL') return '내부 최종 장소';
  const role = String(mission?.clueRole || mission?.markerType || '');
  if (role.includes('START')) return '시작 장소';
  if (role.includes('ANSWER')) return '정답 키워드';
  if (role.includes('DESTINATION')) return '장소 키워드';
  if (role.includes('STORY')) return '스토리 단서';
  return '조사 후보';
}

function puzzleTypeLabel(type) {
  return {
    OBSERVATION: '관찰형',
    NUMBER_LOCK: '숫자 암호',
    INITIAL_SOUND: '초성/언어',
    PATTERN: '패턴 추론',
    STORY_COMBINATION: '스토리 조합'
  }[type] || '퍼즐 미정';
}

function missionReadinessLabel(mission) {
  if (!mission?.questionText || !mission?.answer) return '문제 보강 필요';
  if (mission.answer === '검수필요') return '현장 검수 필요';
  if (!Array.isArray(mission.hints) || mission.hints.length < 3) return '힌트 보강 필요';
  return '초안 준비';
}

function isReviewRequiredSpot(spot) {
  const puzzle = spot?.puzzle || {};
  const hintText = (puzzle.hints || []).map((hint) => hint?.hintText).join(' ');
  return hasReviewRequiredText([
    spot?.storyText,
    puzzle.questionText,
    puzzle.answer,
    puzzle.rewardClue,
    puzzle.rewardPayload,
    hintText
  ].join(' '));
}

function hasReviewRequiredText(value) {
  const normalized = String(value || '').replace(/\s+/g, '').toLowerCase();
  return normalized.includes('검수필요')
    || normalized.includes('관리자검수')
    || normalized.includes('review-required')
    || normalized.includes('reviewrequired');
}

function ensureDraftIllustrationCards(draft) {
  draft.suspects = Array.isArray(draft.suspects) ? draft.suspects : [];
  draft.evidences = Array.isArray(draft.evidences) ? draft.evidences : [];
  const hasPhoto = draft.evidences.some((evidence) => evidence.type === 'PHOTO');
  if (!hasPhoto) {
    draft.evidences.unshift({
      title: '사건 현장 스케치',
      type: 'PHOTO',
      imageUrl: '',
      textSummary: '사건이 시작된 장소에서 발견된 봉투와 훼손된 기록 조각입니다.',
      sourceMissionOrder: 1
    });
  }
  draft.evidences.forEach((evidence) => {
    evidence.type = safeEvidenceType(evidence.type);
  });
  ensureDraftImagePrompts(draft);
}

function ensureDraftImagePrompts(draft) {
  draft.suspects = Array.isArray(draft.suspects) ? draft.suspects : [];
  draft.suspects.forEach((suspect) => {
    suspect.imagePrompt = strengthenKoreanPersonPrompt(suspect.imagePrompt || buildSuspectImagePrompt(suspect));
  });
  draft.evidences = Array.isArray(draft.evidences) ? draft.evidences : [];
  draft.evidences.forEach((evidence) => {
    evidence.imagePrompt = strengthenKoreanEvidencePrompt(evidence.imagePrompt || buildEvidenceImagePrompt(evidence));
  });
}

function strengthenKoreanPersonPrompt(prompt) {
  const text = String(prompt || '').trim();
  const normalized = text.toLowerCase();
  if (!text) return text;
  const casting = normalized.includes('fictional korean person') || normalized.includes('korean identity')
    ? ''
    : ' Mandatory casting: every visible person must be a fictional Korean person from Seoul, South Korea. Preserve the story-specific age, gender, occupation, and historical era. Do not cast a Western or European-looking model, and do not change the character’s Korean identity.';
  const noText = normalized.includes('no korean letters') && normalized.includes('no handwriting')
    ? ''
    : ' No readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark.';
  return `${text}${casting}${noText}`;
}

function strengthenKoreanEvidencePrompt(prompt) {
  const text = String(prompt || '').trim();
  const normalized = text.toLowerCase();
  if (!text) return text;
  const casting = normalized.includes('if any person') || normalized.includes('every visible person')
    ? ''
    : ' If any person, hand, portrait, reflection, or silhouette appears, it must depict a fictional Korean person from Seoul and match the story-specific age and era. Do not cast a Western or European-looking model.';
  const noText = normalized.includes('no korean letters') && normalized.includes('no handwriting')
    ? ''
    : ' Use only abstract route lines, stains, folds, torn edges, and non-text shapes. No readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark.';
  return `${text}${casting}${noText}`;
}

function strengthenCaseMaterials(draft) {
  if (!draft) return;
  const missions = Array.isArray(draft.missions) ? draft.missions : [];
  const finalAnswerType = draft.finalAnswerType || 'EVIDENCE';
  const suspectSeeds = [
    {
      alias: '관계자 A',
      displayName: '붉은 우산의 의뢰인',
      relation: '사건 의뢰를 가장 먼저 전달한 인물',
      suspicion: '현장 사진이 사라진 시간대에 조사 경로 근처에서 반복적으로 목격되었다.',
      alibi: '비가 오기 전 카페 골목에 있었다고 주장하지만, 장소 키워드와 동선이 일부 겹친다.'
    },
    {
      alias: '관계자 B',
      displayName: '잃어버린 필름의 조수',
      relation: '피해자의 기록 정리를 맡았던 조수',
      suspicion: '사진과 메모의 순서를 알고 있어 단서를 바꿔치기할 수 있는 위치에 있었다.',
      alibi: '자료실에 있었다고 말하지만, 정답 키워드 단서 중 하나가 그의 진술과 충돌한다.'
    },
    {
      alias: '관계자 C',
      displayName: '회색 봉투의 전달자',
      relation: '마지막 문서를 전달한 익명의 중개인',
      suspicion: '장소 키워드 두 개가 모두 이 인물의 이동 방향을 가리킨다.',
      alibi: '봉투만 전달했을 뿐이라고 주장하지만, 봉투 안쪽에 사건의 핵심 단어가 남아 있다.'
    }
  ];
  draft.suspects = suspectSeeds.map((seed, index) => {
    const current = draft.suspects?.[index] || {};
    const displayName = isWeakText(current.displayName) ? seed.displayName : current.displayName;
    return {
      ...current,
      alias: isWeakText(current.alias) ? seed.alias : current.alias,
      displayName,
      shortDescription: isWeakText(current.shortDescription)
        ? `${displayName}은 다른 이들이 알기 전부터 ${seed.suspicion} 그러나 그 정보의 출처는 끝까지 숨겼습니다.`
        : current.shortDescription,
      relationToVictim: seed.relation,
      suspiciousPoint: isWeakText(current.suspiciousPoint) ? seed.suspicion : current.suspiciousPoint,
      alibiSummary: isWeakText(current.alibiSummary) ? seed.alibi : current.alibiSummary,
      imagePrompt: current.imagePrompt || buildSuspectImagePrompt({ ...current, displayName, alias: seed.alias, suspiciousPoint: seed.suspicion }),
      portraitImageUrl: current.portraitImageUrl || ''
    };
  });
  const evidenceByOrder = new Map((draft.evidences || []).map((evidence) => [Number(evidence.sourceMissionOrder || 0), evidence]));
  draft.evidences = missions.map((mission, index) => {
    const order = Number(mission.order || index + 1);
    const type = safeEvidenceType(evidenceTypeForRole(mission.clueRole || mission.markerType));
    const current = evidenceByOrder.get(order) || {};
    const title = String(isWeakText(current.title) ? evidenceTitleForMission(mission, type) : current.title).slice(0, 255);
    const textSummary = isWeakText(current.textSummary) ? evidenceSummaryForMission(mission) : current.textSummary;
    return {
      ...current,
      title,
      type,
      textSummary,
      sourceMissionOrder: order,
      imagePrompt: current.imagePrompt || buildEvidenceImagePrompt({ ...current, title, type, textSummary }),
      imageUrl: isWeakImageUrl(current.imageUrl) ? '' : current.imageUrl
    };
  });
}

function isWeakText(value) {
  const text = String(value || '').trim();
  if (!text) return true;
  return ['AI 초안', 'placeholder', '검수', '운영 공개 전', '관리자 검수', '사건 현장 스케치', '조사 시작 단서 카드', '초안입니다', '알리바이'].some((word) => text.includes(word));
}

function isWeakImageUrl(value) {
  const url = String(value || '').trim();
  return !url || url.includes('generated-case-card') || url.includes('placeholder');
}

function buildSuspectImagePrompt(suspect = {}) {
  const name = suspect.displayName || suspect.alias || 'mission character';
  const suspicion = suspect.suspiciousPoint || suspect.shortDescription || 'a hidden contradiction in the route timeline';
  return [
    `Create a fictional Korean character archive card illustration of ${name}.`,
    'Mandatory casting: depict a fictional Korean person from Seoul, South Korea. The subject must look unmistakably Korean while preserving the story-specific age, gender, occupation, and historical era.',
    'Do not cast a Western or European-looking model, and do not change the character’s Korean identity.',
    'Visual style: flat 2D Korean webtoon and printed storybook illustration, muted earth-tone palette, matte paper grain, archival texture, clean dark ink outlines, gentle cel shading, calm documentary adventure mood.',
    `Character clue: ${suspicion}`,
    'Composition: character archive card, bust portrait, 3/4 view, natural Korean styling appropriate to the character and era, restrained expression, simple archival background.',
    'Negative constraints: no photorealism, no 3D render, no glossy game art, no Western comic style, no foreign tourist styling, no readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark, no logo, not a real celebrity, not a real historical person.'
  ].join(' ');
}

function buildEvidenceImagePrompt(evidence = {}) {
  const title = evidence.title || 'mission archive item';
  const summary = evidence.textSummary || 'a clue object connected to the route and final deduction';
  const type = evidence.type || 'EVIDENCE';
  return [
    'Create a Korean outdoor story-mission archive card illustration.',
    `Subject: ${title}. Evidence type: ${type}.`,
    `Story detail: ${summary}`,
    'Visual style: flat 2D Korean webtoon and printed storybook illustration, muted earth-tone palette, matte paper grain, archival texture, simplified shapes, clean dark ink outlines, gentle cel shading.',
    'Composition: mission archive card, one clearly recognizable illustrated object or document silhouette, abstract route lines, stains, folds, and torn edges only, balanced margins, calm documentary adventure mood.',
    'Human casting rule: if any person, hand, portrait, reflection, or silhouette appears, depict a fictional Korean person from Seoul and match the story-specific age and era.',
    'Negative constraints: no photorealism, no 3D render, no glossy game art, no Western comic style, no Western or European-looking models, no readable text, no Korean letters, no labels, no handwriting, no symbols resembling text, no UI frame, no watermark, no logo.'
  ].join(' ');
}

async function copyImagePrompt(prompt) {
  const text = String(prompt || '').trim();
  if (!text) {
    setMessage('복사할 이미지 프롬프트가 없습니다.', 'error');
    return;
  }
  try {
    await navigator.clipboard.writeText(text);
    setMessage('이미지 프롬프트를 클립보드에 복사했습니다.', 'success');
  } catch {
    setMessage('클립보드 복사에 실패했습니다. 프롬프트를 직접 선택해 복사해 주세요.', 'error');
  }
}

async function generateAnswerPlan() {
  if (!prepareDraftInputFromSelection()) return;
  startDraftProgress('plan', 'Gemini가 선택 장소에 맞는 에피소드 장르와 최종 정답 키워드를 설계하고 있습니다.');
  try {
    let payload = JSON.parse(draftInput.value);
    if (!siteDataEnriched.value) {
      draftProgressStep.value = 'request';
      draftStatus.value = '현장 근거를 먼저 자동 보강한 뒤 장르/정답 키워드를 요청합니다.';
      payload = await adminEpisodeApi.enrichSiteData(payload);
      draftInput.value = JSON.stringify(payload, null, 2);
      siteDataEnriched.value = true;
    }
    draftProgressStep.value = 'request';
    draftPlan.value = await adminEpisodeApi.createAnswerPlan(payload);
    draftPlan.value.finalAnswerKeywords = (draftPlan.value.finalAnswerKeywords || [])
      .map((item) => ({ ...item, keyword: normalizeAnswerKeywordValue(item.keyword) }))
      .filter((item) => item.keyword);
    if (!draftPlan.value.planReviewRequired) {
      payload = applyDraftPlanToPayload(payload);
      draftInput.value = JSON.stringify(payload, null, 2);
    }
    draftProgressStep.value = 'hydrate';
    if (draftPlan.value.planReviewRequired) {
      finishDraftProgress('현장 근거가 부족해 자동 초안 생성을 중단했습니다.');
      setMessage(draftPlan.value.reviewReason || '현장 관찰 데이터를 보강하세요.', 'error');
    } else {
      finishDraftProgress('장르와 최종 정답 키워드 계획이 생성되었습니다. 확인 후 전체 초안을 생성하세요.');
      setMessage('AI 장르/정답 키워드 계획이 준비되었습니다. 아직 전체 초안은 생성되지 않았습니다.', 'success');
    }
  } catch (error) {
    failDraftProgress(error.userMessage || error.message || '장르/정답 키워드 계획을 생성할 수 없습니다.');
    setMessage(draftError.value, 'error');
  }
}

function applyDraftPlanToPayload(payload) {
  if (draftPlan.value?.planReviewRequired) {
    return payload;
  }

  const finalAnswerKeywordItems = (draftPlan.value?.finalAnswerKeywords || [])
    .map((item) => ({
      slotId: String(item?.slotId || '').trim(),
      label: String(item?.label || '').trim(),
      keyword: normalizeAnswerKeywordValue(item?.keyword),
      aliases: Array.isArray(item?.aliases)
        ? item.aliases.map((alias) => String(alias || '').trim()).filter(Boolean)
        : [],

      sourcePlaceOrder: item?.sourcePlaceOrder ?? null,
      sourceBasis: String(item?.sourceBasis || '').trim(),
      sourceType: String(item?.sourceType || '').trim(),
      sourcePlaceName: String(item?.sourcePlaceName || '').trim(),
      sourceText: String(item?.sourceText || '').trim(),
      risk: String(item?.risk || '').trim()
    }))
    .filter((item) => item.slotId && item.label && item.keyword);

  const keywords = finalAnswerKeywordItems.map((item) => item.keyword);

  return {
    ...payload,
    selectedGenreId: draftPlan.value?.selectedGenreId || payload.selectedGenreId,
    selectedGenreName: draftPlan.value?.selectedGenreName || payload.selectedGenreName,
    finalAnswerKeywordItems,
    finalAnswerKeywords: keywords
  };
}

function finalAnswerTypeLabel(type) {
  return {
    CULPRIT: '진범',
    WEAPON: '흉기',
    EVIDENCE: '핵심 증거',
    HIDDEN_DOCUMENT: '숨겨진 문서',
    SECRET_KEYWORD: '비밀 키워드',
    HIDDEN_TRUTH: '숨겨진 진실'
  }[type] || '사건의 핵심 진실';
}

function safeEvidenceType(type) {
  const normalized = String(type || 'NOTE').toUpperCase();
  const allowed = ['PHOTO', 'MEMO', 'NOTE', 'DOCUMENT', 'EVIDENCE', 'SUSPECT_CLUE', 'POST_IT', 'ANSWER_CLUE', 'DESTINATION_CLUE', 'STORY_CLUE'];
  if (allowed.includes(normalized)) return normalized;
  if (['IMAGE', 'PICTURE', 'SCENE'].includes(normalized)) return 'PHOTO';
  return 'NOTE';
}

function safePublicMarkerType(type, mission) {
  const normalized = String(type || '').toUpperCase();
  if (mission?.finalPlace || mission?.markerType === 'FINAL' || normalized === 'FINAL') return 'DESTINATION_HINT';
  return ['START', 'ANSWER_HINT', 'DESTINATION_HINT'].includes(normalized)
    ? normalized
    : String(mission?.markerType || 'ANSWER_HINT').replace('FINAL', 'DESTINATION_HINT');
}

function generatedSuspectPortraitDataUrl(name = '관계자', alias = 'SUSPECT', seedText = '') {
  const hash = hashString(`${name}-${alias}-${seedText}`);
  const skin = ['#f2c6a0', '#d9a77e', '#c68b6b', '#e4b98f'][hash % 4];
  const shirt = ['#93c5fd', '#86efac', '#fca5a5', '#c4b5fd', '#fde68a'][hash % 5];
  const hair = ['#111827', '#292524', '#3f3f46'][hash % 3];
  const safeName = escapeXml(name || '관계자');
  const safeAlias = escapeXml(alias || 'SUSPECT');
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="520" height="680" viewBox="0 0 520 680">
      <rect width="520" height="680" fill="#f8ead0"/>
      <rect x="36" y="36" width="448" height="608" fill="#fff7ed" stroke="#94a3b8" stroke-width="3"/>
      <text x="66" y="86" fill="#111827" font-family="Arial" font-size="34" font-weight="900">관계자 정보</text>
      <text x="66" y="120" fill="#7c2d12" font-family="Arial" font-size="20" font-weight="900">${safeAlias}</text>
      <rect x="86" y="144" width="348" height="330" fill="#e2e8f0" stroke="#475569" stroke-width="4"/>
      <circle cx="260" cy="268" r="78" fill="${skin}"/>
      <path d="M180 258 C176 170, 340 160, 340 260 C300 222, 236 240, 180 258Z" fill="${hair}"/>
      <circle cx="232" cy="280" r="7" fill="#111827"/><circle cx="288" cy="280" r="7" fill="#111827"/>
      <path d="M236 326 C254 342, 280 342, 298 326" fill="none" stroke="#7f1d1d" stroke-width="6" stroke-linecap="round"/>
      <path d="M126 454 C150 380, 204 350, 260 350 C318 350, 372 382, 396 454" fill="${shirt}"/>
      <path d="M122 526 H398 M122 570 H322" stroke="#94a3b8" stroke-width="10" stroke-linecap="round"/>
      <text x="122" y="626" fill="#111827" font-family="Arial" font-size="28" font-weight="900">${safeName}</text>
    </svg>`;
  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`;
}

function syncDraftMissionRole(mission) {
  if (!mission) return;
  const isFinal = mission.finalPlace === true || mission.markerType === 'FINAL';
  if (isFinal) {
    mission.finalPlace = true;
    mission.markerType = 'FINAL';
    mission.clueRole = 'FINAL_PLACE';
    mission.publicMarkerType = 'DESTINATION_HINT';
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
  } else {
    mission.markerType = 'ANSWER_HINT';
    mission.clueRole = 'ANSWER_HINT';
    mission.publicMarkerType = 'ANSWER_HINT';
  }
}

function generatedEvidenceCardDataUrl(title = 'CASE FILE', type = 'EVIDENCE', summary = '') {
  const safeTitle = escapeXml(title || 'CASE FILE');
  const safeSummary = escapeXml(summary || evidenceVisualCaption(type));
  const normalizedType = String(type || 'EVIDENCE').toUpperCase();
  const safeType = escapeXml(normalizedType);
  const hash = hashString(`${title}-${normalizedType}`);
  const palette = evidencePalette(normalizedType, hash);
  const motif = evidenceMotifSvg(normalizedType, hash, safeTitle);
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
      <defs>
        <linearGradient id="bg" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stop-color="${palette.bg1}"/>
          <stop offset="0.62" stop-color="${palette.bg2}"/>
          <stop offset="1" stop-color="${palette.bg3}"/>
        </linearGradient>
        <filter id="paperShadow" x="-20%" y="-20%" width="140%" height="140%">
          <feDropShadow dx="0" dy="18" stdDeviation="18" flood-color="#020617" flood-opacity="0.28"/>
        </filter>
      </defs>
      <rect width="960" height="640" fill="url(#bg)"/>
      <circle cx="${150 + (hash % 170)}" cy="${110 + (hash % 70)}" r="210" fill="#ffffff" opacity="0.06"/>
      <circle cx="${720 + (hash % 90)}" cy="${420 - (hash % 80)}" r="250" fill="#000000" opacity="0.12"/>
      <rect x="74" y="54" width="812" height="532" rx="30" fill="#f8ead0" opacity="0.97" filter="url(#paperShadow)"/>
      <path d="M104 110 H856" stroke="${palette.line}" stroke-width="2" opacity="0.26"/>
      <text x="116" y="94" fill="${palette.accent}" font-family="Georgia, serif" font-size="28" font-weight="700">OPERATION KOREA</text>
      <text x="116" y="134" fill="#334155" font-family="Arial, sans-serif" font-size="18" font-weight="700">DIGITAL CASE FILE · ${safeType}</text>
      ${motif}
      <rect x="108" y="506" width="744" height="54" rx="14" fill="#111827" opacity="0.9"/>
      <text x="132" y="542" fill="#f8fafc" font-family="Arial, sans-serif" font-size="26" font-weight="800">${safeTitle}</text>
      <text x="132" y="584" fill="${palette.line}" font-family="Arial, sans-serif" font-size="18">${safeSummary.slice(0, 54)}</text>
    </svg>
  `;
  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svg)))}`;
}

function evidenceVisualCaption(type) {
  const normalized = String(type || '').toUpperCase();
  if (normalized === 'PHOTO') return '현장 사진처럼 읽히는 사건 분위기 스케치';
  if (normalized === 'MEMO' || normalized === 'POST_IT') return '접힌 메모와 손상된 기록 조각';
  if (normalized === 'DESTINATION_CLUE') return '장소 분위기를 좁히는 동선 메모';
  if (normalized === 'ANSWER_CLUE') return '정답의 형태를 좁히는 증거 조각';
  return '미션 파일에 보관된 조사 자료';
}

function hashString(value) {
  return String(value || '').split('').reduce((hash, char) => ((hash << 5) - hash + char.charCodeAt(0)) >>> 0, 2166136261);
}

function evidencePalette(type, hash) {
  const palettes = {
    PHOTO: ['#0f172a', '#1e3a8a', '#92400e', '#f59e0b', '#60a5fa'],
    MEMO: ['#422006', '#854d0e', '#172554', '#facc15', '#fb923c'],
    NOTE: ['#111827', '#334155', '#78350f', '#f97316', '#cbd5e1'],
    DOCUMENT: ['#1f2937', '#4b5563', '#7f1d1d', '#f87171', '#94a3b8'],
    SUSPECT_CLUE: ['#18181b', '#3f3f46', '#7c2d12', '#fb7185', '#fbbf24'],
    POST_IT: ['#365314', '#3f6212', '#854d0e', '#bef264', '#facc15'],
    ANSWER_CLUE: ['#431407', '#9a3412', '#111827', '#fb923c', '#fde68a'],
    DESTINATION_CLUE: ['#312e81', '#4c1d95', '#0f172a', '#c084fc', '#93c5fd'],
    STORY_CLUE: ['#064e3b', '#065f46', '#1e1b4b', '#34d399', '#a7f3d0']
  };
  const base = palettes[type] || ['#111827', '#1f2937', '#78350f', '#f59e0b', '#cbd5e1'];
  return { bg1: base[0], bg2: base[1], bg3: base[2], accent: base[3], line: base[4] };
}

function evidenceMotifSvg(type, hash, safeTitle) {
  const stamp = `<g transform="translate(650 160) rotate(${(hash % 18) - 9})"><rect x="0" y="0" width="184" height="72" rx="10" fill="none" stroke="#7f1d1d" stroke-width="7" opacity="0.45"/><text x="24" y="47" fill="#7f1d1d" font-family="Arial" font-size="24" font-weight="900" opacity="0.55">CASE</text></g>`;
  if (type === 'PHOTO') return `${stamp}<g transform="translate(128 190)"><rect x="0" y="0" width="420" height="274" rx="18" fill="#0f172a"/><rect x="28" y="28" width="364" height="218" rx="12" fill="#1e293b"/><circle cx="128" cy="104" r="46" fill="#f59e0b" opacity="0.78"/><path d="M32 232 L150 138 L240 206 L292 160 L392 238" fill="none" stroke="#93c5fd" stroke-width="18" stroke-linecap="round" stroke-linejoin="round"/><rect x="244" y="70" width="86" height="56" rx="10" fill="#111827" stroke="#f8fafc" stroke-width="4" opacity="0.78"/></g>`;
  if (type === 'MEMO' || type === 'POST_IT') return `${stamp}<g transform="translate(148 174) rotate(-4)"><rect x="0" y="0" width="360" height="300" rx="18" fill="#fde68a"/><path d="M0 52 H360" stroke="#f59e0b" stroke-width="4" opacity="0.35"/><path d="M54 118 H300 M54 170 H280 M54 222 H320" stroke="#78350f" stroke-width="14" stroke-linecap="round" opacity="0.55"/><circle cx="302" cy="48" r="26" fill="#ef4444" opacity="0.72"/></g>`;
  if (type === 'DOCUMENT') return `${stamp}<g transform="translate(150 168)"><path d="M0 0 H340 L408 70 V330 H0 Z" fill="#fff7ed" stroke="#92400e" stroke-width="5"/><path d="M340 0 V72 H408" fill="none" stroke="#92400e" stroke-width="5"/><path d="M56 104 H330 M56 154 H352 M56 204 H294 M56 254 H342" stroke="#475569" stroke-width="12" stroke-linecap="round" opacity="0.58"/><path d="M250 262 C286 226, 342 236, 374 294" fill="none" stroke="#b91c1c" stroke-width="9" opacity="0.62"/></g>`;
  if (type === 'SUSPECT_CLUE') return `${stamp}<g transform="translate(164 168)"><rect x="0" y="0" width="330" height="330" rx="26" fill="#111827"/><circle cx="165" cy="118" r="66" fill="#64748b"/><path d="M74 290 C92 214, 128 190, 165 190 C206 190, 252 220, 286 290" fill="#94a3b8"/><path d="M62 52 L282 52 M62 286 L282 286" stroke="#fbbf24" stroke-width="10" opacity="0.72"/></g>`;
  if (type === 'DESTINATION_CLUE') return `${stamp}<g transform="translate(132 184)"><path d="M68 86 C196 10, 310 24, 440 96" fill="none" stroke="#c4b5fd" stroke-width="18" stroke-linecap="round" stroke-dasharray="20 22"/><path d="M120 42 C70 42, 36 80, 36 130 C36 210, 120 286, 120 286 C120 286, 204 210, 204 130 C204 80, 170 42, 120 42 Z" fill="#7c3aed"/><circle cx="120" cy="130" r="34" fill="#f8fafc"/><path d="M384 74 C334 74, 300 112, 300 162 C300 242, 384 318, 384 318 C384 318, 468 242, 468 162 C468 112, 434 74, 384 74 Z" fill="#111827"/><circle cx="384" cy="162" r="34" fill="#fbbf24"/></g>`;
  return `${stamp}<g transform="translate(140 178)"><rect x="0" y="0" width="410" height="290" rx="28" fill="#111827"/><path d="M66 84 H340 M66 146 H278 M66 208 H320" stroke="#f59e0b" stroke-width="16" stroke-linecap="round" opacity="0.76"/><circle cx="318" cy="80" r="42" fill="#fef3c7" opacity="0.9"/><path d="M302 80 L318 96 L352 56" fill="none" stroke="#78350f" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/><text x="66" y="262" fill="#e5e7eb" font-family="Arial" font-size="20" font-weight="700">${safeTitle.slice(0, 28)}</text></g>`;
}function escapeXml(value) {
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

function safeAiInputText(value) {
  const text = String(value || '').trim();
  if (!text) return '';

  const blocked = [
    '관리자',
    '검수',
    '확인 필요',
    '현장 메모',
    'TourAPI 기준',
    'Kakao Local 기준',
    '공식 설명이 없어',
    '데이터 보강',
    '관찰 데이터 부족'
  ];

  return blocked.some((word) => text.includes(word)) ? '' : text;
}

function coordinateValue(...values) {
  for (const value of values) {
    if (value === null || value === undefined || value === '') continue;
    const numberValue = Number(value);
    if (!Number.isNaN(numberValue)) return numberValue;
  }
  return null;
}

function normalizeCandidate(candidate = {}) {
  return {
    ...candidate,
    title: String(candidate.title || candidate.name || candidate.placeName || '').trim(),
    address: String(candidate.address || candidate.addr1 || candidate.roadAddress || '').trim(),
    latitude: coordinateValue(candidate.latitude, candidate.lat, candidate.mapY, candidate.mapy, candidate.y),
    longitude: coordinateValue(candidate.longitude, candidate.lng, candidate.mapX, candidate.mapx, candidate.x),
    areaCode: String(candidate.areaCode || candidate.area || '').trim(),
    source: String(candidate.source || '').trim(),
    description: String(candidate.description || candidate.overview || '').trim(),
    contentId: String(candidate.contentId || candidate.contentid || '').trim()
  };
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
  candidateLoadError.value = '';
  anchorCandidate.value = null;
  nearbyCandidates.value = [];
  nearbyLoaded.value = false;
  selectedCandidates.value = [];
  siteDataEnriched.value = false;
  try {
    const candidates = await adminEpisodeApi.getPlaceCandidates(candidateAreaCode.value);
    if (!Array.isArray(candidates)) {
      throw new Error('TourAPI 후보 응답 형식이 올바르지 않습니다.');
    }
    placeCandidates.value = candidates.map(normalizeCandidate);
    candidateLoaded.value = true;
    setMessage(placeCandidates.value.length ? 'TourAPI 장소 후보를 불러왔습니다.' : 'TourAPI 장소 후보가 없습니다.', placeCandidates.value.length ? 'success' : 'error');
  } catch (error) {
    placeCandidates.value = [];
    candidateLoaded.value = true;
    candidateLoadError.value = error.userMessage || error.message || 'TourAPI 장소 후보를 불러올 수 없습니다.';
    setMessage(candidateLoadError.value, 'error');
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
  siteDataEnriched.value = false;
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
    selectedCandidates.value = buildRecommendedRouteCandidates(normalizedAnchor, nearbyCandidates.value);
    nearbyLoaded.value = true;
    setMessage(
      selectedCandidates.value.length >= 8
        ? 'Kakao Local 주변 후보를 추천 선택 상태로 불러왔습니다. 바로 초안 작성이 가능합니다.'
        : '주변 후보가 부족합니다. 선택된 후보를 유지하고 반경을 넓히거나 수동 후보를 추가하세요.',
      selectedCandidates.value.length >= 8 ? 'success' : 'error'
    );
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
  siteDataEnriched.value = false;
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

function isLocalBusinessCandidate(candidate) {
  const value = [candidate.title, candidate.address, candidate.source, candidate.description]
    .map((item) => String(item || '').toLowerCase())
    .join(' ');
  return ['카페', 'cafe', '커피', '시장', '상가', '골목', '맛집', '식당', '분식', '공방', '서점', '빵', '베이커리', '편집숍']
    .some((keyword) => value.includes(keyword));
}

function candidateRouteScore(candidate, anchor) {
  const normalized = normalizeCandidate(candidate);
  let score = 0;
  if (isLocalBusinessCandidate(normalized)) score += 40;
  const value = [normalized.title, normalized.address, normalized.source, normalized.description]
    .map((item) => String(item || '').toLowerCase())
    .join(' ');
  if (['문화', '박물관', '미술관', '전시', '책', '역사', '공원', '거리'].some((keyword) => value.includes(keyword))) score += 24;
  const distance = candidateDistanceMeters(anchor, normalized);
  if (Number.isFinite(distance)) {
    if (distance >= 120 && distance <= nearbyRadius.value) score += 20;
    score -= Math.min(24, distance / 250);
  }
  return score;
}

const recommendedMinSpotDistanceMeters = 180;

function isFarEnoughFromRoute(candidate, selected, minDistance = recommendedMinSpotDistanceMeters) {
  if (!selected.length || minDistance <= 0) return true;
  return selected.every((item) => {
    const distance = candidateDistanceMeters(candidate, item);
    return !Number.isFinite(distance) || distance >= minDistance;
  });
}

function pickSpacedRouteCandidates(pool, anchor, count = 8) {
  const normalizedAnchor = normalizeCandidate(anchor);
  const selected = [];
  const selectedForSpacing = hasCandidateCoordinate(normalizedAnchor) ? [normalizedAnchor] : [];
  const distancePasses = [
    recommendedMinSpotDistanceMeters,
    Math.round(recommendedMinSpotDistanceMeters * 0.65),
    0
  ];
  for (const minDistance of distancePasses) {
    for (const candidate of pool) {
      if (selected.some((item) => candidateKey(item) === candidateKey(candidate))) continue;
      if (!isFarEnoughFromRoute(candidate, selectedForSpacing, minDistance)) continue;
      selected.push(candidate);
      selectedForSpacing.push(candidate);
      if (selected.length >= count) return selected;
    }
  }
  return selected;
}

function candidateDistanceMeters(a, b) {
  const from = normalizeCandidate(a);
  const to = normalizeCandidate(b);
  if (!hasCandidateCoordinate(from) || !hasCandidateCoordinate(to)) return Number.POSITIVE_INFINITY;
  const earthRadius = 6371000;
  const lat1 = from.latitude * Math.PI / 180;
  const lat2 = to.latitude * Math.PI / 180;
  const deltaLat = (to.latitude - from.latitude) * Math.PI / 180;
  const deltaLng = (to.longitude - from.longitude) * Math.PI / 180;
  const h = Math.sin(deltaLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) ** 2;
  return earthRadius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function buildRecommendedRouteCandidates(anchor, candidates) {
  const normalizedAnchor = normalizeCandidate(anchor);
  const anchorKey = candidateKey(normalizedAnchor);
  const pool = candidates
    .map(normalizeCandidate)
    .filter((candidate) => hasCandidateCoordinate(candidate) && candidateKey(candidate) !== anchorKey)
    .sort((a, b) => candidateRouteScore(b, normalizedAnchor) - candidateRouteScore(a, normalizedAnchor));
  const selected = pickSpacedRouteCandidates(pool, normalizedAnchor, 8);
  return [...selected, normalizedAnchor].slice(0, 9);
}

function rerollRecommendedRoute() {
  if (!anchorCandidate.value) return;
  const currentKeys = new Set(selectedCandidates.value.map(candidateKey));
  const anchor = normalizeCandidate(anchorCandidate.value);
  const anchorKey = candidateKey(anchor);
  const pool = nearbyCandidates.value
    .map(normalizeCandidate)
    .filter((candidate) => hasCandidateCoordinate(candidate) && candidateKey(candidate) !== anchorKey)
    .sort((a, b) => {
      const aSelectedPenalty = currentKeys.has(candidateKey(a)) ? -18 : 0;
      const bSelectedPenalty = currentKeys.has(candidateKey(b)) ? -18 : 0;
      return (candidateRouteScore(b, anchor) + bSelectedPenalty) - (candidateRouteScore(a, anchor) + aSelectedPenalty);
    });
  selectedCandidates.value = [...pickSpacedRouteCandidates(pool, anchor, 8), anchor].slice(0, 9);
  siteDataEnriched.value = false;
  applyCandidatesToDraft(false);
  setMessage('추천 루트를 다시 구성했습니다. 필요하면 후보별 교체 버튼으로 더 조정하세요.', 'success');
}

function replaceSelectedCandidate(candidate) {
  if (isAnchorCandidate(candidate)) {
    setMessage('TourAPI 기준 장소는 내부 최종 장소라 이 단계에서 교체할 수 없습니다.', 'error');
    return;
  }
  const oldKey = candidateKey(candidate);
  const selectedKeys = new Set(selectedCandidates.value.map(candidateKey));
  const anchor = anchorCandidate.value || selectedCandidates.value[0];
  const replacement = nearbyCandidates.value
    .map(normalizeCandidate)
    .filter((item) => hasCandidateCoordinate(item) && !selectedKeys.has(candidateKey(item)))
    .filter((item) => isFarEnoughFromRoute(item, selectedCandidates.value.filter((selected) => candidateKey(selected) !== oldKey)))
    .sort((a, b) => candidateRouteScore(b, anchor) - candidateRouteScore(a, anchor))[0];
  if (!replacement) {
    setMessage('교체할 수 있는 후보가 없습니다. 반경을 넓히거나 수동 후보를 추가하세요.', 'error');
    return;
  }
  selectedCandidates.value = selectedCandidates.value.map((item) => candidateKey(item) === oldKey ? replacement : item);
  siteDataEnriched.value = false;
  applyCandidatesToDraft(false);
  setMessage('후보를 ' + replacement.title + '로 교체했습니다.', 'success');
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
    siteDataEnriched.value = false;
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
  siteDataEnriched.value = false;
}

function applyCandidatesToDraft(showMessage = true) {
  if (selectedCandidates.value.length < 9) {
    setMessage('기준 장소를 포함해 9개 장소를 선택해 주세요.', 'error');
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
  era: inferEraFromCandidates(orderedCandidates),
  theme: '장소 기반 스토리 미션',
  targetAudience: '야외 방탈출 플레이어',
  playTime: '90~120분',

  genreCatalog: DEFAULT_ESCAPE_GENRE_CATALOG,

  missionPolicy: {
    missionCount: orderedCandidates.length,
    minMissionCount: 6,
    maxMissionCount: 9,
    minCluesPerAnswerSlot: 2,
    answerHintRatio: 0.6,
    destinationHintRatio: 0.4,
    allowDynamicMissionCount: true
  },

  puzzlePolicy: {
    maxSamePuzzleTypeCount: 3,
    forbidPlaceNameTextExtraction: true,
    forbidFinalKeywordAsPuzzleAnswer: true,
    requireUniquePuzzleAnswer: true,
    allowedPuzzleTypes: [
      'OBSERVATION',
      'NUMBER_LOCK',
      'INITIAL_SOUND',
      'PATTERN',
      'STORY_COMBINATION'
    ],
    blockedGenericAnswers: [
      '기록',
      '단서',
      '문서',
      '메모',
      '진실',
      '비밀',
      '장소',
      '물건',
      '사건',
      '흔적',
      '정답',
      '검수필요'
    ]
  },

    places: orderedCandidates.map((candidate, index) => {
      const area = areaLabel(candidateAreaCode.value);
      const safeDescription = safeAiInputText(candidate.description);

      return {
        name: candidate.title,
        address: candidate.address,
        latitude: candidate.latitude,
        longitude: candidate.longitude,
        description: safeDescription,
        visibleElements: [],
        numbers: [],
        keywords: [
          candidate.title,
          area,
          ...(candidate.category ? [candidate.category] : [])
        ].filter(Boolean),
        adminMemo: '',
        role: roles[index],
        publicMarkerType: publicMarkerForCandidate(index, roles[index], orderedCandidates.length),
        arrivalRadius: 50
      };
    })
  };
  draftInput.value = JSON.stringify(payload, null, 2);
  draftResult.value = null;
  draftValidation.value = null;
  if (showMessage) {
    setMessage('선택한 후보가 초안 입력에 반영되었습니다. 사용자 지도에는 모든 장소가 조사 후보로만 표시됩니다.', 'success');
  }
}

function buildRoles(count) {
  const roles = [];
  for (let index = 0; index < count; index += 1) {
    if (index === 0) roles.push('START');
    else if (index === count - 1) roles.push('FINAL');
    else if (index >= count - 4) roles.push('DESTINATION_HINT');
    else roles.push('ANSWER_HINT');
  }
  return roles;
}

function publicMarkerForCandidate(index, role, count) {
  if (role === 'FINAL') return 'DESTINATION_HINT';
  return role;
}

function roleForCandidate(index) {
  return buildRoles(orderedSelectedCandidates.value.length)[index] || 'ANSWER_HINT';
}

function roleLabel(role) {
  return {
    START: '시작 장소',
    ANSWER_HINT: '정답 키워드',
    DESTINATION_HINT: '장소 키워드',
    FINAL: '내부 최종 장소'
  }[role] || role;
}

function areaLabel(areaCode) {
  const labels = {
    seoul: '서울',
    capital_area: '서울 외 수도권',
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

function inferEraFromCandidates(candidates = []) {
  const source = candidates
    .flatMap((candidate) => [candidate.title, candidate.address, candidate.description, candidate.adminMemo, ...(candidate.keywords || [])])
    .join(' ');
  if (source.includes('대한제국') || source.includes('정동') || source.includes('1905') || source.includes('1897')) return '대한제국 말기';
  if (source.includes('조선') || source.includes('궁') || source.includes('한양')) return '조선 후기';
  if (source.includes('근대') || source.includes('개화') || source.includes('일제')) return '근대 전환기';
  if (source.includes('일제강점기') || source.includes('일제') || source.includes('식민지')) return '일제강점기';
  if (source.includes('한국전쟁') || source.includes('전쟁기념관') || source.includes('산업화')) return '근현대';
  return '현대';
}

function setMessage(text, type = 'success') {
  message.value = text;
  messageType.value = type;
}

function scrollToAdminSection(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
.admin-page-tabs { width: min(100%, 1180px); margin: 0 auto 14px; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; padding: 8px; border: 1px solid rgba(245,158,11,.22); border-radius: 18px; background: rgba(2,6,23,.48); }
.admin-page-tabs button { min-height: 46px; border: 1px solid rgba(148,163,184,.2); background: rgba(15,23,42,.68); color: #cbd5e1; }
.admin-page-tabs button.active { border-color: rgba(245,158,11,.72); background: linear-gradient(135deg, rgba(245,158,11,.95), rgba(251,191,36,.78)); color: #111827; box-shadow: 0 14px 30px rgba(245,158,11,.18); }
.layout { width: min(100%, 1180px); margin: 0 auto; display: grid; grid-template-columns: 330px 1fr; gap: 14px; }
.layout.builder-layout { grid-template-columns: minmax(0, 1fr); }
.episode-list, .detail-card, .draft-panel { border: 1px solid rgba(148,163,184,.2); border-radius: 18px; background: rgba(15,23,42,.68); padding: 16px; }
.episode-list { position: sticky; top: 16px; max-height: calc(100vh - 32px); overflow: auto; scrollbar-color: rgba(245,158,11,.55) rgba(15,23,42,.45); }
.draft-panel.full-width { grid-column: 1 / -1; margin-top: 0; }
.section-title { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
h2, h3 { margin: 0 0 10px; }
.episode-card { padding: 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(2,6,23,.38); margin-top: 10px; cursor: pointer; }
.episode-card.active { border-color: #f59e0b; box-shadow: 0 0 0 1px rgba(245,158,11,.38) inset; }
.episode-card strong, .spot-card strong, .mini-grid strong { display: block; }
.episode-card span, .spot-card span, .mini-grid span { color: #cbd5e1; font-size: .84rem; }
.metrics { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.metrics em { border-radius: 999px; background: rgba(245,158,11,.14); color: #fde68a; padding: 5px 8px; font-style: normal; font-size: .76rem; }
.message { padding: 10px; border-radius: 12px; margin: 0 0 10px; }
.global-message { width: min(100%, 1180px); box-sizing: border-box; margin: 0 auto 14px; }
.message.success { background: rgba(22,101,52,.22); color: #bbf7d0; }
.message.error { background: rgba(127,29,29,.34); color: #fecaca; }
.draft-feedback-panel { margin: 10px 0 12px; padding: 12px; border: 1px solid rgba(34,197,94,.28); border-radius: 14px; background: rgba(22,101,52,.16); }
.draft-feedback-panel.invalid { border-color: rgba(248,113,113,.42); background: rgba(127,29,29,.2); }
.draft-feedback-panel strong { color: #fde68a; }
.draft-feedback-panel p { margin: 6px 0; color: #e2e8f0; }
.draft-feedback-panel ul { margin: 8px 0 0; padding-left: 18px; color: #fecaca; line-height: 1.55; }
.draft-feedback-panel li b { margin-right: 6px; color: #fbbf24; }
.draft-feedback-panel li span { margin-right: 6px; color: #bfdbfe; font-weight: 900; }
.draft-feedback-panel li em { margin-right: 6px; color: #cbd5e1; font-style: normal; }
.draft-feedback-panel small { display: block; margin-top: 8px; color: #cbd5e1; }
.case-builder-next { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 12px 0; padding: 14px; border: 1px solid rgba(245,158,11,.32); border-radius: 16px; background: linear-gradient(135deg, rgba(120,53,15,.26), rgba(15,23,42,.72)); }
.case-builder-next strong { color: #fde68a; font-size: 1rem; }
.case-builder-next p { margin: 5px 0 0; color: #e2e8f0; line-height: 1.5; font-size: .88rem; }
.case-builder-next button { flex: 0 0 auto; }
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
.admin-section-tabs { position: sticky; top: 10px; z-index: 5; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; margin: 14px 0; padding: 8px; border: 1px solid rgba(245,158,11,.26); border-radius: 16px; background: rgba(15,23,42,.94); backdrop-filter: blur(14px); }
.admin-section-tabs button { min-height: 38px; border: 1px solid rgba(148,163,184,.22); background: rgba(2,6,23,.42); color: #fde68a; }
.admin-anchor { scroll-margin-top: 78px; }
.preview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; margin-top: 12px; }
.preview-grid article { border: 1px solid rgba(148,163,184,.16); border-radius: 14px; background: rgba(2,6,23,.32); padding: 12px; }
.preview-grid strong { display: block; margin-bottom: 8px; color: #fde68a; }
.preview-grid ul { display: grid; gap: 7px; list-style: none; padding: 0; margin: 0; }
.preview-grid li { display: grid; gap: 4px; color: #e2e8f0; font-size: .84rem; line-height: 1.4; }
.preview-grid span { width: fit-content; border-radius: 999px; padding: 3px 7px; background: rgba(148,163,184,.14); color: #cbd5e1; font-size: .72rem; font-weight: 900; }
.preview-grid span.START { color: #93c5fd; background: rgba(37,99,235,.16); }
.preview-grid span.ANSWER_HINT { color: #fdba74; background: rgba(234,88,12,.16); }
.preview-grid span.DESTINATION_HINT { color: #d8b4fe; background: rgba(126,34,206,.16); }


.preview-grid em { color: #94a3b8; font-size: .72rem; font-style: normal; }
.stat-grid, .mini-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 9px; margin: 12px 0 18px; }
.stat-grid article, .mini-grid article, .spot-card { border: 1px solid rgba(148,163,184,.16); border-radius: 14px; background: rgba(2,6,23,.32); padding: 12px; }
.stat-grid strong { font-size: 1.5rem; color: #fde68a; }
.stat-grid span, .empty { color: #94a3b8; }
.spot-list { display: grid; gap: 10px; }
.spot-card.final { border-color: rgba(248,113,113,.62); }
.spot-card.review-required { border-color: rgba(248,113,113,.78); background: linear-gradient(135deg, rgba(127,29,29,.5), rgba(69,10,10,.34) 48%, rgba(15,23,42,.56)); box-shadow: 0 0 0 1px rgba(248,113,113,.2) inset, 0 16px 34px rgba(127,29,29,.18); }
.spot-card.review-required.final { border-color: rgba(252,165,165,.92); }
.review-required-badge { width: fit-content; margin: 9px 0 4px; border: 1px solid rgba(254,202,202,.46); border-radius: 999px; background: rgba(127,29,29,.72); color: #fee2e2; padding: 6px 10px; font-size: .76rem; font-weight: 900; letter-spacing: .02em; }
.plan-keyword-list { display: grid; gap: 8px; margin: 10px 0; }
.plan-keyword-item { display: grid; gap: 4px; padding: 10px 12px; border: 1px solid rgba(148,163,184,.26); border-radius: 10px; background: rgba(15,23,42,.42); }
.plan-keyword-item small { color: #94a3b8; overflow-wrap: anywhere; }
.plan-review-warning { padding: 10px 12px; border: 1px solid rgba(248,113,113,.55); border-radius: 10px; background: rgba(127,29,29,.42); color: #fecaca; font-weight: 800; }
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
.draft-section-help { margin: 4px 0 10px; color: #cbd5e1; font-size: .84rem; line-height: 1.5; }
.draft-mission-list { display: grid; gap: 10px; margin-top: 10px; }
.draft-mission-card { border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(15,23,42,.5); padding: 12px; }
.draft-mission-card.final { border-color: rgba(248,113,113,.55); }
.draft-card-summary { cursor: pointer; display: flex; align-items: center; justify-content: space-between; gap: 10px; list-style: none; }
.draft-card-summary::-webkit-details-marker { display: none; }
.draft-mission-tags { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0 10px; }
.draft-mission-tags span { border: 1px solid rgba(148,163,184,.2); border-radius: 999px; padding: 4px 8px; color: #dbeafe; background: rgba(30,41,59,.7); font-size: .75rem; font-weight: 800; }
.draft-card-summary span { display: grid; gap: 4px; min-width: 0; }
.draft-card-summary strong { color: #fff7ed; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.draft-card-summary small { color: #93c5fd; font-weight: 800; font-size: .76rem; }
.draft-card-summary em { flex: 0 0 auto; border: 1px solid rgba(245,158,11,.34); border-radius: 999px; padding: 5px 8px; color: #fde68a; font-style: normal; font-size: .72rem; font-weight: 900; }
.draft-card-preview { margin: 10px 0 12px; padding: 9px 10px; border-radius: 10px; background: rgba(2,6,23,.4); color: #e2e8f0; font-size: .84rem; line-height: 1.5; }
.draft-mini-card { border: 1px solid rgba(148,163,184,.18); border-radius: 14px; background: rgba(15,23,42,.45); padding: 10px; }
.draft-mini-card[open] { border-color: rgba(245,158,11,.35); }
.evidence-preview-box { display: grid; gap: 8px; }
.evidence-preview-box small, .image-url-edit summary { color: #cbd5e1; font-size: .78rem; line-height: 1.45; }
.image-url-edit { margin-top: 4px; }
.hint-edit-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 10px; }
.hint-edit-list label, .draft-edit-block .mini-grid label { display: grid; gap: 6px; color: #cbd5e1; font-size: .8rem; font-weight: 800; }
.draft-evidence-image { width: 100%; aspect-ratio: 3 / 2; object-fit: cover; border-radius: 12px; border: 1px solid rgba(245,158,11,.25); background: rgba(2,6,23,.5); }
.draft-suspect-image { width: min(100%, 180px); aspect-ratio: 3 / 4; object-fit: cover; border-radius: 12px; border: 1px solid rgba(245,158,11,.25); background: rgba(2,6,23,.5); }
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
.audit-panel { margin-top: 18px; padding: 14px; border: 1px solid rgba(56,189,248,.28); border-radius: 16px; background: linear-gradient(145deg, rgba(8,47,73,.25), rgba(2,6,23,.48)); }
.audit-panel .section-title { align-items: flex-start; }
.audit-help { margin: 4px 0 12px; color: #bae6fd; font-size: .82rem; line-height: 1.5; }
.audit-list { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }
.audit-list li { position: relative; display: grid; grid-template-columns: 18px minmax(0, 1fr); gap: 10px; padding: 10px 0; }
.audit-list li:not(:last-child)::after { content: ''; position: absolute; left: 6px; top: 27px; bottom: -3px; width: 1px; background: rgba(148,163,184,.24); }
.audit-marker { position: relative; z-index: 1; width: 11px; height: 11px; margin-top: 5px; border: 2px solid #0f172a; border-radius: 50%; background: #38bdf8; box-shadow: 0 0 0 2px rgba(56,189,248,.24); }
.audit-marker.create { background: #22c55e; box-shadow: 0 0 0 2px rgba(34,197,94,.24); }
.audit-marker.publish { background: #f59e0b; box-shadow: 0 0 0 2px rgba(245,158,11,.28); }
.audit-marker.delete { background: #ef4444; box-shadow: 0 0 0 2px rgba(239,68,68,.24); }
.audit-title { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.audit-title strong { color: #f8fafc; }
.audit-title time { color: #94a3b8; font-size: .74rem; white-space: nowrap; }
.audit-list p { margin: 4px 0; color: #cbd5e1; font-size: .84rem; }
.audit-list small { display: block; color: #7dd3fc; font: .72rem ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }
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
.candidate-actions { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.ghost-btn.mini { min-height: 32px; padding: 0 9px; font-size: .76rem; }
.selection-summary { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin: 10px 0; padding: 10px 12px; border: 1px solid rgba(148,163,184,.18); border-radius: 12px; background: rgba(15,23,42,.55); }
.selection-summary strong { color: #fff7ed; }
.selection-summary span { color: #fecaca; font-size: .82rem; font-weight: 900; }
.selection-summary span.ready { color: #86efac; }
.selected-route { margin-top: 12px; padding: 12px; border: 1px solid rgba(59,130,246,.26); border-radius: 14px; background: rgba(30,64,175,.13); }
.selected-route h4 { margin: 0 0 8px; color: #bfdbfe; }
.selected-route .route-summary { margin: 0 0 10px; color: #fde68a; font-weight: 800; }
.selected-route ol { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
.selected-route li { display: grid; grid-template-columns: 28px 1fr auto; align-items: center; gap: 8px; padding: 8px; border-radius: 10px; background: rgba(2,6,23,.34); }
.selected-route b { display: grid; place-content: center; width: 24px; height: 24px; border-radius: 999px; background: rgba(148,163,184,.2); color: #e2e8f0; }
.selected-route li > span { border-radius: 999px; padding: 4px 7px; font-size: .72rem; font-weight: 900; }
.selected-route .START { color: #93c5fd; background: rgba(37,99,235,.16); }
.selected-route .ANSWER_HINT { color: #fdba74; background: rgba(234,88,12,.16); }
.selected-route .DESTINATION_HINT { color: #d8b4fe; background: rgba(126,34,206,.16); }

.selected-route .FINAL { color: #fecaca; background: rgba(127,29,29,.22); }
.selected-route em { grid-column: 2 / -1; color: #fecaca; font-size: .75rem; font-style: normal; font-weight: 900; }
.selected-route p { margin: 10px 0 0; color: #cbd5e1; font-size: .82rem; }
.draft-actions-helper { display: flex; justify-content: space-between; gap: 10px; margin: 14px 0 8px; padding: 10px 12px; border-radius: 12px; background: rgba(245,158,11,.1); border: 1px solid rgba(245,158,11,.22); }
.draft-actions-helper strong { color: #fde68a; }
.draft-actions-helper span { color: #cbd5e1; font-size: .84rem; line-height: 1.45; }
.reward { opacity: .82; }
@media (max-width: 860px) { .admin-hero, .layout { display: block; } .episode-list { position: static; max-height: 420px; } .hero-actions { margin-top: 12px; } .detail-panel { margin-top: 14px; } .admin-section-tabs, .stat-grid, .mini-grid, .edit-grid, .candidate-grid, .manual-grid, .hint-edit-list, .creation-flow, .preview-grid, .ai-mode-grid, .draft-step-list { grid-template-columns: 1fr; } .selection-summary, .draft-actions-helper { display: grid; } .selected-route li { grid-template-columns: 24px 1fr; } .selected-route li > span { grid-column: 2; justify-self: start; } }
</style>


