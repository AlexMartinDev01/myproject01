#pragma once
#include "app.h"
bool storage_init(void);
bool storage_load_config(void);
bool storage_save_config(void);
bool storage_restore_batch(void);
bool storage_append_event(const record_t *rec);
bool storage_append_reference(uint32_t id,usgm_grade_t grade);
bool storage_clear_batch(void);
