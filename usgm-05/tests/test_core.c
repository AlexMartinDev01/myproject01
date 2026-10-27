#include "usgm_core.h"
#include <assert.h>
#include <stdio.h>

static int tests=0;
#define CHECK(expr) do {tests++;if(!(expr)){fprintf(stderr,"FAIL %s:%d: %s\n",__FILE__,__LINE__,#expr);return 1;}}while(0)
static void ready(usgm_core_t *c,uint32_t t){(void)usgm_core_push(c,0,true,t);(void)usgm_core_push(c,0,true,t+310);}
static usgm_event_t fruit(usgm_core_t *c,int32_t mg,uint32_t t){(void)usgm_core_push(c,mg,true,t);(void)usgm_core_push(c,mg,true,t+250);return usgm_core_push(c,mg,true,t+510);}
int main(void){
 usgm_settings_t cfg=usgm_default_settings(); usgm_core_t c; usgm_core_init(&c,cfg);
 CHECK(usgm_settings_valid(&cfg));CHECK(c.state==USGM_ARMING);
 CHECK(usgm_classify(&cfg,19999)==USGM_INVALID); CHECK(usgm_classify(&cfg,20000)==USGM_SMALL);
 CHECK(usgm_classify(&cfg,79999)==USGM_SMALL);CHECK(usgm_classify(&cfg,80000)==USGM_MEDIUM);
 CHECK(usgm_classify(&cfg,109999)==USGM_MEDIUM); CHECK(usgm_classify(&cfg,110000)==USGM_LARGE);
 CHECK(usgm_classify(&cfg,5000001)==USGM_INVALID);
 usgm_event_t e=fruit(&c,105000,0);CHECK(!e.accepted);CHECK(c.accepted_total==0);
 ready(&c,1000);CHECK(c.state==USGM_READY);
 e=fruit(&c,105000,1400);CHECK(e.accepted);CHECK(e.grade==USGM_MEDIUM);
 CHECK(e.weight_mg==105000);CHECK(e.latency_ms==510); CHECK(c.counts[USGM_MEDIUM]==1);
 for(uint32_t t=2000;t<5000;t+=100){e=usgm_core_push(&c,105000,true,t);CHECK(!e.accepted);}
 CHECK(c.accepted_total==1); CHECK(c.state==USGM_WAIT_REMOVAL);
 /* a 100ms empty dip does not rearm */
 usgm_core_push(&c,0,true,5100);usgm_core_push(&c,0,true,5200);
 e=fruit(&c,115000,5400); CHECK(!e.accepted); CHECK(c.accepted_total==1);
 ready(&c,6100);CHECK(c.state==USGM_READY);
 e=fruit(&c,115000,6600);CHECK(e.accepted);CHECK(e.grade==USGM_LARGE);CHECK(c.accepted_total==2);
 /* Connection loss during wait-removal cannot produce duplicate. */
 usgm_core_tick(&c,12000);CHECK(c.stale);CHECK(c.state==USGM_WAIT_REMOVAL);
 e=fruit(&c,115000,12100);CHECK(!e.accepted);CHECK(c.accepted_total==2);
 /* Reconfiguration requires empty confirmation. */
 cfg.small_upper_mg=90000;cfg.medium_upper_mg=120000;
 usgm_core_reconfigure(&c,cfg);CHECK(c.state==USGM_ARMING);
 CHECK(usgm_classify(&cfg,110000)==USGM_MEDIUM);
 ready(&c,14000);e=fruit(&c,110000,14500);CHECK(e.accepted);CHECK(e.grade==USGM_MEDIUM);
 /* Stable detector rejects drifting data. */
 ready(&c,16000); (void)usgm_core_push(&c,80000,true,16500);
 for(int i=1;i<=10;i++){e=usgm_core_push(&c,80000+i*2000,true,16500+i*100);CHECK(!e.accepted);}
 CHECK(c.state==USGM_SETTLING);
 e=usgm_core_push(&c,100000,true,18100);CHECK(!e.accepted);
 e=usgm_core_push(&c,100000,true,18400);CHECK(e.accepted);
 e=usgm_core_push(&c,100000,true,18650);CHECK(!e.accepted);
 /* settings guards */
 cfg.small_upper_mg=cfg.medium_upper_mg;CHECK(!usgm_settings_valid(&cfg));
 cfg=usgm_default_settings();cfg.empty_max_mg=cfg.min_weight_mg;CHECK(!usgm_settings_valid(&cfg));
 cfg=usgm_default_settings();cfg.stable_ms=0;CHECK(!usgm_settings_valid(&cfg));
 cfg=usgm_default_settings();usgm_core_clear_batch(&c);CHECK(c.accepted_total==0);CHECK(c.state==USGM_ARMING);
 usgm_core_restore_count(&c,USGM_LARGE);CHECK(c.counts[USGM_LARGE]==1);
 printf("PASS core assertions: %d\n",tests);return 0;
}
