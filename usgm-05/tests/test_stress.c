#include "usgm_core.h"
#include <assert.h>
#include <stdint.h>
#include <stdio.h>

static uint32_t rnd=0x12345678u;
static uint32_t next(void){rnd^=rnd<<13;rnd^=rnd>>17;rnd^=rnd<<5;return rnd;}
int main(void){
 usgm_settings_t cfg=usgm_default_settings();usgm_core_t c;usgm_core_init(&c,cfg);
 uint32_t t=0,expected[4]={0};
 for(int i=0;i<2500;i++){
   (void)usgm_core_push(&c,0,true,t);(void)usgm_core_push(&c,0,true,t+350);
   int32_t mg=(int32_t)(20000+next()%150000u);t+=500;
   usgm_grade_t grade=usgm_classify(&cfg,mg);assert(grade>=USGM_SMALL&&grade<=USGM_LARGE);
   usgm_event_t e={0};
   for(int j=0;j<12;j++){
      e=usgm_core_push(&c,mg,true,t+j*100);
      if(j==5){assert(e.accepted);assert(e.grade==grade);assert(e.weight_mg==mg);expected[grade]++;}
      else assert(!e.accepted);
   }
   /* disconnected Wi-Fi and duplicate sampling never creates a new grading event */
   for(int j=0;j<8;j++){e=usgm_core_push(&c,mg,true,t+1200+j*100);assert(!e.accepted);}
   t+=2500;
 }
 assert(c.accepted_total==2500);
 for(int i=1;i<=3;i++)assert(c.counts[i]==expected[i]);
 printf("PASS stress cycles: 2500; small=%u medium=%u large=%u\n",expected[1],expected[2],expected[3]);
 return 0;
}
