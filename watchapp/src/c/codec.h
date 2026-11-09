#pragma once
// Parsing of the phone's records and the RLE card art. Pure (unit tested on
// the host), no Pebble APIs.
#include <stdbool.h>
#include <stdint.h>

#define CARD_NAME_BUF 32
#define CARD_FLAG_TRANSIT 1  // transit IC: the card outline has a notch

// Card record: balance int32 LE, scanned uint32 LE (unix time), art hash
// uint32 LE, flags, colour (GColor8 argb, used until the art arrives),
// name length, name (UTF-8).
#define CARD_RECORD_HEADER 15

typedef struct {
  char name[CARD_NAME_BUF];
  int32_t balance;
  uint32_t scanned;
  uint32_t art_hash;
  uint8_t flags;
  uint8_t color;
} CardInfo;

typedef void (*CardRecordCb)(uint8_t idx, const CardInfo *info, void *ctx);
// Returns the number of complete records; a truncated tail is ignored.
int codec_parse_cards(const uint8_t *data, uint16_t len, CardRecordCb cb, void *ctx);

// History record: kind, flags (bit 0: money in), then three length-prefixed
// UTF-8 strings: title, when (date and time), amount ("-¥178").
#define TX_TITLE_BUF 48
#define TX_WHEN_BUF 24
#define TX_AMOUNT_BUF 16
#define TX_FLAG_CREDIT 1

typedef enum { TxTrain = 0, TxBus = 1, TxPurchase = 2, TxCharge = 3, TxOther = 4 } TxKind;

typedef struct {
  char title[TX_TITLE_BUF];
  char when[TX_WHEN_BUF];
  char amount[TX_AMOUNT_BUF];
  uint8_t kind;
  uint8_t flags;
} TxInfo;

typedef void (*TxRecordCb)(uint16_t idx, const TxInfo *tx, void *ctx);
// first = index of the first record in data.
int codec_parse_history(const uint8_t *data, uint16_t len, uint16_t first, TxRecordCb cb, void *ctx);

// RLE art: (count 1..255, GColor8 argb) pairs, rows left to right, top to
// bottom. Calls cb for every pixel; returns false if the data doesn't cover
// exactly width * height pixels.
typedef void (*PixelCb)(uint16_t x, uint16_t y, uint8_t argb, void *ctx);
bool codec_decode_art(const uint8_t *rle, uint16_t len, uint16_t width, uint16_t height, PixelCb cb,
                      void *ctx);

// Black or white for a colour pixel on 1-bit screens: ordered dithering of
// its brightness. Fully transparent pixels are background (false).
bool codec_dither_white(uint8_t argb, uint16_t x, uint16_t y);
