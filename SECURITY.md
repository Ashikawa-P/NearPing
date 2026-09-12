# Security Policy

## Unterstützte Version

Sicherheitskorrekturen beziehen sich auf die jeweils neueste veröffentlichte Version. Aktuell ist dies NearPing v1.2.0 mit Wire Protocol v3 und LAN Transport v1. Die Entwicklungsprotokolle v1 und v2 dienen nur der Dokumentation und sind nicht mit v3 kompatibel.

## Sicherheitsmodell

NearPing besitzt keinen zentralen Server und keine dauerhafte Identität. Direkte Verbindungen werden in der Funkumgebung über Google Nearby Connections oder innerhalb desselben lokalen WLANs über DNS-SD und TCP aufgebaut. Passende Verbindungen werden automatisch akzeptiert, weil die spontane Liste andernfalls vor jeder sichtbaren Person einen manuellen Kopplungsschritt verlangen würde.

Diese Entscheidung bedeutet, dass Anzeigenamen nicht die reale Identität einer Person beweisen. Die offizielle App zeigt ein aktuelles Sitzungsfoto als visuelle Erkennungshilfe, doch ein veränderter Client kann einen beliebigen Namen oder ein beliebiges Bild verwenden. Nearby-Authentifizierungsziffern werden nicht manuell verglichen. Der LAN-Transport verwendet pro TCP-Verbindung ein neues ECDH-P-256-Schlüsselpaar, leitet mit HKDF-SHA-256 getrennte Richtungsschlüssel ab und schützt Nachrichten mit AES-256-GCM und monotonen Frame-Zählern. Das schützt gegen passives Mitlesen, authentifiziert die Gegenstelle jedoch nicht und verhindert keinen aktiven Man-in-the-Middle-Angriff. Dieses Restrisiko ist Teil des veröffentlichten Proof-of-Concept-Sicherheitsmodells und darf nicht mit verifizierter Identität verwechselt werden.

Das Sitzungsfoto ist bewusst Teil des sichtbaren Profils und wird beim Öffnen automatisch übertragen. Protocol v3 beantwortet nur gültige, zielgerichtete Anfragen eines verbundenen Profils, sendet pro Verbindung höchstens eine Fotoantwort und verknüpft Antwort und Anfrage über die `requestMessageId`. Das verhindert keine Speicherung durch einen veränderten empfangenden Client.

Eingehende Nachrichten werden auf Protokollversion, Absender- und Zielsession, maximale Größe und typspezifische Struktur geprüft. LAN-Verbindungen prüfen zusätzlich Transportversion, Schlüsselformat, verschlüsselte Rahmengröße und lückenlose Frame-Zähler. Fotos müssen innerhalb des Größenlimits liegen und einen JPEG-Header besitzen. Koordinationssignale werden ausschließlich aus einer lokalen Whitelist erzeugt; empfangener Freitext wird nicht dargestellt. Mehrere Wege derselben Sitzungskennung werden zu einer Person zusammengeführt. Der Wechsel in den Hintergrund beendet die Sitzung nicht mehr. Der Sitzungszustand wird entfernt, wenn die Sitzung ausdrücklich beendet, die App zwangsweise gestoppt, der Prozess beendet oder der letzte Verbindungsweg zur jeweiligen Person verloren wird.

Die Hintergrundsitzung läuft als Android-Foreground-Service des Typs `connectedDevice` und ist durch eine dauerhafte Systembenachrichtigung sichtbar. Ereignisbenachrichtigungen können Namen und feste Signale enthalten und sind mit privater Sperrbildschirmsichtbarkeit markiert. Nutzereinstellungen, kompromittierte Geräte oder abweichende Android-Implementierungen können diese Darstellung dennoch beeinflussen.

## Noch nicht enthalten

Version 1.2.0 enthält keine Konten, verifizierten Identitäten, zentrale Sperrliste, Missbrauchsmeldung, dauerhafte lokale Blockliste, manuelle Nearby-Tokenprüfung oder authentifizierte LAN-Schlüssel. Ein gemeinsames WLAN kann zudem räumlich größer als die unmittelbare Umgebung sein. Nutzer sollten eine Begegnung abbrechen, wenn Name, Foto oder Verhalten nicht zur erkennbaren Person passen. Bei akuter Gefahr ist NearPing kein Ersatz für örtliche Sicherheits- oder Notfalldienste.

## Schwachstellen melden

Nutze nach Möglichkeit GitHubs private Funktion „Report a vulnerability“. Falls sie im Repository nicht aktiviert ist, erstelle ein knappes Issue ohne Exploitcode, private Fotos, exakte Aufenthaltsorte oder andere sensible Daten und bitte um einen privaten Kommunikationsweg. Gib betroffene Version, Android-Version, Gerätetyp, beobachtetes Verhalten und reproduzierbare Schritte an.

Veröffentliche eine noch nicht behobene Schwachstelle nicht zusammen mit funktionierendem Angriffscode. Allgemeine Funktionsfehler ohne Sicherheitsauswirkung können direkt als normales GitHub-Issue gemeldet werden.
