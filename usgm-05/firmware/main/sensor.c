#include "sensor.h"
#include "app.h"
#include "freertos/task.h"
#include "driver/gpio.h"
#include "esp_rom_sys.h"
#include "esp_log.h"
#include <math.h>
#include <stdint.h>
#include <limits.h>

#define HX_DOUT GPIO_NUM_0
#define HX_SCK GPIO_NUM_1
static const char *TAG="USGM_HX";
static portMUX_TYPE hx_lock = portMUX_INITIALIZER_UNLOCKED;

static bool read_hx711(int32_t *raw) {
    if(gpio_get_level(HX_DOUT)!=0)return false;
    uint32_t v=0;
    portENTER_CRITICAL(&hx_lock);
    /* 24 pulses + 1 gain-select pulse (channel A, gain 128).
       GPIO bit-banging is adequate for 10 SPS experimental mode; verify timing on board. */
    for(int i=0;i<24;i++){
        gpio_set_level(HX_SCK,1);esp_rom_delay_us(2);
        v=(v<<1)|(uint32_t)gpio_get_level(HX_DOUT);
        gpio_set_level(HX_SCK,0);esp_rom_delay_us(2);
    }
    gpio_set_level(HX_SCK,1);esp_rom_delay_us(2);
    gpio_set_level(HX_SCK,0);
    portEXIT_CRITICAL(&hx_lock);
    if(v&0x800000u)v|=0xFF000000u;
    *raw=(int32_t)v;return true;
}
static void sensor_task(void *arg) {
    (void)arg;
    while(1) {
        xSemaphoreTake(g_app.mutex,portMAX_DELAY);
        source_t src=g_app.source;
        int32_t sim=g_app.sim_mg;
        xSemaphoreGive(g_app.mutex);
        if(src==SOURCE_SIM) {
            app_on_sample(sim,true);
        } else {
            int32_t raw=0;
            if(read_hx711(&raw)){
                xSemaphoreTake(g_app.mutex,portMAX_DELAY);
                g_app.last_raw=raw;g_app.last_hx_ready=true;
                app_cal_raw(raw);
                bool calibrated=g_app.calibrated;
                double grams=calibrated?((double)raw-g_app.zero_raw)/g_app.counts_per_g:0;
                bool valid=calibrated && g_app.cal_state!=CAL_ZERO &&
                    g_app.cal_state!=CAL_SPAN && isfinite(grams) && fabs(grams)<100000;
                int32_t mg=valid?(int32_t)llround(grams*1000.0):0;
                xSemaphoreGive(g_app.mutex);
                app_on_sample(mg,valid);
            } else {
                xSemaphoreTake(g_app.mutex,portMAX_DELAY);
                usgm_core_tick(&g_app.core,uptime_ms());
                xSemaphoreGive(g_app.mutex);
            }
        }
        vTaskDelay(pdMS_TO_TICKS(100));
    }
}
void sensor_start(void) {
    gpio_config_t in={.pin_bit_mask=1ULL<<HX_DOUT,.mode=GPIO_MODE_INPUT,
                      .pull_up_en=GPIO_PULLUP_ENABLE};
    gpio_config_t out={.pin_bit_mask=1ULL<<HX_SCK,.mode=GPIO_MODE_OUTPUT};
    ESP_ERROR_CHECK(gpio_config(&in));ESP_ERROR_CHECK(gpio_config(&out));
    gpio_set_level(HX_SCK,0);
    BaseType_t ok=xTaskCreate(sensor_task,"sensor",6144,NULL,6,NULL);
    if(ok!=pdPASS)ESP_LOGE(TAG,"Cannot start sensor task");
}
