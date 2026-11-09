#pragma once
// Text for the card screen. Pure (unit tested on the host).
#include <stdbool.h>
#include <stdint.h>

// A local date and time (fields as in struct tm, which Pebble's headers keep
// to pebble.h).
typedef struct {
  int year;  // e.g. 2026
  int mon;   // 0..11
  int mday;  // 1..31
  int yday;  // 0..365
  int hour;
  int min;
} LocalTime;

// "¥1,234" or "-¥50".
void format_yen(int32_t amount, char *buf, uint16_t cap);

// "Scanned today 14:20", "Scanned yesterday 9:05 PM", "Scanned 3 Nov 14:20",
// or with the year when it was another year. scanned and now are local times.
void format_scanned(const LocalTime *scanned, const LocalTime *now, bool clock24, char *buf,
                    uint16_t cap);
