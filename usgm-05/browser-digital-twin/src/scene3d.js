/* USGM-05 procedural 3D digital-twin viewport.
   Real 3D coordinates + camera orbit + perspective projection, drawn to 2D canvas.
   It is a geometric/visual and fault-rule simulator, NOT SPICE or an HX711 silicon model.
   All input effects are driven by the same USGM WASM engine in the existing simulator.
*/
(()=>{
  'use strict';
  const cvs=document.getElementById('twin-canvas');
  if(!cvs) return;
  const ctx=cvs.getContext('2d',{alpha:false});
  if(!ctx){ document.getElementById('twin-hint').textContent='此浏览器无法初始化Canvas';return; }
  const $=id=>document.getElementById(id);
  let yaw=-.52,pitch=.69,zoom=1,showWires=true,showGlass=true,explode=false;
  let activePart='scale',fruitGrams=0,rotateSinceDown=0;
  let width=950,height=570,dpr=1,frame=0;
  let pointer=null;
  const parts={
    scale:{name:'5kg 圆形称重组件',sub:'透明双层亚克力 / 单点式称重梁 / 载物台',spec:'5kg 量程 · HX711 集成套件',at:[-2.35,.86,-.35],radius:140},
    hx:{name:'HX711 称重 ADC',sub:'应变片信号放大与24位模数转换',spec:'VCC · GND · DT · SCK',at:[-3.16,.36,-.94],radius:60},
    esp:{name:'ESP32-C3 SuperMini',sub:'采集、分级、Wi-Fi 本地手机网页与事件记录',spec:'GPIO0/1 · GPIO4/5/6 · 3V3 · GND',at:[.52,.20,-.56],radius:105},
    amp:{name:'MAX98357A I²S 功放',sub:'接收三线I²S，驱动4Ω 3W喇叭',spec:'BCLK · LRC · DIN · VIN · GND · SPK±',at:[2.85,.20,-1.25],radius:67},
    speaker:{name:'4Ω 3W 普通喇叭',sub:'真实产品播报中文等级；浏览器播放同源PCM素材',spec:'SPK+ 与 SPK− 两线，负极不是GND',at:[2.90,.37,1.37],radius:95},
  };
  const faults={dt:false,audio:false,power:false};
  const colors={white:'#f4f8fb',ink:'#dceef5',gold:'#f0b84b',green:'#56d9ad'};
  const wires=[
    {id:'dt',from:[-3.51,.47,-1.05],to:[-.25,.28,-1.18],color:'#7fee9d',name:'HX711 DT → GPIO0',signal:'数据输出 / 3.3V逻辑'},
    {id:'sck',from:[-3.42,.47,-1.03],to:[-.22,.28,-.94],color:'#7fbeff',name:'HX711 SCK → GPIO1',signal:'读取时钟 / 3.3V逻辑'},
    {id:'vcc',from:[-3.31,.47,-1.04],to:[-.22,.28,-.70],color:'#f47464',name:'ESP32 3V3 → HX711 VCC',signal:'仅限3.3V兼容模块'},
    {id:'gnd',from:[-3.20,.47,-1.02],to:[-.22,.28,-.48],color:'#687a96',name:'共地：HX711 ↔ ESP32',signal:'实验低压系统共地'},
    {id:'bclk',from:[1.25,.29,-1.16],to:[2.41,.30,-1.73],color:'#ffa450',name:'GPIO4 → 功放 BCLK',signal:'I²S 位时钟'},
    {id:'lrc',from:[1.25,.29,-.90],to:[2.53,.30,-1.73],color:'#f3d56c',name:'GPIO5 → 功放 LRC',signal:'I²S 字选择'},
    {id:'din',from:[1.25,.29,-.64],to:[2.65,.30,-1.73],color:'#e2a0fa',name:'GPIO6 → 功放 DIN',signal:'I²S 音频数据'},
    {id:'spkp',from:[3.25,.30,-.96],to:[2.73,.38,.78],color:'#ff8585',name:'MAX98357A SPK+ → 喇叭',signal:'桥接功放输出正端'},
    {id:'spkn',from:[3.18,.30,-.96],to:[2.96,.38,.78],color:'#a4bbc9',name:'MAX98357A SPK− → 喇叭',signal:'桥接功放输出负端，禁止接地'},
  ];
  const pinPoints=new Map();
  function rgba(hex,a){const h=hex.replace('#','');const c=h.length===3?h.split('').map(v=>v+v).join(''):h;const n=parseInt(c,16);return `rgba(${(n>>16)&255},${(n>>8)&255},${n&255},${a})`;}
  function p3(x,y,z){return [x,y,z];}
  function cam(p){let [x,y,z]=p;const cy=Math.cos(yaw),sy=Math.sin(yaw),cp=Math.cos(pitch),sp=Math.sin(pitch);const X=x*cy-z*sy,dep=x*sy+z*cy;return [X,y*cp-dep*sp,y*sp+dep*cp];}
  function screen(p){const [X,Y,D]=cam(p);const sf=(9.6/(9.6-D))*Math.min(width/9.4,height/5.7)*zoom;return {x:width*.50+X*sf,y:height*.57-Y*sf,d:D,s:sf};}
  function poly(points,fill,stroke=null,lw=1,alpha=1){let projs=points.map(screen);ctx.beginPath();projs.forEach((s,i)=>i?ctx.lineTo(s.x,s.y):ctx.moveTo(s.x,s.y));ctx.closePath();ctx.globalAlpha=alpha;ctx.fillStyle=fill;ctx.fill();if(stroke){ctx.strokeStyle=stroke;ctx.lineWidth=lw;ctx.stroke()}ctx.globalAlpha=1;}
  function path3(points,color,widthPx=2,alpha=1){ctx.beginPath();points.map(screen).forEach((s,i)=>i?ctx.lineTo(s.x,s.y):ctx.moveTo(s.x,s.y));ctx.strokeStyle=color;ctx.lineWidth=widthPx;ctx.lineCap='round';ctx.lineJoin='round';ctx.globalAlpha=alpha;ctx.stroke();ctx.globalAlpha=1;}
  function circle3(center,r,steps=46){const [x,y,z]=center;return Array.from({length:steps},(_,i)=>[x+r*Math.cos(i*Math.PI*2/steps),y,z+r*Math.sin(i*Math.PI*2/steps)]);}
  function polygon3(cx,cy,cz,dx,dz){return [[cx-dx/2,cy,cz-dz/2],[cx+dx/2,cy,cz-dz/2],[cx+dx/2,cy,cz+dz/2],[cx-dx/2,cy,cz+dz/2]];}
  function disc(x,y,z,r,fill,stroke,alpha=1){poly(circle3([x,y,z],r),fill,stroke,1.1,alpha)}
  function box(x,y,z,dx,dy,dz,front='#728395',top='#a4b5c2',side='#4b6278'){
    const yf=y+dy/2,yb=y-dy/2,x0=x-dx/2,x1=x+dx/2,z0=z-dz/2,z1=z+dz/2;
    poly([[x0,yb,z1],[x1,yb,z1],[x1,yf,z1],[x0,yf,z1]],front,'#ffffff15',.6);
    poly([[x1,yb,z0],[x1,yb,z1],[x1,yf,z1],[x1,yf,z0]],side,'#ffffff10',.6);
    poly([[x0,yf,z0],[x1,yf,z0],[x1,yf,z1],[x0,yf,z1]],top,'#f1ffff2b',.7);
  }
  function cylinder(cx,y,cz,r,h,color,edge='#ddd',opacity=1){
    const lo=circle3([cx,y-h/2,cz],r),hi=circle3([cx,y+h/2,cz],r);
    for(let i=0;i<lo.length;i++){const j=(i+1)%lo.length;if(i%2===0)poly([lo[i],lo[j],hi[j],hi[i]],color,null,1,opacity)}
    poly(hi,color,edge,.85,opacity);
  }
  function text3(txt,p,color='#d8eff8',fontPx=11,weight=600){const a=screen(p);const s=Math.max(.63,Math.min(1.25,a.s/73));ctx.fillStyle=color;ctx.textBaseline='middle';ctx.font=`${weight} ${Math.round(fontPx*s)}px Inter, system-ui, sans-serif`;ctx.textAlign='center';ctx.fillText(txt,a.x,a.y)}
  function board(cx,z,dx,dz,paint,title,kind){
    box(cx,-.055,z,dx,.105,dz,rgba(paint,.94),paint,rgba(paint,.78));
    poly(polygon3(cx,.012,z,dx*.98,dz*.98),paint,rgba('#b7fff1',.37),.8);
    // PCB traces
    for(let i=0;i<10;i++){const xv=cx+(i%5-2)*dx*.16,zv=z+(Math.floor(i/5)-.5)*dz*.50;path3([[xv,.02,zv],[xv+.09,.021,zv],[xv+.09,.021,zv+.14]],rgba('#d3b67a',.45),1)}
    for(let i=0;i<8;i++){
      const t=(i-3.5)/4;const a=cx-dx*.44, b=cx+dx*.44;
      box(a,.055,z+t*dz*.36,.065,.16,.08,'#d5c8a0','#dbcb8c','#aaa17f');
      box(b,.055,z+t*dz*.36,.065,.16,.08,'#d5c8a0','#dbcb8c','#aaa17f');
    }
    if(kind==='esp'){
      box(cx,.065,z+.23,.67,.16,.83,'#19212b','#29303e','#0f1920');
      text3('ESP32-C3',[cx,.16,z+.28],'#dfe9f1',10,800);
      box(cx,.075,z-dz*.42,.48,.16,.28,'#a7b6c2','#d3d9dc','#778999');
      text3('USB-C',[cx,.165,z-dz*.42],'#243b4f',7);
      text3('SUPERMini',[cx,.04,z+dz*.42],'#f6f8f7',9,800);
      for(let i=0;i<7;i++){text3('•',[cx-dx*.30,.04,z+(i-3)*.21],'#eae8ad',8)}
    } else if(kind==='hx'){
      box(cx,.07,z,.34,.13,.52,'#151d22','#2c3337','#070c11');
      text3('HX711',[cx,.17,z],'#f5faf8',9,700);
      box(cx+dx*.02,.10,z-dz*.27,.26,.15,.25,'#e8e6dc','#ece9de','#a8afaf');
      text3('E+ E− A+ A−',[cx,.045,z+dz*.40],'#edf5e9',6,600);
    } else if(kind==='amp'){
      box(cx,.08,z,.40,.13,.48,'#1d1e2e','#292d3e','#1a1327');
      text3('MAX98357A',[cx,.17,z+dz*.2],'#ffffff',9,700);
      box(cx+dx*.25,.08,z+dz*.29,.36,.20,.26,'#4c8a66','#77a98c','#3c6952');
      text3('SPK ±',[cx+dx*.24,.17,z+dz*.29],'#f4e2b0',7,700);
    }
    if(activePart===kind){poly(polygon3(cx,.19,z,dx*1.1,dz*1.1),rgba('#42eed0',.06),rgba('#50efd8',.75),1.8)}
  }
  function wireArc(w){const a=w.from,b=w.to,pts=[];for(let i=0;i<=30;i++){
    let t=i/30, arc=Math.sin(t*Math.PI);pts.push([a[0]+(b[0]-a[0])*t, a[1]+(b[1]-a[1])*t + .36*arc,a[2]+(b[2]-a[2])*t + .40*arc]);
  }return pts;}
  function drawWires(){if(!showWires)return;for(const w of wires){const broken=w.id==='dt'&&faults.dt||w.id==='sck'&&faults.dt||w.id==='din'&&faults.audio||faults.power;
    const pts=wireArc(w);if(broken){path3(pts.slice(0,14),w.color,3,0.8);path3(pts.slice(19),w.color,3,0.7);const q=screen(pts[15]);ctx.fillStyle='#ff6a79';ctx.font='700 18px Inter';ctx.textAlign='center';ctx.fillText('×',q.x,q.y);}
    else {path3(pts,'#030b17',6,.7);path3(pts,w.color,3.15,.88)}
    for(const endpoint of [w.from,w.to]){const s=screen(endpoint);ctx.beginPath();ctx.arc(s.x,s.y,3.3,0,Math.PI*2);ctx.fillStyle=w.color;ctx.fill()}
  }}
  function drawFruit(){if(fruitGrams<15)return;const cx=-2.35,cy=1.65,cz=-.35;const r=.41;
    for(let lat=0;lat<10;lat++){const a0=-Math.PI/2+lat*Math.PI/10,a1=-Math.PI/2+(lat+1)*Math.PI/10;
      const band=[];for(let i=0;i<=28;i++){const az=i/28*2*Math.PI;band.push([cx+r*Math.cos(a1)*Math.cos(az),cy+r*1.06*Math.sin(a1),cz+r*.9*Math.cos(a1)*Math.sin(az)])}
      const mid=lat/10,color=mid<.5?'#7fac47':'#739c35';poly(band,color,null,1,.96);
    }
    // translucent specular glint
    cylinder(cx,cy+.44,cz,.06,.14,'#6b7544','#9ba76d');
    text3(`${fruitGrams.toFixed(0)}g`,[cx,cy+.65,cz],'#ebffcf',12,800);
  }
  function ground(){
    const gr=ctx.createLinearGradient(0,0,0,height);gr.addColorStop(0,'#101f32');gr.addColorStop(.55,'#0e1825');gr.addColorStop(1,'#07111d');ctx.fillStyle=gr;ctx.fillRect(0,0,width,height);
    // grid on perspective floor
    for(let i=-10;i<=10;i++){path3([[-10,-.25,i*.6],[10,-.25,i*.6]],'#2a455d',.6,.18);path3([[i*.6,-.25,-10],[i*.6,-.25,10]],'#27495e',.6,.18)}
    disc(-2.35,-.18,-.35,2.05,'#020a14','#355674',.24);
    disc(.52,-.19,-.56,1.30,'#020a14',null,.20);
    disc(2.85,-.20,-1.25,.9,'#020a14',null,.19);
  }
  function scale(){const x=-2.35,z=-.35;
    // bottom acrylic glass disc
    cylinder(x,.03,z,1.55,.095,'#60869b','#b5d8ed',.27);
    disc(x,.085,z,1.54,'#d9f5ff','#b6e2ff',.10);
    for(const [dx,dz] of [[-1,-1],[1,-1],[-1,1],[1,1]]){
      cylinder(x+dx*.93,.57,z+dz*.93,.085,.98,'#7790a3','#c8dce5',.75);
      cylinder(x+dx*.93,1.10,z+dz*.93,.13,.05,'#b7c1c7','#fff',.85);
    }
    box(x,.38,z,2.55,.22,.35,'#9eacb6','#e5ecf1','#6e8190');
    box(x-.6,.45,z,.50,.10,.46,'#f6f7f5','#ffffff','#949f9f');
    box(x+.62,.49,z,.43,.18,.44,'#c6d1d5','#edf4f1','#758e9a');
    for(let i of [-.77,.77])cylinder(x+i,.58,z,.092,.18,'#8b99aa','#f1f6ff');
    board(-3.16,-.94,.84,.72,'#28735a','HX711','hx');
    // transparent upper plate, line contour and inward refraction suggestion
    cylinder(x,1.18,z,1.65,.084,'#95cee3','#d7f6ff',.21);
    disc(x,1.24,z,1.65,'#caefff','#dcf8ff',showGlass?.15:.025);
    for(let r of [1.48,1.28])disc(x,1.247,z,r,rgba('#ffffff',.005),rgba('#cfebf8',.22),showGlass?.52:.08);
    if(activePart==='scale')disc(x,1.26,z,1.72,rgba('#3de0c3',.02),'#3deedc',.85);
  }
  function speaker(){const x=2.9,z=1.37;
    cylinder(x,.14,z,.70,.30,'#16202c','#5b6c83');
    cylinder(x,.35,z,.68,.15,'#303843','#93a8b5');
    disc(x,.44,z,.63,'#192634','#5e7386');
    disc(x,.465,z,.46,'#3e505f','#607e8e');
    disc(x,.47,z,.18,'#0d1822','#253a44');
    for(let a=0;a<2*Math.PI;a+=Math.PI/8){const p=[x+.54*Math.cos(a),.48,z+.54*Math.sin(a)];let s=screen(p);ctx.fillStyle='#73899a';ctx.beginPath();ctx.arc(s.x,s.y,2.1,0,Math.PI*2);ctx.fill()}
    if(activePart==='speaker')disc(x,.50,z,.73,rgba('#31e5c9',.03),'#31e5c9',.7);
  }
  function dimensionLabels(){
    const tags=[['scale','5kg 圆形秤'],['hx','HX711'],['esp','ESP32-C3'],['amp','MAX98357A'],['speaker','4Ω / 3W']];
    for(const [id,name] of tags){let pp=parts[id].at.slice();pp[1]+=(id==='scale'?1.05:.63);const p=screen(pp);if(p.x<10||p.x>width-10||p.y<0||p.y>height-15)continue;
      const fs=Math.max(10,Math.min(12,10*zoom));ctx.font=`650 ${fs}px Inter,system-ui`;const tw=ctx.measureText(name).width;
      ctx.fillStyle=activePart===id?'#124c55cc':'#142e3dcc';ctx.strokeStyle=activePart===id?'#5df9db':'#45647b';ctx.lineWidth=1;
      ctx.beginPath();ctx.roundRect(p.x-tw/2-10,p.y-13,tw+20,25,8);ctx.fill();ctx.stroke();ctx.fillStyle=activePart===id?'#baffef':'#e2edf4';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(name,p.x,p.y);
    }
  }
  function render(){frame++;
    const rect=cvs.getBoundingClientRect();if(rect.width>0&&rect.height>0&&(Math.abs(rect.width-width)>1||Math.abs(rect.height-height)>1)){
      width=rect.width;height=rect.height;dpr=Math.min(window.devicePixelRatio||1,2);cvs.width=Math.round(width*dpr);cvs.height=Math.round(height*dpr);ctx.setTransform(dpr,0,0,dpr,0,0);
    }
    ground();scale();drawFruit();board(.52,-.56,1.45,2.18,'#23486b','ESP32','esp');board(2.85,-1.25,1.07,1.25,'#503f6d','AMP','amp');speaker();drawWires();dimensionLabels();
    requestAnimationFrame(render);
  }
  function selected(id){activePart=id;const p=parts[id];$('twin-selected-title').textContent=p.name;$('twin-selected-desc').textContent=p.sub;$('twin-selected-spec').textContent=p.spec;document.querySelectorAll('[data-part]').forEach(e=>e.classList.toggle('selected',e.dataset.part===id));}
  function setFruit(mg){fruitGrams=Number(mg)/1000;}
  function sync(){const raw=Number(($('labweight')?.textContent||'0').replace(/[^\d.]/g,''));if(Number.isFinite(raw))fruitGrams=raw;
    const g=$('grade-big')?.textContent;$('twin-grade').textContent=g||'等待放果';$('twin-mass').textContent=(Number.isFinite(raw)?raw:0).toFixed(1)+' g';
    const el=$('twin-power-state');if(el)el.textContent=faults.power?'模拟断电':'低压电源 / 正常';
    const cnt=['small','medium','large'].map(z=>Number($('c-'+z)?.textContent||0));$('twin-count').textContent=cnt.reduce((a,b)=>a+b,0)+' 次';
    requestAnimationFrame(()=>{});
  }
  function setFault(name,off){if(!(name in faults))return;faults[name]=!!off;const cmd=$('fault-'+name);if(cmd){cmd.classList.toggle('danger',!!off);cmd.textContent=off?'恢复'+(name==='dt'?'HX711数据线':name==='audio'?'音频DIN':'主电源'):'拔掉'+(name==='dt'?'HX711数据线':name==='audio'?'音频DIN':'主电源')}
    if(name==='dt')window.__twinDTPresent=!off;
    if(name==='audio')window.__twinAudioPresent=!off;
    if(name==='power')window.__twinPowerPresent=!off;
    const e=$('twin-faultstate');e.textContent=Object.entries(faults).filter(([k,v])=>v).map(([k])=>({dt:'HX711 DT / SCK 断线',audio:'I²S DIN 断线',power:'主电源断开'}[k])).join('、')||'所有连接完好';
    sync();}
  cvs.addEventListener('pointerdown',e=>{pointer={x:e.clientX,y:e.clientY};rotateSinceDown=0;cvs.setPointerCapture(e.pointerId);});
  cvs.addEventListener('pointermove',e=>{if(!pointer)return;const dx=e.clientX-pointer.x,dy=e.clientY-pointer.y;yaw+=dx*.0075;pitch=Math.max(.23,Math.min(1.44,pitch+dy*.006));rotateSinceDown+=Math.abs(dx)+Math.abs(dy);pointer={x:e.clientX,y:e.clientY};});
  cvs.addEventListener('pointerup',e=>{if(rotateSinceDown>8)return;const r=cvs.getBoundingClientRect();const x=e.clientX-r.left,y=e.clientY-r.top;let best=null,dist=Infinity;for(const [id,p] of Object.entries(parts)){const a=screen(p.at),d=Math.hypot((x-a.x),(y-a.y));if(d<dist){dist=d;best=id}}if(best&&dist<150)selected(best)});
  cvs.addEventListener('wheel',e=>{e.preventDefault();zoom=Math.max(.55,Math.min(1.6,zoom*(e.deltaY>0?.91:1.1)));},{passive:false});
  cvs.addEventListener('dragover',e=>{e.preventDefault();cvs.classList.add('drag-over')});
  cvs.addEventListener('dragleave',()=>cvs.classList.remove('drag-over'));
  cvs.addEventListener('drop',e=>{e.preventDefault();cvs.classList.remove('drag-over');const grams=Number(e.dataTransfer.getData('text/fruit-grams'));if(grams>=0&&grams<=5000){$('labvalue').value=grams;$('btn-custom').click();selected('scale')}});
  document.querySelectorAll('[draggable="true"][data-grams]').forEach(el=>el.addEventListener('dragstart',e=>e.dataTransfer.setData('text/fruit-grams',el.dataset.grams)));
  document.querySelectorAll('[data-part]').forEach(el=>el.onclick=()=>selected(el.dataset.part));
  for(const fault of Object.keys(faults))$('fault-'+fault).addEventListener('click',()=>setFault(fault,!faults[fault]));
  $('twin-orbit-reset').onclick=()=>{yaw=-.52;pitch=.69;zoom=1;selected('scale')};
  $('twin-toggle-wires').onclick=()=>{showWires=!showWires;$('twin-toggle-wires').classList.toggle('selected',showWires)};
  $('twin-toggle-glass').onclick=()=>{showGlass=!showGlass;$('twin-toggle-glass').classList.toggle('selected',showGlass)};
  $('twin-snapshot').onclick=()=>{let link=document.createElement('a');link.download='usgm05-digital-twin-3d.png';link.href=cvs.toDataURL('image/png');link.click()};
  selected('scale');sync();setInterval(sync,230);requestAnimationFrame(render);
  window.USGM_3D={setFault,setFruit,selected,getGeometry:()=>({yaw,pitch,zoom,parts,wires,faults})};
})();
