#pragma once
// Message types and wire formats shared with the Android app
// (app/src/main/java/io/github/grillza/icreader/pebble/WatchProtocol.kt).
//
// Watch -> phone
//   MsgHello:          Width, Height = card art size wanted; Total = inbox size;
//                      Count = largest RLE art accepted; Offset = 1 for a
//                      black-and-white screen; Version = PROTOCOL_VERSION.
//   MsgHistoryRequest: Index = card.
// Phone -> watch
//   MsgCards:   Count, Data = card records (see codec.h). Replaces the list.
//   MsgArt:     Index, Offset, Total = RLE size, Data = slice of the RLE art.
//   MsgHistory: Index, Offset = first record, Total = record count, Data = records.

#define PROTOCOL_VERSION 1

typedef enum {
  MsgHello = 1,
  MsgHistoryRequest = 2,
  MsgCards = 10,
  MsgArt = 11,
  MsgHistory = 12,
} MsgType;
