# Ryuza Member Native Android

**Native Android app using Kotlin + Jetpack Compose. No WebView and no embedded HTML UI.**

## App screens
- Home: collection hero, catalogue/favorite counters, featured and latest skin sections
- Katalog: native search, category filters, 2-column skin grid
- Detail: skin artwork, category, download options, favorite toggle
- Favorit: saved skins persisted locally on device
- Profil: app information and collection counters

## Data source
Catalogue data is extracted from the uploaded Ryuza backup:
- 182 skins
- 13 categories

Only member-facing catalogue fields are included. Admin HTML, admin source, GitHub credentials and Firebase credentials are not packaged.

## Technology
- Kotlin
- Jetpack Compose + Material 3
- Coil for remote catalogue artwork
- SharedPreferences for local favorites
- Android intents for external download links

## Build
Open this folder in Android Studio with JDK 17, allow Gradle sync, then use:
**Build > Build Bundle(s) / APK(s) > Build APK(s)**

Debug APK output:
`app/build/outputs/apk/debug/app-debug.apk`

The GitHub Actions workflow also builds the APK and uploads it as an artifact on pushes/PRs to `main` or manual workflow dispatch.

## Important feature boundaries
This native version implements the member catalogue experience using the available backup data. It does not yet implement Firebase-backed analytics, remote request submission, authentication, or admin features: the backup contains client configuration but does not establish safe server-side authorization or request API contracts. Those features need verified Firebase Security Rules and a defined member API before being added safely.

## Validation status
- Catalogue JSON extracted and parsed.
- Member-only data extraction verified.
- No HTML/WebView references are used by the native Activity.
- Android XML resources/manifest are well-formed.
- A real Android Gradle build and emulator/device interaction test still need to run in Android Studio or GitHub Actions.
