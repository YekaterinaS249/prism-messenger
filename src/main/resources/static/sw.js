// Minimal service worker — exists only so the app is installable as a PWA (that's the one
// thing it's for). It does NOT cache index.html, app.js, style.css, or lamp.js — those are
// the files that change with every fix/feature, and a service worker sitting in front of them
// is exactly what caused chat themes (and anything else) to silently stop updating for
// returning users, sometimes for good, since a SW can keep serving a frozen snapshot even
// after the cache "strategy" is fixed server-side (the fix itself can get stuck the same way).
// Only rarely-changing icon/manifest assets are cached; everything else always goes straight
// to the network, untouched, exactly as if there were no service worker at all.
const CACHE_NAME = 'prism-shell-v3';
const CACHEABLE_FILES = [
  '/favicon.svg',
  '/manifest.json',
  '/icon-192.png',
  '/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(CACHEABLE_FILES)).catch(() => {})
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  const cacheable = CACHEABLE_FILES.includes(url.pathname);
  // Everything that isn't one of the few static icon/manifest files is left completely
  // untouched — no respondWith() at all, so the browser handles it exactly like there's no
  // service worker in the picture. This guarantees app.js/style.css/index.html are always
  // fetched fresh from the network.
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || !cacheable) {
    return;
  }
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const copy = response.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copy)).catch(() => {});
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
