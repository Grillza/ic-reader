#include "history_window.h"

#include <pebble.h>

#include "cards.h"
#include "comms.h"
#include "format.h"

static Window *s_window;
static MenuLayer *s_menu;
static TextLayer *s_message;
static uint8_t s_card;
static char s_header[CARD_NAME_BUF + 32];

static int16_t row_height(void) {
  return PBL_IF_ROUND_ELSE(PBL_DISPLAY_WIDTH >= 240 ? 62 : 52, PBL_DISPLAY_WIDTH >= 200 ? 54 : 44);
}

static const char *title_font(void) {
  return PBL_DISPLAY_WIDTH >= 200 ? FONT_KEY_GOTHIC_24_BOLD : FONT_KEY_GOTHIC_18_BOLD;
}

static const char *small_font(void) {
  return PBL_DISPLAY_WIDTH >= 200 ? FONT_KEY_GOTHIC_18 : FONT_KEY_GOTHIC_14;
}

static uint16_t num_rows(MenuLayer *menu, uint16_t section, void *ctx) {
  return cards_history_card() == s_card ? cards_history_count() : 0;
}

static int16_t cell_height(MenuLayer *menu, MenuIndex *index, void *ctx) {
  return row_height();
}

static int16_t header_height(MenuLayer *menu, uint16_t section, void *ctx) {
  return PBL_DISPLAY_WIDTH >= 200 ? 26 : 20;
}

static uint16_t num_sections(MenuLayer *menu, void *ctx) {
  return 1;
}

static void draw_header(GContext *ctx, const Layer *cell, uint16_t section, void *data) {
  GRect b = layer_get_bounds(cell);
  graphics_context_set_fill_color(ctx, PBL_IF_COLOR_ELSE(GColorWhite, GColorBlack));
  graphics_fill_rect(ctx, b, 0, GCornerNone);
  graphics_context_set_text_color(ctx, PBL_IF_COLOR_ELSE(GColorBlack, GColorWhite));
  graphics_draw_text(ctx, s_header, fonts_get_system_font(small_font()),
                     GRect(4, PBL_DISPLAY_WIDTH >= 200 ? 2 : 1, b.size.w - 8, b.size.h),
                     GTextOverflowModeTrailingEllipsis, GTextAlignmentCenter, NULL);
}

static void draw_row(GContext *ctx, const Layer *cell, MenuIndex *index, void *data) {
  const TxInfo *tx = cards_history_get(index->row);
  if (!tx) return;
  GRect b = layer_get_bounds(cell);
  bool selected = menu_cell_layer_is_highlighted(cell);
  GColor fg = selected ? GColorBlack : GColorWhite;
  GColor sub = fg;
  int16_t pad = PBL_IF_ROUND_ELSE(22, 5);
  int16_t title_h = PBL_DISPLAY_WIDTH >= 200 ? 28 : 22;
  int16_t top = PBL_DISPLAY_WIDTH >= 200 ? 0 : -2;
  GFont small = fonts_get_system_font(small_font());
  GTextAlignment align = PBL_IF_ROUND_ELSE(GTextAlignmentCenter, GTextAlignmentLeft);

  graphics_context_set_text_color(ctx, fg);
  graphics_draw_text(ctx, tx->title, fonts_get_system_font(title_font()),
                     GRect(pad, top, b.size.w - 2 * pad, title_h), GTextOverflowModeTrailingEllipsis,
                     align, NULL);
  GRect line = GRect(pad, top + title_h - 1, b.size.w - 2 * pad, title_h);
#if defined(PBL_ROUND)
  char both[48];
  snprintf(both, sizeof(both), "%s  %s", tx->when, tx->amount);
  graphics_context_set_text_color(ctx, sub);
  graphics_draw_text(ctx, both, small, line, GTextOverflowModeTrailingEllipsis, align, NULL);
#else
  GColor money = (tx->flags & TX_FLAG_CREDIT) && !selected ? PBL_IF_COLOR_ELSE(GColorScreaminGreen, GColorWhite) : fg;
  graphics_context_set_text_color(ctx, sub);
  graphics_draw_text(ctx, tx->when, small, line, GTextOverflowModeTrailingEllipsis, GTextAlignmentLeft,
                     NULL);
  graphics_context_set_text_color(ctx, money);
  graphics_draw_text(ctx, tx->amount, fonts_get_system_font(PBL_DISPLAY_WIDTH >= 200 ? FONT_KEY_GOTHIC_18_BOLD : FONT_KEY_GOTHIC_14_BOLD),
                     line, GTextOverflowModeTrailingEllipsis, GTextAlignmentRight, NULL);
#endif
}

static void update_message(void) {
  const char *text = NULL;
  bool mine = cards_history_card() == s_card;
  if (!mine || cards_history_total() < 0) {
    text = cards_phone_connected() ? "Loading..." : "Phone not connected";
  } else if (cards_history_total() == 0) {
    text = "No history on this card yet.";
  }
  layer_set_hidden(text_layer_get_layer(s_message), text == NULL);
  if (text) text_layer_set_text(s_message, text);
}

static void cards_changed(CardsChange change, int idx) {
  if (change == CardsChangedHistory || change == CardsChangedList) menu_layer_reload_data(s_menu);
  update_message();
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  GRect b = layer_get_bounds(root);
  s_menu = menu_layer_create(b);
  menu_layer_set_callbacks(s_menu, NULL, (MenuLayerCallbacks){
    .get_num_sections = num_sections,
    .get_num_rows = num_rows,
    .get_cell_height = cell_height,
    .get_header_height = header_height,
    .draw_header = draw_header,
    .draw_row = draw_row,
  });
  menu_layer_set_normal_colors(s_menu, GColorBlack, GColorWhite);
  menu_layer_set_highlight_colors(s_menu, PBL_IF_COLOR_ELSE(GColorVividCerulean, GColorWhite), GColorBlack);
  menu_layer_set_click_config_onto_window(s_menu, window);
  layer_add_child(root, menu_layer_get_layer(s_menu));

  int16_t pad = PBL_IF_ROUND_ELSE(24, 10);
  s_message = text_layer_create(GRect(pad, b.size.h / 2 - 24, b.size.w - 2 * pad, 60));
  text_layer_set_background_color(s_message, GColorClear);
  text_layer_set_text_color(s_message, GColorWhite);
  text_layer_set_font(s_message, fonts_get_system_font(FONT_KEY_GOTHIC_18));
  text_layer_set_text_alignment(s_message, GTextAlignmentCenter);
  layer_add_child(root, text_layer_get_layer(s_message));

  cards_observe(cards_changed);
  update_message();
}

static void window_unload(Window *window) {
  cards_unobserve(cards_changed);
  text_layer_destroy(s_message);
  menu_layer_destroy(s_menu);
  window_destroy(window);
  cards_history_reset(0xFF);
}

void history_window_push(uint8_t card) {
  const Card *c = cards_get(card);
  if (!c) return;
  s_card = card;
  char balance[20];
  format_yen(c->info.balance, balance, sizeof(balance));
  snprintf(s_header, sizeof(s_header), "%s  %s", c->info.name, balance);
  cards_history_reset(card);
  comms_request_history(card);
  s_window = window_create();
  window_set_background_color(s_window, GColorBlack);
  window_set_window_handlers(s_window, (WindowHandlers){.load = window_load, .unload = window_unload});
  window_stack_push(s_window, true);
}
