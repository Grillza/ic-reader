#pragma once
// Card art: the phone renders each card at this size and sends it RLE
// compressed; it's decoded into a bitmap when the card is on screen.
#include <pebble.h>

#include "cards.h"

GSize art_size(void);
// Decodes the card's art into a new bitmap (caller destroys), or NULL.
GBitmap *art_create_bitmap(const Card *card);
