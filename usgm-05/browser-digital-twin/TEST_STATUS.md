# USGM-05 Digital Twin · Browser E2E Test Summary

- Test environment: headless Chromium browser, real C-to-WASM engine from existing USGM firmware and original phone HTML/JS via virtual API adapter.
- **30 assertions passed**, no JavaScript page errors on 1570×960 desktop; 390px mobile rendering smoke check passed.
- Confirmed: C WASM load, original phone UI, interactive 3D camera orbit/reset, component inspection, 75g→small, 95g→medium, 135g→large, no repeat count while fruit remains placed, phone threshold update 100g/120g and 95g→small, HX711 DT disconnection blocks grading and recovery after rearm, simulated audio DIN and power faults, report view, CSV export.
- Physical HX711, MAX98357A Chinese audio output, ESP32 Wi-Fi SoftAP and true component dimensions **not physically tested**. Browser is geometric/procedural digital twin, **not Wokwi MCU simulation or SPICE**.
- Editable scripts, source provenance and reproducible Playwright test code are included in the downloadable full ZIP artifact provided with this delivery; the GitHub branch contains a fully standalone executable HTML and summary.
