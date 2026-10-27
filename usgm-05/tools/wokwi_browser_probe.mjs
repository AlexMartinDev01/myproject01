import fs from "node:fs";
import {chromium} from "playwright";

const dir="wokwi-proof";
fs.mkdirSync(dir,{recursive:true});
const browser=await chromium.launch({headless:true,args:["--no-sandbox","--disable-dev-shm-usage"]});
const page=await browser.newPage({viewport:{width:1560,height:920},deviceScaleFactor:1});
const errors=[];
page.on("pageerror",e=>errors.push("PAGE "+String(e).slice(0,350)));
page.on("console",m=>{if(m.type()==="error")errors.push("CONSOLE "+m.text().slice(0,350))});
page.on("requestfailed",r=>errors.push("REQUEST "+r.url().slice(0,140)+" "+r.failure()?.errorText));
try {
 const u="https://wokwi.com/projects/new/esp32-c3";
 console.log("NAVIGATE",u);
 await page.goto(u,{waitUntil:"domcontentloaded",timeout:85000});
 await page.waitForTimeout(9000);
 console.log("CURRENT_URL",page.url());
 console.log("TITLE",await page.title());
 const state=await page.evaluate(()=>({
  text:document.body.innerText.slice(0,14000),
  buttons:[...document.querySelectorAll("button")].slice(0,70).map(x=>({text:x.innerText.slice(0,80),title:x.getAttribute("title"),aria:x.getAttribute("aria-label")})),
  editors:document.querySelectorAll(".monaco-editor").length,
  tabs:[...document.querySelectorAll('[role="tab"]')].slice(0,35).map(x=>x.innerText)
 }));
 fs.writeFileSync(dir+"/probe.json",JSON.stringify({state,errors},null,2));
 console.log("BROWSER_STATE",JSON.stringify(state).slice(0,8000));
 await page.screenshot({path:dir+"/wokwi-editor.png",fullPage:true});
} catch(err) {
 console.log("BROWSER_ERROR",String(err));
 await page.screenshot({path:dir+"/error.png",fullPage:true}).catch(()=>{});
 fs.writeFileSync(dir+"/probe.json",JSON.stringify({error:String(err),errors},null,2));
 process.exitCode=1;
} finally {console.log("ERRORS",JSON.stringify(errors.slice(0,20)));await browser.close()}
