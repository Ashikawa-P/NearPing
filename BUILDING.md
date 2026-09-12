# NearPing bauen und signieren

## Voraussetzungen

NearPing benötigt JDK 17 und Android SDK 36. Das Repository enthält den Gradle Wrapper und lädt die in den Builddateien festgelegten Plugin- und Bibliotheksversionen. Der v1.2-Zweig besitzt `minSdk 33`; die erzeugte App läuft daher auf Android 13 oder neuer.

## Entwicklungs-Build

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Die Debug-APK liegt anschließend unter `app/build/outputs/apk/debug/app-debug.apk`. Sie ist nur für Entwicklung und lokale Tests bestimmt und wird mit dem Android-Debugschlüssel signiert.

## Release-Build

Öffentliche APKs müssen mit einem dauerhaft gesicherten privaten Release-Schlüssel signiert werden. Gradle liest die Signaturdaten ausschließlich aus folgenden Umgebungsvariablen:

```text
NEARPING_STORE_FILE
NEARPING_STORE_PASSWORD
NEARPING_KEY_ALIAS
NEARPING_KEY_PASSWORD
```

Unter Linux oder macOS können sie beispielsweise für einen einzelnen Build gesetzt werden, ohne eine geheime Datei im Repository anzulegen:

```shell
NEARPING_STORE_FILE=/absoluter/pfad/NearPing-release-key.p12 \
NEARPING_STORE_PASSWORD='...' \
NEARPING_KEY_ALIAS=nearping-release \
NEARPING_KEY_PASSWORD='...' \
./gradlew clean testDebugUnitTest lintRelease assembleRelease
```

Die signierte APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`. `release` ist ausdrücklich nicht debugbar. R8-Minifizierung bleibt in der v1-Releaselinie deaktiviert, damit der veröffentlichte Build möglichst transparent und nah am getesteten Code bleibt.

## Signatur prüfen

Mit den Android Build Tools kann die APK verifiziert werden:

```shell
apksigner verify --verbose --print-certs app-release.apk
```

Der erwartete SHA-256-Zertifikatsfingerabdruck für die öffentliche v1-Releaselinie lautet:

```text
0B:E4:E7:CA:31:20:A0:4D:31:18:11:C3:15:6C:65:32:AE:DE:5B:06:3F:36:60:E6:46:8E:E1:BE:6B:92:2A:7D
```

Das öffentliche Zertifikat liegt unter `docs/nearping-release-certificate.pem`. Es enthält keinen privaten Schlüssel.

## Schlüsselaufbewahrung

Der private Keystore und sein Passwort dürfen niemals in Git, GitHub Actions Logs, Issues oder öffentliche Release-Assets gelangen. Bewahre mindestens zwei verschlüsselte Backups an getrennten Orten auf und hinterlege das Passwort in einem Passwortmanager. Ein verlorener Schlüssel kann nicht rekonstruiert werden; ohne ihn können vorhandene Installationen nicht durch eine kompatibel signierte APK aktualisiert werden.

Für GitHub Actions sollten Keystore und Passwörter als verschlüsselte Repository-Secrets hinterlegt und nur in einem geschützten Release-Workflow verwendet werden. Der enthaltene Standardworkflow baut absichtlich ausschließlich die Debug-Variante und benötigt keine privaten Signaturdaten.
