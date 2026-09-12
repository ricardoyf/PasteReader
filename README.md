<!-- app-release:start -->
[**Descargar APK v7**](https://github.com/ricardoyf/PasteReader/releases/download/v7/PasteReader-v7.apk) · [SHA-256](https://github.com/ricardoyf/PasteReader/raw/refs/heads/main/checksums/PasteReader-v7.apk.sha256)

`6e99170dd93150df50522fa5f057a827391a08566c3672c72e5641c8bfba0bca`
<!-- app-release:end -->

# PasteReader v7

App Android nativa en Kotlin + Jetpack Compose para pegar un texto cualquiera y escucharlo al momento con el TTS nativo de Android.

## APK

La versión instalable y compatible con las versiones anteriores está publicada en GitHub Releases:

[`PasteReader-v7.apk`](https://github.com/ricardoyf/PasteReader/releases/download/v7/PasteReader-v7.apk)

## Novedad de v7

- La lectura y el resaltado conservan la palabra actual al girar el móvil entre vertical y horizontal.
- Si Android necesita recrear el motor TTS, la lectura se reanuda automáticamente desde el último offset conocido.
- Una lectura pausada sigue pausada después de la rotación y Play continúa desde el mismo punto.
- Stop y el final de la lectura sí dejan el siguiente Play preparado desde el principio.

## Qué hace

- Muestra un cuadro de texto grande al abrir.
- Permite pegar desde el portapapeles o escribir directamente.
- Lee el texto con el TTS de Android en español.
- Incluye play, pausa/reanudar, stop, borrar y control de velocidad.
- Resalta en tiempo real la palabra pronunciada.
- Mantiene visible la lectura desplazando automáticamente el texto.
- Al pausar, guarda la posición exacta y continúa desde esa palabra.
- No usa biblioteca de archivos, no guarda historiales de lectura y no necesita permisos de almacenamiento.

## Base reutilizada

Parte de la base de `TTS Reader`, pero con una pantalla principal nueva y `applicationId` propio:

```text
com.ricardo.pastereader
```

La etiqueta visible de la app es:

```text
PasteReader
```

## Compilar

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK de CI generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Ese APK usa una firma efímera de CI y se publica únicamente como `PasteReader-CI-NO-DISTRIBUIR`; no sustituye al APK compatible de GitHub Releases.

Requisitos: JDK 17 y Android SDK 34.
