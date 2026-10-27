from pathlib import Path
import json
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
with sync_playwright() as p:
 b=p.chromium.launch(executable_path='/usr/bin/chromium' if Path('/usr/bin/chromium').exists() else None,headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
 page=b.new_page(viewport={'width':390,'height':844},device_scale_factor=1)
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.set_content((root/'index.html').read_text(),wait_until='domcontentloaded');page.wait_for_timeout(1500)
 page.screenshot(path=str(root/'docs/mobile_portrait.png'),full_page=True)
 canvas=page.locator('#twin-canvas').bounding_box();screen=page.locator('#phone').bounding_box()
 assert canvas['width']<390 and canvas['width']>300,(canvas)
 assert page.frame_locator('#phone').locator('#weight').count()==1
 assert not errors,errors
 print(json.dumps({'mobile_canvas':canvas,'mobile_phone':screen,'errors':errors},ensure_ascii=False))
 b.close()
