# Roadmap

This roadmap is intentionally lightweight. It describes likely directions, not a
promise that every item will be built.

## Current Focus

- Keep the live dashboard reliable across common ELM327 adapters.
- Improve diagnostic rules with explicit thresholds, clear evidence, and tests.
- Polish Android Auto read-only screens while staying within driver-safe template
  constraints.
- Make post-drive summaries more useful without collecting remote telemetry.

## Candidate Improvements

- Add more real-world adapter compatibility notes.
- Add screenshots or short demo media to the README.
- Expand unit tests around edge-case ELM327 responses and multi-frame DTC data.
- Improve diagnostics explanations and data limitation messages.
- Continue hardening CI and signed-release automation as the project grows.
- Add optional import/export flows for user-shared diagnostic sessions, with
  privacy safeguards.

## Out of Scope for Now

- Cloud sync, telemetry, or remote analytics.
- Repair recommendations that claim certainty beyond the available sensor data.
- A multi-module architecture or dependency injection framework.
- Write-capable Android Auto actions such as clearing DTCs.
