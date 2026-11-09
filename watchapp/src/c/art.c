#include "art.h"

GSize art_size(void) {
#if defined(PBL_PLATFORM_EMERY)
  return GSize(164, 103);
#elif defined(PBL_PLATFORM_GABBRO)
  return GSize(170, 107);
#elif defined(PBL_ROUND)
  return GSize(116, 73);
#else
  return GSize(124, 78);
#endif
}

typedef struct {
  uint8_t *data;
  uint16_t stride;
} Target;

#if defined(PBL_COLOR)
static void put_pixel(uint16_t x, uint16_t y, uint8_t argb, void *ctx) {
  Target *t = ctx;
  t->data[y * t->stride + x] = argb;
}
#define ART_FORMAT GBitmapFormat8Bit
#else
static void put_pixel(uint16_t x, uint16_t y, uint8_t argb, void *ctx) {
  Target *t = ctx;
  if (codec_dither_white(argb, x, y)) t->data[y * t->stride + x / 8] |= 1 << (x % 8);
}
#define ART_FORMAT GBitmapFormat1Bit
#endif

GBitmap *art_create_bitmap(const Card *card) {
  if (!cards_art_ready(card)) return NULL;
  GSize size = art_size();
  GBitmap *bitmap = gbitmap_create_blank(size, ART_FORMAT);
  if (!bitmap) return NULL;
  Target t = {.data = gbitmap_get_data(bitmap), .stride = gbitmap_get_bytes_per_row(bitmap)};
  if (!codec_decode_art(card->art, card->art_total, size.w, size.h, put_pixel, &t)) {
    gbitmap_destroy(bitmap);
    return NULL;
  }
  return bitmap;
}
