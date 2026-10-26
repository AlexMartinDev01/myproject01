import {gram,GRADE_ZH,summarise,reportHtml,escapeHtml} from './report.mjs';
const $=id=>document.getElementById(id);
let status=null;let activeTab='dashboard';let cachedEvents=[];let loadedSettings=false;
const stateZh={arming:'待空秤确认',ready:'等待放果',settling:'重量稳定判断中',wait_removal:'已分级，等待取走'};
function notice(text,error=false){$('message').innerHTML=`<div class="alert ${error?'error':'success'}">${escapeHtml(text)}</div>`;setTimeout(()=>$('message').replaceChildren(),5000);}
async function request(path,body){
 const opts={cache:'no-store'};
 if(body!==undefined){opts.method='POST';opts.headers={'Content-Type':'application/json'};opts.body=JSON.stringify(body);}
 const response=await fetch(path,opts);const payload=await response.json();
 if(!response.ok||payload.ok===false)throw new Error(payload.message||`HTTP ${response.status}`);
 return payload;
}
function go(tab){activeTab=tab;document.querySelectorAll('nav button').forEach(x=>x.classList.toggle('active',x.dataset.tab===tab));document.querySelectorAll('.tab').forEach(x=>x.classList.toggle('show',x.id===`tab-${tab}`));if(tab==='report')refreshReport();}
document.querySelectorAll('nav button').forEach(x=>x.addEventListener('click',()=>go(x.dataset.tab)));
function label(mg){if(!status||status.stale)return '无有效数据';if(mg<status.settings.min_mg)return '未放果';if(mg<status.settings.small_upper_mg)return '小果';if(mg<status.settings.medium_upper_mg)return '中果';return '大果';}
function update(s){
 status=s;const sim=s.mode==='SIMULATION';
 $('mode-warning').className=sim?'alert':'banner';
 $('mode-warning').textContent=sim?'模拟模式：这些读数不是来自真实传感器，不能当作硬件测试结果。':'HX711 实测模式：称重准确度取决于传感器安装、标定与参考砝码。';
 $('weight').textContent=s.stale?'—':`${gram(s.weight_mg)} g`;
 $('grade').textContent=label(s.weight_mg);
 $('state-label').textContent=stateZh[s.state]||s.state;
 $('state-label').className=`tag ${s.stale?'bad':s.state==='ready'?'good':''}`;
 $('n-small').textContent=s.counts.small;$('n-medium').textContent=s.counts.medium;$('n-large').textContent=s.counts.large;
 $('sample-age').textContent=s.stale?'重量采样无效':`样本年龄 ${s.sample_age_ms} ms`;
 $('mode-here').textContent=`当前：${s.mode} · 批次 ${s.batch} · ${s.events}/${s.limit} 条记录`;
 $('cal-status').textContent=['等待操作','采样零点中','跨度标定中','标定完成','标定失败'][s.cal_state] || '未知';
 $('sys-status').textContent=`存储：${s.log_ok?'正常':'异常'} · HX711标定：${s.calibrated?'完成':'未完成'} · 当前原始值：${s.last_raw} · ${s.log_full?'记录已满：请导出并新建批次':''}`;
 if(!s.log_ok)$('sys-status').textContent+=' · 注意：日志可能不完整，禁止以当前日志出具正式性能结论';
 if(!loadedSettings){$('min').value=s.settings.min_mg/1000;$('small-upper').value=s.settings.small_upper_mg/1000;$('medium-upper').value=s.settings.medium_upper_mg/1000;$('stable-ms').value=s.settings.stable_ms;loadedSettings=true;}
}
async function poll(){try{update(await request('/api/status'));}catch(e){$('state-label').textContent='设备离线';$('state-label').className='tag bad';}}
setInterval(poll,900);poll();
async function action(path,body){try{const r=await request(path,body);notice(r.message||'操作成功');await poll();return true;}catch(e){notice(e.message,true);return false;}}
$('settings-form').addEventListener('submit',async e=>{
 e.preventDefault();const min=Math.round(Number($('min').value)*1000),a=Math.round(Number($('small-upper').value)*1000),b=Math.round(Number($('medium-upper').value)*1000),stable=Math.round(Number($('stable-ms').value));
 if(!(0<=min&&min<a&&a<b)){notice('边界必须依次递增',true);return;}
 await action('/api/settings',{min_mg:min,small_upper_mg:a,medium_upper_mg:b,stable_ms:stable});
});
$('sim-go').onclick=async()=>{const mg=Math.round(Number($('sim-grams').value)*1000);if(!Number.isSafeInteger(mg))return notice('重量无效',true);await action('/api/simulate',{weight_mg:mg});};
document.querySelectorAll('.sim-preset').forEach(x=>x.onclick=async()=>{const g=Number(x.dataset.grams);$('sim-grams').value=g;await action('/api/simulate',{weight_mg:g*1000});});
$('switch-sim').onclick=()=>action('/api/mode',{mode:'SIMULATION'});
$('switch-hx').onclick=()=>action('/api/mode',{mode:'HX711'});
$('zero').onclick=()=>action('/api/cal/zero',{});
$('span').onclick=()=>{const mg=Math.round(Number($('reference-g').value)*1000);if(mg<20000)return notice('参考质量至少20g',true);return action('/api/cal/span',{reference_mg:mg});};
$('batch-new').onclick=async()=>{if(confirm('确定已导出当前 CSV？此操作将永久清空该批次记录！')){if(await action('/api/batch/new',{})){cachedEvents=[];loadedSettings=false;}}};
async function fetchAllEvents(){const out=[];let total=Infinity;
 while(out.length<total){const page=await request(`/api/events?offset=${out.length}&limit=50`);total=page.total;out.push(...page.items);if(page.items.length===0)break;}
 return out;
}
async function refreshReport(){
 try{
  cachedEvents=await fetchAllEvents();
  const summary=summarise(cachedEvents);
  $('report-document').innerHTML=reportHtml(cachedEvents,{batch:status?.batch??'',logOk:status?.log_ok!==false});
  if(!summary.verified)notice('报告没有独立参考真值，仅显示分级数量，准确率暂不可计算');
  let html='<table><thead><tr><th>ID</th><th>重量</th><th>自动结果</th><th>独立参考</th><th>操作</th></tr></thead><tbody>';
  for(const e of cachedEvents.slice(-40).reverse()){
    html+=`<tr><td>${e.id}</td><td>${gram(e.weight_mg)}g</td><td>${GRADE_ZH[e.grade]}</td><td><select id="reference-${e.id}" aria-label="事件${e.id}的参考等级"><option value="0">未标注</option>${['small','medium','large'].map((g,i)=>`<option value="${i+1}" ${e.reference===g?'selected':''}>${GRADE_ZH[g]}</option>`).join('')}</select></td><td><button class="ref-save" data-id="${e.id}">保存</button></td></tr>`;
  }
  html+='</tbody></table>';$('event-table').innerHTML=html;
  document.querySelectorAll('.ref-save').forEach(btn=>btn.addEventListener('click',async()=>{
    const grade=Number($(`reference-${btn.dataset.id}`).value);
    if(grade===0)return notice('请选择真实参考等级',true);
    if(await action('/api/reference',{event_id:Number(btn.dataset.id),reference_grade:grade}))refreshReport();
  }));
 }catch(e){notice('报告加载失败：'+e.message,true);}
}
$('report-refresh').onclick=refreshReport;
$('report-csv').onclick=()=>{window.location.href='/api/report.csv';};
$('report-print').onclick=async()=>{if(!cachedEvents.length)await refreshReport();window.print();};
