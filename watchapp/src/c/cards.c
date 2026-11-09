#include "cards.h"

// Saved list: KEY_LEN holds the byte length of the last list message, split
// over keys from KEY_BASE (persist values are at most 256 bytes).
#define KEY_LEN 1
#define KEY_BASE 2
#define LIST_BYTES_MAX (MAX_CARDS * (CARD_RECORD_HEADER + CARD_NAME_BUF))
#define MAX_OBSERVERS 3

static Card s_cards[MAX_CARDS];
static uint8_t s_count;
static bool s_fresh;
static bool s_phone = true;
static CardsObserver s_observers[MAX_OBSERVERS];

static TxInfo *s_tx;
static uint16_t s_tx_count;
static int s_tx_total = -1;
static int s_tx_card = -1;

static void notify(CardsChange change, int idx) {
  for (int i = 0; i < MAX_OBSERVERS; i++) {
    if (s_observers[i]) s_observers[i](change, idx);
  }
}

void cards_observe(CardsObserver observer) {
  for (int i = 0; i < MAX_OBSERVERS; i++) {
    if (!s_observers[i]) {
      s_observers[i] = observer;
      return;
    }
  }
}

void cards_unobserve(CardsObserver observer) {
  for (int i = 0; i < MAX_OBSERVERS; i++) {
    if (s_observers[i] == observer) s_observers[i] = NULL;
  }
}

uint8_t cards_count(void) {
  return s_count;
}

const Card *cards_get(uint8_t idx) {
  return idx < s_count ? &s_cards[idx] : NULL;
}

bool cards_art_ready(const Card *card) {
  return card && card->art && card->art_total && card->art_len == card->art_total;
}

bool cards_fresh(void) {
  return s_fresh;
}

// New list: keep art whose hash is unchanged (the phone sends only new art).
typedef struct {
  Card old[MAX_CARDS];
  uint8_t old_count;
} Previous;

static void store_card(uint8_t idx, const CardInfo *info, void *ctx) {
  if (idx >= MAX_CARDS) return;
  Previous *prev = ctx;
  Card *c = &s_cards[idx];
  memset(c, 0, sizeof(Card));
  c->info = *info;
  if (!prev) return;
  for (uint8_t i = 0; i < prev->old_count; i++) {
    Card *o = &prev->old[i];
    if (o->art && o->info.art_hash == info->art_hash && cards_art_ready(o)) {
      c->art = o->art;
      c->art_len = o->art_len;
      c->art_total = o->art_total;
      o->art = NULL;
      break;
    }
  }
}

static void parse_list(const uint8_t *data, uint16_t len, Previous *prev) {
  int n = codec_parse_cards(data, len, store_card, prev);
  s_count = n < MAX_CARDS ? n : MAX_CARDS;
}

static void save_list(const uint8_t *data, uint16_t len) {
  persist_write_int(KEY_LEN, len);
  for (uint16_t off = 0, key = KEY_BASE; off < len; off += PERSIST_DATA_MAX_LENGTH, key++) {
    uint16_t chunk = len - off < PERSIST_DATA_MAX_LENGTH ? len - off : PERSIST_DATA_MAX_LENGTH;
    persist_write_data(key, data + off, chunk);
  }
}

static void load_list(void) {
  int32_t len = persist_read_int(KEY_LEN);
  if (len <= 0 || len > LIST_BYTES_MAX) return;
  uint8_t *buf = malloc(len);
  if (!buf) return;
  for (int32_t off = 0, key = KEY_BASE; off < len; off += PERSIST_DATA_MAX_LENGTH, key++) {
    int32_t chunk = len - off < PERSIST_DATA_MAX_LENGTH ? len - off : PERSIST_DATA_MAX_LENGTH;
    if (persist_read_data(key, buf + off, chunk) != chunk) {
      free(buf);
      return;
    }
  }
  parse_list(buf, len, NULL);
  free(buf);
}

void cards_init(void) {
  load_list();
}

void cards_deinit(void) {
  for (uint8_t i = 0; i < MAX_CARDS; i++) {
    free(s_cards[i].art);
    s_cards[i].art = NULL;
  }
  free(s_tx);
  s_tx = NULL;
}

void cards_apply_list(const uint8_t *data, uint16_t len) {
  if (len > LIST_BYTES_MAX) len = LIST_BYTES_MAX;
  Previous *prev = malloc(sizeof(Previous));
  if (prev) {
    memcpy(prev->old, s_cards, sizeof(s_cards));
    prev->old_count = s_count;
  } else {
    for (uint8_t i = 0; i < MAX_CARDS; i++) free(s_cards[i].art);
  }
  memset(s_cards, 0, sizeof(s_cards));
  parse_list(data, len, prev);
  if (prev) {
    for (uint8_t i = 0; i < MAX_CARDS; i++) free(prev->old[i].art);
    free(prev);
  }
  s_fresh = true;
  save_list(data, len);
  // The history belonged to the old list's numbering.
  if (s_tx_card >= s_count) cards_history_reset(0xFF);
  notify(CardsChangedList, -1);
}

void cards_apply_art(uint8_t idx, uint16_t offset, uint16_t total, const uint8_t *data, uint16_t len) {
  if (idx >= s_count || total == 0 || total > MAX_ART_BYTES) return;
  Card *c = &s_cards[idx];
  if (offset == 0 || !c->art || c->art_total != total) {
    free(c->art);
    c->art = malloc(total);
    c->art_len = 0;
    c->art_total = total;
    if (!c->art) return;
  }
  // Slices arrive in order; anything else means a restarted transfer.
  if (offset != c->art_len || offset + len > total) return;
  memcpy(c->art + offset, data, len);
  c->art_len += len;
  if (c->art_len == c->art_total) notify(CardsChangedArt, idx);
}

void cards_history_reset(uint8_t idx) {
  free(s_tx);
  s_tx = NULL;
  s_tx_count = 0;
  s_tx_total = -1;
  s_tx_card = idx < s_count ? idx : -1;
}

static void store_tx(uint16_t idx, const TxInfo *tx, void *ctx) {
  if (idx >= MAX_TX || !s_tx) return;
  s_tx[idx] = *tx;
  if (idx >= s_tx_count) s_tx_count = idx + 1;
}

void cards_apply_history(uint8_t idx, uint16_t offset, uint16_t total, const uint8_t *data, uint16_t len) {
  if ((int)idx != s_tx_card) return;
  if (!s_tx) {
    s_tx = calloc(MAX_TX, sizeof(TxInfo));
    if (!s_tx) return;
  }
  s_tx_total = total < MAX_TX ? total : MAX_TX;
  codec_parse_history(data, len, offset, store_tx, NULL);
  notify(CardsChangedHistory, idx);
}

int cards_history_card(void) {
  return s_tx_card;
}

int cards_history_total(void) {
  return s_tx_total;
}

uint16_t cards_history_count(void) {
  return s_tx_count;
}

const TxInfo *cards_history_get(uint16_t idx) {
  return (s_tx && idx < s_tx_count) ? &s_tx[idx] : NULL;
}

void cards_set_phone(bool connected) {
  if (connected == s_phone) return;
  s_phone = connected;
  notify(CardsChangedStatus, -1);
}

bool cards_phone_connected(void) {
  return s_phone;
}
