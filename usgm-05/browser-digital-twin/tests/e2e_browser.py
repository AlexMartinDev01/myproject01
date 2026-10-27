from pathlib import Path
import json,hashlib,re
from playwright.sync_api import sync_playwright, expect
ROOT=Path(__file__).resolve().parents[1]
REPORT=ROOT/'docs/browser_e2e_results.json'
checks=[]
def ck(name,condition,detail=''):
    checks.append({'name':name,'ok':bool(condition),'detail':str(detail)[:280]})
    if not condition: raise AssertionError(f'{name}: {detail}')

def count(page):
    return [int(page.locator('#c-'+x).inner_text()) for x in ['small','medium','large']]

def step(page,n,g):
    page.locator('#twin-fruit-'+n).click()
    page.wait_for_timeout(1050)
    ck(f'{n} {g}g recorded',count(page)[['small','medium','large'].index(n)]>=1, count(page))
    ck(f'3D fruit {g}g',page.locator('#twin-mass').inner_text()==f'{g:.1f} g',page.locator('#twin-mass').inner_text())
    page.locator('#twin-fruit-remove').click()
    page.wait_for_timeout(500)

with sync_playwright() as p:
    browser=p.chromium.launch(headless=True,executable_path='/usr/bin/chromium' if Path('/usr/bin/chromium').exists() else None,args=['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader'])
    page=browser.new_page(viewport={'width':1570,'height':960},device_scale_factor=1,accept_downloads=True)
    errors=[];page.on('pageerror',lambda e: errors.append(str(e)))
    page.set_content((ROOT/'index.html').read_text(encoding='utf-8'),wait_until='domcontentloaded')
    page.wait_for_timeout(1200)
    ck('Original C WASM ready','同源 C 核心已加载' in page.locator('#bootmsg').inner_text())
    ck('Original phone UI present',page.frame_locator('#phone').locator('nav button').count()>=4)
    ck('Procedural 3D canvas visible',page.locator('#twin-canvas').is_visible())
    ck('No TM1637 part model', not any('TM1637' in x for x in page.locator('[data-part]').all_text_contents()))
    ck('Initial counts zero',count(page)==[0,0,0],count(page))
    ck('Original wasm actual bytes', (ROOT/'docs/usgm_core_original.wasm').read_bytes()[:4]==b'\x00asm')
    # 3D rendering active, orbit has real effect
    box=page.locator('#twin-canvas').bounding_box();assert box
    before=page.evaluate('window.USGM_3D.getGeometry().yaw')
    page.mouse.move(box['x']+430,box['y']+260);page.mouse.down();page.mouse.move(box['x']+570,box['y']+285,steps=5);page.mouse.up()
    after=page.evaluate('window.USGM_3D.getGeometry().yaw')
    ck('Real 3D orbit alters camera',abs(after-before)>.20,(before,after))
    page.locator('#twin-orbit-reset').click()
    ck('Camera reset',abs(page.evaluate('window.USGM_3D.getGeometry().yaw')+.52)<.001)
    page.locator('[data-part="amp"]').click()
    ck('Selectable 3D component',page.locator('#twin-selected-title').inner_text().startswith('MAX98357A'))
    page.screenshot(path=str(ROOT/'docs/01_initial_digital_twin.png'),full_page=False)
    step(page,'small',75)
    step(page,'medium',95)
    step(page,'large',135)
    ck('3 classes exactly once',count(page)==[1,1,1],count(page))
    ck('3 events',page.locator('#eventCount').inner_text().startswith('3'))
    page.locator('#twin-fruit-medium').click();page.wait_for_timeout(950)
    just_once=count(page); page.wait_for_timeout(1200)
    ck('One placement not recounted',count(page)==just_once,count(page))
    ck('Fresh medium after removal is one new event',count(page)==[1,2,1],count(page))
    page.screenshot(path=str(ROOT/'docs/02_real_wasm_three_grades_and_phone.png'),full_page=False)
    # status and settings through ORIGINAL firmware phone UI
    phone=page.frame_locator('#phone')
    phone.locator('nav [data-tab="settings"]').click()
    phone.locator('#small-upper').fill('100')
    phone.locator('#medium-upper').fill('120')
    phone.locator('#settings-form button[type=submit]').click()
    page.wait_for_timeout(450)
    ck('Phone saved config into WASM','100.0' in page.locator('#simlog').inner_text(),page.locator('#simlog').inner_text()[:170])
    page.screenshot(path=str(ROOT/'docs/03_phone_threshold_settings.png'),full_page=False)
    page.locator('#twin-fruit-remove').click();page.wait_for_timeout(650)
    page.locator('#twin-fruit-medium').click();page.wait_for_timeout(1000)
    ck('New 95g now small according to phone config',count(page)==[2,2,1],count(page))
    # disconnection tests
    page.locator('#twin-fruit-remove').click();page.wait_for_timeout(600)
    page.locator('#fault-dt').click()
    page.locator('#twin-fruit-large').click();page.wait_for_timeout(1150)
    ck('Disconnect HX711 stops grading',count(page)==[2,2,1],count(page))
    page.screenshot(path=str(ROOT/'docs/04_disconnect_fault.png'),full_page=False)
    page.locator('#fault-dt').click();page.locator('#twin-fruit-remove').click();page.wait_for_timeout(600)
    page.locator('#twin-fruit-large').click();page.wait_for_timeout(1000)
    ck('HX711 restored after rearm',count(page)==[2,2,2],count(page))
    page.locator('#fault-audio').click()
    ck('I2S DIN fault stored',page.evaluate('window.USGM_3D.getGeometry().faults.audio') is True)
    page.locator('#fault-audio').click()
    page.locator('#fault-power').click()
    ck('Power fault shown',page.locator('#twin-power-state').inner_text()=='模拟断电')
    page.locator('#fault-power').click()
    ck('Power restored',page.locator('#twin-power-state').inner_text().startswith('低压'))
    phone.locator('nav [data-tab="report"]').click();page.wait_for_timeout(450)
    ck('Report accessible',phone.locator('#report-document').inner_text().strip()!='')
    with page.expect_download(timeout=8000) as d:
        page.locator('#btn-csv').click()
    dl=d.value;target=ROOT/'docs/exported_simulation.csv';dl.save_as(str(target));csv=target.read_text(encoding='utf-8-sig')
    ck('CSV exported with rows',len(csv.splitlines())>=6,len(csv.splitlines()))
    ck('Sim report not real hardware', 'SIMULATION' in csv or 'HX711' in csv,csv[:250])
    page.screenshot(path=str(ROOT/'docs/05_phone_final_report.png'),full_page=False)
    ck('No JS exceptions',not errors,errors[:5])
    results={'status':'PASS','checks':checks,'count':len(checks),'errors':errors,'source':'BROWSER_MODEL not Wokwi or physical hardware','build_sha256':hashlib.sha256((ROOT/'index.html').read_bytes()).hexdigest()}
    REPORT.write_text(json.dumps(results,indent=2,ensure_ascii=False),encoding='utf-8')
    print(json.dumps({'passed':len(checks),'counts':count(page),'errors':errors},ensure_ascii=False))
    browser.close()
