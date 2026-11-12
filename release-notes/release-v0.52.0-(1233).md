### Release Notes - v0.52.0 (1233)

Features
- Connections to the wallet backend and rWSCA are now certificate-pinned
- TLS connections are restricted to current cipher suites
- A revoked wallet now stays locked after a restart, explains why on a blocking screen, and notifies the user

Fixes
- Exported logs no longer include the contents of push messages

Known issues
- PID credentials data is displayed incorrectly or not at all (german-national-wallet/issues-tracker-android#18)
