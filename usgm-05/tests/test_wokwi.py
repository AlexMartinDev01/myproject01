import json
import pathlib
import tomllib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
FW = ROOT / "firmware"

class WokwiConfigurationTests(unittest.TestCase):
    def setUp(self):
        self.diagram = json.loads((FW / "diagram.json").read_text())
        self.parts = {p["id"]: p for p in self.diagram["parts"]}
        self.edges = {frozenset((x[0], x[1])) for x in self.diagram["connections"]}

    def test_unique_ids_and_types(self):
        self.assertEqual(len(self.parts), len(self.diagram["parts"]))
        self.assertEqual(self.parts["esp"]["type"], "board-esp32-c3-devkitm-1")
        self.assertEqual(self.parts["hx711"]["attrs"]["type"], "5kg")
        self.assertEqual(self.parts["hx711_logic"]["type"], "wokwi-logic-analyzer")

    def test_hx711_gpio_connections(self):
        for src, dst in [("hx711:DT", "esp:0"), ("hx711:SCK", "esp:1"),
                         ("hx711:VCC", "esp:3V3.1"), ("hx711:GND", "esp:GND.1")]:
            self.assertIn(frozenset((src, dst)), self.edges)

    def test_hx711_signal_probe_and_simulated_audio(self):
        for src, dst in [("hx711:DT", "hx711_logic:D0"), ("hx711:SCK", "hx711_logic:D1")]:
            self.assertIn(frozenset((src, dst)), self.edges)
        self.assertNotIn("audio_logic", self.parts)
        audio = (FW / "main/audio.c").read_text()
        self.assertIn("WOKWI_AUDIO_PROXY", audio)
        self.assertIn("WOKWI_AUDIO_INTENT", audio)

    def test_no_unknown_part_ids(self):
        for a, b, _, route in self.diagram["connections"]:
            self.assertIsInstance(route, list)
            for endpoint in (a, b):
                if endpoint.startswith("$serialMonitor:"):
                    continue
                self.assertIn(endpoint.split(":")[0], self.parts)

    def test_build_profile_is_separate(self):
        config = tomllib.loads((FW / "wokwi.toml").read_text())
        self.assertEqual(config["wokwi"]["firmware"], "build-wokwi/flasher_args.json")
        self.assertEqual(config["wokwi"]["elf"], "build-wokwi/usgm05.elf")
        self.assertIn("CONFIG_USGM_WOKWI_MODE=y", (FW / "sdkconfig.wokwi.defaults").read_text())
        self.assertIn("default n", (FW / "main/Kconfig.projbuild").read_text())

    def test_virtual_data_cannot_claim_physical_accuracy(self):
        txt = (FW / "main/http.c").read_text()
        self.assertIn("WOKWI_VIRTUAL_HX711", txt)
        self.assertIn('can_claim_hardware_accuracy",false', txt)

    def test_scenario_values_and_expected_grades(self):
        text = (ROOT / "wokwi/three_grades.scenario.yaml").read_text()
        for expected in ["0.075", "0.095", "0.135",
                         "grade=small", "grade=medium", "grade=large"]:
            self.assertIn(expected, text)
        self.assertEqual(text.count("name: load"), 6)

if __name__ == "__main__":
    unittest.main()
