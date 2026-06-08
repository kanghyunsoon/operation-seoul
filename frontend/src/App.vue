<template>
  <router-view />
</template>

<style>
body {
  margin: 0;
  padding: 0;
  background-color: #050505;
}

button,
a[role="button"],
.button-like {
  transition: transform .16s ease, box-shadow .16s ease, filter .16s ease, opacity .16s ease;
}

button:not(:disabled),
a[role="button"],
.button-like {
  cursor: pointer;
}

button:not(:disabled):hover,
a[role="button"]:hover,
.button-like:hover {
  transform: translateY(-2px);
  filter: brightness(1.08) saturate(1.08);
  box-shadow: 0 12px 26px rgba(0, 0, 0, .24);
}

button:not(:disabled):active,
a[role="button"]:active,
.button-like:active {
  transform: translateY(1px) scale(.98);
  filter: brightness(.96);
}

button:disabled {
  cursor: not-allowed;
}
</style>
