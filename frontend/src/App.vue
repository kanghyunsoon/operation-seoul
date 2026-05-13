<template>
  <!-- 모든 화면은 router/index.js의 현재 route가 결정합니다. App.vue는 전역 shell 역할만 합니다. -->
  <router-view />
</template>

<style>
/* 전역 스타일: 배경을 검정색으로 통일해서 끊김 없는 몰입감 제공 */
body {
  margin: 0;
  padding: 0;
  background-color: #050505;
}
</style>
