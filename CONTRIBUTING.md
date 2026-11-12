# Contributing

This repository is a **one-way mirror** of an internal repository. Code is
published here after passing a sanitizing pipeline; nothing merged here can
travel back upstream.

As a consequence:

- **Issue tracking is enabled.** See [Issues](#issues) below.
- **Pull requests are not accepted.** There is no path for a change made here
  to reach the shipped application, so a PR cannot be merged even in principle.
  To propose a change, open an issue describing it.
- **The published history is a series of snapshots.** Each commit is one
  sanitized snapshot of the internal `main`; it is not the development history,
  and commits do not correspond one-to-one with internal changes.

## Issues

Use [GitHub issues](https://github.com/german-national-wallet/de-eudi-wallet-android/issues)
for bug reports, questions and suggestions about the published source. Please
name the snapshot you looked at (the commit, or the date in its subject) and,
for behaviour of the released app, the app version and the device.

Issues are triaged by the internal team. A fix arrives with a later snapshot;
there is no commit here that references the issue, so we close it once the
snapshot that carries the fix is published.

**Do not report security vulnerabilities in issues.** See
[Security research](#security-research). An issue that describes a
vulnerability may be removed, and you will be asked to submit it through the
bug bounty program instead.

## Security research

Security reports are welcome, but not as issues. Our bug bounty program is the
channel for security findings. See [SECURITY.md](SECURITY.md) for scope and how
to report.

## Licence of contributions

Should contributions be opened in future, they would be under EUPL-1.2, the
licence of this work.
