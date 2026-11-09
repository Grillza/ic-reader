#include <pebble.h>

#include "card_window.h"
#include "cards.h"
#include "comms.h"

int main(void) {
  cards_init();
  card_window_push();
  comms_init();
  app_event_loop();
  comms_deinit();
  cards_deinit();
}
