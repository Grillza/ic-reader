#pragma once
// The cards shown on the watch: the list from the phone (saved, so it shows
// before the phone answers), each card's art, and the history of one card.
#include <pebble.h>

#include "codec.h"

#if defined(PBL_PLATFORM_APLITE)
#define MAX_CARDS 6
#define MAX_TX 20
#define MAX_ART_BYTES 2048
#else
#define MAX_CARDS 16
#define MAX_TX 50
#define MAX_ART_BYTES 6144
#endif

typedef struct {
  CardInfo info;
  uint8_t *art;       // RLE bytes, NULL until they start arriving
  uint16_t art_len;   // received so far
  uint16_t art_total;
} Card;

typedef enum {
  CardsChangedList,     // list replaced or a card's details changed
  CardsChangedArt,      // idx's art is complete
  CardsChangedHistory,  // history records arrived
  CardsChangedStatus,   // phone connection
} CardsChange;

typedef void (*CardsObserver)(CardsChange change, int idx);

void cards_init(void);
void cards_deinit(void);
void cards_observe(CardsObserver observer);
void cards_unobserve(CardsObserver observer);

uint8_t cards_count(void);
const Card *cards_get(uint8_t idx);
bool cards_art_ready(const Card *card);
// True once the phone has sent this session's list.
bool cards_fresh(void);

void cards_apply_list(const uint8_t *data, uint16_t len);
void cards_apply_art(uint8_t idx, uint16_t offset, uint16_t total, const uint8_t *data, uint16_t len);

// History of one card, requested when its screen opens.
void cards_history_reset(uint8_t idx);
void cards_apply_history(uint8_t idx, uint16_t offset, uint16_t total, const uint8_t *data, uint16_t len);
int cards_history_card(void);       // card the history belongs to, -1 if none
int cards_history_total(void);      // records the phone has, -1 until known
uint16_t cards_history_count(void); // records received
const TxInfo *cards_history_get(uint16_t idx);

void cards_set_phone(bool connected);
bool cards_phone_connected(void);
