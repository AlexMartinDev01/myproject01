import * as T from 'three';
// Real-time GPU card renderer. No GIF, video, generated poster or network CDN.
const clamp=(v,a,b)=>Math.min(b,Math.max(a,v)), ease=t=>t*t*(3-2*t);
const face=id=>['M02','M17','M19'].includes(id)?'assets/art3d/'+id+'.svg':'assets/'+id+'.svg';
let active=[];
function load(url){return new Promise(resolve=>new T.TextureLoader().load(url,t=>{t.colorSpace=T.SRGBColorSpace;t.anisotropy=4;resolve(t)},undefined,()=>resolve(null)));}
function card(front,back){
 const g=new T.Group(), w=1.56,h=2.62;
 const gold=new T.MeshStandardMaterial({color:0xe1bd7e,metalness:.72,roughness:.3});
 const body=new T.Mesh(new T.BoxGeometry(w,h,.095),[gold,gold,gold,gold,
  new T.MeshBasicMaterial({map:front,color:0xffffff}),new T.MeshBasicMaterial({map:back,color:0xffffff})]);
 g.add(body);
 const line=new T.LineSegments(new T.EdgesGeometry(new T.BoxGeometry(w+.009,h+.009,.109)),new T.LineBasicMaterial({color:0xf7d69d,transparent:true,opacity:.82}));
 g.add(line);
 const mat=new T.ShaderMaterial({
  uniforms:{time:{value:0},strength:{value:.48}},
  vertexShader:'varying vec2 p;void main(){p=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.);}',
  fragmentShader:'varying vec2 p;uniform float time;uniform float strength;void main(){float d=p.x+p.y*.42;float sweep=exp(-pow((d-mod(time*.25,1.8))/.11,2.));float edge=1.-smoothstep(.01,.052,min(min(p.x,1.-p.x),min(p.y,1.-p.y)));float a=strength*(sweep*.28+edge*.34);gl_FragColor=vec4(1.,.81,.47,a);}',
  transparent:true,depthWrite:false,blending:T.AdditiveBlending,side:T.DoubleSide
 });
 const gloss=new T.Mesh(new T.PlaneGeometry(w-.025,h-.025),mat);
 gloss.position.z=.052;g.add(gloss);g.userData.gloss=gloss;
 return g;
}
function ring(rad,opacity){
 const arr=[];for(let i=0;i<=128;i++){let a=i*Math.PI*2/128;arr.push(new T.Vector3(Math.cos(a)*rad,Math.sin(a)*rad,-1.3))}
 return new T.Line(new T.BufferGeometry().setFromPoints(arr),new T.LineBasicMaterial({color:0xc5a06e,transparent:true,opacity}));
}
function stars(n){const pos=new Float32Array(n*3);for(let i=0;i<n;i++){const a=Math.random()*Math.PI*2,r=2+Math.random()*2.8;pos[i*3]=Math.cos(a)*r;pos[i*3+1]=Math.sin(a)*r;pos[i*3+2]=-2.4;}const geo=new T.BufferGeometry();geo.setAttribute('position',new T.BufferAttribute(pos,3));return new T.Points(geo,new T.PointsMaterial({color:0xe5bd83,size:.028,opacity:.7,transparent:true}));}
export class TarotScene{
 constructor(el){
  this.el=el;this.kind=el.dataset.scene||'lab';this.reduce=el.dataset.reduceMotion==='true'||matchMedia('(prefers-reduced-motion: reduce)').matches;
  this.quality=el.dataset.quality||'auto';this.glint=.48;this.cards=[];this.running=true;this.yaw=0;this.pitch=0;this.targetYaw=0;this.targetPitch=0;this.faceUp=this.kind!=='flip';this.orbiting=this.kind!=='lab'&&!this.reduce;this.cutSpread=0;this.drag=null;this.flipping=false;
  const w=Math.max(1,el.clientWidth),h=Math.max(1,el.clientHeight);this.scene=new T.Scene();this.camera=new T.PerspectiveCamera(38,w/h,.1,80);this.camera.position.set(0,.15,8.4);
  this.renderer=new T.WebGLRenderer({alpha:true,antialias:true,powerPreference:'high-performance'});this.renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,this.quality==='low'?1:1.65));this.renderer.setSize(w,h,false);this.renderer.outputColorSpace=T.SRGBColorSpace;this.renderer.toneMapping=T.ACESFilmicToneMapping;
  this.renderer.domElement.className='gpu-canvas';el.insertBefore(this.renderer.domElement,el.firstChild);
  this.scene.add(new T.AmbientLight(0xffe0c0,.85));const lamp=new T.DirectionalLight(0xffd9a0,2.5);lamp.position.set(-2,3,4);this.scene.add(lamp);
  const violet=new T.PointLight(0x8363cb,17,12);violet.position.set(2,-1,3);this.scene.add(violet);
  this.group=new T.Group();this.scene.add(this.group);this.orbits=new T.Group();this.scene.add(this.orbits);this.orbits.add(ring(2.6,.3),ring(3,.17),stars(this.quality==='low'?60:95));
  this.time=0;this.last=performance.now();
  this.down=e=>{this.drag={x:e.clientX,y:e.clientY,origin:e.clientX,moved:false};el.setPointerCapture?.(e.pointerId)};
  this.move=e=>{if(!this.drag)return;const dx=e.clientX-this.drag.x,dy=e.clientY-this.drag.y;if(Math.abs(dx)+Math.abs(dy)>2)this.drag.moved=true;this.drag.x=e.clientX;this.drag.y=e.clientY;if(['lab','flip','home'].includes(this.kind)){this.targetYaw+=dx*.008;this.targetPitch=clamp(this.targetPitch+dy*.006,-.5,.5)}if(this.kind==='cut')this.cutSpread=clamp(this.cutSpread+dx*.005,0,.9);this.invalidate()};
  this.up=e=>{if(!this.drag)return;const tapped=!this.drag.moved;this.drag=null;if(this.kind==='lab'&&tapped)this.flip();if(this.kind==='draw'&&tapped){const r=el.getBoundingClientRect();el.dispatchEvent(new CustomEvent('tarot:pick',{bubbles:true,detail:{index:clamp(Math.floor((e.clientX-r.left)/r.width*9),0,8)}}));}if(this.kind==='cut'&&!tapped&&this.cutSpread>.2)el.dispatchEvent(new CustomEvent('tarot:cut',{bubbles:true,detail:{ratio:clamp(this.cutSpread,.15,.85)}}));this.invalidate()};
  el.addEventListener('pointerdown',this.down);el.addEventListener('pointermove',this.move);el.addEventListener('pointerup',this.up);
  this.observer=new ResizeObserver(()=>this.resize());this.observer.observe(el);
  this.ready=this.setup();
 }
 async setup(){
  const ids=this.kind==='lab'?[this.el.dataset.card||'M17']:this.kind==='flip'?[this.el.dataset.card||'M17']:this.kind==='home'?['M02','M17','M19']:this.kind==='result'?(this.el.dataset.cards||'M17').split(','):[];
  const textures=await Promise.all([load('assets/tarot_back.svg'),...ids.map(id=>load(face(id)))]);if(!this.running)return;
  this.back=textures[0];
  if(ids.length){ids.forEach((id,i)=>{const m=card(textures[i+1],this.back),n=ids.length;m.scale.setScalar(n===1?1.15:.76);m.position.x=(i-(n-1)/2)*1.25;m.position.z=i===1?.15:0;m.rotation.y=this.kind==='flip'?Math.PI:(i-(n-1)/2)*-.1;this.group.add(m);this.cards.push(m)});}
  else{let n=this.kind==='shuffle'?15:this.kind==='draw'?9:12;for(let i=0;i<n;i++){const m=card(null,this.back);m.scale.setScalar(this.kind==='draw'?.55:.52);m.rotation.y=Math.PI;m.userData.index=i;this.group.add(m);this.cards.push(m)}}
  this.el.classList.add('gpu-ready');this.el.closest('.motion-scene')?.classList.add('gpu-ready');this.invalidate();
 }
 async setFlipCard(id){if(!this.cards.length)return;const tx=await load(face(id));if(!this.running)return;const m=this.cards[0];m.children[0].material[4].map=tx;m.children[0].material[4].needsUpdate=true;this.faceUp=false;m.rotation.y=Math.PI;this.invalidate()}
 flip(){if(this.flipping)return Promise.resolve();this.flipping=true;this.progress=0;this.from=this.faceUp?0:Math.PI;this.to=this.faceUp?Math.PI:0;return new Promise(resolve=>{this.resolveFlip=resolve;this.invalidate()})}
 rotateAuto(v){this.orbiting=!!v;this.invalidate()}
 setGlint(v){this.glint=clamp(v,0,1);this.invalidate()}
 setQuality(q){this.quality=q;this.renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,q==='low'?1:1.65));this.resize()}
 resize(){if(!this.running)return;const w=Math.max(1,this.el.clientWidth),h=Math.max(1,this.el.clientHeight);this.camera.aspect=w/h;this.camera.updateProjectionMatrix();this.renderer.setSize(w,h,false);this.invalidate()}
 invalidate(){if(this.running&&!this.raf)this.raf=requestAnimationFrame(now=>this.frame(now))}
 frame(now){this.raf=0;if(!this.running)return;const dt=Math.min(.05,(now-this.last)/1000||.017);this.last=now;this.time+=dt;const t=this.time;this.yaw+=(this.targetYaw-this.yaw)*.13;this.pitch+=(this.targetPitch-this.pitch)*.13;
  this.cards.forEach((m,i)=>{const k=i-(this.cards.length-1)/2;
   if(this.kind==='lab'||this.kind==='flip'){if(this.flipping){this.progress=clamp(this.progress+dt/.78,0,1);m.rotation.y=this.from+(this.to-this.from)*(this.progress*this.progress*(3-2*this.progress));if(this.progress>=1){this.flipping=false;this.faceUp=!this.faceUp;this.resolveFlip?.();}}else m.rotation.y=this.yaw+(this.faceUp?0:Math.PI);m.rotation.x=this.pitch;m.position.y=this.reduce?0:Math.sin(t*.85)*.06;}
   if(this.kind==='home'||this.kind==='result'){m.position.y=this.reduce?0:Math.sin(t*.7+i)*.06;m.rotation.y=k*-.12+Math.sin(t*.5+i)*.04;}
   if(this.kind==='shuffle'){m.position.set(k*.19+Math.sin(t*1.4+i*.3)*.16,Math.sin(k*.25+t*.8)*.22,(i%4)*.03);m.rotation.z=k*.055+Math.sin(t*.8+i*.2)*.03;}
   if(this.kind==='cut'){let x=i<6?-1:1;m.position.set(x*(this.cutSpread||.35+.15*Math.sin(t)),(i%6)*.015, (i%6)*.03);}
   if(this.kind==='draw'){m.position.set(k*.46,-Math.abs(k)*.09,Math.abs(k)*-.03);m.rotation.z=-k*.09;}
   if(m.userData.gloss){m.userData.gloss.material.uniforms.time.value=t;m.userData.gloss.material.uniforms.strength.value=this.glint;}
  });this.orbits.rotation.z=t*.022;this.renderer.render(this.scene,this.camera);
  if(!this.reduce&&(this.orbiting||['shuffle','cut','draw','result','home'].includes(this.kind))||this.flipping||this.drag)this.invalidate();
 }
 dispose(){this.running=false;cancelAnimationFrame(this.raf);this.observer.disconnect();this.el.removeEventListener('pointerdown',this.down);this.el.removeEventListener('pointermove',this.move);this.el.removeEventListener('pointerup',this.up);this.scene.traverse(o=>{o.geometry?.dispose();if(o.material)(Array.isArray(o.material)?o.material:[o.material]).forEach(m=>m.dispose())});this.renderer.dispose();this.renderer.forceContextLoss();this.renderer.domElement.remove()}
}
export async function mountTarotStages(){active.forEach(x=>x.dispose());active=[];for(const el of document.querySelectorAll('.gpu-stage')){try{const s=new TarotScene(el);active.push(s);await s.ready}catch(e){console.warn('GPU fallback',e);el.classList.add('gpu-fallback-active')}}return active}
export function disposeTarotStages(){active.forEach(s=>s.dispose());active=[]}
