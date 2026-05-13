<template>
  <div class="tactical-fullscreen">
    <!-- 모바일 현장 사용을 우선한 전술 단말 형태의 지도 화면입니다. -->
    <div class="device-frame">
      <header class="device-header">
        <div class="status-lights">
          <span class="light red" :class="{ blink: !isArrived }"></span>
          <span class="light green" :class="{ blink: isArrived }"></span>
        </div>
        <h2>📍 작전 구역: {{ regionName }}</h2>
        <div class="battery">BAT 87%</div>
      </header>

      <div class="screen-container">
        <div class="screen-overlay scanline"></div>
        <div id="map" class="map-view" ref="mapContainer"></div>
        <div v-if="mapLoadFailed" class="map-error-state">
          <strong>MAP LINK FAILED</strong>
          <span>{{ mapLoadMessage }}</span>
        </div>

        <button class="hint-collection-btn" @click="showHintModal = true">
          💡 획득한 단서 {{ collectedHints }} / {{ requiredHints }}
        </button>

        <Transition name="hint-reveal">
          <div
            v-if="hintReveal"
            class="hint-reveal-card"
            :class="[hintReveal.status, { folding: hintReveal.folding }]"
            @click="foldHintRevealNow"
          >
            <p class="hint-reveal-kicker">
              {{ hintReveal.status === 'error' ? '분석 실패' : '단서 해금' }}
            </p>
            <strong>{{ hintReveal.title }}</strong>
            <p class="hint-reveal-text">{{ hintReveal.message }}</p>
          </div>
        </Transition>

        <div v-if="collectedHints >= 1" class="coord-overlay top-right" :class="{ 'final-dist-blink': isArrived }">
          최종 TGT DIST: {{ finalDistance }}
        </div>

        <div class="floating-chat-btn" @click="goToChat">
          <div class="chat-icon">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
              <path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM9 11H7V9h2v2zm4 0h-2V9h2v2zm4 0h-2V9h2v2z"/>
            </svg>
          </div>
        </div>
      </div>

      <div class="control-panel">
        <div class="info-screen">
          <p class="tgt-text">TGT: {{ currentTargetName }}</p>
          <p class="distance">DIST: {{ isArrived ? '0' : targetDistance }}m</p>
          <div v-if="currentMission" class="arrival-meter" :class="{ ready: isArrived }">
            <span>LOCK RANGE {{ currentArrivalRadius }}m</span>
            <span>{{ arrivalProgressText }}</span>
          </div>
          <p class="status-text" :class="{ 'ready blink-fast': isArrived }">
            {{ isArrived ? '> SIGNAL_LOCKED: 현장 도착 완료!' : '> 이동 중 (TRACKING...)' }}
          </p>
        </div>

        <button v-if="!isArrived && isAdmin" @click="forceArrival" class="override-btn">
          [ MANUAL_OVERRIDE : 강제 도착 ]
        </button>

        <div v-if="isArrived && !getIsFinal(currentMission)" class="target-guide">
          📸 촬영 목표: <span class="highlight">{{ currentMission?.visionKeyword }}</span>
        </div>
        <button v-if="isArrived && !getIsFinal(currentMission)" @click="isScannerOpen = true" class="capture-btn">
          [ 스캐너 가동 ]
        </button>

        <div v-if="isArrived && getIsFinal(currentMission)" class="target-guide">
          최종 현장 단서: <span class="highlight">{{ currentMission?.visionKeyword || '현장 표식' }}</span>
          <br>
          <span class="field-clue-text">{{ currentMission?.fieldClue || '마지막 표식은 이름을 감추고 연도와 인물의 그림자만 남긴다. 닫힌 사건의 방향이 한쪽으로 기울어 있다.' }}</span>
        </div>
        <button v-if="isArrived && getIsFinal(currentMission)" @click="goToChat" class="capture-btn final-btn">
          [ 최종 분석 채널 접속 ]
        </button>

        <button
          v-if="currentMission && getIsFinal(currentMission) && getIsUnlocked(currentMission)"
          @click="drawTmapRoute(currentMission)"
          class="tmap-nav-btn"
        >
          🗺️ 작전지 경로 재탐색
        </button>
      </div>
    </div>

    <div v-if="isScannerOpen" class="scanner-modal">
      <CameraScanner @capture="uploadImage" @close="isScannerOpen = false" />
      <button @click="isScannerOpen = false" class="abort-btn">ABORT_SCAN</button>
    </div>

    <div v-if="showHintModal" class="hint-modal-overlay" @click="showHintModal = false">
      <div class="hint-modal-content" @click.stop>
        <h3>🔍 분석 완료된 단서 목록</h3>
        <ul v-if="clearedMissions.length > 0">
          <li v-for="m in clearedMissions" :key="m.id">
            📍 {{ m.title }} <br>
            <span class="highlight clue-text">[단서]: {{ m.clue }}</span>
          </li>
        </ul>
        <p v-else class="no-hints">아직 획득한 단서가 없습니다.</p>
        <button class="close-btn" @click="showHintModal = false">닫기</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/sessionStore';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();
const mapContainer = ref(null);

const sessionStore = useSessionStore();
const isAdmin = computed(() => sessionStore.userInfo?.isAdmin || false);
const userId = computed(() => sessionStore.userId);

// 지도 상단/하단 HUD와 현재 선택 미션 상태입니다.
const regionName = ref('조회 중...');
const isArrived = ref(false);
const currentTargetName = ref('타겟 미지정 (마커를 선택하세요)');
const targetDistance = ref(0);
const showHintModal = ref(false);
const isScannerOpen = ref(false);
const collectedHints = ref(0);
const requiredHints = ref(3);
const hintReveal = ref(null);

const currentMission = ref(null);
const clearedMissions = ref([]);

const currentLat = ref(null);
const currentLng = ref(null);
const mapLoadFailed = ref(false);
const mapLoadMessage = ref('지도 통신망 연결 중...');

const regionId = route.query.regionId || 1;
const missions = ref([]);
let map = null;
let userMarker = null;
let gpsWatcherId = null;
let markerOverlays = [];
let activeTooltipOverlay = null;
let hintFoldTimer = null;
let hintDismissTimer = null;

// 최종 미션 해금 후 Tmap 경로를 한 번만 자동 실행하기 위한 상태입니다.
let polylineOverlay = null;
const isNavLaunched = ref(false);

// 단서 획득 카드의 접힘/사라짐 타이머를 정리합니다.
const clearHintRevealTimers = () => {
  if (hintFoldTimer) {
    clearTimeout(hintFoldTimer);
    hintFoldTimer = null;
  }
  if (hintDismissTimer) {
    clearTimeout(hintDismissTimer);
    hintDismissTimer = null;
  }
};

// Vision 인증 결과를 지도 중앙에 표시한 뒤 단서 버튼 쪽으로 접어 사라지게 합니다.
const showHintReveal = ({ status = 'success', title = '현장 단서', message = '' }) => {
  clearHintRevealTimers();
  hintReveal.value = {
    status,
    title,
    message: message || (status === 'error' ? '다시 시도하십시오.' : '단서가 해금되었습니다.'),
    folding: false
  };

  const foldDelay = status === 'error' ? 2400 : 3600;
  const dismissDelay = status === 'error' ? 3300 : 4700;

  hintFoldTimer = setTimeout(() => {
    if (hintReveal.value) {
      hintReveal.value = { ...hintReveal.value, folding: true };
    }
  }, foldDelay);

  hintDismissTimer = setTimeout(() => {
    hintReveal.value = null;
    clearHintRevealTimers();
  }, dismissDelay);
};

// 사용자가 단서 카드를 누르면 기다리지 않고 즉시 접히게 합니다.
const foldHintRevealNow = () => {
  if (!hintReveal.value) return;

  clearHintRevealTimers();
  hintReveal.value = { ...hintReveal.value, folding: true };
  hintDismissTimer = setTimeout(() => {
    hintReveal.value = null;
    clearHintRevealTimers();
  }, 760);
};

// 프론트 실시간 거리 표시와 이동 거리 기록에 사용하는 하버사인 거리 계산입니다.
const calculateDistance = (lat1, lon1, lat2, lon2) => {
  if (!lat1 || !lon1 || !lat2 || !lon2) return 0;
  const R = 6371e3;
  const p1 = lat1 * Math.PI / 180;
  const p2 = lat2 * Math.PI / 180;
  const dp = (lat2 - lat1) * Math.PI / 180;
  const dl = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.floor(R * c);
};

// 작전별 시간/이동거리 기록은 userId+regionId 기준 localStorage에 저장합니다.
const getMetricKey = () => `operation-seoul:mission-metrics:${userId.value || 1}:${regionId}`;

// 채팅에서 점수 계산 payload로 보내기 위해 누적 이동 기록을 읽습니다.
const readMissionMetrics = () => {
  try {
    const saved = localStorage.getItem(getMetricKey());
    if (saved) return JSON.parse(saved);
  } catch (error) {
    console.warn('Mission metric read failed:', error);
  }
  return {
    startedAt: Date.now(),
    routeDistanceMeters: 0,
    lastLat: null,
    lastLng: null
  };
};

// GPS watcher가 갱신한 작전 진행 metric을 저장합니다.
const writeMissionMetrics = (metrics) => {
  localStorage.setItem(getMetricKey(), JSON.stringify(metrics));
};

// GPS 좌표가 정상적으로 이동한 경우에만 누적 이동 거리에 더합니다.
const recordRoutePosition = (lat, lng) => {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

  const metrics = readMissionMetrics();
  if (!metrics.startedAt) {
    metrics.startedAt = Date.now();
  }

  if (Number.isFinite(metrics.lastLat) && Number.isFinite(metrics.lastLng)) {
    const delta = calculateDistance(metrics.lastLat, metrics.lastLng, lat, lng);
    if (delta >= 3 && delta <= 250) {
      metrics.routeDistanceMeters = Math.round((Number(metrics.routeDistanceMeters) || 0) + delta);
    }
  }

  metrics.lastLat = lat;
  metrics.lastLng = lng;
  metrics.updatedAt = Date.now();
  writeMissionMetrics(metrics);
};

// 백엔드/프론트 통신에서 isFinal/final 필드명이 달라져도 같은 의미로 처리하는 helper입니다.
const getIsFinal = (m) => m && (m.missionType === 'FINAL' || m.isFinal === true || m.final === true);
const getIsUnlocked = (m) => m && (m.isUnlocked === true || m.unlocked === true);

const currentArrivalRadius = computed(() => getArrivalRadius(currentMission.value));
const remainingArrivalDistance = computed(() => Math.max(0, targetDistance.value - currentArrivalRadius.value));
const arrivalProgressText = computed(() => {
  if (!currentMission.value) return 'NO TARGET';
  if (isArrived.value) return 'SIGNAL LOCKED';
  return `REMAIN ${remainingArrivalDistance.value}m`;
});

// 힌트를 1개 이상 모은 뒤 최종 목적지까지의 거리를 HUD에 표시합니다.
const finalDistance = computed(() => {
  const fMission = missions.value.find(m => getIsFinal(m));

  if (!fMission || fMission.targetLat == null || currentLat.value === null || currentLng.value === null) {
    return '---';
  }
  return calculateDistance(currentLat.value, currentLng.value, fMission.targetLat, fMission.targetLng) + 'm';
});

// Kakao 지도 위에 Tmap 도보 경로 Polyline을 그립니다. 실패하면 직선 fallback을 표시합니다.
const drawTmapRoute = async (mission) => {
  if (!currentLat.value || !currentLng.value || !mission.targetLat || !mission.targetLng) return;

  if (polylineOverlay) {
    polylineOverlay.setMap(null);
    polylineOverlay = null;
  }

  try {
    const tmapAppKey = import.meta.env.VITE_TMAP_APP_KEY || '';
    const url = 'https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1&format=json';

    const payload = {
      startX: currentLng.value.toString(),
      startY: currentLat.value.toString(),
      endX: mission.targetLng.toString(),
      endY: mission.targetLat.toString(),
      reqCoordType: "WGS84GEO",
      resCoordType: "WGS84GEO",
      startName: "현재위치",
      endName: "작전지"
    };

    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'appKey': tmapAppKey
      },
      body: JSON.stringify(payload)
    });

    if (!response.ok) {
      throw new Error(`Tmap API 통신 에러: ${response.status}`);
    }
    const data = await response.json();

    const startPoint = new window.kakao.maps.LatLng(currentLat.value, currentLng.value);
    const endPoint = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);
    const linePath = [startPoint];
    data.features.forEach(feature => {
      if (feature.geometry.type === "LineString") {
        feature.geometry.coordinates.forEach(coord => {
          linePath.push(new window.kakao.maps.LatLng(coord[1], coord[0]));
        });
      }
    });
    linePath.push(endPoint);

    polylineOverlay = new window.kakao.maps.Polyline({
      path: linePath,
      strokeWeight: 7,
      strokeColor: '#f229b3',
      strokeOpacity: 0.6,
      strokeStyle: 'solid'
    });
    polylineOverlay.setMap(map);
    console.log("🚀 Tmap 도보 경로 탐색 완료");

  } catch (error) {
    console.error("🚨 Tmap 경로 통신 실패! 비상용 레이더(직선)를 그립니다.", error);
    const fallbackPath = [
      new window.kakao.maps.LatLng(currentLat.value, currentLng.value),
      new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng)
    ];

    polylineOverlay = new window.kakao.maps.Polyline({
      path: fallbackPath,
      strokeWeight: 7,
      strokeColor: '#ff003c',
      strokeOpacity: 0.6,
      strokeStyle: 'solid'
    });
    polylineOverlay.setMap(map);
    alert("Tmap 통신망에 간섭이 발생했습니다. 보조 레이더망(직선 경로)을 가동합니다.");
  }
};

// 힌트 3개로 최종 미션이 해금되면 경로를 자동으로 한 번 표시합니다.
const checkAndDrawNavigation = () => {
  const fMission = missions.value.find(m => getIsFinal(m));

  if (fMission && getIsUnlocked(fMission) && currentLat.value !== null && currentLng.value !== null && !isNavLaunched.value) {
    isNavLaunched.value = true;
    alert("🚨 모든 단서를 모았습니다! 최종 목적지의 위치가 해독되어 맵에 표시됩니다.");
    drawTmapRoute(fMission);
  }
};

// 마커 클릭 시 현재 목표를 바꾸고, 이미 도착 반경 안인지 즉시 계산합니다.
const handleMissionClick = (mission) => {
  if (activeTooltipOverlay) {
    activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
  }

  currentMission.value = mission;
  currentTargetName.value = mission.title;

  if (currentLat.value !== null && currentLng.value !== null) {
    targetDistance.value = calculateDistance(
      currentLat.value,
      currentLng.value,
      mission.targetLat,
      mission.targetLng
    );
    isArrived.value = targetDistance.value <= getArrivalRadius(mission);
  } else {
    targetDistance.value = 999;
    isArrived.value = false;
  }
};

// 미션별 radiusInMeters가 없으면 힌트 50m, 최종 30m 기본값을 사용합니다.
const getArrivalRadius = (mission) => {
  if (!mission) return 50;
  const radius = Number(mission.radiusInMeters ?? mission.radius ?? 0);
  if (radius > 0) return radius;
  return getIsFinal(mission) ? 30 : 50;
};

// 이미 클리어한 힌트 마커를 누르면 단서 tooltip을 보여줍니다.
const toggleTooltip = (mission, latLng) => {
  if (activeTooltipOverlay && activeTooltipOverlay.getTitle() === mission.id.toString()) {
    activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
    return;
  }
  if (activeTooltipOverlay) {
    activeTooltipOverlay.setMap(null);
  }

  const content = document.createElement('div');
  content.className = 'marker-tooltip';
  content.innerHTML = `
    <h4>${mission.title}</h4>
    <p>${mission.clue}</p>
  `;

  content.onclick = () => {
    if (activeTooltipOverlay) activeTooltipOverlay.setMap(null);
    activeTooltipOverlay = null;
  };

  activeTooltipOverlay = new window.kakao.maps.CustomOverlay({
    map: map,
    position: latLng,
    content: content,
    yAnchor: 2.2,
    zIndex: 10
  });

  activeTooltipOverlay.getTitle = () => mission.id.toString();
};

// 지역 내 미션 목록과 사용자 진행 상태를 불러와 지도 마커를 다시 그립니다.
const loadMissionsData = async () => {
  try {
    const misRes = await apiClient.get(`/v1/regions/${regionId}/missions`, {
      params: { userId: userId.value }
    });
    missions.value = misRes.data;

    markerOverlays.forEach(overlay => overlay.setMap(null));
    markerOverlays = [];

    if (activeTooltipOverlay) {
      activeTooltipOverlay.setMap(null);
      activeTooltipOverlay = null;
    }

    clearedMissions.value = missions.value.filter(m => m.sessionStatus === 'CLEARED');
    collectedHints.value = clearedMissions.value.length;

    missions.value.forEach((mission) => {
      const isFinalFlag = getIsFinal(mission);
      const isUnlockedFlag = getIsUnlocked(mission);

      // 최종 목적지인데 아직 해금 안 됐다면 맵에 핀을 그리지 않습니다.
      if (isFinalFlag && !isUnlockedFlag) return;

      const isCleared = mission.sessionStatus === 'CLEARED';
      const content = document.createElement('div');

      content.className = isCleared ? 'custom-marker cleared' : (isFinalFlag ? 'custom-marker final' : 'custom-marker');

      const position = new window.kakao.maps.LatLng(mission.targetLat, mission.targetLng);

      content.onclick = () => {
        if (isCleared) {
           toggleTooltip(mission, position);
        } else {
          handleMissionClick(mission);
        }
      };

      const customOverlay = new window.kakao.maps.CustomOverlay({
        map: map,
        position: position,
        content: content,
        yAnchor: 1.2,
        xAnchor: 0.5,
        zIndex: 2
      });

      markerOverlays.push(customOverlay);
    });

    // 맵 데이터 로드 시점에도 조건이 맞으면 네비게이션 작동
    checkAndDrawNavigation();
    await syncMapLayout();

  } catch (error) {
    console.error("미션 데이터 갱신 중 오류:", error);
  }
};

// 브라우저 GPS를 감시합니다. 권한이 없거나 지연되면 첫 미션 근처 fallback 좌표를 사용합니다.
const startGpsTracking = () => {
  const executeFakeGpsFallback = () => {
    let baseLat = 37.5665;
    let baseLng = 126.9780;

    if (currentMission.value && currentMission.value.targetLat) {
      baseLat = currentMission.value.targetLat;
      baseLng = currentMission.value.targetLng;
    } else if (missions.value.length > 0) {
      baseLat = missions.value[0].targetLat;
      baseLng = missions.value[0].targetLng;
    }

    const fakeLat = baseLat - 0.0003;
    const fakeLng = baseLng - 0.0003;
    const fakePosition = new window.kakao.maps.LatLng(fakeLat, fakeLng);

    currentLat.value = fakeLat;
    currentLng.value = fakeLng;
    recordRoutePosition(fakeLat, fakeLng);

    if (!userMarker) {
      const userContent = document.createElement('div');
      userContent.className = 'custom-marker user';

      userMarker = new window.kakao.maps.CustomOverlay({
        map: map,
        position: fakePosition,
        content: userContent,
        yAnchor: 0.5,
        xAnchor: 0.5,
        zIndex: 3
      });
    } else {
      userMarker.setPosition(fakePosition);
    }

    if (currentMission.value && currentMission.value.targetLat) {
      targetDistance.value = calculateDistance(fakeLat, fakeLng, currentMission.value.targetLat, currentMission.value.targetLng);
      isArrived.value = targetDistance.value <= getArrivalRadius(currentMission.value);
    }

    map.setCenter(fakePosition);
    syncMapLayout(fakePosition);
    checkAndDrawNavigation();
  };

  executeFakeGpsFallback();

  if (navigator.geolocation) {
    gpsWatcherId = navigator.geolocation.watchPosition((position) => {
      const lat = position.coords.latitude;
      const lng = position.coords.longitude;
      const locPosition = new window.kakao.maps.LatLng(lat, lng);

      currentLat.value = lat;
      currentLng.value = lng;
      recordRoutePosition(lat, lng);

      if (!userMarker) {
        const userContent = document.createElement('div');
        userContent.className = 'custom-marker user';

        userMarker = new window.kakao.maps.CustomOverlay({
          map: map,
          position: locPosition,
          content: userContent,
          yAnchor: 0.5,
          xAnchor: 0.5,
          zIndex: 3
        });
      } else {
        userMarker.setPosition(locPosition);
      }

      if (currentMission.value && currentMission.value.targetLat) {
        targetDistance.value = calculateDistance(lat, lng, currentMission.value.targetLat, currentMission.value.targetLng);
        isArrived.value = targetDistance.value <= getArrivalRadius(currentMission.value);
      }

      // 내 위치 갱신 후, 선이 그려져 있으면 업데이트하고 조건이 맞으면 새로 그립니다.
      const fMission = missions.value.find(m => getIsFinal(m));
      if (fMission && getIsUnlocked(fMission)) {
        if (polylineOverlay) {
          drawTmapRoute(fMission);
        } else {
          checkAndDrawNavigation();
        }
      }

    }, (error) => {
       console.log("GPS 통신 지연 혹은 권한 없음. Fallback 좌표를 계속 유지합니다.");
    }, { enableHighAccuracy: true, maximumAge: 0, timeout: 5000 });
  }
};

// index.html에서 Kakao Maps SDK가 로드될 때까지 짧게 대기합니다.
const waitForKakaoMapSdk = () => {
  return new Promise((resolve, reject) => {
    if (window.kakao?.maps?.Map && window.kakao?.maps?.LatLng) {
      resolve(window.kakao.maps);
      return;
    }

    let retries = 0;
    const timer = window.setInterval(() => {
      if (window.kakao?.maps?.Map && window.kakao?.maps?.LatLng) {
        window.clearInterval(timer);
        resolve(window.kakao.maps);
        return;
      }

      retries += 1;
      if (retries >= 80) {
        window.clearInterval(timer);
        reject(new Error('Kakao Maps SDK가 index.html에서 로드되지 않았습니다.'));
      }
    }, 100);
  });
};

// Kakao 지도는 컨테이너 크기가 변한 뒤 relayout을 호출해야 정상 표시됩니다.
const syncMapLayout = async (center = null) => {
  if (!map) return;

  await nextTick();
  const nextCenter = center || map.getCenter();
  await new Promise(resolve => {
    window.requestAnimationFrame(() => {
      if (!map) {
        resolve();
        return;
      }
      map.relayout();
      if (nextCenter) {
        map.setCenter(nextCenter);
      }
      resolve();
    });
  });
};

// Kakao 지도 인스턴스를 만들고 Region/Mission 데이터를 불러온 뒤 GPS 추적을 시작합니다.
const initializeMap = async () => {
  if (!mapContainer.value) {
    throw new Error('지도 컨테이너를 찾을 수 없습니다.');
  }

  const options = { center: new window.kakao.maps.LatLng(37.5665, 126.9780), level: 4 };
  map = new window.kakao.maps.Map(mapContainer.value, options);
  map.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID);
  await syncMapLayout(options.center);

  try {
    const regRes = await apiClient.get(`/v1/regions/${regionId}`);
    regionName.value = regRes.data.name;

    await loadMissionsData();

    if (missions.value.length > 0) {
      const firstMissionPosition = new window.kakao.maps.LatLng(missions.value[0].targetLat, missions.value[0].targetLng);
      map.setCenter(firstMissionPosition);
      await syncMapLayout(firstMissionPosition);
    }
    startGpsTracking();
  } catch (error) {
    console.error('지도 데이터 수신 실패:', error);
    currentTargetName.value = '데이터 수신 실패';
  }
};

onMounted(async () => {
  try {
    await waitForKakaoMapSdk();
    await initializeMap();
  } catch (error) {
    console.error('Kakao 지도 초기화 실패:', error);
    mapLoadFailed.value = true;
    mapLoadMessage.value = error.message || 'Kakao 지도 초기화에 실패했습니다.';
    currentTargetName.value = '지도 로딩 실패';
  }
});

onUnmounted(() => {
  if (gpsWatcherId && navigator.geolocation) {
    navigator.geolocation.clearWatch(gpsWatcherId);
  }
  clearHintRevealTimers();
});

const goToChat = () => {
  if (currentMission.value) {
    router.push({ name: 'Chat', params: { sessionId: currentMission.value.id }, query: { regionId } });
  } else {
    alert("먼저 지도에서 작전을 수행할 마커를 선택해 주십시오.");
  }
};

// 관리자 테스트 편의를 위해 실제 GPS 도착 판정 없이 도착 상태로 바꿉니다.
const forceArrival = () => {
  if(!currentMission.value) return;
  isArrived.value = true;
};

// CameraScanner의 data URL을 File로 변환한 뒤 Vision 인증 API에 업로드합니다.
const uploadImage = async (imageFile) => {
  if (!currentMission.value?.id) {
    showHintReveal({
      status: 'error',
      title: '대상 미지정',
      message: '먼저 지도에서 분석할 현장 마커를 선택하십시오.'
    });
    return;
  }

  let finalFile = imageFile;

  if (typeof imageFile === 'string' && imageFile.startsWith('data:image')) {
    try {
      const arr = imageFile.split(',');
      const mime = arr[0].match(/:(.*?);/)[1];
      const bstr = atob(arr[1]);
      let n = bstr.length;
      const u8arr = new Uint8Array(n);
      while (n--) { u8arr[n] = bstr.charCodeAt(n); }
      finalFile = new File([u8arr], 'capture.png', { type: mime });
    } catch (e) {
      isScannerOpen.value = false;
      await nextTick();
      showHintReveal({
        status: 'error',
        title: '이미지 오류',
        message: '촬영 데이터를 분석 파일로 변환하지 못했습니다.'
      });
      return;
    }
  }

  if (!finalFile || !(finalFile instanceof File)) {
    isScannerOpen.value = false;
    await nextTick();
    showHintReveal({
      status: 'error',
      title: '이미지 오류',
      message: '분석할 수 있는 촬영 파일이 없습니다.'
    });
    return;
  }

  try {
    const completedMissionId = currentMission.value.id;
    const completedMissionTitle = currentMission.value.title || '현장 단서';
    const formData = new FormData();
    formData.append('image', finalFile);
    formData.append('userId', userId.value);

    if(isAdmin.value) {
        formData.append('isAdmin', 'true');
    }

    const response = await apiClient.post(`/v1/sessions/${currentMission.value.id}/vision`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    if (response.data.success) {
      await loadMissionsData();
      const acquiredMission = missions.value.find(m => String(m.id) === String(completedMissionId));
      showHintReveal({
        status: 'success',
        title: acquiredMission?.title || completedMissionTitle,
        message: acquiredMission?.clue || acquiredMission?.description || response.data?.message || '단서가 해금되었습니다.'
      });
      currentMission.value = null;
      currentTargetName.value = '타겟 미지정 (마커를 선택하세요)';
      isArrived.value = false;
      targetDistance.value = 0;
    } else {
      showHintReveal({
        status: 'error',
        title: '분석 실패',
        message: response.data?.message || '목표물을 정확히 프레임에 담아주십시오.'
      });
    }
  } catch (error) {
    showHintReveal({
      status: 'error',
      title: '통신 오류',
      message: '본부와의 통신 연결이 원활하지 않습니다. 잠시 후 다시 시도하십시오.'
    });
  } finally {
    isScannerOpen.value = false;
  }
};
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono&display=swap');

.tactical-fullscreen { width: 100vw; height: 100vh; background: #050505; display: flex; justify-content: center; align-items: center; font-family: 'Share Tech Mono', monospace; color: #00ffcc; padding: 10px; box-sizing: border-box; overflow: hidden; }
.device-frame { width: 100%; height: 100%; max-width: 600px; background: #111; border: 2px solid #333; border-radius: 12px; box-shadow: 0 0 20px rgba(0, 0, 0, 0.8); padding: 15px; box-sizing: border-box; display: flex; flex-direction: column; gap: 10px; position: relative; }
.device-header { display: flex; justify-content: space-between; align-items: center; }
.status-lights { display: flex; gap: 5px; }
.light { width: 8px; height: 8px; border-radius: 50%; }
.light.red { background: #ff4444; box-shadow: 0 0 5px #ff4444; }
.light.green { background: #00ffcc; }

.blink { animation: blinker 2s linear infinite; }
@keyframes blinker { 50% { opacity: 0.3; } }

.device-header h2 { margin: 0; font-size: 1rem; color: #aaa; }
.battery { font-size: 0.8rem; color: #aaa; border: 1px solid #aaa; padding: 2px 4px; border-radius: 3px; }

.screen-container { flex: 1; position: relative; width: 100%; border: 2px solid #00ffcc; border-radius: 8px; overflow: hidden; }
.map-view { width: 100%; height: 100%; }
.map-error-state {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  background: rgba(0, 0, 0, 0.86);
  color: #ff4444;
  text-align: center;
  font-weight: 700;
}
.map-error-state strong {
  color: #ff4444;
  font-size: 1rem;
}
.map-error-state span {
  color: #fca5a5;
  font-size: 0.82rem;
  line-height: 1.5;
}
.screen-overlay { position: absolute; inset: 0; pointer-events: none; z-index: 10; }
.scanline { background: linear-gradient(rgba(0, 255, 204, 0.05) 50%, rgba(0, 0, 0, 0.1) 50%); background-size: 100% 4px; }

.hint-collection-btn {
  position: absolute; top: 15px; left: 15px;
  padding: 10px 16px; font-size: 0.9rem; font-weight: bold;
  background-color: rgba(0, 20, 30, 0.85); color: #00ffcc;
  border: 2px solid #00ffcc; border-radius: 6px;
  z-index: 1000; cursor: pointer;
  box-shadow: 0 0 10px rgba(0, 255, 204, 0.4);
  font-family: inherit; transition: all 0.2s ease;
}
.hint-collection-btn:hover { background-color: #00ffcc; color: #000; }

.hint-reveal-card {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 1002;
  width: min(82%, 430px);
  max-height: 58%;
  overflow: hidden;
  padding: 18px 20px;
  transform: translate(-50%, -50%) scale(1);
  transform-origin: top left;
  background: rgba(3, 12, 18, 0.94);
  border: 2px solid #00ffcc;
  border-radius: 8px;
  color: #dffef8;
  box-shadow: 0 0 22px rgba(0, 255, 204, 0.45), inset 0 0 18px rgba(0, 255, 204, 0.08);
  pointer-events: auto;
  cursor: pointer;
  transition: top 0.75s ease, left 0.75s ease, transform 0.75s ease, opacity 0.75s ease;
}
.hint-reveal-card.error {
  border-color: #ff4444;
  box-shadow: 0 0 22px rgba(255, 68, 68, 0.35), inset 0 0 18px rgba(255, 68, 68, 0.08);
}
.hint-reveal-card.folding {
  top: 20px;
  left: 22px;
  transform: translate(0, 0) scale(0.16);
  opacity: 0;
}
.hint-reveal-kicker {
  margin: 0 0 8px;
  color: #ffaa00;
  font-size: 0.74rem;
  font-weight: bold;
  letter-spacing: 0;
}
.hint-reveal-card.error .hint-reveal-kicker { color: #ff6b6b; }
.hint-reveal-card strong {
  display: block;
  color: #fff;
  font-size: 1rem;
  line-height: 1.35;
  margin-bottom: 10px;
}
.hint-reveal-text {
  margin: 0;
  color: #c7fff4;
  font-size: 0.88rem;
  line-height: 1.62;
  white-space: pre-line;
}
.hint-reveal-card.error .hint-reveal-text { color: #ffd6d6; }
.hint-reveal-enter-active,
.hint-reveal-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.hint-reveal-enter-from,
.hint-reveal-leave-to {
  opacity: 0;
  transform: translate(-50%, -46%) scale(0.96);
}

.coord-overlay { position: absolute; background: rgba(0,0,0,0.8); padding: 5px 10px; font-size: 0.8rem; z-index: 11; font-weight: bold; }
.top-right { top: 15px; right: 15px; border-right: 2px solid #00ffcc; }

.final-dist-blink { color: #00ffcc; text-shadow: 0 0 10px #00ffcc; animation: blinker-fast 0.8s linear infinite; }
.blink-fast { animation: blinker-fast 0.8s linear infinite; }
@keyframes blinker-fast { 50% { opacity: 0; } }

.control-panel { background: #0a0a0a; border: 1px solid #222; padding: 15px; border-radius: 8px; text-align: center; }

.tgt-text { margin: 0 0 5px 0; color: #00ffcc; font-size: 1rem; }
.distance { margin: 0 0 5px 0; font-size: 1.4rem; font-weight: bold; color: #fff; }
.arrival-meter {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin: 0 0 8px 0;
  padding: 6px 8px;
  border: 1px solid rgba(0, 255, 204, 0.25);
  border-radius: 4px;
  color: #8fa3a3;
  font-size: 0.72rem;
  letter-spacing: 0;
}
.arrival-meter.ready {
  border-color: rgba(0, 255, 204, 0.7);
  color: #00ffcc;
  box-shadow: 0 0 8px rgba(0, 255, 204, 0.18);
}
.status-text { margin: 0 0 10px 0; color: #ffaa00; font-size: 0.9rem; }
.status-text.ready { color: #00ffcc; font-weight: bold; }

.target-guide { margin-bottom: 8px; font-size: 0.9rem; color: #bbb; }
.target-guide .highlight { color: #ffaa00; font-weight: bold; }
.field-clue-text { display: inline-block; margin-top: 6px; color: #dbeafe; line-height: 1.5; }
.clue-text { white-space: pre-line; line-height: 1.62; }

.override-btn { width: 100%; padding: 15px; background: transparent; color: #ffaa00; border: 1px solid #ffaa00; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; }
.override-btn:hover { background: rgba(255, 170, 0, 0.2); }

.capture-btn { width: 100%; padding: 12px; background: rgba(0, 255, 204, 0.1); color: #00ffcc; border: 1px solid #00ffcc; font-family: inherit; font-weight: bold; border-radius: 4px; cursor: pointer; text-align: center; box-sizing: border-box; }
.capture-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 15px #00ffcc; }
.final-btn { border-color: #ff4444 !important; color: #ff4444 !important; background: rgba(255, 68, 68, 0.1) !important; margin-top: 5px; }
.final-btn:hover { background: #ff4444 !important; color: #fff !important; box-shadow: 0 0 15px #ff4444 !important; }

.scanner-modal { position: fixed; inset: 0; z-index: 1000; background: #000; }
.abort-btn { position: absolute; top: 20px; right: 20px; background: transparent; border: 1px solid #ff4444; color: #ff4444; padding: 8px 15px; z-index: 1001; cursor: pointer; font-family: inherit; font-weight: bold; }

.hint-modal-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(0, 0, 0, 0.8); display: flex; justify-content: center; align-items: center; z-index: 2000;
}
.hint-modal-content {
  background: #111; border: 2px solid #00ffcc; padding: 25px;
  border-radius: 12px; width: 85%; max-width: 400px; color: white;
  box-shadow: 0 0 20px rgba(0, 255, 204, 0.3);
}
.hint-modal-content h3 { color: #00ffcc; margin-top: 0; margin-bottom: 20px; border-bottom: 1px dashed #00ffcc; padding-bottom: 10px;}
.hint-modal-content ul { list-style: none; padding: 0; margin: 0; line-height: 1.8; }
.hint-modal-content li { margin-bottom: 15px; background: rgba(0, 255, 204, 0.05); padding: 10px; border-radius: 6px; }
.no-hints { color: #aaa; text-align: center; margin: 20px 0; }
.close-btn { margin-top: 15px; width: 100%; padding: 12px; background: transparent; border: 1px solid #ff4444; color: #ff4444; font-family: inherit; font-weight: bold; border-radius: 8px; cursor: pointer; transition: 0.2s;}
.close-btn:hover { background: #ff4444; color: #fff; }

.floating-chat-btn {
  position: absolute; right: 15px; bottom: 15px;
  width: 55px; height: 55px; background: rgba(0, 40, 60, 0.85);
  border: 2px solid #00ffcc; border-radius: 50%;
  display: flex; justify-content: center; align-items: center;
  box-shadow: 0 0 10px rgba(0, 255, 204, 0.4);
  cursor: pointer; z-index: 99; transition: all 0.3s ease; color: #00ffcc;
}
.floating-chat-btn:hover { background: #00ffcc; color: #000; box-shadow: 0 0 20px rgba(0, 255, 204, 0.8); }

:deep(.custom-marker) { width: 24px; height: 24px; background-color: rgba(0, 255, 204, 0.8); border: 2px solid #000; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); transform-origin: center; box-shadow: 0 0 10px #00ffcc; cursor: pointer; position: relative; }
:deep(.custom-marker.cleared) { background-color: #8fa3a3; border: 2px solid #fff; box-shadow: 0 0 5px rgba(255, 255, 255, 0.5); opacity: 0.95; }
:deep(.custom-marker.final) { background-color: rgba(237, 45, 48, 0.8); box-shadow: 0 0 15px #ff4444; }

:deep(.custom-marker.user) {
  width: 16px;
  height: 16px;
  background-color: #ff007a;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 0 15px #ff007a;
  transform: none;
  position: relative;
  animation: pulse-gps 4s infinite;
}

:deep(.marker-tooltip) {
  background: rgba(10, 20, 30, 0.95);
  border: 1px solid #00ffcc;
  padding: 10px 14px;
  border-radius: 8px;
  color: white;
  text-align: center;
  cursor: pointer;
  min-width: 200px;
  max-width: 300px;
  white-space: normal;
  box-shadow: 0 4px 15px rgba(0, 255, 204, 0.5);
  position: relative;
  font-family: 'Share Tech Mono', monospace;
  bottom: -100px;
}

:deep(.marker-tooltip h4) {
  margin: 0 0 6px 0;
  color: #00ffcc;
  font-size: 0.9rem;
  border-bottom: 1px solid rgba(0, 255, 204, 0.3);
  padding-bottom: 4px;
}

:deep(.marker-tooltip p) {
  margin: 0;
  font-size: 0.8rem;
  color: #f0f0f0;
  line-height: 1.3;
}

:deep(.marker-tooltip::after) {
  content: '';
  position: absolute;
  bottom: -8px;
  left: 46%;
  transform: translateX(-50%);
  border-width: 8px 8px 0;
  border-style: solid;
  border-color: rgba(10, 20, 30, 0.95) transparent transparent transparent;
  filter: drop-shadow(0 2px 2px rgba(0,255,204,0.4));
}

.tmap-nav-btn {
  width: 100%;
  padding: 12px;
  margin-top: 10px;
  background-color: rgba(0, 50, 40, 0.8);
  border: 1px solid #00ffcc;
  color: #00ffcc;
  font-weight: bold;
  font-family: inherit;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 0 5px rgba(0, 255, 204, 0.3);
}

.tmap-nav-btn:hover {
  background-color: #00ffcc;
  color: #000;
  box-shadow: 0 0 15px rgba(0, 255, 204, 0.6);
}
</style>
