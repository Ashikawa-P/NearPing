# NearPing Wire Protocol v3

Protocol v3 ist der plattformneutrale Kompatibilitätsvertrag des öffentlichen NearPing-v1-Releases. Android verwendet es über Google Nearby Connections mit der Service-ID `de.gabriel.nearping.protocol.v3` und `P2P_CLUSTER`. Seit v1.1.0 können dieselben Nachrichten zusätzlich innerhalb von [LAN Transport v1](lan-transport-v1.md) übertragen werden.

Jede Nachricht besteht aus einem UTF-8-JSON-Objekt mit höchstens 30.000 Bytes. Unbekannte JSON-Felder werden ignoriert. Nachrichten mit falscher Protokollversion, wechselnder Absendersession, ungültiger Zielsession, fehlerhafter Struktur oder überschrittenem Größenlimit werden verworfen.

## Envelope

```json
{
  "protocolVersion": 3,
  "type": "HELLO",
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "sentAtEpochMs": 1787065200000
}
```

`messageId` ist pro Nachricht eindeutig. `targetSessionId` entfällt nur bei `HELLO` und ist bei allen gerichteten Nachrichten erforderlich. Endpoint-IDs des jeweiligen Transports werden niemals übertragen.

## HELLO

```json
{
  "protocolVersion": 3,
  "type": "HELLO",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "sentAtEpochMs": 1787065200000,
  "hello": { "displayName": "Gabriel" }
}
```

`HELLO` wird unmittelbar nach dem Verbindungsaufbau gesendet. Erst ein nichtleerer Anzeigename mit höchstens 32 Zeichen macht den Endpoint sichtbar. Das Profil enthält keine Koordinaten, Standortgenauigkeit oder Distanz.

## PHOTO_REQUEST und PHOTO_DATA

Beim Öffnen eines Profils erstellt der anfragende Client eine gerichtete `PHOTO_REQUEST`-Nachricht und speichert deren `messageId` als ausstehende Fotoanfrage.

Der offizielle empfangende Client beantwortet pro logischer Peer-Sitzung höchstens eine gültige Fotoanfrage. Mehrere parallele Transport-Endpunkte derselben Sitzung erhöhen dieses Limit nicht. Die Antwort enthält die ID der ursprünglichen Anfrage:

```json
{
  "protocolVersion": 3,
  "type": "PHOTO_DATA",
  "messageId": "...",
  "senderSessionId": "1e6686d25c5d4ed0",
  "targetSessionId": "72f05250bb244dc8",
  "photo": {
    "requestMessageId": "id-der-photo-request-nachricht",
    "jpegBase64": "/9j/4AAQSk..."
  }
}
```

Der anfragende Client akzeptiert `PHOTO_DATA` nur, wenn `requestMessageId` exakt seiner ausstehenden Anfrage entspricht. Danach wird die Anfrage geschlossen. Das decodierte Bild muss mit dem JPEG-Marker beginnen und darf höchstens 22.000 Bytes groß sein. Android erzeugt Bilder von höchstens 20.000 Bytes, damit Base64 und JSON unter dem Nachrichtenlimit bleiben.

## PING

```json
{
  "protocolVersion": 3,
  "type": "PING",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "ping": { "active": true }
}
```

Eine gesetzte Richtung erzeugt den roten Zustand. Sind beide Richtungen gesetzt, entsteht der grüne Zustand. Protocol v3 kennt keinen Rückzug, weil der gesamte Zustand beim Ende der Sitzung oder nach Verlust ihres letzten Transportwegs verworfen wird.

## COORDINATION_SIGNAL

Koordinationssignale werden nur nach einem gegenseitigen Ping gesendet und empfangen. Der Payload enthält ausschließlich einen festgelegten Code und gegebenenfalls eine Option:

```json
{
  "protocolVersion": 3,
  "type": "COORDINATION_SIGNAL",
  "messageId": "...",
  "senderSessionId": "72f05250bb244dc8",
  "targetSessionId": "1e6686d25c5d4ed0",
  "coordinationSignal": {
    "code": "floor",
    "optionCode": "OG3"
  }
}
```

Die Codes `come_to_you`, `come_to_me`, `wave`, `yes` und `no` besitzen keine Option. `floor` akzeptiert `UG3`, `UG2`, `UG1`, `EG` sowie `OG1` bis `OG20`. `meet_at` akzeptiert `main_entrance`, `exit`, `reception`, `elevator`, `stairs`, `kitchen`, `cafeteria` und `cafe`. Andere Werte werden ignoriert. Der sichtbare Text entsteht ausschließlich aus der lokalen Whitelist.

Android bewahrt pro Peer höchstens 30 Signalereignisse im Speicher auf und begrenzt ausgehende Signale auf eines pro 750 Millisekunden.

## Transportwege und zukünftiges iOS

Der lexikografisch kleinere zufällige Session-Identifier initiiert bevorzugt eine direkte Verbindung. Nearby Connections und LAN Transport v1 besitzen jeweils eine verzögerte Ausweichlogik, falls die Erkennung nur in eine Richtung funktioniert. Endpoints aus unterschiedlichen Transporten werden nicht im JSON übertragen. Der Empfänger ordnet sie anhand der `senderSessionId` derselben logischen Person zu.

Ein zukünftiger iOS-Client muss Feldnamen, Enumwerte, Anfragebindung, Größenlimits, Zielsessionprüfung und Zustandssemantik übernehmen. Für den gemeinsamen WLAN-Weg kann er LAN Transport v1 über Bonjour implementieren. Die konkrete Endpoint-ID und weitere Funkimplementierungen bleiben plattformspezifisch.
