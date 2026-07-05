<h1 align="center">perCINEl</h1>

<p align="center">
  <b>Your private movie &amp; series diary.</b><br>
  Log what you watch, rate it, and keep it yours — on your device, in your pocket, ~2&nbsp;MB.
</p>

<p align="center">
  <a href="https://github.com/gopeshr/percinel/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/gopeshr/percinel"></a>
  <img alt="APK size" src="https://img.shields.io/badge/apk-~1.7%20MB-brightgreen">
  <img alt="Platform" src="https://img.shields.io/badge/android-8.0%2B-3ddc84">
</p>

---

## 📲 Download

Grab the latest APK from the **[Releases page](https://github.com/gopeshr/percinel/releases/latest)**.

1. Download `percinel-x.y.apk` on your Android phone.
2. Tap it → allow installing from your browser if prompted.
3. Open percinel, set up your profile, and start logging.

> Not on the Play Store (yet) — percinel is distributed as a direct download, so it stays free and installs with your own signing. Tip: point [Obtainium](https://github.com/ImranR98/Obtainium) at this repo to get automatic updates.

## Screenshots

| Welcome | Your watches | A watch | Stats |
|:--:|:--:|:--:|:--:|
| <img src="docs/screenshots/onboarding.png" width="200"> | <img src="docs/screenshots/home.png" width="200"> | <img src="docs/screenshots/entry.png" width="200"> | <img src="docs/screenshots/stats.png" width="200"> |

## Features

- 🎬 **Log movies & series** with poster, year, and a rating from 1–10 (two decimals), plus the date & time you watched.
- ✍️ **Your take first** — open a title and you see *your* rating and notes, not a Wikipedia dump. Full details are one tap away.
- 📋 **Watchlist** for things you plan to see; mark them watched later.
- 📊 **Stats** — totals, average, this year, rated 9+, highest & lowest — and every card taps through to the exact list.
- 🔎 **Fast search** via TMDB, with **manual entry** for anything not found.
- 📤 **Export** your whole diary to Excel (`.xlsx`) or plain text.
- ☁️ **Optional cloud backup** to your *own* Google Drive — see below.
- 🪶 **Tiny & offline-first** — the whole app is ~1.7 MB and works with no connection.

## Privacy & cloud sync

percinel is **local-first**. Everything you log lives on your device; there are no accounts and no servers.

Backup is **opt-in**. When you turn it on, your diary is stored in a **private, app-only folder in your own Google Drive** (`appDataFolder`) — the developer never sees or stores it. It lets you restore on a new phone or sync across your own devices, merging without ever losing a watch.

See the full [Privacy Policy](https://gopeshr.github.io/percinel/privacy-policy).

## Tech

Native Android, deliberately lean:

- **Kotlin + Jetpack Compose + Material 3**
- **SQLite** via `SQLiteOpenHelper` (no ORM)
- **HttpURLConnection + org.json** for TMDB and Google Drive REST (no Retrofit, no Drive SDK)
- **Coil** for images
- **Google Play Services Auth** only for the native account picker
- R8 + resource shrinking → ~1.7 MB APK

## Build from source

```bash
git clone https://github.com/gopeshr/percinel.git
cd percinel
```

Two secret files are gitignored — create them yourself:

- `app/tmdb.properties`
  ```properties
  TMDB_TOKEN=your_tmdb_v4_read_access_token
  ```
- `keystore.properties` (only needed for release builds) + a `release.keystore`
  ```properties
  storePassword=...
  keyAlias=...
  keyPassword=...
  ```

Then:

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # signed release APK
./gradlew :app:testDebugUnitTest  # run the sync-merge tests
```

## Attribution

This product uses the **TMDB API** but is not endorsed or certified by TMDB.

<sub>Movie & series data and images from <a href="https://www.themoviedb.org/">The Movie Database</a>.</sub>
