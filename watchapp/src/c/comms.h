#pragma once
// AppMessage link to IC Reader on the phone.
#include <pebble.h>

void comms_init(void);
void comms_deinit(void);
void comms_request_history(uint8_t idx);
