export const regionAreas = [
  { regionId: 1, code: 'seoul', label: '서울', includes: '서울', color: '#2563eb', x: 162, y: 86, w: 54, h: 42 },
  { regionId: 2, code: 'capital_area', label: '수도권', includes: '인천 / 경기', color: '#0891b2', x: 112, y: 68, w: 102, h: 92 },
  { regionId: 3, code: 'gangwon', label: '강원권', includes: '강원', color: '#16a34a', x: 218, y: 44, w: 114, h: 108 },
  { regionId: 4, code: 'chungnam', label: '충남권', includes: '충남 / 세종 / 대전', color: '#ca8a04', x: 98, y: 166, w: 106, h: 86 },
  { regionId: 5, code: 'chungbuk', label: '충북권', includes: '충북', color: '#f97316', x: 202, y: 156, w: 92, h: 90 },
  { regionId: 6, code: 'jeonbuk', label: '전북권', includes: '전북', color: '#dc2626', x: 122, y: 256, w: 122, h: 74 },
  { regionId: 7, code: 'jeonnam', label: '전남권', includes: '전남 / 광주', color: '#be123c', x: 88, y: 336, w: 142, h: 92 },
  { regionId: 8, code: 'gyeongbuk', label: '경북권', includes: '경북 / 대구', color: '#7c3aed', x: 246, y: 246, w: 108, h: 114 },
  { regionId: 9, code: 'gyeongnam', label: '경남권', includes: '경남 / 울산 / 부산', color: '#9333ea', x: 230, y: 366, w: 130, h: 84 },
  { regionId: 10, code: 'jeju', label: '제주', includes: '제주', color: '#059669', x: 138, y: 486, w: 104, h: 46 }
];

export function regionLabel(code) {
  return regionAreas.find((area) => area.code === code || String(area.regionId) === String(code))?.label || '전체 권역';
}
