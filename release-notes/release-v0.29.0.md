# Release Notes — v0.25.13 to v0.29.0

Everything shipped after the v0.25.13 baseline, up to and including v0.29.0
(11 tagged releases, 50 commits).

This batch of releases lets you present a credential while another one is
being issued, refreshes the app's look and feel, and makes the eID card
reader easier to use.

## What's New
- **Present a credential while issuing another.** You can now answer a
  verifier's request even while a PID or EAA issuance is still in progress —
  the wallet pauses issuance, handles the presentation, then resumes and
  finishes issuing your credential.
- **A fresh look and feel.** Buttons, colors, fonts, the dashboard title and
  the toggle styling have been updated to align with the EUDI design
  language. This is not the final design, and will keep evolving.
- **A new splash screen** with a gradient background and the app icon greets
  you on startup.
- **The eID card reader is now easier to use.** If your eID card is already
  resting on the NFC sensor, it's read automatically — you no longer have to
  lift and tap it again. The system tag-detection beep and vibration are
  silenced, and you can see the read progress (0–100%) under the loader.
- **Clearer errors when issuance is interrupted.** If the system kills the
  app mid-authorization, you now see a proper error message instead of being
  left on a stuck spinner.
- **The "no document" screen now points you to add a document** directly
  from its primary button.

## Fixes
- **Verifier requests now pick the right claims.** When several claims
  displayed the same value (for example, country, issuing country and
  nationality all showing "DE" in a German PID), the wrong one could be sent
  to the verifier. Claims are now matched by their stable identifier instead
  of their displayed value.
- **Missing documents raise a clear error** during presentation instead of
  silently failing.
- **EAA credentials issue correctly.** EAA issuers no
  longer inherit settings meant for PID providers, and expiry dates in
  date-only format are handled alongside full ISO timestamps.
- **Single-use pre-authorized Credential offers no longer fail.** The app no longer
  re-fetches an offer that can only be used once, so issuance from a
  pre-authorized link works reliably.
- **The card reader handles missing authentication results** gracefully
  instead of erroring out.
- **Issuance resumes correctly** after the app returns from authorization,
  with no more stuck loading state.

## Behind the Scenes (Build & Tooling)
- **Minimum Android version raised to 14 (API 34)**, matching the latest
  architecture documentation; obsolete compatibility code was removed.

## Known Issues
- **PIN salt clearing isn't used yet.**
- **First CAN entry from the PIN route still reports "PIN blocked."** The
  retry-after-two-wrong-PINs fix works, but the very first CAN entry path
  still misreports the state.
- **Multi-credential presentation lifecycle is fragile.** The presentation
  session must stay alive across the request and loading screens; cleanup is
  now explicit but the design is held together with workarounds.
- **Older / partially-set-up wallets are tolerated, not migrated.** Users
  can still delete local Personal ID documents, but there's no migration
  path.
- **Release builds are size-optimized but not obfuscated.** Obfuscation is
  intentionally disabled at this stage.
- **Stacked cards needed an explicit height fix.** The card stack was
  measured as one card tall; the fix uses an explicit stack height rather
  than automatic measurement.
