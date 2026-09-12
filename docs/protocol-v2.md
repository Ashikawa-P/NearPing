# NearPing wire protocol v2

This document is the compatibility contract for future Android and iOS transports. The transport must offer discovered endpoint events, full-duplex reliable byte messages, disconnect events, and a per-session endpoint identifier. It must not interpret message bodies.

Every payload is one UTF-8 JSON object no larger than 30,000 bytes. Unknown JSON fields must be ignored. A receiver ignores unsupported `protocolVersion` values, malformed payloads, messages from a session ID that changes during one endpoint connection, and directed messages whose `targetSessionId` does not equal its own current session.

Version 2 is intentionally not wire-compatible with version 1. It uses the service ID `de.gabriel.nearping.protocol.v2`, removes all location fields, and adds whitelisted coordination signals.

## Envelope

```json
{
  "protocolVersion": 2,
  "type": "HELLO",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000
}
```

`targetSessionId` is omitted for `HELLO` and required for all other message types. `messageId` is unique per message. Coordination-signal IDs are retained per peer for receive-side deduplication during the session.

## HELLO

```json
{
  "protocolVersion": 2,
  "type": "HELLO",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "sentAtEpochMs": 1787065200000,
  "hello": { "displayName": "Gabriel" }
}
```

`HELLO` is sent immediately after connection. A nonblank display name of at most 32 characters makes the endpoint visible. There are no coordinate, accuracy, or distance fields.

## PING

```json
{
  "protocolVersion": 2,
  "type": "PING",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000,
  "ping": { "active": true }
}
```

One true direction produces the red state. Both directions produce the green state. Version 2 does not define withdrawal because the state is destroyed when the peer connection or session ends.

## PHOTO_REQUEST and PHOTO_DATA

`PHOTO_REQUEST` is sent when a visible list entry is opened. `PHOTO_DATA` answers with `{ "photo": { "jpegBase64": "..." } }`. The decoded value must begin with a JPEG marker and be no larger than 22,000 bytes. Android currently creates a square thumbnail no larger than 20,000 bytes.

Both message types are directed and must include the receiving session's ID.

## COORDINATION_SIGNAL

```json
{
  "protocolVersion": 2,
  "type": "COORDINATION_SIGNAL",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000,
  "coordinationSignal": {
    "code": "floor",
    "optionCode": "OG3"
  }
}
```

A coordination signal is accepted only while the two peers have mutually pinged. Receivers derive display text locally from the following whitelist; transmitted arbitrary text is never rendered.

The codes `come_to_you`, `come_to_me`, `wave`, `yes`, and `no` require no `optionCode`. `floor` requires one of `UG3`, `UG2`, `UG1`, `EG`, or `OG1` through `OG20`. `meet_at` requires one of `main_entrance`, `exit`, `reception`, `elevator`, `stairs`, `kitchen`, `cafeteria`, or `cafe`. Unknown codes, missing required options, and unexpected options are ignored.

Clients should keep a per-peer session list of recent coordination events. Android caps this list at 30 events and applies a 750-millisecond send cooldown.

## Compatibility rule for iOS

An iOS implementation must use the same service ID, field names, enum strings, validation limits, target-session checks, and state semantics. Transport-specific endpoint IDs never appear in the wire protocol. Connection initiation prefers the lexicographically lower random session ID; the other endpoint may initiate after a short fallback delay to tolerate one-sided discovery.
