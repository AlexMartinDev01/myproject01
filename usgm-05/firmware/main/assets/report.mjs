/* Shared, pure report calculations. No cloud and no DOM dependencies. */
export const GRADES=['invalid','small','medium','large'];
export const GRADE_ZH={invalid:'未标注',small:'小果',medium:'中果',large:'大果'};
export const gram=mg=>Number.isFinite(mg)?(mg/1000).toFixed(1):'—';
export const escapeHtml=v=>String(v).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
export function summarise(events){
  const grades={small:0,medium:0,large:0};
  const confusion={small:{small:0,medium:0,large:0},medium:{small:0,medium:0,large:0},large:{small:0,medium:0,large:0}};
  let correct=0,verified=0,simulation=0,hardware=0;
  const lat=[];
  for(const e of events){
    if(!['small','medium','large'].includes(e.grade))continue;
    grades[e.grade]++;
    if(e.mode==='HX711')hardware++;else simulation++;
    if(Number.isFinite(e.latency_ms)&&e.latency_ms>=0)lat.push(e.latency_ms);
    if(['small','medium','large'].includes(e.reference)){
      verified++;confusion[e.reference][e.grade]++;
      if(e.reference===e.grade)correct++;
    }
  }
  lat.sort((a,b)=>a-b);
  return {total:Object.values(grades).reduce((x,y)=>x+y,0),grades,
    verified,correct,wrong:verified-correct,
    acceptedAccuracy:verified?correct/verified:null,
    unverified:Object.values(grades).reduce((x,y)=>x+y,0)-verified,
    simulation,hardware,
    latencyP95:lat.length?lat[Math.ceil(lat.length*.95)-1]:null,
    confusion, note:'Only independently labeled accepted events contribute to accuracy. Unobserved attempts cannot be used to claim coverage.'};
}
export function reportHtml(events,{batch='',label='USGM-05测试报告',logOk=true}={}){
  const s=summarise(events);
  const row=(label,value)=>`<tr><th>${escapeHtml(label)}</th><td>${escapeHtml(value)}</td></tr>`;
  const cell=(n)=>n?String(n):'0';
  const gradeTable=['small','medium','large'].map(g=>row(GRADE_ZH[g],`${s.grades[g]}次`)).join('');
  const conf=['small','medium','large'].map(g=>`<tr><th>${GRADE_ZH[g]}</th>${['small','medium','large'].map(h=>`<td>${cell(s.confusion[g][h])}</td>`).join('')}</tr>`).join('');
  const accuracy=s.acceptedAccuracy===null?'无独立真值，不得报告准确率':`${(s.acceptedAccuracy*100).toFixed(2)}%（${s.correct}/${s.verified}）`;
  const type=s.hardware>0?'包含HX711输入记录（尚不代表外部地秤显示监听已验证）':'仅模拟数据：不构成硬件性能证据';
  return `<article><h1>${escapeHtml(label)}</h1><p>批次 ${escapeHtml(batch)} · ${escapeHtml(type)}</p><table>${row('已接受事件',s.total)}${gradeTable}${row('独立参考标注',s.verified)}${row('未标注',s.unverified)}${row('有真值事件准确率',accuracy)}${row('响应延迟P95',s.latencyP95===null?'无有效数据':`${s.latencyP95} ms`)}${row('日志完整性',logOk?'未报告持久化故障':'警告：日志可能不完整')}</table><h2>混淆矩阵（行=参考、列=预测）</h2><table><tr><th></th><th>小果</th><th>中果</th><th>大果</th></tr>${conf}</table><p>说明：未检测到的放果尝试不会自动出现在记录中；覆盖率与漏检率需要独立的试验次数记录。所有比例只针对可核实的样本。报告不证明任何实际外部电子秤兼容性。</p></article>`;
}
