#include "comms.h"

#include "art.h"
#include "cards.h"
#include "protocol.h"

// The phone sizes its messages to the watch's inbox: big enough for a card's
// art in one go. The original Pebble has little memory to spare.
#if defined(PBL_PLATFORM_APLITE)
#define INBOX_CAP 1024
#else
#define INBOX_CAP 8192
#endif
#define OUTBOX_SIZE 64
#define QUEUE_LEN 4
#define MAX_ATTEMPTS 5
#define BUSY_RETRY_MS 50
#define NACK_RETRY_MS 300
// Say hello again until the phone answers (its app may still be starting).
#define HELLO_RETRY_MS 5000

typedef struct {
  uint8_t type;
  uint8_t idx;
  uint8_t attempts;
} Command;

static Command s_queue[QUEUE_LEN];
static uint8_t s_queue_len;
static bool s_sending;
static AppTimer *s_retry_timer;
static AppTimer *s_hello_timer;
static uint32_t s_inbox_size;

static void pump(void);

static void retry_fired(void *data) {
  s_retry_timer = NULL;
  pump();
}

static void retry_in(uint32_t ms) {
  if (!s_retry_timer) s_retry_timer = app_timer_register(ms, retry_fired, NULL);
}

static void drop_head(void) {
  if (!s_queue_len) return;
  memmove(&s_queue[0], &s_queue[1], (s_queue_len - 1) * sizeof(Command));
  s_queue_len--;
}

static void enqueue(uint8_t type, uint8_t idx) {
  for (uint8_t i = s_sending ? 1 : 0; i < s_queue_len; i++) {
    if (s_queue[i].type == type) {
      s_queue[i].idx = idx;
      return;
    }
  }
  if (s_queue_len == QUEUE_LEN) drop_head();
  s_queue[s_queue_len++] = (Command){.type = type, .idx = idx};
  pump();
}

static void pump(void) {
  if (s_sending || s_retry_timer || !s_queue_len) return;
  DictionaryIterator *iter;
  if (app_message_outbox_begin(&iter) != APP_MSG_OK) {
    retry_in(BUSY_RETRY_MS);
    return;
  }
  const Command *c = &s_queue[0];
  dict_write_uint8(iter, MESSAGE_KEY_Type, c->type);
  if (c->type == MsgHello) {
    GSize size = art_size();
    dict_write_uint16(iter, MESSAGE_KEY_Width, size.w);
    dict_write_uint16(iter, MESSAGE_KEY_Height, size.h);
    dict_write_uint32(iter, MESSAGE_KEY_Total, s_inbox_size);
    dict_write_uint16(iter, MESSAGE_KEY_Count, MAX_ART_BYTES);
    // Offset = 1 on black-and-white screens: the phone draws the art in black and white.
    dict_write_uint8(iter, MESSAGE_KEY_Offset, PBL_IF_COLOR_ELSE(0, 1));
    dict_write_uint8(iter, MESSAGE_KEY_Version, PROTOCOL_VERSION);
  } else {
    dict_write_uint8(iter, MESSAGE_KEY_Index, c->idx);
  }
  AppMessageResult r = app_message_outbox_send();
  if (r == APP_MSG_OK) {
    s_sending = true;
  } else {
    retry_in(r == APP_MSG_BUSY ? BUSY_RETRY_MS : NACK_RETRY_MS);
  }
}

static void outbox_sent(DictionaryIterator *iter, void *context) {
  s_sending = false;
  drop_head();
  pump();
}

static void outbox_failed(DictionaryIterator *iter, AppMessageResult reason, void *context) {
  s_sending = false;
  if (s_queue_len && ++s_queue[0].attempts >= MAX_ATTEMPTS) drop_head();
  retry_in(reason == APP_MSG_BUSY ? BUSY_RETRY_MS : NACK_RETRY_MS);
}

static int32_t read_int(DictionaryIterator *iter, uint32_t key, int32_t fallback) {
  Tuple *t = dict_find(iter, key);
  if (!t || (t->type != TUPLE_INT && t->type != TUPLE_UINT)) return fallback;
  if (t->length == 1) return t->type == TUPLE_INT ? t->value->int8 : t->value->uint8;
  if (t->length == 2) return t->type == TUPLE_INT ? t->value->int16 : t->value->uint16;
  return t->value->int32;
}

static void hello_fired(void *data) {
  s_hello_timer = NULL;
  if (cards_fresh()) return;
  enqueue(MsgHello, 0);
  s_hello_timer = app_timer_register(HELLO_RETRY_MS, hello_fired, NULL);
}

static void say_hello(void) {
  if (s_hello_timer) app_timer_cancel(s_hello_timer);
  s_hello_timer = NULL;
  enqueue(MsgHello, 0);
  s_hello_timer = app_timer_register(HELLO_RETRY_MS, hello_fired, NULL);
}

static void inbox_received(DictionaryIterator *iter, void *context) {
  int32_t type = read_int(iter, MESSAGE_KEY_Type, -1);
  int32_t idx = read_int(iter, MESSAGE_KEY_Index, 0);
  int32_t offset = read_int(iter, MESSAGE_KEY_Offset, 0);
  int32_t total = read_int(iter, MESSAGE_KEY_Total, 0);
  Tuple *data = dict_find(iter, MESSAGE_KEY_Data);
  const uint8_t *bytes = (data && data->type == TUPLE_BYTE_ARRAY) ? data->value->data : NULL;
  uint16_t len = bytes ? data->length : 0;
  cards_set_phone(true);

  switch (type) {
    case MsgCards:
      cards_apply_list(bytes, len);
      // Reopened history: ask again, the numbering may have changed.
      if (cards_history_card() >= 0) comms_request_history(cards_history_card());
      break;
    case MsgArt:
      if (bytes && idx >= 0 && idx < 255) cards_apply_art(idx, offset, total, bytes, len);
      break;
    case MsgHistory:
      if (idx >= 0 && idx < 255) cards_apply_history(idx, offset, total, bytes, len);
      break;
    default:
      break;
  }
}

static void phone_connection(bool connected) {
  cards_set_phone(connected);
  if (connected) say_hello();
}

void comms_init(void) {
  app_message_register_inbox_received(inbox_received);
  app_message_register_outbox_sent(outbox_sent);
  app_message_register_outbox_failed(outbox_failed);
  uint32_t inbox = app_message_inbox_size_maximum();
  s_inbox_size = inbox < INBOX_CAP ? inbox : INBOX_CAP;
  app_message_open(s_inbox_size, OUTBOX_SIZE);
  connection_service_subscribe((ConnectionHandlers){.pebble_app_connection_handler = phone_connection});
  cards_set_phone(connection_service_peek_pebble_app_connection());
  say_hello();
}

void comms_deinit(void) {
  connection_service_unsubscribe();
  if (s_retry_timer) app_timer_cancel(s_retry_timer);
  if (s_hello_timer) app_timer_cancel(s_hello_timer);
  s_retry_timer = s_hello_timer = NULL;
}

void comms_request_history(uint8_t idx) {
  enqueue(MsgHistoryRequest, idx);
}
