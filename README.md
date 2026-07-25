# PasteReader v6

App Android nativa en Kotlin + Jetpack Compose para pegar un texto cualquiera y escucharlo al momento con el TTS nativo de Android.

## APK

La versión compilada incluida en el repositorio es:

[`PasteReader-v6-app-debug.apk`](./PasteReader-v6-app-debug.apk)

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

## Compilar APK

```bash
cd "/home/n95/gDrive/gitHub/pastereader"
./gradlew assembleDebug
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Requisitos: JDK 17 y Android SDK 34.
