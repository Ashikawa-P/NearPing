# Datenschutzhinweis für NearPing

NearPing v1.2.0 ist eine serverlose Proof-of-Concept-Anwendung. Der NearPing-Projektcode betreibt keinen zentralen Anwendungsserver, legt keine Konten an und erstellt keine dauerhafte Nutzerhistorie.

## Verarbeitete Daten

Für eine aktive Sitzung verarbeitet die App den eingegebenen Anzeigenamen, das mit der Kamera aufgenommene Sitzungsfoto, eine zufällige Sitzungskennung, Pingzustände und ausgewählte Koordinationssignale. Diese Daten befinden sich im Arbeitsspeicher des Geräts. Name und Sitzungskennung werden nach dem Aufbau einer direkten Nearby- oder LAN-Verbindung an verbundene NearPing-Geräte übertragen. Das Foto wird automatisch direkt übertragen, wenn ein verbundenes Gerät das Profil öffnet; eine gesonderte Freigabe einzelner Fotos findet bewusst nicht statt. Pings und Signale werden nur an das jeweils adressierte Gerät gesendet.

Der Wechsel in den Hintergrund verwirft die Sitzung ab v1.2.0 nicht mehr. Stattdessen hält ein sichtbarer Android-Foreground-Service Ankündigung, Suche und Direktverbindungen aufrecht. Beim ausdrücklichen Beenden der Sitzung, beim Zwangsstoppen der App oder beim Prozessende verwirft NearPing die Sitzungsdaten. Die App rekonstruiert eine beendete Sitzung nicht automatisch und speichert das Foto weder in der Galerie noch als Profildatei. Die genannten Daten werden nicht auf einen von NearPing betriebenen Server hochgeladen.

## Berechtigungen und Standort

NearPing benötigt Kamera, Benachrichtigungen, Bluetooth, Nearby-Geräte, WLAN- und Netzwerkstatus sowie grobe und genaue Standortberechtigung. Die Standortberechtigungen werden ausschließlich bereitgehalten, weil Google Nearby Connections und bestimmte Android- beziehungsweise Play-Services-Versionen sie technisch voraussetzen. Für die lokale LAN-Erkennung verwendet die App Android Network Service Discovery, DNS-SD beziehungsweise mDNS und einen nur während der Sitzung geöffneten TCP-Port. NearPing ruft keinen Standortprovider auf, liest keine Koordinaten und berechnet keine GPS-Entfernung.

## Hintergrundbetrieb und Systembenachrichtigungen

Android zeigt während jeder aktiven Hintergrundsitzung eine dauerhafte, stille Benachrichtigung. Sie enthält den Sitzungsstatus und die Zahl der momentan erreichbaren Personen. Eingehende Pings, Matches und feste Koordinationssignale erzeugen eine gesonderte Benachrichtigung. Deren Text kann den Anzeigenamen der anderen Person und den Inhalt eines festen Signals enthalten. Die App markiert diese Hinweise für den Sperrbildschirm als privat; die konkrete Darstellung wird dennoch durch Android sowie die Benachrichtigungs- und Sperrbildschirmeinstellungen des Nutzers bestimmt.

NearPing fordert die Android-Benachrichtigungsberechtigung vor dem Sitzungsstart an. Nutzer können Töne, Vibration oder ganze Benachrichtigungskanäle später in den Systemeinstellungen verändern. Ohne sichtbare Benachrichtigung ist ein verlässlicher Hintergrundbetrieb nicht zugesichert. Die offizielle App legt selbst kein Benachrichtigungsarchiv an; Betriebssysteme und Gerätehersteller können Benachrichtigungen jedoch systemseitig protokollieren.

Es gibt keinen zentralen Push-Dienst. Ereignisbenachrichtigungen entstehen nur aus Nachrichten, die während einer bestehenden direkten Nearby- oder LAN-Verbindung empfangen werden.

LAN-Nutzdaten werden für jede direkte Verbindung mit einem flüchtigen ECDH-Schlüsselaustausch und AES-GCM verschlüsselt. Die Verschlüsselung erschwert passives Mitlesen im WLAN, authentifiziert aber nicht die reale Person oder das Gerät und schützt daher nicht vor einem aktiven Man-in-the-Middle-Angriff. Google Nearby Connections und der LAN-Transport sind voneinander unabhängige Verbindungswege.

NearPing verwendet Google Play-Dienste als Systemkomponente. Eine mögliche Verarbeitung technischer Diagnose- oder Gerätedaten durch Google richtet sich nach den Bedingungen und Datenschutzhinweisen des jeweiligen Geräte- und Play-Services-Anbieters und liegt außerhalb des NearPing-Anwendungsservers, da ein solcher nicht existiert.

## Sichtbarkeit und Empfänger

Die App ist dafür bestimmt, Name und aktuelles Foto gegenüber anderen NearPing-Nutzern in der Funkumgebung oder im selben lokalen WLAN sichtbar zu machen. Die tatsächliche Nearby-Reichweite hängt von den Geräten und der Umgebung ab. Ein gemeinsames Campus-, Firmen- oder Gebäudenetz kann deutlich über die unmittelbare Sichtweite hinausreichen. Router können die Erkennung umgekehrt durch Client-Isolation oder blockiertes Multicast vollständig verhindern. Empfänger kontrollieren ihre Geräte selbst. Ein veränderter Client kann empfangene Informationen speichern, kopieren oder weitergeben, obwohl die offizielle App keine Historie anlegt.

Nutze daher nur einen Namen und ein Foto, die du unbekannten Personen in deiner Funkumgebung zeigen möchtest. Durch das Beenden der Sitzung stoppst du weitere NearPing-Übertragungen und entfernst die Daten aus dem Speicher der offiziellen App. Bereits an ein anderes Gerät übertragene Daten können dort technisch nicht nachträglich gelöscht werden.

## Änderungen und Kontakt

Änderungen dieses Datenmodells werden in Repository und Changelog dokumentiert. Technische Datenschutzfragen können über die GitHub-Issues des Projekts gestellt werden. Veröffentliche dort keine privaten Fotos, Standortdaten oder anderen sensiblen Informationen.

Stand: 19. August 2026, NearPing v1.2.0
