<template>
  <router-view />
</template>

<style>
body {
  margin: 0;
  padding: 0;
  background-color: #101010;
}

button,
a[role="button"],
.button-like {
  transition: transform .16s ease, box-shadow .16s ease, filter .16s ease, opacity .16s ease;
  font-weight: 700 !important;
}

button:not(:disabled),
a[role="button"],
.button-like {
  cursor: pointer;
}

button:not(:disabled):hover,
a[role="button"]:hover,
.button-like:hover {
  transform: translateY(-1px);
  filter: none;
  box-shadow: 0 0 15px rgba(92, 88, 85, 0.2);
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
