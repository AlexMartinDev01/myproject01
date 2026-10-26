import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright-core';
const out='wokwi-proof';
fs.mkdirSync(path.join(out,'videos'),{recursive:true});
const logs=[], passed={opened:false,diagram:false,firmware:false,started:false};
const browser=await chromium.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--disable-gpu','--use-angle=swiftshader','--no-first-run']});
const context=await browser.newContext({viewport:{width:1550,height:920},deviceScaleFactor:1,recordVideo:{dir:path.join(out,'videos'),size:{width:1550,height:920}}});
const page=await context.newPage();
page.setDefaultTimeout(12000);
page.on('pageerror',x=>logs.push('PAGEERROR '+String(x).slice(0,400)));
page.on('console',x=>{if(x.type()==='error')logs.push('CONSOLE '+x.text().slice(0,400))});
const shot=async name=>{try{await page.screenshot({path:path.join(out,name+'.png')})}catch(e){logs.push('SCREENSHOT '+e)}};
try {
 await page.goto('https://wokwi.com/projects/new/esp32-c3',{waitUntil:'domcontentloaded',timeout:50000});
 await page.waitForTimeout(5000);
 passed.opened=await page.locator('button').filter({hasText:'diagram.json'}).count()>0;
 await shot('01-real-wokwi-editor');
 if(!passed.opened)throw Error('Wokwi editor did not open');
 const diagram=fs.readFileSync('usgm-05/firmware/diagram.json','utf8');
 JSON.parse(diagram);
 await page.locator('button').filter({hasText:'diagram.json'}).first().click();
 await page.waitForTimeout(1000);
 await context.grantPermissions(['clipboard-read','clipboard-write'],{origin:'https://wokwi.com'});
 await page.locator('.monaco-editor').first().click({position:{x:230,y:110}});
 await page.evaluate(async data => { await navigator.clipboard.writeText(data); },diagram);
 await page.keyboard.press('Control+A');
 await page.keyboard.press('Control+V'); // paste as one document change, no auto-indent per line
 await page.waitForTimeout(5500);
 const editorProbe=await page.evaluate(()=>({
  monoModels:!!(window.monaco && window.monaco.editor && window.monaco.editor.getModels),
  visibleEditor:document.querySelector('.monaco-editor .view-lines')?.innerText.slice(0,550),
  editorErrors:document.querySelectorAll('.monaco-editor .squiggly-error').length
 }));
 fs.writeFileSync(path.join(out,'editor-probe.json'),JSON.stringify(editorProbe,null,2));
 console.log('PASTED_DIAGRAM',JSON.stringify(editorProbe).slice(0,1300));
 await shot('02-usgm-real-components-wires');
 const ds=await page.evaluate(()=>({body:document.body.innerText.slice(-3000),ids:[...document.querySelectorAll('[id]')].filter(x=>/hx711|sim_speaker/.test(x.id)).slice(0,30).map(x=>x.id)}));
 fs.writeFileSync(path.join(out,'diagram-state.json'),JSON.stringify(ds,null,2));
 console.log('DIAGRAM',JSON.stringify(ds).slice(0,2000));
 passed.diagram=ds.ids.length>=1;
 const sketch=page.locator('button').filter({hasText:/sketch\.ino|main\.c/});
 if(await sketch.count())await sketch.first().click();
 await page.waitForTimeout(800);
 await page.locator('.monaco-editor').first().click({position:{x:230,y:80}});
 await page.keyboard.press('F1');
 await page.waitForTimeout(1000);
 await shot('03-real-wokwi-upload-menu');
 console.log('PALETTE',(await page.locator('body').innerText()).slice(-1000));
 const chooserPromise=page.waitForEvent('filechooser',{timeout:15000});
 await page.keyboard.insertText('Upload Firmware');
 await page.keyboard.press('Enter');
 const chooser=await chooserPromise;
 const firmware='wokwi-bin/fullflash.bin';
 console.log('FIRMWARE_BIN_BYTES',fs.statSync(firmware).size);
 await chooser.setFiles(firmware);
 passed.firmware=true;
 await page.waitForTimeout(22000);
 await shot('04-usgm-real-firmware-loaded');
 console.log('AFTER_UPLOAD',(await page.locator('body').innerText()).slice(-4200));


 // Genuine Wokwi load cell: clicking the center of the weighing beam opens
 // the official 5kg slider (confirmed by previous real-run DOM evidence).
 // Wokwi UI rounds its slider in 0.01kg increments (10 grams).
 const hx=page.locator('#hx711').first();
 const rect=await hx.boundingBox().catch(()=>null);
 if(!rect) throw Error('Wokwi HX711 DOM element missing');
 const target={x:rect.x+rect.width*0.75,y:rect.y+rect.height*0.48};
 await page.mouse.click(target.x,target.y);
 await page.waitForTimeout(950);
 const slider=page.locator('input[type="range"]').first();
 if(await slider.count()===0)throw Error('HX711 load slider did not appear after clicking weighing cell');
 const sliderAttrs=await slider.evaluate(el=>({min:el.min,max:el.max,step:el.step,value:el.value}));
 fs.writeFileSync(path.join(out,'real-hx711-slider.json'),JSON.stringify(sliderAttrs,null,2));
 console.log('REAL_HX711_LOAD_SLIDER',JSON.stringify(sliderAttrs));
 const sequence=[['tare_0','0'],['small_60g','0.06'],['empty_a','0'],['medium_90g','0.09'],['empty_b','0'],['large_130g','0.13'],['empty_c','0']];
 const marks=[];
 for(const [label,kg] of sequence){
   await slider.fill(kg);
   await page.waitForTimeout(2350);
   const serial=await page.locator('body').innerText();
   const recent=serial.match(/USGM_GRADE[^\\r\\n]*|WOKWI_AUDIO_INTENT[^\\r\\n]*/g)||[];
   console.log('REAL_WEIGHT_SET',label,kg,JSON.stringify(recent.slice(-7)));
   marks.push({label,kg,recent:recent.slice(-7)});
   await shot('weight-'+label);
 }
 fs.writeFileSync(path.join(out,'grade-markers.json'),JSON.stringify(marks,null,2));
 await page.waitForTimeout(4000);
 const final=await page.locator('body').innerText();
 fs.writeFileSync(path.join(out,'body-final.txt'),final);
 await shot('07-real-wokwi-final');
 passed.started=final.includes('USGM');
} catch(e){logs.push('EXCEPTION '+String(e));console.log('CAPTURE_ERROR',String(e));await shot('error-state')}
finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify({passed,logs,finalUrl:page.url()},null,2));console.log('RESULTS',JSON.stringify(passed),'ERRORS',JSON.stringify(logs.slice(0,15)));await context.close();await browser.close()}