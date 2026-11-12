### Release Notes - v0.39.0

Note: updating clears the app data on first start, so the wallet has to be set up again.

Features
- Added the revocation onboarding screens
- Implemented the final EAA credential card design
- Updated the PIN screen to the new design
- Added push notification support
- Added a step to turn NFC on when it is off during PID issuance
- Improved NFC card reading: reading a card resting on the sensor works reliably, and the detection sound and vibration are back
- Added reusable templates for the content screens
- Unified iOS and Android error handling, and network error dialogs now show a trace ID
- Made the exported logs readable and complete for review, with network calls included and sensitive values redacted

Fixes
- PID issuance now picks the newest credential configuration the issuer advertises, falling back to the stable one
- Removed the Google Play Integrity check, device integrity rests on Android key attestation alone
- Reduced feature flag fetching to once a day
- PID issuance sent the issuer's URL as the wallet's client id
- A presentation request that arrived after a completed issuance returned to the wrong screen, leaving the next request nowhere to go
- Transaction codes that were not 6 digits long could not be entered
- The transaction code screen showed the wallet PIN button label

Known issues
- If test card is blocked or PUK is required, the app doesn't give user feedback