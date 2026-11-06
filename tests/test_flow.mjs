import assert from 'node:assert/strict';
const storage=new Map();
globalThis.localStorage={getItem(k){return storage.get(k)||null},setItem(k,v){storage.set(k,v)}};
const elements=new Map();
const events={};
const stub=(selector)=>{
 if(!elements.has(selector))elements.set(selector,{innerHTML:'',textContent:'',value:'',classList:{toggle(){},add(){},remove(){}},scrollTop:0,scrollHeight:1200,scrollTo(){}});
 return elements.get(selector);
};
globalThis.document={querySelector:stub,addEventListener(type,cb){events[type]=cb},body:{classList:{toggle(){}}},documentElement:{style:{setProperty(){}}}};
globalThis.window={alert(){},confirm(){return false}};
await import('../app.js');
function state(){return JSON.parse(storage.get('starweave_tarot_v01'))}
function click(action,value){
 const el={dataset:{action,value},querySelector(){return {classList:{add(){}}}}};
 events.click({target:{closest(){return el}}});
}
click('start');assert.equal(state().screen,'question');
stub('#questionInput').value='我怎样更好地考虑新的职业机会？';
click('toSpread');assert.equal(state().screen,'spread');
click('chooseSpread','analysis');
click('init');assert.equal(state().deck.length,78);assert.equal(new Set(state().deck).size,78);
click('toCut');click('toDraw');
assert.equal(state().screen,'draw');
for(let i=0;i<3;i++)click('autoPick');
assert.equal(state().screen,'reveal');
assert.equal(state().drawn.length,3);assert.equal(state().deck.length,75);
assert.equal(new Set(state().drawn.map(x=>x.cardId)).size,3);
assert.deepEqual(state().drawn.map(x=>x.positionKey),['present','obstacle','advice']);
const snap=JSON.stringify(state().drawn);
click('flipAll');click('complete');
assert.equal(state().screen,'result');assert.equal(JSON.stringify(state().drawn),snap);
click('saveReading');assert.equal(state().history.length,1);
click('history');click('openHistory','0');assert.equal(state().screen,'result');
assert.equal(JSON.stringify(state().drawn),snap);
console.log('PASS: Full flow, unique 78 deck, 3 draws, positions, reveal, immutable draws, saved history');
