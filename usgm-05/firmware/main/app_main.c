#include "app.h"
#include "storage.h"
#include "sensor.h"
#include "audio.h"
#include "http.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "esp_netif.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "nvs_flash.h"
#include <math.h>
#include <limits.h>
#include <string.h>

static const char *TAG="USGM";
usgm_app_t g_app;
uint32_t uptime_ms(void) {return (uint32_t)(esp_timer_get_time()/1000ULL);}
void app_save_config(void) {if (!storage_save_config()) ESP_LOGE(TAG,"NVS config save failed");}

static void wifi_start(void) {
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_ap();
    wifi_init_config_t cfg=WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    wifi_config_t ap={0};
    strncpy((char*)ap.ap.ssid,USGM_AP_SSID,sizeof(ap.ap.ssid)-1);
    strncpy((char*)ap.ap.password,USGM_AP_PASSWORD,sizeof(ap.ap.password)-1);
    ap.ap.ssid_len=strlen(USGM_AP_SSID);
    ap.ap.channel=6;ap.ap.max_connection=2;
    ap.ap.authmode=WIFI_AUTH_WPA2_PSK;
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_AP));
    ESP_ERROR_CHECK(esp_wifi_set_config(WIFI_IF_AP,&ap));
    ESP_ERROR_CHECK(esp_wifi_start());
}

void app_reset_batch(void) {
    /* Caller holds mutex. Avoid silently erasing anything when SPIFFS failed. */
    if (!g_app.storage_ready || !storage_clear_batch()) return;
    g_app.batch_id++;g_app.event_count=0;g_app.next_id=1;
    g_app.log_full=false;g_app.log_ok=true;g_app.count_persist_error=0;
    usgm_core_clear_batch(&g_app.core);
    (void)storage_save_config();
}
void app_set_source(source_t src) {
    if (g_app.source==src) return;
    /* Caller must clear batch first: SIM/HX711 data must never be mixed. */
    g_app.source=src;usgm_core_reconfigure(&g_app.core,g_app.core.cfg);
    (void)storage_save_config();
}
void app_start_cal(cal_state_t kind,int32_t span_mg) {
    g_app.cal_state=kind;g_app.cal_samples=0;g_app.cal_sum=0;
    g_app.cal_min=INT32_MAX;g_app.cal_max=INT32_MIN;g_app.span_mg=span_mg;
    usgm_core_reconfigure(&g_app.core,g_app.core.cfg);
}
void app_cal_raw(int32_t raw) {
    if (g_app.cal_state!=CAL_ZERO && g_app.cal_state!=CAL_SPAN) return;
    if (raw<g_app.cal_min)g_app.cal_min=raw;
    if (raw>g_app.cal_max)g_app.cal_max=raw;
    g_app.cal_sum+=raw;g_app.cal_samples++;
    if (g_app.cal_samples<24) return;
    double average=(double)g_app.cal_sum/(double)g_app.cal_samples;
    /* Unstable readings must not be written as calibration. Relative check for span,
       conservative absolute check for zero. Final accuracy requires known weights. */
    double swing=(double)((int64_t)g_app.cal_max-g_app.cal_min);
    if (g_app.cal_state==CAL_ZERO) {
        if (swing>20000.0) {g_app.cal_state=CAL_ERROR;return;}
        g_app.zero_raw=(int32_t)llround(average);g_app.has_zero=true;
        g_app.calibrated=false;g_app.counts_per_g=0;
    } else {
        double diff=average-g_app.zero_raw;
        double grams=(double)g_app.span_mg/1000.0;
        if (!g_app.has_zero || grams<20 || fabs(diff)<grams*0.5 ||
            swing>fabs(diff)*0.05) {g_app.cal_state=CAL_ERROR;return;}
        g_app.counts_per_g=diff/grams;g_app.calibrated=true;
    }
    g_app.cal_state=CAL_DONE;
    (void)storage_save_config();
}
void app_on_sample(int32_t mg,bool valid) {
    uint32_t now=uptime_ms();
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    usgm_event_t e=usgm_core_push(&g_app.core,mg,valid,now);
    if (e.accepted) {
        if (g_app.event_count>=USGM_MAX_EVENTS) {
            g_app.log_full=true;g_app.log_ok=false;
        } else {
            record_t rec={.id=g_app.next_id,.uptime_ms=now,.weight_mg=e.weight_mg,
                .latency_ms=e.latency_ms,.grade=e.grade,.source=g_app.source,.reference=USGM_INVALID,
                .min_mg=g_app.core.cfg.min_weight_mg,
                .small_upper_mg=g_app.core.cfg.small_upper_mg,
                .medium_upper_mg=g_app.core.cfg.medium_upper_mg};
            if (storage_append_event(&rec)) {
                g_app.events[g_app.event_count++]=rec;g_app.next_id++;
            } else {
                g_app.log_ok=false;g_app.count_persist_error++;
            }
        }
        /* Always announce current grade, but explicit log fault is shown on UI. */
        audio_enqueue(e.grade);
    }
    xSemaphoreGive(g_app.mutex);
}

void app_main(void) {
    /* Never silently erase stored settings on NVS mount/version errors. */
    ESP_ERROR_CHECK(nvs_flash_init());
    memset(&g_app,0,sizeof(g_app));
    g_app.mutex=xSemaphoreCreateMutex();configASSERT(g_app.mutex);
    usgm_core_init(&g_app.core,usgm_default_settings());
    g_app.source=SOURCE_SIM;g_app.batch_id=1;g_app.next_id=1;
    g_app.storage_ready=storage_init();g_app.log_ok=g_app.storage_ready;
    (void)storage_load_config();
    if (g_app.storage_ready && !storage_restore_batch())g_app.log_ok=false;
    audio_init();sensor_start();wifi_start();http_start();
    ESP_LOGW(TAG,"SIM mode default. HX711 must be calibrated before live grams are valid.");
    ESP_LOGW(TAG,"Change the demo AP password via idf.py menuconfig before field use.");
    while (1) {vTaskDelay(pdMS_TO_TICKS(1000));}
}
