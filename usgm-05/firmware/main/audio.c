#include "audio.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"
#include "driver/i2s_std.h"
#include "esp_log.h"
#include <stdint.h>
#include <string.h>

extern const uint8_t _binary_small_raw_start[] asm("_binary_small_raw_start");
extern const uint8_t _binary_small_raw_end[] asm("_binary_small_raw_end");
extern const uint8_t _binary_medium_raw_start[] asm("_binary_medium_raw_start");
extern const uint8_t _binary_medium_raw_end[] asm("_binary_medium_raw_end");
extern const uint8_t _binary_large_raw_start[] asm("_binary_large_raw_start");
extern const uint8_t _binary_large_raw_end[] asm("_binary_large_raw_end");
static QueueHandle_t queue;
static i2s_chan_handle_t tx;
static const char *TAG="USGM_AUDIO";
static void play(const uint8_t *start,const uint8_t *end) {
    size_t bytes=(size_t)(end-start)&~(size_t)1;
    const int16_t *mono=(const int16_t*)start;
    int16_t stereo[512];
    for(size_t cursor=0;cursor<bytes/2;){
        size_t n=(bytes/2-cursor)>256?256:(bytes/2-cursor);
        for(size_t i=0;i<n;i++){stereo[i*2]=mono[cursor+i];stereo[i*2+1]=mono[cursor+i];}
        size_t written=0;
        esp_err_t e=i2s_channel_write(tx,stereo,n*4,&written,1000);
        if(e!=ESP_OK || written!=n*4){ESP_LOGE(TAG,"Audio write failed");break;}
        cursor+=n;
    }
    /* Silence to drain the class-D amplifier before next clip. */
    memset(stereo,0,sizeof(stereo));size_t written=0;
    (void)i2s_channel_write(tx,stereo,sizeof(stereo),&written,1000);
}
static void task(void *arg) {
    (void)arg;usgm_grade_t grade;
    while(1) {
        if(xQueueReceive(queue,&grade,portMAX_DELAY)==pdTRUE) {
            if(grade==USGM_SMALL)play(_binary_small_raw_start,_binary_small_raw_end);
            if(grade==USGM_MEDIUM)play(_binary_medium_raw_start,_binary_medium_raw_end);
            if(grade==USGM_LARGE)play(_binary_large_raw_start,_binary_large_raw_end);
        }
    }
}
void audio_enqueue(usgm_grade_t grade) {
    if(queue && xQueueSend(queue,&grade,0)!=pdTRUE) ESP_LOGW(TAG,"Audio queue full");
}
void audio_init(void) {
    queue=xQueueCreate(6,sizeof(usgm_grade_t));configASSERT(queue);
    i2s_chan_config_t chan=I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0,I2S_ROLE_MASTER);
    ESP_ERROR_CHECK(i2s_new_channel(&chan,&tx,NULL));
    i2s_std_config_t cfg={
        .clk_cfg=I2S_STD_CLK_DEFAULT_CONFIG(16000),
        .slot_cfg=I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT,I2S_SLOT_MODE_STEREO),
        .gpio_cfg={.mclk=I2S_GPIO_UNUSED,.bclk=GPIO_NUM_4,.ws=GPIO_NUM_5,
                   .dout=GPIO_NUM_6,.din=I2S_GPIO_UNUSED,.invert_flags={0}}
    };
    ESP_ERROR_CHECK(i2s_channel_init_std_mode(tx,&cfg));
    ESP_ERROR_CHECK(i2s_channel_enable(tx));
    configASSERT(xTaskCreate(task,"voice",4096,NULL,3,NULL)==pdPASS);
}
