<!-- Hallmark · proposal critique: P5 H4 E3 S5 R5 V4. Design proposal only; rendered execution remains unverified. -->
# OBDvis UI redesign proposal

Status: approved by the user and implemented in 0.1.13. This document preserves the original proposal; see [the delivered redesign and validation](ui-redesign/README.md).

## Direction

A dark instrument panel: clear readings, compact navigation, readable diagnostic evidence. Keep OBDvis's blue identity and native Android feel. Genre: modern-minimal, adapted to the existing dark theme. Structure: an instrument workbench for the dashboard, a report for drive summaries, and grouped lists for setup and settings.

Retain the four dashboard destinations and their current behavior. Give every screen the same typography, spacing, buttons, headers, and semantic status colors. Screen structure should follow its job rather than repeat an identical stack of cards.

## Pre-flight evidence

- `android/app/src/main/java/com/obdvis/android/ui/theme/Theme.kt:8`: existing dark background, surfaces, blue primary, and text tokens. `OBDvisTheme` supplies colors but no custom typography or shapes.
- `android/app/src/main/java/com/obdvis/android/ui/DashboardScreen.kt:42`: screen collects ViewModel StateFlows and owns dashboard navigation. Tab callbacks also control polling context; preserve those callbacks.
- `android/app/src/main/java/com/obdvis/android/ui/overview/OverviewScreen.kt:37`: Overview receives values and actions as parameters; gauges and charts are drawn locally. Its current hierarchy is two equal gauges, G-force, throttle, speed history, coolant/battery, then diagnostics.
- `android/app/src/main/java/com/obdvis/android/ui/components/LiveLineChart.kt:21`: native MPAndroidChart integration remains the chart renderer.
- Existing screen code mostly uses a 4 dp spacing rhythm, with local exceptions and repeated shapes/colors.
- Existing Material interactions and some Compose transitions; no separate motion framework needed.
- Existing logo artwork and five screenshots are available. Screenshots illustrate the visual language but are older than parts of the current source.
- No root `design.md` or existing Hallmark system was found in the inspected project files.

## Architecture and approaches

MainActivity selects permission, connection, dashboard, history, and summary screens. DashboardScreen connects a single MainViewModel to the four tabs. Most screen components already receive state and callbacks, so layout changes can stay within the presentation layer.

| Approach | Simplicity | Maintainability / extensibility | Testability | Performance | Risk |
| --- | --- | --- | --- | --- | --- |
| Theme-only refresh | Lowest effort | Central colors improve; duplicated component styles and current layouts remain | Easy visual comparison; limited coverage of the actual problems | Essentially unchanged | Low, but weak result |
| Shared theme/components + in-place screen redesign | Moderate, fits current ownership | Shared primitives keep screens consistent and make later changes easier | Existing logic tests plus focused screen previews and interaction checks | Preserve chart renderer and sample flow; avoid new animation work | Moderate visual scope, contained behavioral risk |
| Replace the UI/navigation structure | Highest effort | Larger migration and new ownership boundaries | Requires broader navigation and lifecycle coverage | No established benefit | Highest; unnecessary for this brief |

Recommendation: shared theme/components with in-place screen redesign.

## Proposed system

- Palette: keep the current blue-tinted dark base; brighten secondary text and interactive blue where contrast requires it. Use existing token names. Centralize warning, error, success, and chart roles rather than scattering color literals.
- Typography: retain native Android body text; define explicit title, body, caption, and numeric styles. Use tabular digits for changing readings, stronger weight for primary measurements, and sentence-case labels. Avoid tiny, faint all-cap headings.
- Spacing: named 4/8/12/16/24/32 dp steps. Use 16 dp compact screen margins, with additional space where width permits. Section spacing communicates hierarchy.
- Shapes: restrained 8–12 dp corners on controls and genuine grouped surfaces. Use separators for simple rows instead of outlining every piece of information.
- Actions: a clear filled primary action, quiet secondary controls, minimum 48 dp touch targets. Preserve confirmation for DTC clearing and saved-drive deletion.
- Motion: native pressed/focus feedback; no decorative entrance animation or extra smoothing of sensor data. Avoid rapid transitions while readings update.
- Status: color plus a readable label or icon. Demo, connecting, waiting, paused, errors, active/fading findings, and unavailable measurements remain distinguishable.
- Responsive behavior: stack content on narrow screens and use available landscape width. Support font scaling, TalkBack, and keyboard focus.
- Native mapping: implement runtime tokens in Kotlin/MaterialTheme. Hallmark's web-only CSS, hover, viewport, and animation rules translate to native equivalents; CSS exports are portability documentation, not Android runtime dependencies.

## Screen inventory and proposed layouts

All paths below are relative to `android/app/src/main/java/com/obdvis/android/ui/`.

| File / screen | Purpose | Proposed visual change |
| --- | --- | --- |
| `PermissionScreen.kt` | Bluetooth permission onboarding | Clear title, concise explanation, prominent grant action, legible denied state |
| `DevicePickerScreen.kt` | Paired adapter selection, connection, demo, history | Calm setup header, full-width device rows, distinct connecting/error state, visible demo and history actions |
| `DashboardScreen.kt` | Session header and four-tab navigation | Separate session status from navigation; four readable chips with appropriate touch targets and integrated counts; recognizable end-drive action |
| `overview/OverviewScreen.kt` | Live driving instruments and diagnostic shortcuts | Lead with speed, keep RPM alongside it with a compact arc; put coolant/battery and health/DTC summaries before the secondary motion instruments; group throttle, speed history, and G-force coherently |
| `components/LiveLineChart.kt` + `components/SensorSidebar.kt` | Live graph, values, enabled signals, normalization/export | Give plot the largest available area; improve axis/label contrast; align sensor names and values in rows; separate selection controls from measurements |
| `health/VehicleHealthScreen.kt` | Findings, readiness, coverage, event log | Strong status heading; readable severity/title/evidence hierarchy; less repetitive borders and badge clutter; retain findings, coverage, readiness, guide, event log, and Auto control |
| `health/FuelTrimDivePanel.kt` | Trim chart, bank readings, interpretation | Consistent back/header treatment, clearly keyed chart, aligned bank values, readable interpretation |
| `dtc/DtcScreen.kt` | Read/clear codes and code lookup | Separate read and destructive actions; concise stored/pending sections; prominent codes and descriptions; explicit loading, empty, and error views |
| `PostDriveScreen.kt` | Current or saved drive report, CSV export | Report hierarchy: session details, peak readings, operating-state breakdown, findings and DTCs; aligned stats, fewer independent boxes |
| `DriveHistoryScreen.kt` | Saved drive browsing and deletion | Compact date-led rows with duration, severity, and storage secondary; clear empty state |
| `SettingsScreen.kt` | Background monitoring and notifications | Grouped preference rows, consistent controls, visible validation and supporting text |
| `AboutScreen.kt` | App identity, version, source/license navigation | Existing logo and clean metadata rows using the shared header |
| `ThirdPartyNoticesScreen.kt` | License text | Shared header, comfortable reading spacing, preserved legal content |

### Overview reading order

1. OBDvis, device/demo status, session actions.
2. Overview / Live / Health / DTCs navigation.
3. Operating state and primary speed/RPM instruments.
4. Coolant and voltage readings.
5. Health and DTC navigation rows with actual current states.
6. Secondary throttle, speed history, and G-force instruments.

Unknown data uses an unavailable indication, not invented measurements. Any change to existing missing-value presentation will receive a focused check.

## Exact planned file scope

Modify all screen/component files listed in the inventory, plus:

- `android/app/src/main/java/com/obdvis/android/ui/theme/Theme.kt` — shared colors, typography, shapes, spacing.
- `android/app/build.gradle.kts` — increment versionCode/versionName for the implemented redesign.

Create:

- `design.md` — approved system and portable token exports.
- `tokens.css` — portable token reference requested by Hallmark; not consumed by Android.
- `.hallmark/preflight.json` — pre-flight record.
- `.hallmark/log.json` — one app-wide redesign record.
- `android/app/src/main/java/com/obdvis/android/ui/components/AppComponents.kt` — shared screen header, section heading, and status/row primitives where genuinely reused.
- `android/app/src/debug/java/com/obdvis/android/ui/RedesignPreviews.kt` — representative Compose preview states and widths for visual review.

No deletions planned. Preserve public screen signatures, ViewModel APIs, polling, diagnostics, session lifecycle, exports, and persistence. Android Auto remains a native Car App Library interface controlled by host templates; this proposal targets the phone UI.

## Validation before delivery

- Build the debug APK and run existing unit tests.
- Review representative layouts at 320, 375, 414, and 768 dp widths, portrait and landscape; report any unavailable rendering checks honestly.
- Check enlarged text, long device names, long findings, empty/connecting/error states, unavailable sensors, multiple badges, and active/fading findings.
- Exercise tab switching and back behavior, chart normalization and toggles, Health pause/resume and fuel-trim navigation, DTC confirmation, history, summary export, and settings validation where a device/emulator is available.
- Verify contrast of actual text/background pairs and shared control states. Check touch targets and accessibility labels.
- Review actual rendered screens against Hallmark's applicable native design rules. No claim of a complete visual pass before rendering.

## Original confirmation step (completed)

Hallmark `references/verbs/redesign.md`, Multi-page flow, step 2 explicitly says: “Wait for confirmation. Only after the user confirms (or says ‘go ahead’) do you write `design.md` and start redesigning pages.”

The user approved this direction: “sounds good, let’s proceed”.
