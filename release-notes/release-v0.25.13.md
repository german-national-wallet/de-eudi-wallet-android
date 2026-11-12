# Release Notes — v0.15.1 to v0.25.13

Everything shipped after the v0.15.0 baseline, up to and including v0.25.13
(42 tagged releases, 147 commits).

This batch of releases adds the ability to present multiple credentials at once,
encrypts all data stored on the device, introduces a forced app update when your
installed version is too old, and ships a long list of security and stability
fixes.

## What's New
- **Present multiple credentials in one go.** A new flow lets you present several
  credentials together, in one presentation.
- **Stacked credential cards on the dashboard.** Credentials now appear as a
  stack of cards showing the issuer's logo and title, so you can see what you
  hold at a glance.
- **A clear "no document" screen** appears in the presentation flow when there's
  nothing to show, instead of a blank or confusing state.
- **Credential background images** are now loaded from the issuer's branding and
  cached on your device (limited to 10 MB so it doesn't eat up storage). Note that this is not a final design and will be updated later.
- **New Wallet Backend endpoints are used** following migration to a production infrastructure.
- **Core engine upgraded** to a newer version, with tighter handling of the
  secure key service, one-time codes wired into key creation, and more robust
  parsing of key data.
- **Clearer issuer and service-provider details screens**, and the dashboard now
  scrolls smoothly when several cards are stacked.
- **key attestations** are now sent to the Credential Endpoint of a PID Provider. Key Attestations are not used for EAA issuance.
- **All wallet data on the device is now stored encrypted** using SQLCipher.
- **Forced app update.** If your installed version is below the required
  minimum, the app blocks use until you update, with a clear on-screen message.
- **Card reader flow rebuilt** as a cleaner, route-based set of screens that are
  easier to navigate and maintain.
- **Automatic data wipe** if you remove your device's lock screen — the wallet
  clears itself to protect your credentials.
- **Credentials are refreshed** if the remaining Credential count is low after the presentation.
   Note that this requires Credential Issuer to provide Refresh Token to the Wallet.

## Security & Privacy
- **Certificate pinning** for the Personal ID issuer on the demo and
  pre-production servers, so the app only talks to the genuine issuer.
- **The root certificate is now pinned** instead of the intermediate, for
  stronger identity assurance.
- **Device validation renewal is now double-signed** with a freshly generated
  key each time.
- **Google Play Integrity checks** can now be toggled via a build setting, and
  the integrity flag replaces the old simulator detection.
- **Non-essential logging removed** from release builds, so no sensitive
  information leaks via logs.
- **Simplified wallet authentication tokens** — the app now uses the primary
  token and drops redundant defaults.
- **The secure-key PIN session is cleared** as soon as you finish presenting a
  credential.
- **Credential request encryption hardened**, using a stronger algorithm and an
  encrypted request header.

## Fixes
- **Invalid server responses** during device validation are now caught
  gracefully instead of crashing.
- **Credential deletion** now targets the right item, and the Personal ID
  deletion confirmation works correctly.
- **Content is no longer hidden** behind the navigation bar.
- **Retry after two wrong wallet PINs** during issuance is unblocked.
- **The presentation scope stays alive** during navigation so multi-credential
  handoff works end-to-end.
- **Documents with no expiry date** are handled correctly (previously caused
  errors).
- **Back navigation restored** on the credential image screen, with a visible
  back arrow.
- **Key leakage during device-validation renewal** prevented with proper
  cleanup.
- **Development build telemetry URL** corrected.
- **PID re-issuance no longer hangs after account deletion.** The issuance state
  is now properly reset so you can delete and immediately re-issue a PID without
  getting stuck on a loading spinner.

## Behind the Scenes (Build & Tooling)
- **Switched the HTTP client** to the OkHttp engine for better network behavior.
- **Full redirect-chain logging** added via a shared network logger.
- **Code quality scanning (SonarQube) enabled**, with test-coverage rules
  cleaned up and UI code excluded from coverage metrics.
- **Optimized release builds** enabled (size-reduced, not yet obfuscated).
- **Device-validation behavior** is now configurable; the CI setting became a
  dropdown.
- **App name and on-screen strings renamed** for clarity, with lint and test
  tooling updated to match.
- **Version code now appears** in build artifact filenames.

## Known Issues
- **PIN salt clearing isn't used yet.**
- **First CAN entry from the PIN route still reports "PIN blocked".** The
  retry-after-two-wrong-PINs fix works, but the very first CAN entry path still
  misreports the state.
- **Multi-credential presentation lifecycle is fragile.** The presentation
  session must stay alive across the request and loading screens; cleanup is
  now explicit but the design is held together with workarounds.
- **Personal ID refresh depends on an open secure-key PIN session.** The refresh
  must run before the session closes, tying the two together.
- **Older / partially-set-up wallets are tolerated, not migrated.** Users can
  still delete local Personal ID documents, but there's no migration path.
- **Release builds are size-optimized but not obfuscated.** Obfuscation is
  intentionally disabled at this stage.
- **Stacked cards needed an explicit height fix.** The card stack was measured
  as one card tall; the fix uses an explicit stack height rather than automatic
  measurement.
