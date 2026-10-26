#include "http.h"
#include "app.h"
#include "storage.h"
#include "esp_http_server.h"
#include "esp_log.h"
#include "cJSON.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>

static const char *TAG="USGM_HTTP";
extern const char _binary_assets_index_html_start[] asm("_binary_assets_index_html_start");
extern const char _binary_assets_app_js_start[] asm("_binary_assets_app_js_start");
extern const char _binary_assets_report_mjs_start[] asm("_binary_assets_report_mjs_start");

static esp_err_t respond_json(httpd_req_t *r,cJSON *obj,int code) {
    char *s=cJSON_PrintUnformatted(obj);cJSON_Delete(obj);
    if(!s)return httpd_resp_send_err(r,HTTPD_500_INTERNAL_SERVER_ERROR,"Out of memory");
    if(code==400)httpd_resp_set_status(r,"400 Bad Request");
    if(code==409)httpd_resp_set_status(r,"409 Conflict");
    if(code==507)httpd_resp_set_status(r,"507 Insufficient Storage");
    httpd_resp_set_type(r,"application/json; charset=utf-8");
    httpd_resp_set_hdr(r,"Cache-Control","no-store");
    esp_err_t ret=httpd_resp_send(r,s,HTTPD_RESP_USE_STRLEN);free(s);return ret;
}
static esp_err_t message(httpd_req_t *r,bool ok,const char *msg,int code) {
    cJSON *o=cJSON_CreateObject();cJSON_AddBoolToObject(o,"ok",ok);
    cJSON_AddStringToObject(o,"message",msg);return respond_json(r,o,code);
}
static cJSON *body_json(httpd_req_t *r) {
    if(r->content_len<=0 || r->content_len>512)return NULL;
    char buf[513];size_t off=0;int n;
    while(off<(size_t)r->content_len) {
        n=httpd_req_recv(r,buf+off,r->content_len-off);
        if (n <= 0) return NULL;
        off += (size_t)n;
    }
    buf[off]='\0';return cJSON_ParseWithLength(buf,off);
}
static bool integral(const cJSON *obj,const char *key,int32_t *out) {
    cJSON *v=cJSON_GetObjectItemCaseSensitive(obj,key);
    if(!cJSON_IsNumber(v)||!isfinite(v->valuedouble)||v->valuedouble<-2147483648.0||
       v->valuedouble>2147483647.0 || floor(v->valuedouble)!=v->valuedouble)return false;
    *out=(int32_t)v->valuedouble;return true;
}
static esp_err_t ui_index(httpd_req_t *r){httpd_resp_set_type(r,"text/html; charset=utf-8");return httpd_resp_send(r,_binary_assets_index_html_start,HTTPD_RESP_USE_STRLEN);}
static esp_err_t ui_app(httpd_req_t *r){httpd_resp_set_type(r,"text/javascript; charset=utf-8");return httpd_resp_send(r,_binary_assets_app_js_start,HTTPD_RESP_USE_STRLEN);}
static esp_err_t ui_report_js(httpd_req_t *r){httpd_resp_set_type(r,"text/javascript; charset=utf-8");return httpd_resp_send(r,_binary_assets_report_mjs_start,HTTPD_RESP_USE_STRLEN);}

static esp_err_t get_status(httpd_req_t *r) {
    cJSON *o=cJSON_CreateObject(),*counts=cJSON_CreateObject(),*settings=cJSON_CreateObject();
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    usgm_core_tick(&g_app.core,uptime_ms());
    cJSON_AddStringToObject(o,"mode",g_app.source==SOURCE_SIM?"SIMULATION":"HX711");
    cJSON_AddStringToObject(o,"state",usgm_state_name(g_app.core.state));
    cJSON_AddBoolToObject(o,"stale",g_app.core.stale);
    cJSON_AddBoolToObject(o,"calibrated",g_app.calibrated);
    cJSON_AddBoolToObject(o,"has_zero",g_app.has_zero);
    cJSON_AddNumberToObject(o,"cal_state",g_app.cal_state);
    cJSON_AddNumberToObject(o,"last_raw",g_app.last_raw);
    cJSON_AddNumberToObject(o,"weight_mg",g_app.core.current_mg);
    cJSON_AddNumberToObject(o,"sample_age_ms",g_app.core.has_sample?(uint32_t)(uptime_ms()-g_app.core.last_sample_ms):0);
    cJSON_AddNumberToObject(o,"batch",g_app.batch_id);
    cJSON_AddNumberToObject(o,"events",g_app.event_count);
    cJSON_AddNumberToObject(o,"limit",USGM_MAX_EVENTS);
    cJSON_AddBoolToObject(o,"log_ok",g_app.log_ok);
    cJSON_AddBoolToObject(o,"log_full",g_app.log_full);
    cJSON_AddNumberToObject(o,"unpersisted_events",g_app.count_persist_error);
    cJSON_AddNumberToObject(counts,"small",g_app.core.counts[USGM_SMALL]);
    cJSON_AddNumberToObject(counts,"medium",g_app.core.counts[USGM_MEDIUM]);
    cJSON_AddNumberToObject(counts,"large",g_app.core.counts[USGM_LARGE]);
    cJSON_AddItemToObject(o,"counts",counts);
    const usgm_settings_t *s=&g_app.core.cfg;
    cJSON_AddNumberToObject(settings,"min_mg",s->min_weight_mg);
    cJSON_AddNumberToObject(settings,"small_upper_mg",s->small_upper_mg);
    cJSON_AddNumberToObject(settings,"medium_upper_mg",s->medium_upper_mg);
    cJSON_AddNumberToObject(settings,"empty_max_mg",s->empty_max_mg);
    cJSON_AddNumberToObject(settings,"jitter_mg",s->jitter_mg);
    cJSON_AddNumberToObject(settings,"max_mg",s->max_weight_mg);
    cJSON_AddNumberToObject(settings,"stable_ms",s->stable_ms);
    cJSON_AddNumberToObject(settings,"empty_ms",s->empty_ms);
    cJSON_AddNumberToObject(settings,"stale_ms",s->stale_ms);
    cJSON_AddItemToObject(o,"settings",settings);
    xSemaphoreGive(g_app.mutex);
    return respond_json(r,o,200);
}
static esp_err_t post_settings(httpd_req_t *r) {
    cJSON *b=body_json(r);if(!b)return message(r,false,"Invalid JSON",400);
    usgm_settings_t s;
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);s=g_app.core.cfg;xSemaphoreGive(g_app.mutex);
    int32_t x=0;bool ok=integral(b,"small_upper_mg",&s.small_upper_mg)&&
        integral(b,"medium_upper_mg",&s.medium_upper_mg);
    /* Optional minimum, jitter, stable interval. */
    if(cJSON_HasObjectItem(b,"min_mg"))ok=ok&&integral(b,"min_mg",&s.min_weight_mg);
    if(cJSON_HasObjectItem(b,"jitter_mg"))ok=ok&&integral(b,"jitter_mg",&s.jitter_mg);
    if(cJSON_HasObjectItem(b,"stable_ms")){
        ok=ok&&integral(b,"stable_ms",&x);if(ok)s.stable_ms=(uint32_t)x;
    }
    cJSON_Delete(b);
    if(!ok||!usgm_settings_valid(&s))return message(r,false,"Invalid boundary/order/range",400);
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    usgm_core_reconfigure(&g_app.core,s);
    bool persisted=storage_save_config();
    xSemaphoreGive(g_app.mutex);
    return message(r,persisted,persisted?"Settings saved; place empty scale before next fruit":"NVS save failed",persisted?200:507);
}
static esp_err_t post_sim(httpd_req_t *r) {
    cJSON *b=body_json(r);if(!b)return message(r,false,"Invalid JSON",400);
    int32_t mg=0;bool ok=integral(b,"weight_mg",&mg);cJSON_Delete(b);
    if(!ok||mg<0||mg>5000000)return message(r,false,"Weight must be 0..5000000 mg",400);
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    if(g_app.source!=SOURCE_SIM){xSemaphoreGive(g_app.mutex);return message(r,false,"Switch to SIMULATION first",409);}
    g_app.sim_mg=mg;xSemaphoreGive(g_app.mutex);
    return message(r,true,"Simulation weight updated",200);
}
static esp_err_t post_mode(httpd_req_t *r) {
    cJSON *b=body_json(r);if(!b)return message(r,false,"Invalid JSON",400);
    cJSON *m=cJSON_GetObjectItemCaseSensitive(b,"mode");
    source_t src;bool ok=true;
    if(cJSON_IsString(m)&&strcmp(m->valuestring,"SIMULATION")==0)src=SOURCE_SIM;
    else if(cJSON_IsString(m)&&strcmp(m->valuestring,"HX711")==0)src=SOURCE_HX711;
    else{src=SOURCE_SIM;ok=false;}
    cJSON_Delete(b);
    if(!ok)return message(r,false,"Unknown mode",400);
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    if(g_app.event_count>0 && g_app.source!=src){xSemaphoreGive(g_app.mutex);return message(r,false,"Export and clear current batch before mode switch",409);}
    app_set_source(src);g_app.sim_mg=0;xSemaphoreGive(g_app.mutex);
    return message(r,true,"Mode set; require empty scale before counting",200);
}
static esp_err_t post_cal(httpd_req_t *r) {
    bool span=strcmp(r->uri,"/api/cal/span")==0;
    int32_t mg=0;
    if(span){cJSON *b=body_json(r);if(!b)return message(r,false,"Invalid JSON",400);
        bool ok=integral(b,"reference_mg",&mg);cJSON_Delete(b);
        if(!ok||mg<20000||mg>5000000)return message(r,false,"Reference mass invalid",400);
    }
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    if(g_app.source!=SOURCE_HX711 || (span&&!g_app.has_zero)){
        xSemaphoreGive(g_app.mutex);return message(r,false,"Select HX711 and perform ZERO before SPAN",409);
    }
    app_start_cal(span?CAL_SPAN:CAL_ZERO,mg);
    xSemaphoreGive(g_app.mutex);
    return message(r,true,"Collecting 24 HX711 samples; check cal_state",200);
}
static esp_err_t post_batch(httpd_req_t *r) {
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    bool ok=g_app.storage_ready && storage_clear_batch();
    if(ok) {
        g_app.batch_id++;g_app.event_count=0;g_app.next_id=1;g_app.log_full=false;
        g_app.log_ok=true;g_app.count_persist_error=0;usgm_core_clear_batch(&g_app.core);
        if(!storage_save_config())g_app.log_ok=false;
    }
    xSemaphoreGive(g_app.mutex);
    return message(r,ok,ok?"New batch started; previous batch deleted":"Storage unavailable; previous batch retained",ok?200:507);
}
static esp_err_t post_reference(httpd_req_t *r) {
    cJSON *b=body_json(r);if(!b)return message(r,false,"Invalid JSON",400);
    int32_t id=0,grade=0;
    bool ok=integral(b,"event_id",&id)&&integral(b,"reference_grade",&grade);
    cJSON_Delete(b);
    if(!ok||id<=0||grade<1||grade>3)return message(r,false,"Reference grade or id invalid",400);
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    bool found=false,stored=false;
    for(size_t i=0;i<g_app.event_count;i++){
        if(g_app.events[i].id==(uint32_t)id){found=true;
            stored=storage_append_reference((uint32_t)id,(usgm_grade_t)grade);
            if(stored)g_app.events[i].reference=(usgm_grade_t)grade;
            else g_app.log_ok=false;
            break;
        }
    }
    xSemaphoreGive(g_app.mutex);
    if(!found)return message(r,false,"Event not found",400);
    return message(r,stored,stored?"Independent reference recorded":"Cannot persist reference",stored?200:507);
}
static void append_event_json(cJSON *arr,const record_t *r) {
    cJSON *o=cJSON_CreateObject();
    cJSON_AddNumberToObject(o,"id",r->id);
    cJSON_AddNumberToObject(o,"uptime_ms",r->uptime_ms);
    cJSON_AddNumberToObject(o,"weight_mg",r->weight_mg);
    cJSON_AddNumberToObject(o,"latency_ms",r->latency_ms);
    cJSON_AddNumberToObject(o,"min_mg",r->min_mg);
    cJSON_AddNumberToObject(o,"small_upper_mg",r->small_upper_mg);
    cJSON_AddNumberToObject(o,"medium_upper_mg",r->medium_upper_mg);
    cJSON_AddStringToObject(o,"grade",usgm_grade_name(r->grade));
    cJSON_AddStringToObject(o,"reference",usgm_grade_name(r->reference));
    cJSON_AddStringToObject(o,"mode",r->source==SOURCE_SIM?"SIMULATION":"HX711");
    cJSON_AddItemToArray(arr,o);
}
static esp_err_t get_events(httpd_req_t *r) {
    char query[96]={0},buf[32]={0};int offset=0,limit=30;
    if(httpd_req_get_url_query_str(r,query,sizeof(query))==ESP_OK){
        if(httpd_query_key_value(query,"offset",buf,sizeof(buf))==ESP_OK)offset=atoi(buf);
        if(httpd_query_key_value(query,"limit",buf,sizeof(buf))==ESP_OK)limit=atoi(buf);
    }
    if(offset<0||offset>USGM_MAX_EVENTS||limit<1||limit>50)return message(r,false,"Invalid pagination",400);
    cJSON *o=cJSON_CreateObject(),*a=cJSON_CreateArray();
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    cJSON_AddNumberToObject(o,"total",g_app.event_count);
    size_t end=(size_t)offset+(size_t)limit;
    if(end>g_app.event_count)end=g_app.event_count;
    for(size_t i=(size_t)offset;i<end;i++)append_event_json(a,&g_app.events[i]);
    xSemaphoreGive(g_app.mutex);
    cJSON_AddItemToObject(o,"items",a);
    return respond_json(r,o,200);
}
static esp_err_t get_summary(httpd_req_t *r) {
    cJSON *o=cJSON_CreateObject();
    uint32_t verified=0,correct=0,sim=0,hardware=0;
    uint64_t latency_sum=0;
    xSemaphoreTake(g_app.mutex,portMAX_DELAY);
    size_t count=g_app.event_count;
    for(size_t i=0;i<count;i++){
        record_t *e=&g_app.events[i];
        if(e->source==SOURCE_SIM)sim++;else hardware++;
        latency_sum+=e->latency_ms;
        if(e->reference!=USGM_INVALID){verified++;if(e->reference==e->grade)correct++;}
    }
    cJSON_AddNumberToObject(o,"count",count);
    cJSON_AddNumberToObject(o,"verified",verified);
    cJSON_AddNumberToObject(o,"correct",correct);
    cJSON_AddNumberToObject(o,"simulation",sim);
    cJSON_AddNumberToObject(o,"hardware",hardware);
    if(verified)cJSON_AddNumberToObject(o,"accepted_accuracy",(double)correct/verified);
    else cJSON_AddNullToObject(o,"accepted_accuracy");
    if(count)cJSON_AddNumberToObject(o,"avg_latency_ms",(double)latency_sum/count);
    else cJSON_AddNullToObject(o,"avg_latency_ms");
    cJSON_AddBoolToObject(o,"can_claim_hardware_accuracy",verified>0&&hardware>0&&sim==0);
    cJSON_AddBoolToObject(o,"log_ok",g_app.log_ok);
    xSemaphoreGive(g_app.mutex);
    return respond_json(r,o,200);
}
static esp_err_t get_csv(httpd_req_t *r) {
    httpd_resp_set_type(r,"text/csv; charset=utf-8");
    httpd_resp_set_hdr(r,"Content-Disposition","attachment; filename=usgm05-events.csv");
    const char *head="event_id,uptime_ms,weight_mg,grade,reference_grade,latency_ms,mode,min_mg,small_upper_mg,medium_upper_mg\n";
    if(httpd_resp_send_chunk(r,head,strlen(head))!=ESP_OK)return ESP_FAIL;
    /* Copy each record under lock; never hold mutex during network send. */
    size_t n; xSemaphoreTake(g_app.mutex,portMAX_DELAY);n=g_app.event_count;xSemaphoreGive(g_app.mutex);
    for(size_t i=0;i<n;i++){
        record_t e; xSemaphoreTake(g_app.mutex,portMAX_DELAY);
        if(i>=g_app.event_count){xSemaphoreGive(g_app.mutex);break;}
        e=g_app.events[i];xSemaphoreGive(g_app.mutex);
        char line[180];int len=snprintf(line,sizeof(line),"%lu,%lu,%ld,%s,%s,%lu,%s,%ld,%ld,%ld\n",
            (unsigned long)e.id,(unsigned long)e.uptime_ms,(long)e.weight_mg,
            usgm_grade_name(e.grade),usgm_grade_name(e.reference),
            (unsigned long)e.latency_ms,e.source==SOURCE_SIM?"SIMULATION":"HX711",
            (long)e.min_mg,(long)e.small_upper_mg,(long)e.medium_upper_mg);
        if(len<=0||len>=(int)sizeof(line)||httpd_resp_send_chunk(r,line,len)!=ESP_OK)return ESP_FAIL;
    }
    return httpd_resp_send_chunk(r,NULL,0);
}
void http_start(void) {
    httpd_config_t c=HTTPD_DEFAULT_CONFIG();c.stack_size=8192;c.max_uri_handlers=16;c.lru_purge_enable=true;
    httpd_handle_t h=NULL;ESP_ERROR_CHECK(httpd_start(&h,&c));
    const struct {const char *uri;httpd_method_t method;esp_err_t(*handler)(httpd_req_t*);} routes[]={
        {"/",HTTP_GET,ui_index},{"/app.js",HTTP_GET,ui_app},{"/report.mjs",HTTP_GET,ui_report_js},
        {"/api/status",HTTP_GET,get_status},{"/api/settings",HTTP_POST,post_settings},
        {"/api/simulate",HTTP_POST,post_sim},{"/api/mode",HTTP_POST,post_mode},
        {"/api/cal/zero",HTTP_POST,post_cal},{"/api/cal/span",HTTP_POST,post_cal},
        {"/api/batch/new",HTTP_POST,post_batch},{"/api/reference",HTTP_POST,post_reference},
        {"/api/events",HTTP_GET,get_events},{"/api/report.json",HTTP_GET,get_summary},
        {"/api/report.csv",HTTP_GET,get_csv}
    };
    for(size_t i=0;i<sizeof(routes)/sizeof(routes[0]);i++){
        httpd_uri_t uri={.uri=routes[i].uri,.method=routes[i].method,.handler=routes[i].handler};
        esp_err_t e=httpd_register_uri_handler(h,&uri);
        if(e!=ESP_OK)ESP_LOGE(TAG,"route %s failed: %d",routes[i].uri,e);
    }
    ESP_LOGI(TAG,"Local UI on http://192.168.4.1/");
}
