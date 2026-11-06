const CACHE='starweave-tarot-v031';
const core=['./','./index.html','./style.css','./app.js','./rules.js','./assets/cards.js','./assets/tarot_back.svg','./assets/renderer3d.bundle.js','./assets/art3d/M02.svg','./assets/art3d/M17.svg','./assets/art3d/M19.svg','./manifest.webmanifest'];
const cards=[...Array(22)].map((_,i)=>`./assets/M${String(i).padStart(2,'0')}.svg`);
for(const s of ['W','C','S','P'])for(let i=1;i<=14;i++)cards.push(`./assets/${s}${String(i).padStart(2,'0')}.svg`);
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll([...core,...cards])).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
 const u=new URL(event.request.url);
 if(event.request.method!=='GET'||u.origin!==self.location.origin||u.pathname.startsWith('/api/')||event.request.headers.has('range'))return;
 event.respondWith(caches.match(event.request).then(existing=>existing||fetch(event.request).then(response=>{
  if(response.ok){const copy=response.clone();caches.open(CACHE).then(c=>c.put(event.request,copy));}
  return response;
 })));
});
