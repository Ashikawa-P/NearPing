# NearPing – v1.2.0

NearPing ist eine serverlose Android-App für spontane Begegnungen im echten Leben. Wer eine Sitzung startet, erstellt ein aktuelles Foto, gibt einen Namen ein und wird für andere aktive NearPing-Geräte in der Funkumgebung sichtbar. Ein gegenseitiger Ping signalisiert beidseitiges Interesse an einem persönlichen Gespräch – ohne Account, Telefonnummer oder freien Chat.

[Release herunterladen](../../releases) · [Datenschutzhinweis](PRIVACY.md) · [Sicherheitsmodell](SECURITY.md)

Version 1.2.0 lässt die bewusst gestartete Sitzung erstmals auch bei ausgeschaltetem Bildschirm oder geschlossener Oberfläche weiterlaufen. Eingehende Pings, gegenseitige Matches und feste Absprachen erzeugen eine Systembenachrichtigung mit Ton. Die Geräte kommunizieren weiterhin ausschließlich direkt über Google Nearby Connections und/oder das lokale WLAN; ein von NearPing betriebener Server wird nicht benötigt.

## Einrichtung

Installiere die signierte Release-APK auf mindestens zwei Geräten mit Android 13 oder neuer. Auf jedem Gerät werden für eine neue Sitzung ein Anzeigename und ein aktuelles Foto benötigt. Anschließend fragt Android nach Kamera-, Benachrichtigungs-, Bluetooth-, Nearby-, WLAN- und Standortberechtigungen.

Die Standortberechtigung wird aus Kompatibilitätsgründen von Google Nearby Connections und einzelnen Geräteimplementierungen verlangt. NearPing enthält keinen Standort-Tracker, ruft keine GPS-Koordinaten ab und überträgt keine Positionen.

Für Nearby müssen Bluetooth und WLAN aktiviert sein; die Geräte benötigen dabei weder dasselbe WLAN noch eine aktive Internetverbindung. Sind beide Geräte zusätzlich mit demselben lokalen WLAN verbunden, verwendet NearPing parallel eine eigene LAN-Erkennung. Google Play-Dienste werden weiterhin für den Nearby-Weg benötigt, nicht jedoch für den LAN-Transport selbst.

## Menschen in deiner Funkumgebung

Während eine Sitzung aktiv ist, kündigt NearPing das Gerät über Google Nearby Connections an und sucht gleichzeitig nach anderen Instanzen. Verwendet wird die `P2P_CLUSTER`-Strategie, sodass mehrere Geräte grundsätzlich gleichzeitig miteinander verbunden sein können. Parallel veröffentlicht `LanPeerTransport` einen DNS-SD-Dienst im lokalen WLAN und entdeckt dort andere NearPing-Instanzen über mDNS. Nach der Erkennung entsteht eine direkte, verschlüsselte TCP-Verbindung zwischen den Telefonen.

Die Nearby-Funkreichweite kann je nach Gerät, Gebäudestruktur und Funkumgebung bis zu ungefähr 100 Meter betragen. Das lokale WLAN kann kleiner sein, aber in großen Heim-, Campus- oder Firmennetzen auch deutlich weiter reichen und mehrere Stockwerke oder Gebäude umfassen. Beide Verfahren sind deshalb Näherungsräume und keine präzise Entfernungsmessung.

Nearby und LAN bleiben vollständig parallel. Wird dieselbe Sitzung über beide Wege gefunden, führt NearPing sie anhand der zufälligen Sitzungskennung zu genau einem Listeneintrag zusammen. Fällt ein Weg aus, bleibt die Person über den anderen sichtbar. Unter jedem Namen zeigt die App an, ob die Verbindung über Nearby, das lokale WLAN oder beide Wege besteht. NFC ist nicht Bestandteil der Implementierung.

## Sitzungsprofil

Jede Sitzung besteht aus einem frei gewählten Namen und einem frisch aufgenommenen Foto. Das Foto wird von CameraX verarbeitet, quadratisch zugeschnitten und als kompaktes JPEG ausschließlich im Arbeitsspeicher gehalten.

Beim Öffnen eines Profils fordert das betrachtende Gerät das Foto direkt vom anderen Telefon an. Die Übertragung erfolgt automatisch, weil das aktuelle Erkennungsfoto bewusst zum sichtbaren NearPing-Sitzungsprofil gehört. Pro Verbindung wird das Foto höchstens einmal ausgeliefert. Protocol v3 bindet jede Fotoantwort an die konkrete Anfrage; unaufgefordert gesendete oder nicht zuordenbare Fotos werden verworfen.

NearPing speichert Fotos nicht in der Galerie und lädt sie nicht auf einen Anwendungsserver hoch. Ein veränderter empfangender Client könnte empfangene Daten dennoch dauerhaft sichern. Nutzer sollten deshalb nur ein Foto aufnehmen, das sie Menschen in ihrer Funkumgebung tatsächlich zeigen möchten.

## Pings

Ein Ping ist eine minimale, gerichtete Interessensbekundung. Pingst du eine Person, erscheint neben ihrem Namen auf beiden Geräten ein roter Punkt. Erwidert sie den Ping, wird der Punkt auf beiden Geräten grün.

Der grüne Zustand bedeutet ausschließlich, dass beide Personen innerhalb derselben laufenden Sitzung Interesse signalisiert haben. Es entstehen kein Kontakt, kein Account und keine dauerhafte Verbindung. Endet eine Sitzung, wird auch der Pingzustand verworfen.

## Kurze Absprache

Nach einem gegenseitigen Ping stehen feste Signale zur Verfügung. Dazu gehören „Ich komme zu dir“, „Kommst du zu mir?“, „Bitte kurz winken“, „Ja“ und „Nein“. Für größere Gebäude gibt es zusätzlich eine Stockwerkauswahl von drei Untergeschossen bis zum 20. Stock sowie Treffpunkte wie Haupteingang, Empfang, Aufzüge, Treppenhaus, Küche, Mensa und Café.

Die Signale sind kein versteckter Chat. Übertragen werden ausschließlich fest definierte Codes und Optionswerte; beliebiger Text eines veränderten Clients wird nicht dargestellt. Dadurch kann eine Begegnung praktisch koordiniert werden, ohne dass die App das persönliche Gespräch ersetzt.

## Hintergrundsitzung und Benachrichtigungen

Nach dem bewussten Start läuft die Sitzung als Android-Foreground-Service weiter, wenn die Oberfläche geschlossen, die Home-Taste gedrückt oder der Bildschirm gesperrt wird. Android zeigt dafür dauerhaft die stille Benachrichtigung `NearPing-Sitzung aktiv`. Sie zeigt die aktuelle Zahl erreichbarer Personen und bietet die Aktion `Beenden`.

Erhält die App im Hintergrund einen Ping, ein gegenseitiges Match oder ein festes Koordinationssignal, erzeugt sie eine separate Benachrichtigung mit Ton und Vibration. Ein Tipp darauf öffnet direkt das zugehörige Profil, sofern die Person noch verbunden ist. Ist NearPing sichtbar geöffnet, bleiben diese Systemhinweise aus und die Zustandsänderung erscheint unmittelbar in der App.

Das ist kein unsichtbarer Dauerbetrieb: Die Sitzung ist immer an der laufenden Systembenachrichtigung erkennbar. `Beenden` in der App oder Benachrichtigung verwirft Foto, Personenliste, Pings und Signale. Gleiches gilt, wenn Android den Prozess beendet oder die App zwangsweise gestoppt wird. Nach einem Prozessneustart wird keine alte Sitzung ohne neues Foto rekonstruiert. Energiesparregeln einzelner Hersteller sowie vom Nutzer deaktivierte Benachrichtigungstöne können die Zuverlässigkeit beeinflussen.

Da NearPing keinen Push- oder Anwendungsserver besitzt, entstehen Hinweise nur, solange die beiden Geräte direkt über Nearby oder LAN verbunden sind. Die Hintergrundsitzung verbessert damit die Nutzbarkeit in der aktuellen Funkumgebung, ist aber keine ortsunabhängige Zustellung wie bei serverbasierten Messengern.

## Installation

Lade `NearPing-v1.2.0.apk` aus dem GitHub-Release herunter. Android verlangt bei einer Installation außerhalb des Play Stores einmalig die Freigabe der verwendeten Installationsquelle. v1.2.0 verwendet denselben dauerhaft verwahrten Release-Schlüssel wie v1.0.0 und v1.1.0 und kann deshalb auf unterstützten Geräten direkt darüber installiert werden.

Wichtig: v1.2.0 setzt Android 13 (API 33) voraus. Für Geräte mit Android 8 bis 12 bleibt v1.1.0 der letzte installierbare Stand; diese Version besitzt noch keine Hintergrundsitzung.

Der öffentliche Signaturfingerabdruck der v1-Releaselinie lautet:

```text
SHA-256: 0B:E4:E7:CA:31:20:A0:4D:31:18:11:C3:15:6C:65:32:AE:DE:5B:06:3F:36:60:E6:46:8E:E1:BE:6B:92:2A:7D
```

Die vorherigen Versionen bis einschließlich 0.2.1 waren Entwicklungs-Builds mit einem Android-Debugschlüssel. Ein Wechsel von diesen Builds auf die öffentliche v1-Releaselinie erfordert deshalb einmalig die Deinstallation der alten App. Dabei gehen nur die ohnehin flüchtigen Sitzungsdaten verloren.

## Aus dem Quellcode bauen

Benötigt werden JDK 17 und Android SDK 36. Ein lokaler Entwicklungs-Build entsteht mit:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Ein öffentlicher Release-Build muss mit einem dauerhaft gesicherten privaten Schlüssel signiert werden. Die benötigten Umgebungsvariablen, Prüfkommandos und Regeln zur Schlüsselaufbewahrung stehen in [BUILDING.md](BUILDING.md). Der private NearPing-Release-Schlüssel gehört niemals in das Repository.

## Architektur

`NearbyPeerTransport` kapselt Google Nearby Connections, während `LanPeerTransport` DNS-SD-Erkennung, direkte TCP-Verbindungen und die verschlüsselte LAN-Rahmung übernimmt. `HybridPeerTransport` startet beide Implementierungen hinter dem gemeinsamen Interface `PeerTransport`. `PeerRouteRegistry` führt mehrere Transport-Endpunkte anhand ihrer Sitzungskennung zu einer logischen Person zusammen. Der an die `NearPingApplication` gebundene `AppViewModel` verwaltet das flüchtige Profil, Pings und Signale unabhängig vom Lebenszyklus der Activity. `NearPingSessionService` hält die ausdrücklich gestartete Sitzung im Hintergrund aktiv; `NearPingNotifications` trennt die stille Betriebsanzeige von hörbaren Ereignissen. `ProtocolCodec` serialisiert weiterhin das dokumentierte, plattformneutrale JSON-Protokoll; `SelfieProcessor`, `PingState`, `BackgroundAlertPolicy` und `CoordinationSignal` isolieren Medienverarbeitung und Fachlogik.

Das aktuelle Nachrichtenprotokoll ist in [docs/protocol-v3.md](docs/protocol-v3.md) beschrieben. Die zusätzliche LAN-Rahmung dokumentiert [docs/lan-transport-v1.md](docs/lan-transport-v1.md). Die Dokumente zu Protocol v1 und v2 bleiben erhalten, damit die Entwicklung des Datenmodells nachvollziehbar bleibt.

## Sicherheit und bekannte Grenzen

NearPing verbindet absichtlich unbekannte Geräte in der Funkumgebung beziehungsweise im lokalen WLAN und akzeptiert passende Verbindungen automatisch. Das ermöglicht die spontane Liste, bedeutet aber auch, dass keine verifizierte reale Identität hinter einem Namen garantiert werden kann. Ein manipulierter Client kann öffentlich gemachte Namen und Sitzungsfotos empfangen oder speichern.

Version 1.2.0 begrenzt Nachrichtengrößen, prüft Session- und Zielkennungen, validiert JPEG-Daten, bindet Fotoantworten an konkrete Anfragen, dedupliziert Anfragen und akzeptiert nur fest definierte Koordinationssignale. Der LAN-Transport verschlüsselt jede Verbindung mit flüchtigem ECDH und AES-GCM gegen passives Mitlesen, besitzt aber weiterhin keine verifizierte Geräteidentität und verhindert keinen aktiven Man-in-the-Middle-Angriff. Die App enthält noch keine Konten, Blockliste, Missbrauchsmeldung oder manuelle Identitätsprüfung. Details und Meldemöglichkeiten stehen in [SECURITY.md](SECURITY.md).

## Dokumentation

Der [Datenschutzhinweis](PRIVACY.md) beschreibt alle innerhalb einer Sitzung verarbeiteten Daten. [BUILDING.md](BUILDING.md) dokumentiert reproduzierbare Entwicklungs- und Release-Builds. [SECURITY.md](SECURITY.md) grenzt das Sicherheitsmodell ab. Der [Zwei-Geräte-Abnahmetest](docs/two-device-test.md) führt durch den gesamten Nutzerablauf. Änderungen pro Version stehen in [CHANGELOG.md](CHANGELOG.md).

## Lizenz

Der eigenständige NearPing-Quellcode wird unter [The Unlicense](UNLICENSE) der Allgemeinheit überlassen. Eingebundene Bibliotheken und Google Play-Dienste behalten ihre jeweiligen Lizenzen und Nutzungsbedingungen; eine Übersicht steht in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Ausblick

NearPing beantwortet zunächst eine klar begrenzte Frage: Kann eine App den unangenehmsten Moment einer spontanen Begegnung – die Unsicherheit, ob die andere Person überhaupt angesprochen werden möchte – durch ein beidseitiges, flüchtiges Signal entschärfen, ohne die Begegnung anschließend in einen gewöhnlichen Online-Chat zu verlagern? Der funktionierende Proof of Concept zeigt, dass dieser Ablauf technisch möglich ist. Seine Schlichtheit ist dabei nicht bloß fehlender Funktionsumfang, sondern Teil der These hinter der App. v1.1.0 zeigte, dass dieses Modell nicht an einen einzigen proprietären Funkweg gebunden sein muss. v1.2.0 löst die Begegnungsbereitschaft nun vom dauernden Blick auf den Bildschirm, ohne sie in ein dauerhaftes Konto oder eine Cloudpräsenz zu verwandeln.

Ein iOS-Port wäre der naheliegendste nächste Schritt. Transport, Protokoll und Sitzungslogik sind bereits getrennt, sodass ein Swift-Client dieselben Nachrichten und Zustandsregeln abbilden könnte. DNS-SD und direkte LAN-Verbindungen bieten dafür erstmals einen grundsätzlich plattformübergreifenden Ausgangspunkt. Vor einer konkreten Umsetzung müssten die Bonjour- und Local-Network-Berechtigungen von iOS, die kryptografische Interoperabilität und ein zusätzlicher Android-iOS-Funkweg außerhalb eines gemeinsamen WLANs geprüft werden.

Auch die Oberfläche kann deutlich weiterentwickelt werden. Denkbar sind eine präzisere visuelle Sprache für eingehende und ausgehende Pings, bessere Barrierefreiheit, dynamische Schriftgrößen, alternative Farbindikatoren, dezente Animationen und eine verständlichere Darstellung mehrerer gleichzeitiger Begegnungen. Solche Änderungen sollten jedoch die Geschwindigkeit des Ablaufs erhöhen und nicht aus einem unmittelbaren Werkzeug ein soziales Netzwerk machen.

Sicherheitsfunktionen stellen eine besonders interessante Gestaltungsfrage dar. Blockieren, lokale Ausschlusslisten, freiwillige Nearby-Codeprüfung oder eine datensparsame Missbrauchsmeldung könnten Schutz schaffen, ohne zwangsläufig zentrale Profile einzuführen. Jede Schutzschicht verändert allerdings auch die spontane Offenheit, die NearPing erst besonders macht. Die langfristige Aufgabe besteht deshalb nicht darin, möglichst viele Funktionen anzusammeln, sondern Vertrauen, Kontrolle und Unmittelbarkeit so auszubalancieren, dass Menschen am Ende weiterhin vom Bildschirm aufblicken und tatsächlich miteinander sprechen.
