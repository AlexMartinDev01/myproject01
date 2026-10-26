#include "storage.h"
#include "esp_log.h"
#include "esp_spiffs.h"
#include "nvs.h"
#include <stdio.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <math.h>

#define LOG_PATH "/spiffs/events.log"
#define SETTINGS_MAGIC 0x55534735UL
#define SETTINGS_VERSION 1
static const char *TAG="USGM_STORE";
typedef struct {
    uint32_t magic,version;
    usgm_settings_t cfg;
    int32_t zero_raw;
    double counts_per_g;
    uint8_t calibrated,has_zero,source,reserved;
    uint32_t batch_id;
} saved_cfg_t;

static uint32_t checksum(const char *s) {
    uint32_t h=2166136261u;
    while (*s) {h ^= (unsigned char)*s++;h *=16777619u;}
    return h;
}
static bool append_journal(const char *line) {
    FILE *f=fopen(LOG_PATH,"a");if(!f)return false;
    bool ok=fputs(line,f)>=0 && fflush(f)==0;
    if (ok) ok=fsync(fileno(f))==0;
    if(fclose(f)!=0)ok=false;
    return ok;
}
static bool append_with_check(const char *prefix) {
    char line[192];
    int n=snprintf(line,sizeof(line),"%s,%08lx\n",prefix,(unsigned long)checksum(prefix));
    return n>0 && n<(int)sizeof(line) && append_journal(line);
}
bool storage_init(void) {
    esp_vfs_spiffs_conf_t cfg={.base_path="/spiffs",.partition_label="storage",
        .max_files=3,.format_if_mount_failed=true};
    esp_err_t e=esp_vfs_spiffs_register(&cfg);
    if(e!=ESP_OK){ESP_LOGE(TAG,"SPIFFS failed: %s",esp_err_to_name(e));return false;}
    ESP_LOGW(TAG,"Prototype SPIFFS auto-formats a corrupt/unformatted partition; EXPORT LOGS often.");
    return true;
}
bool storage_load_config(void) {
    nvs_handle_t h;if(nvs_open("usgm",NVS_READONLY,&h)!=ESP_OK)return false;
    saved_cfg_t x={0};size_t len=sizeof(x);
    esp_err_t e=nvs_get_blob(h,"settings",&x,&len);nvs_close(h);
    if(e!=ESP_OK||len!=sizeof(x)||x.magic!=SETTINGS_MAGIC||x.version!=SETTINGS_VERSION||
       !usgm_settings_valid(&x.cfg)||x.source>SOURCE_HX711) return false;
    g_app.core.cfg=x.cfg;g_app.zero_raw=x.zero_raw;
    g_app.counts_per_g=x.counts_per_g;
    g_app.has_zero=x.has_zero && x.has_zero==1;
    g_app.calibrated=x.calibrated==1 && g_app.has_zero && isfinite(x.counts_per_g) && fabs(x.counts_per_g)>=0.5;
    g_app.source=(source_t)x.source;g_app.batch_id=x.batch_id?x.batch_id:1;
    return true;
}
bool storage_save_config(void) {
    saved_cfg_t x={.magic=SETTINGS_MAGIC,.version=SETTINGS_VERSION,
        .cfg=g_app.core.cfg,.zero_raw=g_app.zero_raw,.counts_per_g=g_app.counts_per_g,
        .calibrated=g_app.calibrated?1:0,.has_zero=g_app.has_zero?1:0,
        .source=(uint8_t)g_app.source,.batch_id=g_app.batch_id};
    nvs_handle_t h;
    if(nvs_open("usgm",NVS_READWRITE,&h)!=ESP_OK)return false;
    esp_err_t e=nvs_set_blob(h,"settings",&x,sizeof(x));
    if(e==ESP_OK)e=nvs_commit(h);
    nvs_close(h);return e==ESP_OK;
}

bool storage_append_event(const record_t *r) {
    char buf[128];int n=snprintf(buf,sizeof(buf),"E,%lu,%lu,%ld,%d,%lu,%d,%ld,%ld,%ld",
        (unsigned long)r->id,(unsigned long)r->uptime_ms,(long)r->weight_mg,
        (int)r->grade,(unsigned long)r->latency_ms,(int)r->source,
        (long)r->min_mg,(long)r->small_upper_mg,(long)r->medium_upper_mg);
    return n>0 && n<(int)sizeof(buf) && append_with_check(buf);
}
bool storage_append_reference(uint32_t id,usgm_grade_t grade) {
    char buf[64];int n=snprintf(buf,sizeof(buf),"R,%lu,%d",(unsigned long)id,(int)grade);
    return n>0 && n<(int)sizeof(buf) && append_with_check(buf);
}
bool storage_clear_batch(void) {
    FILE *f=fopen(LOG_PATH,"w");if(!f)return false;
    bool ok=fputs("#USGM05 v1\n",f)>=0 && fflush(f)==0;
    if(ok)ok=fsync(fileno(f))==0;
    if(fclose(f)!=0)ok=false;
    return ok;
}
static bool checked_prefix(char *line) {
    size_t len=strlen(line);
    if(!len||line[len-1]!='\n')return false;
    line[--len]='\0';
    char *last=strrchr(line,',');if(!last)return false;
    *last++='\0';
    if(strlen(last)!=8)return false;
    char *end=NULL;unsigned long actual=strtoul(last,&end,16);
    return end && *end=='\0' && actual==checksum(line);
}
bool storage_restore_batch(void) {
    FILE *f=fopen(LOG_PATH,"r");if(!f)return true;
    bool good=true;char line[200];
    while(fgets(line,sizeof(line),f)) {
        if(line[0]=='#')continue;
        if(!checked_prefix(line)){good=false;break;}
        if(line[0]=='E') {
            unsigned long id=0,when=0,lat=0;
            long mg=0,minimum=0,small=0,medium=0;int g=0,src=0;char extra;
            if(sscanf(line,"E,%lu,%lu,%ld,%d,%lu,%d,%ld,%ld,%ld%c",&id,&when,&mg,&g,&lat,&src,&minimum,&small,&medium,&extra)!=9 ||
                id==0||id>UINT32_MAX||when>UINT32_MAX||lat>UINT32_MAX||
                mg<INT32_MIN||mg>INT32_MAX||g<1||g>3||src<0||src>1||
                minimum<0||small<=minimum||medium<=small||medium>100000000||
                g_app.event_count>=USGM_MAX_EVENTS||id!=g_app.next_id) {good=false;break;}
            record_t *r=&g_app.events[g_app.event_count++];
            *r=(record_t){.id=(uint32_t)id,.uptime_ms=(uint32_t)when,
                .weight_mg=(int32_t)mg,.grade=(usgm_grade_t)g,
                .latency_ms=(uint32_t)lat,.source=(source_t)src,
                .min_mg=(int32_t)minimum,.small_upper_mg=(int32_t)small,
                .medium_upper_mg=(int32_t)medium};
            g_app.next_id++;usgm_core_restore_count(&g_app.core,r->grade);
        } else if(line[0]=='R') {
            unsigned long id=0;int grade=0;char extra;
            if(sscanf(line,"R,%lu,%d%c",&id,&grade,&extra)!=2 || id==0 || id>UINT32_MAX ||grade<1||grade>3){good=false;break;}
            bool found=false;
            for(size_t i=0;i<g_app.event_count;i++){
                if(g_app.events[i].id==id){g_app.events[i].reference=(usgm_grade_t)grade;found=true;break;}
            }
            if(!found){good=false;break;}
        } else {good=false;break;}
    }
    if(ferror(f))good=false;
    fclose(f);
    if(!good)ESP_LOGE(TAG,"Journal invalid/truncated; prior valid records retained; start a new batch after export.");
    g_app.log_full=g_app.event_count>=USGM_MAX_EVENTS;
    return good;
}
