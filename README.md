# Liquid Player

Музыкальный плеер для Android (Kotlin + Jetpack Compose) в стиле iOS Liquid Glass.

- Играет музыку с телефона (MediaStore) — полностью офлайн
- Скачивает треки с YouTube в MP3 (yt-dlp + ffmpeg внутри приложения)
- Bluetooth-колонки, проводные наушники, кнопки гарнитуры, шторка уведомлений, фоновое воспроизведение
- Свайп между вкладками с эффектом «жидкого стекла»

## Сборка APK
Каждый push в `main` запускает GitHub Actions → APK в Artifacts (`LiquidPlayer-apk`) и в Releases.

Локально: `gradle assembleDebug` (JDK 17, Android SDK 35).
