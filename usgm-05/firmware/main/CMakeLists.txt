idf_component_register(SRCS "app_main.c" "storage.c" "sensor.c" "audio.c" "http.c"
    INCLUDE_DIRS "."
    REQUIRES usgm_core esp_wifi esp_event esp_netif esp_http_server nvs_flash esp_timer driver spiffs json esp_driver_i2s esp_driver_gpio
    EMBED_FILES "assets/audio/small.raw" "assets/audio/medium.raw" "assets/audio/large.raw" "assets/audio/error.raw"
    EMBED_TXTFILES "assets/index.html" "assets/app.js" "assets/report.mjs")
