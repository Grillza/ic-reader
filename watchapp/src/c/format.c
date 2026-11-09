#include "format.h"

#include <stdio.h>
#include <string.h>

void format_yen(int32_t amount, char *buf, uint16_t cap) {
  char digits[16];
  uint32_t value = amount < 0 ? (uint32_t)(-(int64_t)amount) : (uint32_t)amount;
  int n = snprintf(digits, sizeof(digits), "%lu", (unsigned long)value);
  char out[24];
  int o = 0;
  if (amount < 0) out[o++] = '-';
  out[o++] = (char)0xC2;  // ¥ in UTF-8
  out[o++] = (char)0xA5;
  for (int i = 0; i < n; i++) {
    if (i > 0 && (n - i) % 3 == 0) out[o++] = ',';
    out[o++] = digits[i];
  }
  out[o] = '\0';
  snprintf(buf, cap, "%s", out);
}

static const char *const MONTHS[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                     "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

static void format_clock(const LocalTime *t, bool clock24, char *buf, uint16_t cap) {
  if (clock24) {
    snprintf(buf, cap, "%d:%02d", t->hour, t->min);
  } else {
    int h = t->hour % 12;
    snprintf(buf, cap, "%d:%02d %s", h ? h : 12, t->min, t->hour < 12 ? "AM" : "PM");
  }
}

// Days from 1 Jan 2000 to the given local date, for comparing dates.
static int32_t day_number(const LocalTime *t) {
  int32_t y = t->year;
  int32_t days = (y - 2000) * 365 + (y - 1997) / 4 - (y - 1901) / 100 + (y - 1601) / 400;
  return days + t->yday;
}

void format_scanned(const LocalTime *scanned, const LocalTime *now, bool clock24, char *buf,
                    uint16_t cap) {
  char clock[12];
  format_clock(scanned, clock24, clock, sizeof(clock));
  int32_t diff = day_number(now) - day_number(scanned);
  const char *month = MONTHS[scanned->mon % 12];
  if (diff == 0) {
    snprintf(buf, cap, "Scanned today %s", clock);
  } else if (diff == 1) {
    snprintf(buf, cap, "Scanned yesterday %s", clock);
  } else if (scanned->year == now->year) {
    snprintf(buf, cap, "Scanned %d %s %s", scanned->mday, month, clock);
  } else {
    snprintf(buf, cap, "Scanned %d %s %d", scanned->mday, month, scanned->year);
  }
}
