#pragma once
#include <stdbool.h>
#include <stdint.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "usgm_core.h"
#define USGM_MAX_EVENTS 512
#define USGM_AP_SSID "USGM-05"
/* Set via idf.py menuconfig; the default is a PUBLIC DEMO password. */
#define USGM_AP_PASSWORD CONFIG_USGM_AP_PASSWORD

typedef enum {SOURCE_SIM=0,SOURCE_HX711=1} source_t;
typedef enum {CAL_IDLE=0,CAL_ZERO=1,CAL_SPAN=2,CAL_DONE=3,CAL_ERROR=4} cal_state_t;
typedef struct {
    uint32_t id;
    uint32_t uptime_ms;
    int32_t weight_mg;
    uint32_t latency_ms;
    usgm_grade_t grade;
    int32_t min_mg, small_upper_mg, medium_upper_mg;
    usgm_grade_t reference;
    source_t source;
} record_t;
typedef struct {
    SemaphoreHandle_t mutex;
    usgm_core_t core;
    source_t source;
    int32_t sim_mg;
    int32_t last_raw;
    int32_t zero_raw;
    double counts_per_g;
    bool calibrated;
    bool has_zero;
    cal_state_t cal_state;
    int32_t span_mg;
    int64_t cal_sum;
    int32_t cal_min,cal_max;
    int cal_samples;
    bool log_ok;
    bool storage_ready;
    bool last_hx_ready;
    uint32_t batch_id;
    uint32_t next_id;
    record_t events[USGM_MAX_EVENTS];
    size_t event_count;
    bool log_full;
    uint32_t count_persist_error;
} usgm_app_t;
extern usgm_app_t g_app;
uint32_t uptime_ms(void);
void app_on_sample(int32_t mg,bool valid);
void app_reset_batch(void);
void app_start_cal(cal_state_t kind,int32_t span_mg);
void app_cal_raw(int32_t raw);
void app_set_source(source_t src);
void app_save_config(void);
