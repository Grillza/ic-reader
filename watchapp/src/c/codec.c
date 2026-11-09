#include "codec.h"

#include <string.h>

static uint32_t le32(const uint8_t *p) {
  return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

static void copy_text(char *dst, uint16_t cap, const uint8_t *src, uint8_t len) {
  uint16_t n = len < cap - 1 ? len : cap - 1;
  // Never end on half a UTF-8 character.
  if (n < len) {
    while (n > 0 && (src[n] & 0xC0) == 0x80) n--;
  }
  memcpy(dst, src, n);
  dst[n] = '\0';
}

int codec_parse_cards(const uint8_t *data, uint16_t len, CardRecordCb cb, void *ctx) {
  int count = 0;
  uint16_t pos = 0;
  while (pos + CARD_RECORD_HEADER <= len && count < 255) {
    const uint8_t *r = data + pos;
    uint8_t name_len = r[14];
    if (pos + CARD_RECORD_HEADER + name_len > len) break;
    CardInfo info;
    info.balance = (int32_t)le32(r);
    info.scanned = le32(r + 4);
    info.art_hash = le32(r + 8);
    info.flags = r[12];
    info.color = r[13];
    copy_text(info.name, sizeof(info.name), r + CARD_RECORD_HEADER, name_len);
    cb((uint8_t)count, &info, ctx);
    count++;
    pos += CARD_RECORD_HEADER + name_len;
  }
  return count;
}

int codec_parse_history(const uint8_t *data, uint16_t len, uint16_t first, TxRecordCb cb, void *ctx) {
  int count = 0;
  uint16_t pos = 0;
  while (pos + 2 < len) {
    TxInfo tx;
    tx.kind = data[pos];
    tx.flags = data[pos + 1];
    uint16_t p = pos + 2;
    char *fields[3] = {tx.title, tx.when, tx.amount};
    uint16_t caps[3] = {sizeof(tx.title), sizeof(tx.when), sizeof(tx.amount)};
    bool ok = true;
    for (int f = 0; f < 3; f++) {
      if (p >= len || p + 1 + data[p] > len) {
        ok = false;
        break;
      }
      copy_text(fields[f], caps[f], data + p + 1, data[p]);
      p += 1 + data[p];
    }
    if (!ok) break;
    cb(first + count, &tx, ctx);
    count++;
    pos = p;
  }
  return count;
}

bool codec_decode_art(const uint8_t *rle, uint16_t len, uint16_t width, uint16_t height, PixelCb cb,
                      void *ctx) {
  uint32_t total = (uint32_t)width * height;
  uint32_t done = 0;
  for (uint16_t pos = 0; pos + 1 < len; pos += 2) {
    uint8_t run = rle[pos];
    uint8_t argb = rle[pos + 1];
    if (run == 0 || done + run > total) return false;
    for (uint8_t i = 0; i < run; i++, done++) cb(done % width, done / width, argb, ctx);
  }
  return done == total;
}

static const uint8_t BAYER[4][4] = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

bool codec_dither_white(uint8_t argb, uint16_t x, uint16_t y) {
  if ((argb >> 6) == 0) return false;
  uint8_t r = (argb >> 4) & 3, g = (argb >> 2) & 3, b = argb & 3;
  // Brightness 0..48 (each channel 0..3, weighted 4:8:4).
  uint8_t lum = (r * 4 + g * 8 + b * 4);
  return lum * 16 / 48 > BAYER[y & 3][x & 3];
}
