import unittest
from pathlib import Path
import struct
import re
from html.parser import HTMLParser
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'firmware/main/assets'

class IdParser(HTMLParser):
 def __init__(self):super().__init__();self.ids=[]
 def handle_starttag(self,tag,attrs):
  for k,v in attrs:
   if k=='id':self.ids.append(v)

class AssetTests(unittest.TestCase):
 def test_audio_is_real_pcm_not_empty(self):
  clips=[]
  for n in ['small','medium','large','error']:
   b=(ASSETS/'audio'/f'{n}.raw').read_bytes()
   self.assertEqual(len(b)%2,0)
   self.assertGreater(len(b),16000)
   self.assertLess(len(b),16000*2*10)
   samples=struct.unpack('<'+('h'*(len(b)//2)),b)
   self.assertGreater(max(map(abs,samples)),1000)
   clips.append(b)
  self.assertEqual(len(set(clips)),4)
 def test_partition_table_for_4mb(self):
  t=(ROOT/'firmware/partitions.csv').read_text()
  self.assertIn('0x290000,0x170000',t)
  self.assertEqual(0x290000+0x170000,4*1024*1024)
 def test_html_unique_dom_ids(self):
  parser=IdParser();parser.feed((ASSETS/'index.html').read_text())
  self.assertEqual(len(parser.ids),len(set(parser.ids)))
  self.assertGreater(len(parser.ids),30)
 def test_ui_api_routes_match_firmware(self):
  js=(ASSETS/'app.js').read_text()
  c=(ROOT/'firmware/main/http.c').read_text()
  urls=set(re.findall(r"/api/[a-z\-/\.]+",js))
  for path in urls:
   self.assertIn('"'+path+'"',c,path)
 def test_assets_declared_in_build(self):
  cmake=(ROOT/'firmware/main/CMakeLists.txt').read_text()
  for f in ['small.raw','medium.raw','large.raw','index.html','app.js','report.mjs']:
   self.assertIn(f,cmake)
 def test_readme_spells_out_hardware_limit(self):
  text=(ROOT/'README.md').read_text() if (ROOT/'README.md').exists() else ''
  self.assertIn('无法验证',text)
  self.assertIn('HX711',text)

if __name__=='__main__':unittest.main()
