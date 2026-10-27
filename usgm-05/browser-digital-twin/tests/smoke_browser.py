from pathlib import Path
import json
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
with sync_playwright() as p:
 browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium',args=['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader'])
 page=browser.new_page(viewport={'width':1570,'height':960},device_scale_factor=1)
 errors=[]
 page.on('pageerror',lambda e: errors.append(str(e)))
 page.set_content((root/'index.html').read_text(encoding='utf-8'),wait_until='domcontentloaded',timeout=30000)
 page.wait_for_timeout(3200)
 print('title',page.title(),'boot',page.locator('#bootmsg').inner_text())
 print('viewport',page.locator('#twin-canvas').bounding_box())
 print('selected',page.locator('#twin-selected-title').inner_text())
 print('counts',page.locator('#c-small').inner_text(),page.locator('#c-medium').inner_text(),page.locator('#c-large').inner_text())
 print('frame-title',page.frame_locator('#phone').locator('title').count())
 print('errors',errors[:10]);page.screenshot(path=str(root/'docs/first_browser.png'),full_page=False)
 browser.close()
