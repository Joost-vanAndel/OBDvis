# Security Policy

## Supported Versions

Security fixes are considered for the current `main` branch. If tagged releases
are published, the latest release line is the supported line unless stated
otherwise in the release notes.

## Reporting a Vulnerability

Please do not open a public issue for a vulnerability that could expose user data
or compromise a device.

Report security concerns privately through GitHub's private vulnerability
reporting feature if it is enabled for this repository. If it is not enabled,
contact the maintainer through the repository owner's preferred contact channel
and include "OBDvis security" in the subject.

Useful details include:

- A clear description of the issue.
- Steps to reproduce.
- Affected Android versions or device models, if known.
- Whether the issue requires a paired Bluetooth adapter or a physical vehicle.
- Any logs or screenshots that do not contain personal data.

Please avoid sharing VINs, license plates, precise locations, or raw drive logs
unless they are necessary and you are comfortable disclosing them privately.

## Scope

Security-sensitive areas include:

- Bluetooth connection handling.
- Exported Android components and Android Auto service metadata.
- CSV export and file picker behavior.
- Notification behavior.
- Storage of settings, summaries, or vehicle-derived data.

OBDvis is designed to process vehicle data locally and does not intentionally
upload OBD-II data, DTCs, Bluetooth device information, location, or driving
summaries to a remote service.
