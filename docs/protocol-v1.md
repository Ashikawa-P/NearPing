# NearPing wire protocol v1

This document is the compatibility contract for future Android and iOS transports. The transport must offer discovered endpoint events, full-duplex reliable byte messages, disconnect events, and a per-session endpoint identifier. It must not interpret the message bodies.

Every payload is one UTF-8 JSON object no larger than 30,000 bytes. Unknown JSON fields must be ignored. A receiver must ignore unsupported `protocolVersion` values, malformed payloads, messages from a session ID that changes during one endpoint connection, and messages whose `targetSessionId` does not equal its own current session.

## Envelope

```json
{
  "protocolVersion": 1,
  "type": "HELLO",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000
}
```

`targetSessionId` is omitted for broadcast-style `HELLO` messages and required for all directed messages. `messageId` is unique per message and reserved for later deduplication. The MVP's state mutations are idempotent and do not yet retain a deduplication cache.

## HELLO

```json
{
  "protocolVersion": 1,
  "type": "HELLO",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "sentAtEpochMs": 1787065200000,
  "hello": {
    "displayName": "Gabriel",
    "latitude": 51.4818,
    "longitude": 7.2162,
    "accuracyMeters": 8.5,
    "locationCapturedAtEpochMs": 1787065199000
  }
}
```

`HELLO` is sent immediately after connection and after every new local location result. The location fields may be absent while the first location measurement is pending. The receiving app uses its local receipt time for freshness so clock differences between phones do not incorrectly hide peers.

## PING

```json
{
  "protocolVersion": 1,
  "type": "PING",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000,
  "ping": { "active": true }
}
```

One true direction produces the red state. Both true directions produce the green state. Version 1 does not define ping withdrawal because the state is destroyed when the session or peer connection ends.

## PHOTO_REQUEST

```json
{
  "protocolVersion": 1,
  "type": "PHOTO_REQUEST",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000
}
```

This is sent when a visible list entry is opened.

## PHOTO_DATA

```json
{
  "protocolVersion": 1,
  "type": "PHOTO_DATA",
  "messageId": "...",
  "senderSessionId": "1e6686d25c5d4ed0",
  "targetSessionId": "72f05250bb244dc8",
  "sentAtEpochMs": 1787065200000,
  "photo": { "jpegBase64": "/9j/4AAQSk..." }
}
```

The decoded value must be a JPEG no larger than 22,000 bytes. Android currently creates a square thumbnail no larger than 20,000 bytes, leaving room for Base64 expansion and the JSON envelope within Nearby Connections' byte-payload limit.

## Compatibility rule for iOS

The iOS implementation must use the same service ID, cluster topology where supported, session-ID ordering for deciding which side initiates a connection, field names, enum strings, limits, and state semantics. Transport-specific endpoint IDs never appear in the protocol and remain local to each client.
