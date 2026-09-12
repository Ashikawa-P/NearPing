# NearPing LAN Transport v1

LAN Transport v1 ergänzt Wire Protocol v3 um eine serverlose Verbindung für NearPing-Geräte im selben lokalen IP-Netz. Er verändert die JSON-Nachrichten nicht. Eine v1.1.0-Instanz bleibt daher über Google Nearby Connections mit v1.0.0 kompatibel, während nur v1.1.0 und spätere kompatible Clients den LAN-Weg anbieten.

## Erkennung

Jede aktive Sitzung öffnet einen TCP-Server auf einem vom Betriebssystem gewählten Port und veröffentlicht ihn mit Android Network Service Discovery. Der DNS-SD-Diensttyp lautet `_nearping._tcp.`. Der Dienstname beginnt mit `NearPing-v1-` und enthält anschließend die zufällige, 16-stellige hexadezimale Sitzungskennung. Dieselbe Kennung wird als TXT-Attribut `session` veröffentlicht und im anschließenden Handshake erneut geprüft.

DNS-SD verwendet mDNS und ist auf lokale Multicast-Erreichbarkeit angewiesen. Gastnetze, Access-Point- oder Client-Isolation, VLAN-Grenzen und Multicast-Filter können die Erkennung unterbinden. Derselbe WLAN-Name allein garantiert daher noch keine direkte IP-Erreichbarkeit. NearPing scannt keine Adressbereiche und kontaktiert keinen Vermittlungsserver.

## Verbindungsaufbau

Nach der Auflösung des Dienstes baut bevorzugt die Sitzung mit der lexikografisch kleineren Kennung die TCP-Verbindung auf. Die andere Seite versucht den Aufbau nach einer kurzen Verzögerung ebenfalls, wenn noch keine Verbindung besteht. Treffen zwei Verbindungen zusammen, behalten beide Geräte deterministisch die vom kleineren Session-Identifier initiierte Verbindung. Das verhindert dauerhaft doppelte LAN-Sockets, ohne eine zentrale Rollenvergabe zu benötigen.

Der binäre Handshake enthält in dieser Reihenfolge die Magic Number `0x4E504C31`, LAN-Transportversion `1`, Wire-Protocol-Version `3`, die UTF-kodierte Absender-Session, die Länge des öffentlichen Schlüssels und den X.509-kodierten öffentlichen ECDH-Schlüssel. Sitzungskennungen, Versionen und Schlüssellängen werden vor der Freigabe des Endpoints geprüft. Der TCP-Port und alle Verbindungen werden beim Sitzungsende oder beim Wechsel der App in den Hintergrund geschlossen.

## Transportverschlüsselung

Für jede TCP-Verbindung erzeugen beide Seiten ein neues ECDH-Schlüsselpaar auf der Kurve P-256. Aus dem gemeinsamen Geheimnis leitet HKDF-SHA-256 zwei getrennte AES-256-Schlüssel für die beiden Übertragungsrichtungen ab. Der private ECDH-Schlüssel wird weder gespeichert noch außerhalb der Verbindung wiederverwendet.

Jede Wire-Protocol-Nachricht wird einzeln mit AES-GCM verschlüsselt. Der Nonce besteht aus einem verbindungslokalen 64-Bit-Zähler innerhalb eines 96-Bit-Feldes. Sender-Session, Ziel-Session, Transportversion und Zähler werden als Additional Authenticated Data eingebunden. Empfänger akzeptieren ausschließlich den exakt nächsten Zählerwert. Ein Frame enthält die Länge des verschlüsselten Inhalts, den Zähler und den Ciphertext einschließlich 128-Bit-GCM-Tag. Die bestehende Obergrenze von 30.000 Byte für eine Wire-Nachricht bleibt erhalten.

Diese Verschlüsselung schützt die Nutzdaten gegen passives Mitlesen und unbemerkte Veränderung im WLAN. Da NearPing keine dauerhafte Geräteidentität, Zertifizierungsstelle oder manuell geprüften Verbindungscode besitzt, ist der flüchtige Schlüsselaustausch nicht authentifiziert. Ein aktiver Angreifer im lokalen Netz könnte sich daher theoretisch zwischen zwei Geräte schalten. LAN Transport v1 darf nicht als Identitätsnachweis verstanden werden.

## Zusammenführung mit Nearby

`HybridPeerTransport` versieht Nearby- und LAN-Endpunkte intern mit getrennten Namensräumen. `PeerRouteRegistry` ordnet anschließend alle Endpunkte derselben zufälligen Sitzungskennung einer logischen Person zu. Das Profil, Foto, die Pingrichtungen und Koordinationssignale existieren deshalb nur einmal. Ausgehende Nachrichten werden über alle aktuell verfügbaren Wege mit derselben Nachrichtenkennung gesendet; idempotente Zustände und die vorhandenen Deduplizierungsregeln von Wire Protocol v3 verhindern eine doppelte Darstellung.

Diese Trennung ist zugleich die Grundlage für weitere Plattformen. Ein zukünftiger iOS-Client kann DNS-SD über Bonjour veröffentlichen und denselben Handshake sowie Wire Protocol v3 implementieren, ohne Google Nearby Connections nachbilden zu müssen. Für Begegnungen ohne gemeinsames WLAN wäre weiterhin ein zusätzlicher plattformübergreifender Funktransport erforderlich.
