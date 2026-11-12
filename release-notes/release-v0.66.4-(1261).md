### Release Notes - v0.66.4 (1261)

**Note:** Updating to this latest release will clear the current wallet.

**Features**
- Added a new activity log with an overview and detail screen.
- Improved app onboarding screens with new setup steps for privacy policy, notification consent, screen lock, and more general improvements.
- Improved the PID issuance screens.
- Added updated trust anchors for Sandbox Registrar and EUDI Hub.
- Trust anchors split into access and registration CAs, with CRLs checked with soft-fail.

**Fixes**
- A service rejection dialog is now displayed when the verifier rejects the response.
- Dashboard no longer flashes its loader on return.
- The notice box on the presentation request screen now has a title.
- Enabled scroll fade on the setup and presentation screens.
- Removed unneeded Android permissions.
- Credential response encryption is limited to ECDH-ES on P-256 with AES-GCM, and RSA is switched off.
- Explicit AES key size (256) and secure hardware required.
- A presentation that failed after the PIN entry showed two errors at once.

**Known issues**
- PID credentials are displayed incorrectly or not at all (german-national-wallet/issues-tracker-android#18).
