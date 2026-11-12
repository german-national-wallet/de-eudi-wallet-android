### Release Notes - v0.52.0 (1238)

Features
- Using final app name, app icon and splash logo
- Added onboarding screens
- Added bottom navigation bar, activities and settings screens

Fixes
- A correct CAN was reported as wrong, then correct a tap later
- A refused PIN returned to the entry screen with no warning
- Restarting the card reader left the previous one running, handling every eID event twice
- Restarting a cancelled issuance could hang on the loading spinner
- The verifier's redirect cut off the credential batch refresh
- Re-issuance could break an issuance waiting for its redirect
- A low batch re-issued only one PID instead of all

Known issues
- PID credentials are displayed incorrectly or not at all (german-national-wallet/issues-tracker-android#18)
