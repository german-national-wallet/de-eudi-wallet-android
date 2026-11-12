### Release Notes - v0.51.2

Features
- Added a numbered overview of the issuance steps before the consent screen
- Added a consent screen after the card read, listing the credential, its issuer and the PID data
- Applied the final designs across the PID issuance, wallet code and consent screens
- Screen readers now announce the card reader steps and the claims card
- Added explanation sheets for the eID function, the card PIN and the card PIN letter
- Closing the issuance flow, or rejecting the consent, now asks for confirmation
- The card is read on its own screen, with a progress sheet and an animation matching where the device's NFC antenna sits
- The privacy policy now opens without leaving the app
- Re-issuing a credential from the same issuer now replaces the existing one instead of storing both
- The wallet locks itself and wipes its data when the account is revoked
- Added the Common Codes EUDI Hub sandbox trust anchors

Fixes
- Issuance and DPoP metadata are now encrypted on the device
- Entered PINs can no longer be pasted in, captured in screenshots, or left behind in memory
- The toolbar back and close buttons both announced as "Reject" to screen readers
- Long credential names are no longer cut off on the credential card

Known issues
- PID credentials are displayed incorrectly or not at all (german-national-wallet/issues-tracker-android#18)

