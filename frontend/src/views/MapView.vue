import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import apiClient from '@/api/axiosInstance';
import CameraScanner from '@/components/CameraScanner.vue';

const route = useRoute();
const router = useRouter();
const mapContainer = ref(null);

const regionName = ref('조회 중...');
const isArrived = ref(false); 
const currentTargetName = ref('통신 연결 중...'); 
const targetDistance = ref(0); 
const finalDistance = ref(999); 
const showHintModal = ref(false); 
const isScannerOpen = ref(false); 
const collectedHints = ref(0); 
const requiredHints = ref(3); 
const currentMission = ref(null);
const clearedMissions = ref([]); 

const regionId = route.query.regionId || 1;
const missions = ref([]);
let map = null;
let userMarker = null; 
let activeInfoOverlay = null; 
let gpsWatcherId = null;

const calculateDistance = (lat1, lon1, lat2, lon2) => {
  const R = 6371e3; 
  const p1 = lat1 * Math.PI / 180;
  const p2 = lat2 * Math.PI / 180;
  const dp = (lat2 - lat1) * Math.PI / 180;
  const dl = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Math.floor(R * c);
};

// 📍 내 위치 업데이트 공통 함수
const updateUserLocation = (lat, lng) => {
  const locPosition = new window.kakao.maps.LatLng(lat, lng);

  if (!userMarker) {
    const userContent = document.createElement('div');
    userContent.className = 'custom-marker user';
    userMarker = new window.kakao.maps.CustomOverlay({
      map: map, position: locPosition, content: userContent, yAnchor: 0.5, xAnchor: 0.5, zIndex: 3
    });
  } else {
    userMarker.setPosition(locPosition);
  }

  if (currentMission.value && currentMission.value.targetLat) {
    targetDistance.value = calculateDistance(lat, lng, currentMission.value.targetLat, currentMission.value.targetLng);
    isArrived.value = targetDistance.value <= 50;
  }
};

// 📍 GPS 추적 및 거부 시 비상 프로토콜 가동
const startGpsTracking = () => {
  if (navigator.geolocation) {
    gpsWatcherId = navigator.geolocation.watchPosition(
      (position) => {
        updateUserLocation(position.coords.latitude, position.coords.longitude);
      }, 
      (error) => {
        console.warn("🚨 GPS 권한 거부됨. 비상 프로토콜: 최종 목적지 인근으로 좌표 강제 전송.");
        // GPS 거부 시 최종 목적지 또는 현재 미션 좌표 기반으로 임의 낙하
        const target = missions.value.find(m => m.isFinal) || currentMission.value;
        if (target && target.targetLat) {
          // 목적지 기준 반경 20~30m 내외로 임의 좌표 생성
          const fallbackLat = target.targetLat + (Math.random() - 0.5) * 0.0003;
          const fallbackLng = target.targetLng + (Math.random() - 0.5) * 0.0003;
          updateUserLocation(fallbackLat, fallbackLng);
          
          // 지도 중심도 강제 이동
          map.setCenter(new window.kakao.maps.LatLng(fallbackLat, fallbackLng));
        }
      }, 
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }
};

onMounted(() => {
  if (!window.kakao || !window.kakao.maps) return;

  window.kakao.maps.load(async () => {
    const options = { center: new window.kakao.maps.LatLng(37.5665, 126.9780), level: 4 };
    map = new window.kakao.maps.Map(mapContainer.value, options);
    map.setMapTypeId(window.kakao.maps.MapTypeId.HYBRID); 

    try {
      const regRes = await apiClient.get(`/v1/regions/${regionId}`);
      regionName.value = regRes.data.name;

      const misRes = await apiClient.get(`/v1/regions/${regionId}/missions`);
      missions.value = misRes.data;

      if (missions.value.length > 0) {
        currentMission.value = missions.value.find(m => m.sessionStatus !== 'CLEARED') || missions.value[0];
        currentTargetName.value = currentMission.value.title;
        map.setCenter(new window.kakao.maps.LatLng(currentMission.value.targetLat, currentMission.value.targetLng));

        missions.value.forEach((m, idx) => {
          if (!m.targetLat || !m.targetLng) return;
          const position = new window.kakao.maps.LatLng(m.targetLat, m.targetLng);
          const content = document.createElement('div');
          content.className = 'custom-marker';
          
          if (m.sessionStatus === 'CLEARED') content.classList.add('cleared');
          if (m.isFinal) content.classList.add('final-target');
          
          content.innerHTML = `<div class="marker-core"></div><div class="marker-ring"></div><div class="marker-label">${m.isFinal ? 'TGT' : idx + 1}</div>`;

          content.addEventListener('click', () => {
            if (activeInfoOverlay) activeInfoOverlay.setMap(null);
            const infoContent = document.createElement('div');
            infoContent.className = 'info-overlay-content';
            infoContent.innerHTML = `<div class="title">${m.title}</div><div class="desc">${m.visionKeyword ? '목표 단서: ' + m.visionKeyword : '최종 목적지'}</div><div class="status ${m.sessionStatus || 'ACTIVE'}">${m.isFinal ? '[ FINAL TARGET ]' : (m.sessionStatus === 'CLEARED' ? '[ CLEARED ]' : '진행 대기')}</div>`;
            activeInfoOverlay = new window.kakao.maps.CustomOverlay({ map: map, position: position, content: infoContent, yAnchor: 1.5 });
          });

          new window.kakao.maps.CustomOverlay({ map: map, position: position, content: content, yAnchor: 1 });
        });
      }
      startGpsTracking();
    } catch (error) {
      currentTargetName.value = '데이터 수신 실패';
    }
  });
});

onUnmounted(() => {
  if (gpsWatcherId && navigator.geolocation) navigator.geolocation.clearWatch(gpsWatcherId);
});

const goToChat = () => {
  if (currentMission.value) router.push(`/chat/${currentMission.value.id}`);
};

const forceArrival = () => { isArrived.value = true; };

// 📸 캡처된 Base64 데이터를 실제 File 객체로 변환하여 전송
const uploadImage = async (imageDataUrl) => {
  isScannerOpen.value = false;
  
  if (!imageDataUrl) return;

  try {
    // 💡 핵심: Base64 -> Blob -> File 변환 로직
    const fetchRes = await fetch(imageDataUrl);
    const blob = await fetchRes.blob();
    const file = new File([blob], "capture.jpg", { type: "image/jpeg" });

    const formData = new FormData();
    formData.append('image', file);

    const response = await apiClient.post(`/v1/missions/${currentMission.value.id}/vision`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });

    if (response.data.success) {
       alert(`[분석 성공] 단서 획득!`);
       collectedHints.value++;
    } else {
       alert("[분석 실패] 목표물이 감지되지 않았습니다.");
    }
  } catch (error) {
    alert("본부 서버(Vision)와 통신할 수 없습니다.");
  }
};