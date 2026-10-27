from pathlib import Path
from bs4 import BeautifulSoup
import re,base64,hashlib
BASE=Path('/mnt/data/USGM-05_5kg圆形秤_手机调控全过程_可交互仿真.html')
ROOT=Path(__file__).resolve().parents[1]
soup=BeautifulSoup(BASE.read_text(encoding='utf-8'),'html.parser')
header=soup.select_one('.headbrand h1');header.string='USGM-05 · 数字孪生实验室'
soup.select_one('.headbrand p').string='同源 C→WebAssembly 算法 · 三维几何元器件 · 原版手机前端 · 中文语音与报告'
soup.select_one('.caution').clear()
caution=soup.select_one('.caution');caution.append(BeautifulSoup('<strong>可信范围：</strong>这是浏览器三维几何与HX711数据模型，分级调用原C核心WASM，手机是实际固件网页；浏览器播放固件同源音频，不是ESP32芯片级电气仿真、实物声学验收或真实称重。模型尺寸未实测，禁止将模拟数值当作产品精度。','html.parser'))
soup.select_one('.floating-status').string='3D 几何模型 · 可旋转缩放'
soup.select_one('.card h2').string='01 · 高保真三维元器件与接线工作台'
photo=soup.select_one('.photo-wrap');oldimg=photo.select_one('img');src=oldimg['src'];oldfruit=photo.select_one('#fruit');oldlabel=photo.select_one('#fruit-label');
viewport='''<div class="lab-viewport" id="lab-viewport">
<canvas id="twin-canvas" aria-label="三维视角：5kg圆形秤、HX711、ESP32-C3、MAX98357A、喇叭及彩色连接线；可拖动旋转、滚轮缩放、点击元器件"></canvas>
<div class="viewport-top"><div class="sim-chip">● OFFLINE DIGITAL TWIN · 浏览器模拟</div><div class="view-controls">
<button id="twin-orbit-reset" type="button" title="复位三维相机">复位视角</button>
<button id="twin-toggle-wires" type="button" class="selected">彩色连线</button>
<button id="twin-toggle-glass" type="button" class="selected">透明圆盘</button>
<button id="twin-snapshot" type="button">保存截图</button></div></div>
<div class="viewport-bottom"><div class="viewport-readout"><span>虚拟秤台重量 · 仅软件模拟</span><b id="twin-mass">0.0 g</b><small><span style="display:inline;color:#b5d7e1;margin:0">最近等级：</span><strong id="twin-grade">等待放果</strong></small></div>
<div class="viewport-hint" id="twin-hint">拖动旋转 · 滚轮缩放 · 点击元器件检查<br>拖动下方水果到虚拟秤台</div></div></div>
<div class="lab-toolstrip"><span class="mini-label">放置虚拟水果</span>
<button class="fruit-pill" draggable="true" data-grams="75" id="twin-fruit-small" type="button">🥝 75g <em>小果</em></button>
<button class="fruit-pill" draggable="true" data-grams="95" id="twin-fruit-medium" type="button">🥝 95g <em>中果</em></button>
<button class="fruit-pill" draggable="true" data-grams="135" id="twin-fruit-large" type="button">🥝 135g <em>大果</em></button>
<button class="fruit-pill" id="twin-fruit-remove" type="button">↺ 取走</button>
<span style="margin-left:auto;font-size:10px;color:#8fb6c6">正在记录 <b id="twin-count">0 次</b></span>
</div>
<div class="twin-inspector"><div class="inspector-box"><strong>元器件检查</strong><div class="part-tabs">
<button data-part="scale" type="button">5kg秤台</button><button data-part="hx" type="button">HX711</button><button data-part="esp" type="button">ESP32-C3</button><button data-part="amp" type="button">MAX98357A</button><button data-part="speaker" type="button">喇叭</button></div>
<strong id="twin-selected-title">5kg圆形秤</strong><small id="twin-selected-desc"></small><small class="inspector-spec" id="twin-selected-spec"></small>
</div><div class="inspector-box"><strong>线路与故障注入</strong><div class="fault-buttons">
<button id="fault-dt" type="button">拔掉HX711数据线</button><button id="fault-audio" type="button">拔掉音频DIN</button><button id="fault-power" type="button">拔掉主电源</button></div>
<small id="twin-faultstate" class="mini-status">所有连接完好</small><small>供电：<b id="twin-power-state">低压电源 / 正常</b></small>
<small>说明：虚拟断线按软件规则验证；不是模拟芯片损坏、真实过载或功放失真。</small></div></div>
<details class="twin-reference" style="margin-top:10px;color:#8fb2c2;font-size:11px"><summary style="cursor:pointer">与您选购的真实 5kg 圆形秤商品图片对照（展开）</summary>
<img id="twin-original-photo" alt="用户提供的5kg圆形秤商品参考照片" style="display:block;max-width:100%;max-height:240px;margin-top:10px;border-radius:9px" /></details>
<div class="twin-low-trust">本工作台使用真实三维坐标、透视投影和轨道相机，PCB、秤台与导线是程序化几何模型，非精密扫描。原商品的背面焊点、尺寸及真实噪声仍待实物核对；没有实体TM1637数码管。</div>
<div style="display:none"><div id="fruit"></div><div id="fruit-label"></div></div>'''
new=BeautifulSoup(viewport,'html.parser');new.select_one('#twin-original-photo')['src']=src
photo.replace_with(new)
# Render readout / original data beneath viewport; preserve original hardware stats.
ph=soup.select_one('.phone-desc');ph.select_one('b').string='04 · 实际手机网页嵌入模拟器';ph.select_one('.sub').string='固件同源 UI · 阈值/标定/报告均可操作'
soup.select_one('.phone-notice').clear();soup.select_one('.phone-notice').append(BeautifulSoup('手机可<strong>实际点击并保存区间</strong>，监控与3D同源重量。断网不影响浏览器离线演示；将来实物由ESP32本地热点提供同一页面。','html.parser'))
# Add source references under intro.
footer=BeautifulSoup('''<div class="card" style="margin-top:8px"><h2>05 · 测试与真实性说明</h2><p style="color:#a4c4d3;font-size:12px;line-height:1.9;margin:6px 0">软件模拟真实的放果、去皮、分级、计数和报表业务流程；重量与24位HX711数值是<strong>模拟模型</strong>。C算法来自原来的 <code>usgm_core.c</code>，通过WebAssembly执行；中文录音来自固件的PCM音频素材。三维模型与接线属于几何与逻辑层，并不包含完整的模拟电路SPICE或ESP32-C3的I²S物理仿真。</p><p style="color:#9ec5cd;font-size:11px">需要实物才能证明：HX711供电电平、5kg传感器真实精度、ESP32 Wi-Fi SoftAP实机连接、MAX98357A音质和功放电源安全。报告将数据来源标记为模拟。</p></div>''','html.parser')
soup.select_one('section.left').append(footer)
css=soup.new_tag('style',id='digital-twin-theme');css.string=(ROOT/'src/theme.css').read_text();soup.head.append(css)
# Patch script in place carefully. old script injected in soup classic global scope. all original functions survive.
script=soup.find('script')
s=script.string or script.text
old='const isValid=connected&&(mode===\'SIMULATION\'||calibrated);'
assert old in s
s=s.replace(old,"const isValid=connected && window.__twinDTPresent!==false && window.__twinPowerPresent!==false && (mode==='SIMULATION'||calibrated);")
old='function playAudio(grade){if(!audioEnabled||!audioCtx)return;'
assert old in s
s=s.replace(old,"function playAudio(grade){if(window.__twinAudioPresent===false || window.__twinPowerPresent===false){info('虚拟音频DIN已断开或主电源关闭：禁止播放');return;} if(!audioEnabled||!audioCtx)return;")
old='async function backendFetch(url,opts={}){if(!wasm)return jsonResponse'
assert old in s
s=s.replace(old,"async function backendFetch(url,opts={}){if(window.__twinPowerPresent===false)return jsonResponse({ok:false,message:'虚拟主电源已断开，设备离线'},503);if(!wasm)return jsonResponse")
# upgrade preset and drop actions and preserve old buttons.
s += '''\n// Shared control bridge for the independent 3D geometric scene.\nwindow.__usgmTwinSource='BROWSER_MODEL';\ndocument.getElementById('twin-fruit-small').addEventListener('click',()=>document.querySelector('.controls button:nth-child(1)').click());\ndocument.getElementById('twin-fruit-medium').addEventListener('click',()=>document.querySelector('.controls button:nth-child(2)').click());\ndocument.getElementById('twin-fruit-large').addEventListener('click',()=>document.querySelector('.controls button:nth-child(3)').click());\ndocument.getElementById('twin-fruit-remove').addEventListener('click',()=>document.getElementById('btn-zero').click());\n'''
script.string=s
extra=soup.new_tag('script',id='twin-geometry');extra.string=(ROOT/'src/scene3d.js').read_text();soup.body.append(extra)
# Save standalone offline HTML, no CDN dependencies.
out=ROOT/'index.html';out.write_text('<!doctype html>\n'+str(soup).replace('<!DOCTYPE html>',''),encoding='utf-8')
# source provenance, WASM bytes extraction
m=re.search(r'WebAssembly\.instantiate\(b64bytes\("([A-Za-z0-9+/=]+)"\)',s)
assert m,'existing real WebAssembly module must remain'
wb=base64.b64decode(m.group(1)); assert wb[:4]==b'\0asm'; (ROOT/'docs/usgm_core_original.wasm').write_bytes(wb)
(ROOT/'docs/SOURCE_PROVENANCE.md').write_text(f'''# 源代码复用审计\n\n- 原始项目：USGM-05 现有浏览器/手机仿真 HTML，用户此前已提供，同源固件页面和 C 核心 WebAssembly。\n- 真实业务算法：`docs/usgm_core.c` / `docs/usgm_core.h`，wasm binary `docs/usgm_core_original.wasm`，SHA256 `{hashlib.sha256(wb).hexdigest()}`。\n- 原移动端网页源码快照：`docs/index.html`、`docs/app.js`、`docs/report.mjs`。\n- 新版浏览器图形部分：程序化三维几何、轨道相机及引脚/导线拓扑规则；**不是 SPICE 真实电路**。\n- 手机 iframe 来自原项目已嵌入的固件网页；不是新绘制的伪手机。\n- 虚拟原始采样值：根据模拟负载生成，不是原传感器实测。\n- 声音：原 PCM 文件经 WebAudio 播放，不是 MAX98357A 电声仿真。\n- 不采购 TM1637/OLED/RGB/蜂鸣器/面包板。\n''',encoding='utf-8')
print('built',out,'bytes',out.stat().st_size,'wasm',len(wb),'sha256',hashlib.sha256(wb).hexdigest()[:16])
