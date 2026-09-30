# PlantationLedger

Android app (Jetpack Compose, Room, Hilt) for tracking the daily expenses, income,
labour and vendor payments of a cardamom plantation.

## Origin of this source tree

The original project was lost with a failed SSD. This tree was reconstructed from
`app-release.apk` by decompiling it (jadx / CFR / javap) and rewriting the result as
Kotlin. What that means in practice:

* **Data layer is exact.** Entities, DAO queries, Room version 11 and the schema identity
  hash (`841719467de6379a1e08b0c3bf23e087`) match the released APK, so the app opens
  existing databases in place. The JSON export/import format is unchanged.
* **View models and screens are functionally faithful.** Strings, layout order, calculations
  and view-model calls were taken from the decompiled code; exact spacing, colours and
  animations are approximations. `CsvImportViewModel.importFromUri` was rebuilt from
  bytecode and deserves a test against a real CSV.
* **Build config deviates slightly.** Hilt plugin 2.60.1 instead of 2.57 (2.57 cannot be
  applied on AGP 9), and `android.disallowKotlinSourceSets=false` so KSP works with AGP 9's
  built-in Kotlin.

## Rename from EstateExpense

The app was originally published as **EstateExpense** (`com.santhomach.estateexpense`). It is now
**PlantationLedger** with package and application ID `com.santhomach.plantationledger`, and its
database file is `plantation_ledger.db`. Because the application ID changed, it installs as a separate
app alongside the old one. To move your data: in the old app use Settings → Export Data to JSON,
then in PlantationLedger use Settings → Import Data from JSON. Old export files import unchanged.

## Building

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew assembleDebug
```

Gradle 9.4.1 does not run on Java 26, so use Android Studio's bundled JDK (or any JDK 17-25).
The original signing keystore was not recoverable; a release build needs a new one, and an
APK signed with a new key cannot be installed over the existing installation (export the
data from Settings first, reinstall, then import).
