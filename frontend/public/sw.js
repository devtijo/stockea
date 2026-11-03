// Service Worker de Stockea: guarda la "cáscara" de la app (index.html + JS + CSS) para que
// abra SIN internet. Los datos (ventas, cola, catálogo) NO viven aquí: viven en IndexedDB (Dexie).
// Nunca se guardan respuestas de /api ni de /auth: son datos del negocio y sesiones

const CACHE = 'stockea-app-v1';
const BASICOS = ['/', '/index.html', '/manifest.webmanifest', '/favicon.svg', '/icono-192.png', '/icono-512.png',
    '/silent-check-sso.html', '/silent-check-sso.js'];

// Busca los /assets/... que pide index.html (los nombres llevan hash y cambian en cada build)
function assetsDe(html) {
    return [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((m) => m[1]);
}

// Guarda index.html y sus assets; borra los assets de builds anteriores
async function guardarVersion(cache, respuestaIndex) {
    const html = await respuestaIndex.clone().text();
    const actuales = assetsDe(html);
    await cache.put('/index.html', respuestaIndex.clone());
    await cache.put('/', respuestaIndex.clone());
    await cache.addAll(actuales.filter((a) => a));
    for (const pedido of await cache.keys()) {
        const ruta = new URL(pedido.url).pathname;
        if (ruta.startsWith('/assets/') && !actuales.includes(ruta)) await cache.delete(pedido);
    }
}

self.addEventListener('install', (evento) => {
    evento.waitUntil((async () => {
        const cache = await caches.open(CACHE);
        await cache.addAll(BASICOS.filter((b) => b !== '/' && b !== '/index.html'));
        await guardarVersion(cache, await fetch('/index.html', { cache: 'no-cache' }));
        await self.skipWaiting();
    })());
});

self.addEventListener('activate', (evento) => {
    evento.waitUntil((async () => {
        for (const nombre of await caches.keys()) {
            if (nombre !== CACHE) await caches.delete(nombre);
        }
        await self.clients.claim();
    })());
});

// fetch con tope de tiempo: con "internet a medias" no se deja al cajero esperando
function conTiempo(pedido, ms) {
    return Promise.race([
        fetch(pedido),
        new Promise((_, rechazar) => setTimeout(() => rechazar(new Error('tiempo agotado')), ms)),
    ]);
}

self.addEventListener('fetch', (evento) => {
    const pedido = evento.request;
    const url = new URL(pedido.url);
    if (pedido.method !== 'GET' || url.origin !== self.location.origin) return;
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/auth/') || url.pathname.startsWith('/actuator')) return;

    // Páginas: primero la red (versión nueva), si no hay, la guardada
    if (pedido.mode === 'navigate') {
        evento.respondWith((async () => {
            const cache = await caches.open(CACHE);
            try {
                const respuesta = await conTiempo(pedido, 4000);
                if (respuesta.ok && url.pathname === '/') evento.waitUntil(guardarVersion(cache, respuesta.clone()));
                return respuesta;
            } catch {
                return (await cache.match('/index.html')) ?? Response.error();
            }
        })());
        return;
    }

    // JS, CSS e imágenes: primero la caché (llevan hash, no cambian), si no está, la red
    evento.respondWith((async () => {
        const cache = await caches.open(CACHE);
        const guardada = await cache.match(pedido, { ignoreSearch: url.pathname.startsWith('/silent-check-sso') });
        if (guardada) return guardada;
        const respuesta = await fetch(pedido);
        if (respuesta.ok && url.pathname.startsWith('/assets/')) await cache.put(pedido, respuesta.clone());
        return respuesta;
    })());
});
