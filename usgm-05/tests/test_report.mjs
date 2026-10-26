import test from 'node:test';import assert from 'node:assert/strict';
import {summarise,gram,reportHtml,escapeHtml} from '../firmware/main/assets/report.mjs';
test('zero data explicitly yields no accuracy',()=>{const s=summarise([]);assert.equal(s.total,0);assert.equal(s.acceptedAccuracy,null);assert.equal(s.latencyP95,null);});
test('correct denominator uses independently verified subset',()=>{
 const events=[{grade:'small',reference:'small',mode:'SIMULATION',latency_ms:510},
               {grade:'large',reference:'medium',mode:'HX711',latency_ms:880},
               {grade:'medium',reference:'invalid',mode:'HX711',latency_ms:1200}];
 const s=summarise(events);assert.equal(s.total,3);assert.equal(s.verified,2);assert.equal(s.correct,1);
 assert.equal(s.acceptedAccuracy,.5);assert.equal(s.unverified,1);assert.equal(s.hardware,2);assert.equal(s.simulation,1);assert.equal(s.latencyP95,1200);
 assert.equal(s.confusion.medium.large,1);
});
test('simulated results are labeled',()=>{
 const txt=reportHtml([{grade:'small',mode:'SIMULATION',reference:'invalid',latency_ms:900}],{batch:'B1'});
 assert.match(txt,/仅模拟数据/);assert.match(txt,/不得报告准确率/);
});
test('HTML escapes report titles',()=>{
 assert.equal(escapeHtml('<script>'), '&lt;script&gt;');
 assert.doesNotMatch(reportHtml([],{label:'<script>alert(1)</script>'}),/<script>/);
});
test('format mg',()=>assert.equal(gram(105000),'105.0'));
test('null accuracy when references missing despite many events',()=>{
 const events=Array.from({length:300},()=>({grade:'large',reference:'invalid',mode:'HX711',latency_ms:500}));
 assert.equal(summarise(events).acceptedAccuracy,null);
});
