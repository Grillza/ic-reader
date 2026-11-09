// Host tests for the watch app's pure logic. Run: sh tests/run_tests.sh
#include <stdio.h>
#include <string.h>

#include "codec.h"
#include "format.h"

static int s_checks, s_failures;
#define CHECK(cond)                                                  \
  do {                                                               \
    s_checks++;                                                      \
    if (!(cond)) {                                                   \
      s_failures++;                                                  \
      printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond);         \
    }                                                                \
  } while (0)
#define CHECK_STR(a, b) CHECK(strcmp((a), (b)) == 0)

static CardInfo s_card;
static int s_cards;
static void on_card(uint8_t idx, const CardInfo *info, void *ctx) {
  s_card = *info;
  s_cards++;
}

static void test_cards(void) {
  // balance 1234, scanned 0x01020304, hash 0xAABBCCDD, transit, colour, "Suica"
  uint8_t rec[] = {0xD2, 0x04, 0, 0, 4, 3, 2, 1, 0xDD, 0xCC, 0xBB, 0xAA, 1, 0x1D, 5, 'S', 'u', 'i', 'c', 'a',
                   // negative balance, empty name
                   0xF6, 0xFF, 0xFF, 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x3F, 0};
  s_cards = 0;
  CHECK(codec_parse_cards(rec, 20, on_card, NULL) == 1);
  CHECK(s_card.balance == 1234);
  CHECK(s_card.scanned == 0x01020304);
  CHECK(s_card.art_hash == 0xAABBCCDDu);
  CHECK(s_card.flags == CARD_FLAG_TRANSIT);
  CHECK_STR(s_card.name, "Suica");
  CHECK(codec_parse_cards(rec, sizeof(rec), on_card, NULL) == 2);
  CHECK(s_card.balance == -10);
  CHECK_STR(s_card.name, "");
  // A truncated record is ignored.
  CHECK(codec_parse_cards(rec, 18, on_card, NULL) == 0);
}

static void test_card_name_truncation(void) {
  uint8_t rec[CARD_RECORD_HEADER + 40];
  memset(rec, 0, sizeof(rec));
  rec[14] = 40;
  // 13 three-byte characters (39 bytes) and one more byte: cut on a character boundary.
  for (int i = 0; i < 39; i += 3) {
    rec[15 + i] = 0xE3;
    rec[16 + i] = 0x81;
    rec[17 + i] = 0x82;
  }
  rec[15 + 39] = 'x';
  s_cards = 0;
  CHECK(codec_parse_cards(rec, sizeof(rec), on_card, NULL) == 1);
  CHECK(strlen(s_card.name) == 30);  // 10 whole characters fit in 31 bytes
}

static TxInfo s_tx;
static uint16_t s_tx_idx;
static void on_tx(uint16_t idx, const TxInfo *tx, void *ctx) {
  s_tx = *tx;
  s_tx_idx = idx;
}

static void test_history(void) {
  uint8_t rec[] = {TxCharge, TX_FLAG_CREDIT, 6, 'C', 'h', 'a', 'r', 'g', 'e', 6, '3', ' ', 'N', 'o', 'v', '.',
                   4, '+', 0xC2, 0xA5, '5', TxTrain, 0, 1, 'A', 0, 0};
  CHECK(codec_parse_history(rec, sizeof(rec), 10, on_tx, NULL) == 2);
  CHECK(s_tx_idx == 11);
  CHECK_STR(s_tx.title, "A");
  CHECK(codec_parse_history(rec, 21, 10, on_tx, NULL) == 1);
  CHECK(s_tx.kind == TxCharge && s_tx.flags == TX_FLAG_CREDIT);
  CHECK_STR(s_tx.when, "3 Nov.");
  CHECK_STR(s_tx.amount, "+\xC2\xA5" "5");
  CHECK(codec_parse_history(rec, 20, 0, on_tx, NULL) == 0);
}

static uint8_t s_pixels[4 * 3];
static void on_pixel(uint16_t x, uint16_t y, uint8_t argb, void *ctx) {
  s_pixels[y * 4 + x] = argb;
}

static void test_art(void) {
  uint8_t rle[] = {5, 0xC0, 7, 0xFF};
  CHECK(codec_decode_art(rle, sizeof(rle), 4, 3, on_pixel, NULL));
  CHECK(s_pixels[0] == 0xC0 && s_pixels[4] == 0xC0 && s_pixels[5] == 0xFF && s_pixels[11] == 0xFF);
  uint8_t short_rle[] = {5, 0xC0};
  CHECK(!codec_decode_art(short_rle, sizeof(short_rle), 4, 3, on_pixel, NULL));
  uint8_t long_rle[] = {13, 0xC0};
  CHECK(!codec_decode_art(long_rle, sizeof(long_rle), 4, 3, on_pixel, NULL));
  uint8_t zero_run[] = {0, 0xC0, 12, 0xC0};
  CHECK(!codec_decode_art(zero_run, sizeof(zero_run), 4, 3, on_pixel, NULL));
}

static void test_dither(void) {
  int white = 0, black = 0;
  for (int y = 0; y < 4; y++) {
    for (int x = 0; x < 4; x++) {
      white += codec_dither_white(0xFF, x, y);
      black += codec_dither_white(0xC0, x, y);
      CHECK(!codec_dither_white(0x3F, x, y));  // transparent
    }
  }
  CHECK(white == 16);
  CHECK(black == 0);
  int grey = 0;
  for (int y = 0; y < 4; y++) {
    for (int x = 0; x < 4; x++) grey += codec_dither_white(0xEA, x, y);  // light grey
  }
  CHECK(grey > 4 && grey < 16);
}

static void test_yen(void) {
  char buf[24];
  format_yen(0, buf, sizeof(buf));
  CHECK_STR(buf, "\xC2\xA5" "0");
  format_yen(1234, buf, sizeof(buf));
  CHECK_STR(buf, "\xC2\xA5" "1,234");
  format_yen(20000, buf, sizeof(buf));
  CHECK_STR(buf, "\xC2\xA5" "20,000");
  format_yen(-50, buf, sizeof(buf));
  CHECK_STR(buf, "-\xC2\xA5" "50");
  format_yen(1234567, buf, sizeof(buf));
  CHECK_STR(buf, "\xC2\xA5" "1,234,567");
}

static void test_scanned(void) {
  char buf[40];
  LocalTime now = {2026, 10, 10, 313, 9, 30};
  LocalTime today = {2026, 10, 10, 313, 14, 5};
  format_scanned(&today, &now, true, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned today 14:05");
  format_scanned(&today, &now, false, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned today 2:05 PM");
  LocalTime yesterday = {2026, 10, 9, 312, 0, 7};
  format_scanned(&yesterday, &now, false, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned yesterday 12:07 AM");
  LocalTime earlier = {2026, 10, 3, 306, 8, 0};
  format_scanned(&earlier, &now, true, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned 3 Nov 8:00");
  // New Year's Eve seen on 1 January is still "yesterday".
  LocalTime nye = {2025, 11, 31, 364, 23, 0};
  LocalTime jan1 = {2026, 0, 1, 0, 8, 0};
  format_scanned(&nye, &jan1, true, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned yesterday 23:00");
  LocalTime old = {2024, 4, 2, 122, 8, 0};
  format_scanned(&old, &jan1, true, buf, sizeof(buf));
  CHECK_STR(buf, "Scanned 2 May 2024");
}

int main(void) {
  test_cards();
  test_card_name_truncation();
  test_history();
  test_art();
  test_dither();
  test_yen();
  test_scanned();
  printf("Watch tests: %d checks, %d failures\n", s_checks, s_failures);
  return s_failures ? 1 : 0;
}
