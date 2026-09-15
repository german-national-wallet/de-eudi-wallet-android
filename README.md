# EUDI Wallet DE — Android

This is a source code of the German National Wallet Android application.

This repository is one-way, read-only and flows out of an internal
repository.

## Building

The project compiles and launches from a clean clone with no configuration
beyond an Android SDK. Every service endpoint and credential here is a
**placeholder**, so the app starts and the UI is navigable, but nothing that
talks to a service works until you supply your own — see
[Supplying your own services](#supplying-your-own-services).

### Requirements

- **JDK 17 or newer.** Sources target Java 17; verified on JDK 25. The unit
  tests additionally need a JDK 21 toolchain, which Gradle resolves itself.
- **Android SDK** with `compileSdk` 37 (Android 17). `minSdk` is 34.
- **Gradle** is *not* required — the wrapper is included.

### 1. Fetch the two library submodules

The wallet core and OpenID4VCI libraries are built from source. Their URLs and
branches are in [`.gitmodules`](.gitmodules), but **this mirror cannot carry
submodule links**, so clone them by hand into the repository root:

```sh
git clone -b develop-0.30.2 https://github.com/german-national-wallet/eudi-lib-android-wallet-core core
git clone -b develop-0.13.1 https://github.com/german-national-wallet/eudi-lib-jvm-openid4vci-kt openid4vci
```

Both are public forks. `settings.gradle.kts` includes them as `:core:wallet-core`
and `:openid4vci`, so the build fails with `No variants exist` if these two
directories are empty.

### 2. Point the build at your Android SDK

Either export the standard environment variable:

```sh
export ANDROID_HOME=/path/to/Android/sdk
```

…or create `local.properties` in the repository root:

```properties
sdk.dir=/path/to/Android/sdk
```

### 3. Build and run

```sh
chmod u+x ./gradlew
./gradlew :app:installDevDebug
adb shell monkey -p org.sprind.wallet.dev -c android.intent.category.LAUNCHER 1
```

`./gradlew :app:assembleDevDebug` alone writes
`app/build/outputs/apk/dev/debug/app-dev-debug.apk` (application ID
`org.sprind.wallet.dev`).

If the install is refused with `INSTALL_FAILED_VERSION_DOWNGRADE`, a newer build
is already on the device: `adb uninstall org.sprind.wallet.dev` first, or install
with `adb install -r -d <apk>`.

### Running on an emulator

Add one line to `local.properties`:

```properties
IS_SIMULATOR=true
```

This matters. eID card reading otherwise expects real NFC hardware and a real
ID card: `IS_SIMULATOR` switches the card type to `VIRTUAL` and suppresses the
platform-authentication blocking screen. With it set, the first screen offers a
**"Use simulated eID card"** toggle. Any emulator image at API 34 or above works.

### Flavors and build types

Three product flavors — `dev`, `staging`, `sandbox` — combine with `debug` and
`release`, so any `assemble<Flavor><BuildType>` task works, for example
`./gradlew :app:assembleSandboxDebug`. Release builds here are **unsigned**: the
signing configuration is supplied by internal CI and is not published.

## Supplying your own services

Placeholder hosts all use `.example.invalid`, a reserved name that can never
resolve. That is deliberate — the app fails cleanly instead of quietly reaching
a host someone else controls.

Note that a functioning wallet also requires issuers, verifiers, and a wallet backend.

### Tokens and client IDs

Four secrets reach the code as `BuildConfig` fields via the Secrets Gradle
plugin. Committed placeholders live in `secrets.defaults.properties`; your real
values go in **`local.properties`**, which is git-ignored and takes precedence:

| Key | What it is for |
|---|---|
| `WALLET_AUTH_TOKEN` | Authenticates the app against your wallet backend |
| `OTEL_WALLET_AUTH_TOKEN` | Bearer token for your OpenTelemetry collector |
| `FEATURE_FLAG_API_TOKEN` | Auth for the feature-flag service |
| `VCI_ISSUER_CLIENT_ID` | OAuth client ID presented to the OpenID4VCI credential issuer |

```properties
# local.properties — never commit this file
sdk.dir=/path/to/Android/sdk
IS_SIMULATOR=true
WALLET_AUTH_TOKEN=your-token
OTEL_WALLET_AUTH_TOKEN=your-token
FEATURE_FLAG_API_TOKEN=your-token
VCI_ISSUER_CLIENT_ID=your-client-id
```

Do **not** put real values in `secrets.defaults.properties` — it is committed.

### Endpoints

| Service | Where to change it | Placeholder |
|---|---|---|
| Wallet backend | `business-logic/src/{dev,staging,sandbox}/…/EnvironmentConfigImpl.kt` → `serverHostURL` | `https://<flavor>.wallet-backend.example.invalid` |
| Telemetry (OTLP) | `business-logic/src/main/…/config/WalletBackendEnvironmentConfig.kt` → `OTEL_WALLET_URL` | `https://telemetry.example.invalid` |
| Feature flags | same file → `featureFlagApiBaseUrl` | `https://feature-flags.example.invalid/features/` |

### Telemetry

The app is instrumented with OpenTelemetry and exports traces and logs over
OTLP to `OTEL_WALLET_URL`, authenticated with `OTEL_WALLET_AUTH_TOKEN`. The
per-flavor `otelServiceName` identifies the build. With placeholders in place
this appears in logcat, and it is harmless:

```
W HttpExporter: Failed to export logs. Server responded with HTTP status code 401.
```

Point `OTEL_WALLET_URL` at any OTLP/HTTP collector to make it work.

### Feature flags

Flags are fetched from `featureFlagApiBaseUrl` and cached in storage. When the
fetch fails — as it does against the placeholder host — `FeatureFlagManager`
falls back to the value compiled into each `FeatureFlag`, so the app keeps
running on its built-in defaults. `minimum_app_version` defaults to `0.0.0`, so
the force-upgrade screen stays out of the way.

### Push notifications

`app/google-services.json` is a **placeholder** — project
`eudi-wallet-placeholder`, no real API key.

### PID issuer and eID card reading

Unlike the above, these point at **real third-party services** and are published
unchanged, because they are public and their certificate pins are part of the
security model:

- `PidIssuerSpec.kt` carries the Bundesdruckerei `demo` and `preprod` PID issuer
  endpoints together with their public SPKI pins. Access requires entitlement
  from the operator.
- The trust anchors in `resources-logic/src/main/res/raw/*.pem` are public CA
  certificates, loaded through `R.raw` by `WalletCoreConfigImpl`. They contain no
  private key material, and publishing them is what lets a reader see which CAs
  the wallet actually trusts.
- eID reading goes through the AusweisApp2 SDK against the Governikus eID test
  server. It needs real NFC hardware and an ID card — or `IS_SIMULATOR=true` for
  a simulated card.

### Fonts

The brand typefaces (ABC Diatype, Necto Mono) are commercially licensed and
cannot be redistributed. They are replaced by open-licensed substitutes under
the **same resource names**, so the build and the layout are unaffected:

| Resource | Substitute |
|---|---|
| `eudi_diatype_*` | Inter (SIL OFL 1.1) |
| `necto_mono_regular`, `eudi_diatype_semi_mono_medium` | JetBrains Mono (SIL OFL 1.1) |

Licence texts are in [`licenses/`](licenses). Typography will not match
production exactly. To restore it, drop the licensed files into
`resources-logic/src/main/res/font/`, keeping the existing filenames.

### Not a reproducible build

This source would not correspond byte-for-byte to any build obtained via
Google Play.

## Scope

Application source, resources, Android manifests, unit tests, Gradle build
scripts, the convention plugins under `build-logic/` and the version catalog are
published, so the project can be built and inspected as it is built internally.

Not published:

- **Real endpoints, credentials and per-environment configuration** — replaced
  by the placeholders documented above. A handful of files that the build or
  the published unit tests need ship as placeholder overlays at the same path
  (the backend config, the per-flavor config implementations, the certificate
  pinning test); their placeholders carry `.example.invalid` hosts and the
  public Let's Encrypt pin set.
- **Licensed material** — the brand fonts, replaced by open-licensed
  substitutes, and the AusweisApp2 SDK staging licence.
- **CI and release tooling** — GitHub Actions workflows, fastlane, release
  scripts and the signing configuration.
- **Instrumented tests** (`androidTest`), which need a device and reachable
  services.
- **Internal documentation** and the tooling that produces this mirror.

## Upstream

The application is a fork of the European Commission reference implementation,
[`eu-digital-identity-wallet/eudi-app-android-wallet-ui`](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui).
Upstream reference implementation already uses EUPL-1.2 license, hence we are open sourcing it under the same license. Upstream copyright headers are preserved in the files that carry them. The `eu.europa.ec.*` package tree is upstream code; `org.sprind.*` is our own code.

## Dependencies of note

The full declared dependency set is published as `gradle/libs.versions.toml`.
These are worth calling out:

- **AusweisApp2 SDK** (`com.governikus.ausweisapp:sdkwrapper`) — eID card
  reading. Published on Maven Central, so it resolves with no extra repository
  configuration.
- **`eudi-lib-android-wallet-core`** — our fork of the EU wallet core library,
  built from source as `:core:wallet-core`:
  [german-national-wallet/eudi-lib-android-wallet-core](https://github.com/german-national-wallet/eudi-lib-android-wallet-core).
- **`eudi-lib-jvm-openid4vci-kt`** — our fork of the OpenID4VCI library, built
  from source as `:openid4vci`:
  [german-national-wallet/eudi-lib-jvm-openid4vci-kt](https://github.com/german-national-wallet/eudi-lib-jvm-openid4vci-kt).
- **`eudi-lib-android-rqes-ui`** is pinned to a `-SNAPSHOT` resolved from the
  Sonatype snapshot repository. Snapshots are mutable and may be removed
  upstream, which is the most likely cause of a dependency resolution failure
  over time.

## Related documentation

- [Architecture Documentation for the German National EUDI Wallet](https://bmi.usercontent.opencode.de/eudi-wallet/wallet-development-documentation-public/latest/)

## Design System

The link below contains the Design System for the d-you App, including Guidelines, Assets, Template, Components, and the main happy flows for App Onboarding (Wallet Activation), Dashboard, Activities and Settings, PID & EAA Issuance/Inspection/Presentation: [**Figma Design System**](https://www.figma.com/design/vGhn8VyJ987JzmJenIuvI1/2026_09-Design-System-d-you-and-flows)

This Design System is a work in progress and will keep evolving through future iterations. We will periodically push updates with the latest designs. Some components, assets, or templates may change over time.

## Contributing and issues

Issue tracking and pull requests are **not** enabled on this mirror right now. Issue tracking is planned to be enabled in October 2026. For more details see
[CONTRIBUTING.md](CONTRIBUTING.md). For security reports, see
[SECURITY.md](SECURITY.md).

## Licence

EUPL-1.2. See [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).
Third-party font licences are in [`licenses/`](licenses).
