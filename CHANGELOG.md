# Changelog

Alle wesentlichen Änderungen an NearPing werden in diesem Dokument festgehalten.

## 1.2.0 – 2026-08-19

Eine bewusst gestartete NearPing-Sitzung bleibt nun bei geschlossener Oberfläche und ausgeschaltetem Bildschirm aktiv. Ein Android-Foreground-Service des Typs `connectedDevice` hält Nearby- und LAN-Erkennung, Direktverbindungen sowie den flüchtigen Sitzungszustand aufrecht. Eine dauerhafte, stille Systembenachrichtigung macht den Betrieb sichtbar, zeigt die Zahl erreichbarer Personen und erlaubt das unmittelbare Beenden der Sitzung.

Eingehende Pings, gegenseitige Matches und feste Koordinationssignale erzeugen im Hintergrund eine gesonderte Benachrichtigung mit Ton und Vibration. Ein Tipp öffnet das zugehörige Profil, sofern die Person noch verbunden ist. Die sitzungsweite Steuerung ist dafür vom Activity-Lebenszyklus in den Anwendungsprozess verschoben worden; nach einem Prozessende wird aus Datenschutzgründen keine alte Sitzung rekonstruiert.

v1.2.0 setzt Android 13 beziehungsweise API 33 voraus und fordert dessen Laufzeitberechtigung für Benachrichtigungen vor dem Sitzungsstart an. Datenschutz-, Sicherheits- und Testdokumentation beschreiben Hintergrundbetrieb, Sperrbildschirmsichtbarkeit, Hersteller-Energiesparregeln und die bewusste Nutzerkontrolle über `Beenden`.

## 1.1.0 – 2026-08-19

NearPing entdeckt Nutzer nun parallel über Google Nearby Connections und über Android Network Service Discovery im selben lokalen WLAN. LAN-Geräte verbinden sich ohne zentralen Server direkt per TCP. Jede LAN-Verbindung verwendet flüchtiges ECDH P-256, HKDF-SHA-256 und AES-256-GCM zum Schutz gegen passives Mitlesen.

Mehrere Endpunkte derselben zufälligen Sitzungskennung werden zu einem einzigen Listeneintrag zusammengeführt. Ping-, Foto- und Koordinationszustände bleiben dadurch konsistent, wenn Nearby und LAN gleichzeitig verfügbar sind oder einer der beiden Wege ausfällt. Die Oberfläche zeigt den aktiven Verbindungsweg pro Person an. Dokumentation, Datenschutz, Sicherheitsmodell und Zwei-Geräte-Test berücksichtigen nun die mögliche Reichweite großer lokaler Netze und Einschränkungen durch Client-Isolation oder blockiertes Multicast.

## 1.0.0 – 2026-08-19

Erster vollständiger öffentlicher Proof-of-Concept-Release.

NearPing unterstützt direkte M:N-Erkennung über Google Nearby Connections, flüchtige Profile mit aktuellem Sitzungsfoto, gerichtete rote Pings, gegenseitige grüne Pings und fest definierte Koordinationssignale. Standorterfassung und GPS-Distanzfilterung sind nicht enthalten. Protocol v3 bindet Fotoantworten an konkrete Anfragen, begrenzt die Fotoausgabe auf einmal pro Verbindung und verwendet einen neuen Service-Identifier.

Der Build besitzt erstmals eine dauerhaft verwahrbare Release-Signatur, eine explizit nicht-debugbare Release-Variante, The Unlicense, Datenschutz- und Sicherheitsdokumentation, einen reproduzierbaren Buildleitfaden sowie einen GitHub-Actions-Workflow für Tests, Lint und Debug-Build.

## 0.2.1 – 2026-08-19

Kompatibilitätskorrektur für Nearby-Fehler 8034 und 8036 durch erneute Deklaration und Abfrage der Standortberechtigungen ohne Wiedereinführung einer Standort-API.

## 0.2.0 – 2026-08-19

Entfernung aller GPS-Koordinaten und Distanzberechnungen. Einführung der Funkumgebungsdarstellung und der festen Signale nach gegenseitigem Ping.
