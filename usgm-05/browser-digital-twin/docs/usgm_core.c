#include "usgm_core.h"
#include <limits.h>
#include <string.h>

usgm_settings_t usgm_default_settings(void) {
    return (usgm_settings_t){.min_weight_mg=20000,.small_upper_mg=80000,
        .medium_upper_mg=110000,.empty_max_mg=10000,.jitter_mg=1000,
        .max_weight_mg=5000000,.stable_ms=500,.empty_ms=300,.stale_ms=1500};
}
bool usgm_settings_valid(const usgm_settings_t *c) {
    if (!c) return false;
    return c->empty_max_mg >= 0 && c->empty_max_mg < c->min_weight_mg &&
        c->min_weight_mg < c->small_upper_mg &&
        c->small_upper_mg < c->medium_upper_mg &&
        c->medium_upper_mg < c->max_weight_mg &&
        c->max_weight_mg <= 100000000 && c->jitter_mg >= 0 &&
        c->jitter_mg < c->min_weight_mg && c->stable_ms >= 100 &&
        c->stable_ms <= 5000 && c->empty_ms >= 100 && c->empty_ms <= 5000 &&
        c->stale_ms >= 300 && c->stale_ms <= 10000;
}
static void arm(usgm_core_t *c) {
    c->state=USGM_ARMING; c->empty_timer_on=false; c->samples_in_window=0;
}
void usgm_core_init(usgm_core_t *c, usgm_settings_t cfg) {
    memset(c,0,sizeof(*c));
    c->cfg=usgm_settings_valid(&cfg)?cfg:usgm_default_settings();
    arm(c); c->stale=true;
}
void usgm_core_reconfigure(usgm_core_t *c, usgm_settings_t cfg) {
    if (!usgm_settings_valid(&cfg)) return;
    c->cfg=cfg; arm(c);
}
void usgm_core_clear_batch(usgm_core_t *c) {
    memset(c->counts,0,sizeof(c->counts)); c->accepted_total=0; arm(c);
}
void usgm_core_restore_count(usgm_core_t *c,usgm_grade_t grade) {
    if (grade>=USGM_SMALL && grade<=USGM_LARGE) {
        c->counts[grade]++; c->accepted_total++;
    }
}
usgm_grade_t usgm_classify(const usgm_settings_t *c,int32_t mg) {
    if (!usgm_settings_valid(c) || mg < c->min_weight_mg || mg > c->max_weight_mg) return USGM_INVALID;
    if (mg < c->small_upper_mg) return USGM_SMALL;
    if (mg < c->medium_upper_mg) return USGM_MEDIUM;
    return USGM_LARGE;
}
static void begin_candidate(usgm_core_t *c,int32_t mg,uint32_t ms) {
    c->candidate_start_ms=ms; c->window_min_mg=mg;
    c->window_max_mg=mg; c->previous_mg=mg; c->samples_in_window=1;
}
static void observe_empty(usgm_core_t *c,int32_t mg,uint32_t ms) {
    if (mg > c->cfg.empty_max_mg) {c->empty_timer_on=false;return;}
    if (!c->empty_timer_on) {c->empty_timer_on=true;c->empty_start_ms=ms;return;}
    if ((uint32_t)(ms-c->empty_start_ms)>=c->cfg.empty_ms) {
        c->state=USGM_READY;c->empty_timer_on=false;c->samples_in_window=0;
    }
}
void usgm_core_tick(usgm_core_t *c,uint32_t now_ms) {
    if (!c->has_sample || (uint32_t)(now_ms-c->last_sample_ms)>c->cfg.stale_ms) {
        c->stale=true;
        c->empty_timer_on=false;
        if (c->state==USGM_SETTLING || c->state==USGM_READY) arm(c);
        /* WAIT_REMOVAL stays locked until valid empty measurements arrive. */
    }
}
usgm_event_t usgm_core_push(usgm_core_t *c,int32_t mg,bool valid,uint32_t now_ms) {
    usgm_event_t ev={0};
    if (!valid || mg < -c->cfg.max_weight_mg || mg > c->cfg.max_weight_mg) {
        c->stale=true;
        c->empty_timer_on=false;
        if (c->state==USGM_SETTLING) arm(c);
        return ev;
    }
    if (c->has_sample && (uint32_t)(now_ms-c->last_sample_ms)>c->cfg.stale_ms) {
        c->empty_timer_on=false;
        if (c->state==USGM_SETTLING || c->state==USGM_READY) arm(c);
    }
    c->current_mg=mg;c->has_sample=true;c->stale=false;c->last_sample_ms=now_ms;
    if (c->state==USGM_ARMING || c->state==USGM_WAIT_REMOVAL) {
        observe_empty(c,mg,now_ms);return ev;
    }
    if (c->state==USGM_READY) {
        if (mg>=c->cfg.min_weight_mg) {
            c->state=USGM_SETTLING;c->placement_start_ms=now_ms;begin_candidate(c,mg,now_ms);
        }
        return ev;
    }
    if (mg<c->cfg.min_weight_mg) {
        c->state=USGM_READY;c->samples_in_window=0;return ev;
    }
    int64_t delta=(int64_t)mg-c->previous_mg;
    if (delta<0) delta=-delta;
    int32_t lo=mg<c->window_min_mg?mg:c->window_min_mg;
    int32_t hi=mg>c->window_max_mg?mg:c->window_max_mg;
    if (delta>c->cfg.jitter_mg || (int64_t)hi-lo>c->cfg.jitter_mg) {
        begin_candidate(c,mg,now_ms);return ev;
    }
    c->window_min_mg=lo;c->window_max_mg=hi;c->previous_mg=mg;c->samples_in_window++;
    if (c->samples_in_window>=3 && (uint32_t)(now_ms-c->candidate_start_ms)>=c->cfg.stable_ms) {
        usgm_grade_t grade=usgm_classify(&c->cfg,mg);
        if (grade!=USGM_INVALID) {
            c->counts[grade]++;c->accepted_total++;
            ev.accepted=true;ev.grade=grade;ev.weight_mg=mg;
            ev.event_no=c->accepted_total;
            ev.latency_ms=(uint32_t)(now_ms-c->placement_start_ms);
            c->state=USGM_WAIT_REMOVAL;c->empty_timer_on=false;
        }
    }
    return ev;
}
const char *usgm_grade_name(usgm_grade_t g) {
    switch(g) {case USGM_SMALL:return "small";case USGM_MEDIUM:return "medium";case USGM_LARGE:return "large";default:return "invalid";}
}
const char *usgm_state_name(usgm_state_t s) {
    switch(s) {case USGM_ARMING:return "arming";case USGM_READY:return "ready";case USGM_SETTLING:return "settling";case USGM_WAIT_REMOVAL:return "wait_removal";default:return "unknown";}
}
