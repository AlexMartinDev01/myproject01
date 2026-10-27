#ifndef USGM_CORE_H
#define USGM_CORE_H
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef enum {USGM_INVALID=0,USGM_SMALL=1,USGM_MEDIUM=2,USGM_LARGE=3} usgm_grade_t;
typedef enum {USGM_ARMING=0,USGM_READY=1,USGM_SETTLING=2,USGM_WAIT_REMOVAL=3} usgm_state_t;
typedef struct {
    int32_t min_weight_mg;
    int32_t small_upper_mg;
    int32_t medium_upper_mg;
    int32_t empty_max_mg;
    int32_t jitter_mg;
    int32_t max_weight_mg;
    uint32_t stable_ms;
    uint32_t empty_ms;
    uint32_t stale_ms;
} usgm_settings_t;
typedef struct {
    bool accepted;
    usgm_grade_t grade;
    int32_t weight_mg;
    uint32_t latency_ms;
    uint32_t event_no;
} usgm_event_t;
typedef struct {
    usgm_settings_t cfg;
    usgm_state_t state;
    bool has_sample;
    bool stale;
    int32_t current_mg;
    uint32_t last_sample_ms;
    uint32_t candidate_start_ms;
    uint32_t placement_start_ms;
    int32_t window_min_mg;
    int32_t window_max_mg;
    int32_t previous_mg;
    uint32_t samples_in_window;
    bool empty_timer_on;
    uint32_t empty_start_ms;
    uint32_t counts[4];
    uint32_t accepted_total;
} usgm_core_t;

usgm_settings_t usgm_default_settings(void);
bool usgm_settings_valid(const usgm_settings_t *cfg);
void usgm_core_init(usgm_core_t *core, usgm_settings_t cfg);
void usgm_core_reconfigure(usgm_core_t *core, usgm_settings_t cfg);
void usgm_core_clear_batch(usgm_core_t *core);
void usgm_core_restore_count(usgm_core_t *core, usgm_grade_t grade);
usgm_grade_t usgm_classify(const usgm_settings_t *cfg, int32_t mg);
usgm_event_t usgm_core_push(usgm_core_t *core, int32_t mg, bool valid, uint32_t now_ms);
void usgm_core_tick(usgm_core_t *core, uint32_t now_ms);
const char *usgm_grade_name(usgm_grade_t grade);
const char *usgm_state_name(usgm_state_t state);
#ifdef __cplusplus
}
#endif
#endif
