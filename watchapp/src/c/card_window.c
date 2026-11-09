#include "card_window.h"

#include "art.h"
#include "cards.h"
#include "format.h"
#include "history_window.h"

#define KEY_SELECTED 100
#define DOTS_MAX 12

typedef struct {
  int16_t art_y;
  int16_t name_y, name_h;
  int16_t balance_y, balance_h;
  int16_t scanned_y, scanned_h;
  int16_t dots_y;
  const char *name_font, *balance_font, *small_font;
} Layout;

static Window *s_window;
static Layer *s_layer;
static uint8_t s_selected;
static GBitmap *s_art;
static int s_art_for = -1;  // card the decoded art belongs to

static Layout layout(void) {
#if defined(PBL_PLATFORM_EMERY)
  return (Layout){8, 112, 30, 140, 36, 180, 24, 212,
                  FONT_KEY_GOTHIC_24_BOLD, FONT_KEY_GOTHIC_28_BOLD, FONT_KEY_GOTHIC_18};
#elif defined(PBL_PLATFORM_GABBRO)
  return (Layout){30, 138, 30, 166, 36, 202, 24, 234,
                  FONT_KEY_GOTHIC_24_BOLD, FONT_KEY_GOTHIC_28_BOLD, FONT_KEY_GOTHIC_18};
#elif defined(PBL_ROUND)
  return (Layout){20, 92, 22, 110, 32, 142, 18, 166,
                  FONT_KEY_GOTHIC_18_BOLD, FONT_KEY_GOTHIC_28_BOLD, FONT_KEY_GOTHIC_14};
#else
  return (Layout){6, 84, 22, 102, 32, 134, 18, 158,
                  FONT_KEY_GOTHIC_18_BOLD, FONT_KEY_GOTHIC_28_BOLD, FONT_KEY_GOTHIC_14};
#endif
}

static GColor dim_text(void) {
  return PBL_IF_COLOR_ELSE(GColorLightGray, GColorWhite);
}

static void drop_art(void) {
  if (s_art) gbitmap_destroy(s_art);
  s_art = NULL;
  s_art_for = -1;
}

static LocalTime local(time_t t) {
  struct tm *tm = localtime(&t);
  return (LocalTime){tm->tm_year + 1900, tm->tm_mon, tm->tm_mday, tm->tm_yday, tm->tm_hour, tm->tm_min};
}

static void draw_text(GContext *ctx, const char *text, const char *font, GRect r, GColor color) {
  graphics_context_set_text_color(ctx, color);
  graphics_draw_text(ctx, text, fonts_get_system_font(font), r, GTextOverflowModeTrailingEllipsis,
                     GTextAlignmentCenter, NULL);
}

static void draw_placeholder(GContext *ctx, GRect r, const Card *card) {
#if defined(PBL_COLOR)
  graphics_context_set_fill_color(ctx, (GColor){.argb = card->info.color | 0xC0});
  graphics_fill_rect(ctx, r, 8, GCornersAll);
#else
  graphics_context_set_stroke_color(ctx, GColorWhite);
  graphics_draw_round_rect(ctx, r, 8);
#endif
}

static void draw_dots(GContext *ctx, GRect b, int16_t y, uint8_t count) {
  if (count < 2) return;
  if (count > DOTS_MAX) {
    char text[12];
    snprintf(text, sizeof(text), "%d / %d", s_selected + 1, count);
    draw_text(ctx, text, FONT_KEY_GOTHIC_14, GRect(0, y - 9, b.size.w, 18), dim_text());
    return;
  }
  int16_t gap = 10;
  int16_t x = b.size.w / 2 - (count - 1) * gap / 2;
  graphics_context_set_fill_color(ctx, GColorWhite);
  graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(GColorDarkGray, GColorWhite));
  for (uint8_t i = 0; i < count; i++, x += gap) {
    if (i == s_selected) {
      graphics_fill_circle(ctx, GPoint(x, y), 3);
    } else {
      graphics_draw_circle(ctx, GPoint(x, y), 2);
    }
  }
}

static void draw_empty(GContext *ctx, GRect b) {
  const char *title = cards_phone_connected() ? "No cards yet" : "Phone not connected";
  const char *body = cards_phone_connected()
                         ? (cards_fresh() ? "Scan a card in IC Reader on your phone."
                                          : "Waiting for IC Reader on your phone...")
                         : "Your cards show here once the phone is back.";
  int16_t pad = PBL_IF_ROUND_ELSE(24, 8);
  draw_text(ctx, title, FONT_KEY_GOTHIC_24_BOLD, GRect(pad, b.size.h / 2 - 50, b.size.w - 2 * pad, 30),
            GColorWhite);
  draw_text(ctx, body, FONT_KEY_GOTHIC_18, GRect(pad, b.size.h / 2 - 16, b.size.w - 2 * pad, 70),
            dim_text());
}

static void update(Layer *layer, GContext *ctx) {
  GRect b = layer_get_bounds(layer);
  uint8_t count = cards_count();
  if (count == 0) {
    draw_empty(ctx, b);
    return;
  }
  if (s_selected >= count) s_selected = count - 1;
  const Card *card = cards_get(s_selected);
  Layout l = layout();

  GSize size = art_size();
  GRect art_rect = GRect((b.size.w - size.w) / 2, l.art_y, size.w, size.h);
  if (s_art_for != s_selected) {
    drop_art();
    s_art = art_create_bitmap(card);
    if (s_art) s_art_for = s_selected;
  }
  if (s_art) {
    // Colour art has transparent corners; 1-bit art is drawn as is (its corners are black,
    // like the background), since GCompOpSet would treat its white as transparent.
    graphics_context_set_compositing_mode(ctx, PBL_IF_COLOR_ELSE(GCompOpSet, GCompOpAssign));
    graphics_draw_bitmap_in_rect(ctx, s_art, art_rect);
  } else {
    draw_placeholder(ctx, art_rect, card);
  }

  int16_t pad = PBL_IF_ROUND_ELSE(18, 4);
  draw_text(ctx, card->info.name, l.name_font, GRect(pad, l.name_y, b.size.w - 2 * pad, l.name_h),
            GColorWhite);

  char balance[20];
  format_yen(card->info.balance, balance, sizeof(balance));
  draw_text(ctx, balance, l.balance_font, GRect(0, l.balance_y, b.size.w, l.balance_h), GColorWhite);

  char scanned[40];
  if (card->info.scanned) {
    LocalTime now = local(time(NULL));
    LocalTime then = local((time_t)card->info.scanned);
    format_scanned(&then, &now, clock_is_24h_style(), scanned, sizeof(scanned));
  } else {
    scanned[0] = '\0';
  }
  draw_text(ctx, scanned, l.small_font, GRect(pad, l.scanned_y, b.size.w - 2 * pad, l.scanned_h),
            dim_text());

  if (!cards_phone_connected()) {
    draw_text(ctx, "Offline", FONT_KEY_GOTHIC_14, GRect(0, l.dots_y - 9, b.size.w, 18), dim_text());
  } else {
    draw_dots(ctx, b, l.dots_y, count);
  }
}

static void select_card(int delta) {
  uint8_t count = cards_count();
  if (count < 2) return;
  s_selected = (s_selected + count + delta) % count;
  persist_write_int(KEY_SELECTED, s_selected);
  layer_mark_dirty(s_layer);
}

static void up_click(ClickRecognizerRef r, void *ctx) {
  select_card(-1);
}

static void down_click(ClickRecognizerRef r, void *ctx) {
  select_card(1);
}

static void select_click(ClickRecognizerRef r, void *ctx) {
  if (s_selected < cards_count()) history_window_push(s_selected);
}

static void click_config(void *ctx) {
  window_single_click_subscribe(BUTTON_ID_UP, up_click);
  window_single_click_subscribe(BUTTON_ID_DOWN, down_click);
  window_single_click_subscribe(BUTTON_ID_SELECT, select_click);
}

static void cards_changed(CardsChange change, int idx) {
  if (change == CardsChangedList) drop_art();
  if (change == CardsChangedArt && idx != s_selected) return;
  if (change == CardsChangedHistory) return;
  layer_mark_dirty(s_layer);
}

static void minute_tick(struct tm *t, TimeUnits units) {
  layer_mark_dirty(s_layer);  // "Scanned today" becomes "yesterday" at midnight
}

static void window_load(Window *window) {
  Layer *root = window_get_root_layer(window);
  s_layer = layer_create(layer_get_bounds(root));
  layer_set_update_proc(s_layer, update);
  layer_add_child(root, s_layer);
  cards_observe(cards_changed);
  tick_timer_service_subscribe(MINUTE_UNIT, minute_tick);
}

static void window_unload(Window *window) {
  tick_timer_service_unsubscribe();
  cards_unobserve(cards_changed);
  drop_art();
  layer_destroy(s_layer);
}

void card_window_push(void) {
  s_selected = persist_exists(KEY_SELECTED) ? persist_read_int(KEY_SELECTED) : 0;
  s_window = window_create();
  window_set_background_color(s_window, GColorBlack);
  window_set_click_config_provider(s_window, click_config);
  window_set_window_handlers(s_window, (WindowHandlers){.load = window_load, .unload = window_unload});
  window_stack_push(s_window, true);
}
