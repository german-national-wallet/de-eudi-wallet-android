# EUDI Wallet DE — Android

This is a source code of the German National Wallet Android application.

This repository is one-way, read-only and flows out of an internal
repository.

## Scope

Application source code under `src/main`, the Android manifests, and the Gradle
version catalog are published to show what the wallet does, what permissions it holds, and what it depends on.

**Build instructions are coming.** We will add documentation on how to put together the project
and what needs to be supplied in place of the stripped configuration.
Note that a functioning wallet also requires issuers, verifiers, and a wallet backend

Currently, the following are absent:
- Gradle build files, `settings.gradle.kts`, convention plugins. The module structure is visible from the directory layout and the manifests.
- Service endpoints, tokens and per-environment settings are not published, and neither is the single class that holds the endpoints. There are no placeholder values to mistake for real ones.
- Resources such as layouts, strings, themes, drawables and fonts.
- Tests, CI and release tooling: test sources, the test-only modules, GitHub Actions workflows and fastlane.

This is not a reproducible build: this source would not
correspond byte-for-byte to any build obtained via Google Play.

## Upstream

The application is a fork of the European Commission reference implementation,
[`eu-digital-identity-wallet/eudi-app-android-wallet-ui`](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui).
Upstream reference implementation already uses EUPL-1.2 license, hence we are open sourcing it under the same license. Upstream copyright headers are preserved in the files that carry them. The `eu.europa.ec.*` package tree is upstream code; `org.sprind.*` is our own code.

## Dependencies of note

The full declared dependency set is published as `gradle/libs.versions.toml`.
The following ones are worth calling out because they canoot be simply resolved:

- **AusweisApp2 SDK** (`com.governikus.ausweisapp:sdkwrapper`) — eID card
  reading. It is served from a **Governikus-operated Maven repository, not
  Maven Central**, so it is not publicly resolvable.
- **`eudi-lib-android-wallet-core`** — our fork of the EU wallet core library,
  consumed as a git submodule. The submodule link is not published, but the fork
  is public:
  [german-national-wallet/eudi-lib-android-wallet-core](https://github.com/german-national-wallet/eudi-lib-android-wallet-core).
- **`eudi-lib-jvm-openid4vci-kt`** — our fork of the OpenID4VCI library, also a
  submodule and also public:
  [german-national-wallet/eudi-lib-jvm-openid4vci-kt](https://github.com/german-national-wallet/eudi-lib-jvm-openid4vci-kt).

## Related documentation

- [Architecture Documentation for the German National EUDI Wallet](https://bmi.usercontent.opencode.de/eudi-wallet/wallet-development-documentation-public/latest/)

## Contributing and issues

Issue tracking and pull requests are **not** enabled on this mirror right now. Issue tracking is planned to be enabled in September 2026. For more details see
[CONTRIBUTING.md](CONTRIBUTING.md). For security reports, see
[SECURITY.md](SECURITY.md).

## Licence

EUPL-1.2. See [LICENSE.txt](LICENSE.txt) and [NOTICE.txt](NOTICE.txt).