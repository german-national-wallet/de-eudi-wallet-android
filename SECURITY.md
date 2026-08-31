# Security Policy

## Scope

Reports must be demonstrated against the **released application** — the build
distributed through Google Play — or against the backend endpoints it talks to.
The source published here is context for research, not the target of it.

Specifically **out of scope**:

- Findings derived from reading this source alone, without a demonstration
  against the released application. This mirror publishes application source
  only: configuration, build scripts, resources and tests are absent, so code
  paths that appear unreachable here may not be, and vice versa.
- Anything requiring a build of this repository. It is not intended to build.
- Issues in third-party dependencies, which should go to their maintainers.

## Supported versions

Only the **latest published state** of this repository, corresponding to the
current release of the application, is in scope. Earlier snapshots are not
supported.

## Known limitations

Some issues are already known and tracked internally. A report matching one of
them may be closed as a duplicate without detail.

## Reporting

The bug bounty programme for this project is being set up. Until it is live,
this document does not yet name a reporting channel.

## Safe harbour

Security research conducted in good faith and within the scope above will not
lead to legal action from us. Stay within scope, do not access or modify data
belonging to other people, and give us reasonable time to remediate before
disclosing.

## Upstream dependencies

This application depends on public upstream libraries, including the AusweisApp2
SDK and the EU reference wallet libraries. Vulnerabilities in those belong to
their respective maintainers.
