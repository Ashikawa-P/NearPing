# Zwei-Geräte-Abnahmetest für NearPing v1.2.0

Dieser Test prüft den vollständigen öffentlichen Release-Ablauf mit zwei physischen Geräten ab Android 13 und aktuellen Google Play-Diensten. Er umfasst sichtbare und im Hintergrund laufende Sitzungen.

## Vorbereitung

Installiere dieselbe signierte v1.2.0-APK auf beiden Geräten. Eine vorhandene öffentliche v1.0.0 oder v1.1.0 kann auf einem Gerät ab Android 13 direkt aktualisiert werden. Frühere Entwicklungs-APKs bis v0.2.1 müssen wegen des einmaligen Wechsels vom Debug- zum Release-Schlüssel zuvor deinstalliert werden. Aktiviere für den vollständigen Test Bluetooth und WLAN und verbinde beide Geräte mit demselben lokalen WLAN. Eine aktive Internetverbindung ist nicht erforderlich.

Starte NearPing auf beiden Geräten. Verwende auf Gerät A den Namen `A`, nimm ein aktuelles Foto auf und starte die Sitzung. Erteile alle angefragten Kamera-, Benachrichtigungs-, Nearby-, Bluetooth-, WLAN- und Standortberechtigungen. Wiederhole den Ablauf auf Gerät B mit dem Namen `B`. Auf beiden Geräten muss nun die stille, dauerhafte Benachrichtigung `NearPing-Sitzung aktiv` erscheinen.

## Funkerkennung

Lege die Geräte für den ersten Test nebeneinander. Beide Listen müssen schließlich den jeweils anderen Namen unter „Menschen in deiner Funkumgebung“ anzeigen. Bei einem gewöhnlichen WLAN ohne Client-Isolation sollte unter dem Namen schließlich „Über Nearby und lokales WLAN verbunden“ stehen. Der Name darf dabei nicht doppelt erscheinen. Starte ein Gerät probeweise einige Sekunden später; die verzögerte Initiator-Ausweichlogik muss weiterhin eine Verbindung ermöglichen.

Die mögliche Nearby-Reichweite von ungefähr 100 Metern ist keine GPS-Messung. Das gemeinsame WLAN kann räumlich kleiner oder größer sein. Ein fehlender Standortfix darf die Sichtbarkeit nicht beeinflussen.

## Reiner LAN-Test

Beende beide Sitzungen, lasse beide Geräte im selben WLAN und deaktiviere Bluetooth. Starte die Sitzungen erneut. Nearby darf wegen des fehlenden Funkzugriffs einen Fehler melden; die LAN-Suche muss unabhängig weiterlaufen. Beide Geräte sollen einander mit der Kennzeichnung „Direkt über lokales WLAN verbunden“ anzeigen. Öffne die Profile und wiederhole Foto, Ping und mindestens ein festes Signal. Damit ist geprüft, dass LAN nicht lediglich dieselbe Nearby-Verbindung anders beschriftet.

Falls die Geräte im reinen LAN-Test unsichtbar bleiben, aktiviere Bluetooth wieder und prüfe das WLAN auf AP- beziehungsweise Client-Isolation, Gastnetz-Trennung, blockiertes Multicast oder mDNS-Filterung. Solche Routerregeln können direkte Teilnehmerkommunikation verhindern, obwohl beide Geräte denselben WLAN-Namen anzeigen.

## Parallelität und Ausfallsicherheit

Aktiviere Bluetooth auf beiden Geräten wieder und starte neue Sitzungen im selben WLAN. Sobald beide Verbindungswege angezeigt werden, deaktiviere Bluetooth auf einem Gerät, ohne die Sitzung zu beenden. Der vorhandene Listeneintrag, sein Foto und sein Pingzustand müssen über das LAN erhalten bleiben; nur die Verbindungsbeschriftung darf auf lokales WLAN wechseln. Ein erneutes Aktivieren von Bluetooth darf keinen zweiten Listeneintrag erzeugen.

## Fotoaustausch

Öffne auf A das Profil von B. Das Profil zeigt kurz „Foto wird direkt angefragt …“ und anschließend das aktuelle Foto von B. Es erscheint auf B kein einzelner Freigabedialog, weil das Foto bewusst zum sichtbaren Sitzungsprofil gehört. Wiederhole die Prüfung in Gegenrichtung.

Die Fotos dürfen nicht in der Galerie erscheinen. Mehrfaches Öffnen desselben Profils innerhalb der bestehenden Verbindung darf keine weitere Fotoübertragung erfordern.

## Pingzustand

Öffne B auf Gerät A und tippe `Ping senden`. Beide Geräte müssen neben der jeweils anderen Person einen roten Punkt anzeigen. Die Koordinationssignale dürfen noch nicht verfügbar sein.

Öffne A auf Gerät B und tippe `Ping erwidern`. Beide Punkte müssen grün werden. In beiden Profilen erscheinen nun „Kurze Absprache“ und `Signal senden`.

## Koordinationssignale

Sende von A „Ich komme zu dir“. A zeigt die Nachricht als ausgehendes Ereignis mit `Du`, B als eingehendes Ereignis mit `A`. Sende von B `Ja` und prüfe die Gegenrichtung. Teste anschließend einen Stockwerkwert und einen Treffpunkt. Beide Geräte müssen denselben lokal erzeugten Satz anzeigen.

Im Signalablauf darf keine freie Texteingabe oder Bildschirmtastatur erscheinen. Alle Ereignisse müssen dem richtigen Profil zugeordnet bleiben.

## Hintergrundbetrieb und eingehender Ping

Beende beide Sitzungen und starte sie mit neuen Fotos erneut. Warte, bis sich A und B gefunden haben. Drücke auf B die Home-Taste und sperre anschließend den Bildschirm. Die stille Sitzungsbenachrichtigung muss bestehen bleiben; A darf B nicht allein deshalb aus der Liste entfernen.

Öffne auf A das Profil von B und sende einen Ping. B muss eine Benachrichtigung mit Ton oder Vibration und dem Titel `A hat dich angepingt` erhalten. Auf dem Sperrbildschirm soll der Inhalt gemäß Androids privater Benachrichtigungsdarstellung verborgen oder reduziert sein. Ein Tipp auf die Benachrichtigung muss NearPing öffnen und direkt das Profil von A anzeigen, solange A noch verbunden ist. Das Foto wird weiterhin automatisch angefragt; es erscheint kein gesonderter Freigabedialog.

Drücke nun auf A Home, lasse B sichtbar und erwidere auf B den Ping. A muss die Benachrichtigung `Match mit B` erhalten. Nach dem Öffnen müssen beide Pingpunkte grün sein. Sende danach von B ein festes Signal, während A im Hintergrund ist; A muss eine passende Benachrichtigung `Neue Absprache von B` erhalten.

Wiederhole einen eingehenden Ping, während die Empfänger-App sichtbar geöffnet ist. In diesem Fall darf keine zusätzliche Ereignisbenachrichtigung ertönen; der rote oder grüne Zustand wird direkt in der App angezeigt.

## Activity, Verlauf und Prozessgrenzen

Drücke auf A Home und entferne NearPing aus der Liste der zuletzt verwendeten Apps, ohne die Sitzung über `Beenden` zu stoppen. Auf einem Android-Gerät ohne abweichende Herstellerregel soll die Foreground-Service-Benachrichtigung bestehen bleiben und B weiterhin erreichbar sein. Prüfe danach erneut einen Ping an A.

Nutze anschließend in As dauerhafter Benachrichtigung die Aktion `Beenden`. Die Benachrichtigung muss verschwinden, und B muss A entfernen, sobald der letzte Transportweg getrennt wurde. Beim erneuten Öffnen von A erscheint der Einrichtungsbildschirm und verlangt ein neues Foto. Alte Personen, Pings und Signale dürfen nicht wiederkehren.

Starte auf A erneut eine Sitzung und beende NearPing danach über Androids App-Info mit `Stopp erzwingen`. Die Sitzung darf nach einem späteren App-Start nicht automatisch rekonstruiert werden. Dieser Test unterscheidet den absichtlichen Hintergrundbetrieb von einem erzwungenen Prozess- und Sitzungsende.

## Sitzungsende

Beende beide Sitzungen über `Beenden` in der App. Die Listen und dauerhaften Benachrichtigungen müssen verschwinden. Ein bloßer Wechsel zum Startbildschirm darf eine Sitzung in v1.2.0 dagegen nicht mehr beenden.

## Fehlerdiagnose

Der Status zeigt Nearby und WLAN getrennt. Nearby durchläuft normalerweise `Starte Nearby-Ankündigung`, `Nearby-Ankündigung aktiv; starte Suche` und `Nearby-Suche aktiv`. LAN durchläuft `starte lokalen WLAN-Dienst`, `im lokalen WLAN sichtbar` und `lokale WLAN-Suche aktiv`. Bei einem Fehler müssen die vollständige Meldung und eine vorhandene Nearby- oder NSD-Zahl dokumentiert werden.

Fehler 8034 oder 8036 weisen auf fehlende grobe beziehungsweise genaue Standortberechtigung hin. Fehler 8032 weist auf fehlenden WLAN-Statuszugriff hin. Prüfe außerdem Google Play-Dienste, Benachrichtigungs-, Bluetooth-, Nearby- und WLAN-Berechtigungen sowie mögliche Akkuoptimierungen des Herstellers.

Bleibt eine Ereignisbenachrichtigung stumm, prüfe den Android-Kanal `Pings, Matches und Absprachen`, den Nicht-stören-Modus und die Herstellerlautstärke. Bleibt die stille Betriebsanzeige bestehen, aber Geräte verschwinden nach einiger Zeit, deaktiviere für den Test die herstellerspezifische Akkuoptimierung für NearPing. Ein erzwungener Stopp durch den Nutzer beendet die Sitzung absichtlich und ist kein Wiederanlauffehler.

Wenn eine direkte Verbindung gemeldet wird, aber keine Person erscheint, ist ein Transportweg aktiv, während das Protocol-v3-`HELLO` noch fehlt oder abgelehnt wurde. Builds mit Protocol v1 oder v2 können die aktuelle v1-Releaselinie nicht entdecken. v1.0.0 bleibt über Nearby kompatibel, besitzt aber noch keinen LAN-Transport. v1.1.0 unterstützt Nearby und LAN, beendet seine Sitzung jedoch weiterhin beim Wechsel in den Hintergrund.
