<!-- Hallmark · pre-emit critique: P5 H4 E4 S5 R5 V4; representative native renders reviewed. -->
# Design — OBDvis

Approved direction: dark instrument panel, 2026-09-25. One shared system for the phone app. Extend this system when needed; do not pick unrelated themes per screen.

## Genre and structure

Modern-minimal adapted to the established dark Android UI. Dashboard: instrument workbench. Drive summary: report. Setup, history, settings, and legal screens: grouped lists / reading layouts. Four existing dashboard chip destinations remain. App content carries the design; no decorative enrichment.

## Theme

Runtime source: `android/app/src/main/java/com/obdvis/android/ui/theme/Theme.kt`. Preserve public color names. Blue indicates selection/action, peach identifies speed, green/amber/red identify status with accompanying text. Chart signal identity remains owned by PidRegistry. Use named semantic colors, not screen-local literals. Portable exact sRGB-to-OKLCH conversions follow below.

## Typography

Native Android sans-serif body and headings, medium/semibold hierarchy, upright throughout. Native type is an intentional platform-fit exception to Hallmark's web display-font preference. Measurements use `AppType.numeric` with tabular digits. Scale: 12/13/14/16/20/24/28/48/64 sp. Body text has generous line height. Essential labels use SubText or DimText at full opacity. Fonts respect Android font scaling; containers grow rather than clip text.

## Spacing and shapes

`AppSpace`: 4, 8, 12, 16, 24, 32 dp; touch target 48 dp. Screen margins 16 dp; grouped section gaps 24 dp. Cards 12 dp, compact controls 8 dp, visible 1 dp separators. Do not outline every metric or place decorative cards inside cards.

## Motion and interaction

Native Material press, focus, selected and disabled states. No entrance animations, bouncing, or extra smoothing of real sensor data. Loading and error states remain explicit. Preserve DTC-clear and saved-drive-delete confirmations. Permission, connection, pause, demo, and missing values are clearly labeled. Use semantic clickable/selectable controls and accessible action labels. No color-only status meaning.

## CTA voice

Short verbs: Connect, Demo, Read DTCs, Clear DTCs, Save CSV, Back. Primary controls filled blue with dark text; secondary controls outlined or text; destructive actions use Danger. Keep primary navigation on one line, with a scrolling fallback for large font sizes.

## Shared and variable elements

Share the existing wordmark, app headers, typography, palette, shape and spacing. Vary density by task: strong speed/RPM hierarchy, evidence rows for diagnostics, aligned report metrics. Preserve screen APIs, polling callbacks, persistence, and chart behavior. Android Auto remains controlled by host templates.

## Responsive and accessibility checks

Preview 320/375/414/768 dp, landscape, and enlarged text. Use native dp/sp rather than CSS pixels. Avoid fixed text heights and use width-aware grouping. Actual render/device checks must be reported separately from source review or compilation.

## Implementation and verification

Version 0.1.13 (versionCode 14), completed 2026-09-30. The Live tab uses a sensor panel capped at 260 dp in portrait and 240 dp wide in landscape to accommodate larger controls. Overview uses a primary numeric speed reading and a compact RPM arc. Diagnostics and reports share semantic colors and typography.

See `docs/ui-redesign/README.md` for screenshots, verification results, native adaptations, and remaining device checks. The 78 snapshot renders are test fixtures, not live vehicle recordings.

## Exports

These exports are for reuse. Android consumes Kotlin tokens; no web runtime is introduced.

### tokens.css

```css
/* Hallmark · modern-minimal · instrument workbench · design-system: design.md */
:root {
  --color-paper: oklch(19.446% 0.01802 259.705);
  --color-paper-2: oklch(24.905% 0.02636 255.767);
  --color-paper-3: oklch(30.109% 0.03191 254.301);
  --color-rule: oklch(40.408% 0.03747 254.554);
  --color-control-outline: oklch(60.752% 0.03902 254.963);
  --color-accent: oklch(78.585% 0.10855 260.544);
  --color-accent-light: oklch(85.975% 0.06894 261.350);
  --color-accent-surface: oklch(36.138% 0.06649 257.201);
  --color-accent-ink: oklch(25.653% 0.05866 258.037);
  --color-ink: oklch(95.909% 0.00967 252.811);
  --color-ink-2: oklch(80.978% 0.02617 255.580);
  --color-muted: oklch(74.212% 0.03222 254.364);
  --color-success: oklch(82.338% 0.11092 163.327);
  --color-warning: oklch(85.249% 0.11335 69.090);
  --color-caution: oklch(87.067% 0.10559 96.826);
  --color-danger: oklch(79.459% 0.11988 13.697);
  --color-danger-surface: oklch(31.672% 0.04536 359.947);
  --color-speed: oklch(81.939% 0.10690 42.718);
  --color-electric: oklch(83.177% 0.07947 199.764);
  --color-focus: var(--color-accent);
  --font-display: sans-serif;
  --font-body: sans-serif;
  --font-outlier: monospace;
  --space-xxs: 4px;
  --space-xs: 8px;
  --space-sm: 12px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
  --space-touch: 48px;
  --text-xs: 12px;
  --text-sm: 13px;
  --text-md: 14px;
  --text-lg: 16px;
  --text-title: 20px;
  --text-xl: 24px;
  --text-reading: 28px;
  --text-display: 64px;
  --radius-card: 12px;
  --radius-input: 8px;
  --radius-pill: 8px;
  --rule-fine: 1px;
  --dur-short: 150ms;
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in: cubic-bezier(0.7, 0, 0.84, 0);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
}
```

### Tailwind v4

```css
@theme {
  --color-paper: oklch(19.446% 0.01802 259.705);
  --color-paper-2: oklch(24.905% 0.02636 255.767);
  --color-paper-3: oklch(30.109% 0.03191 254.301);
  --color-rule: oklch(40.408% 0.03747 254.554);
  --color-control-outline: oklch(60.752% 0.03902 254.963);
  --color-accent: oklch(78.585% 0.10855 260.544);
  --color-accent-light: oklch(85.975% 0.06894 261.350);
  --color-accent-surface: oklch(36.138% 0.06649 257.201);
  --color-accent-ink: oklch(25.653% 0.05866 258.037);
  --color-ink: oklch(95.909% 0.00967 252.811);
  --color-ink-2: oklch(80.978% 0.02617 255.580);
  --color-muted: oklch(74.212% 0.03222 254.364);
  --color-success: oklch(82.338% 0.11092 163.327);
  --color-warning: oklch(85.249% 0.11335 69.090);
  --color-caution: oklch(87.067% 0.10559 96.826);
  --color-danger: oklch(79.459% 0.11988 13.697);
  --color-danger-surface: oklch(31.672% 0.04536 359.947);
  --color-speed: oklch(81.939% 0.10690 42.718);
  --color-electric: oklch(83.177% 0.07947 199.764);
  --color-focus: var(--color-accent);
  --font-display: sans-serif;
  --font-body: sans-serif;
  --font-outlier: monospace;
  --spacing-xxs: 4px;
  --spacing-xs: 8px;
  --spacing-sm: 12px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  --spacing-touch: 48px;
  --text-xs: 12px;
  --text-sm: 13px;
  --text-md: 14px;
  --text-lg: 16px;
  --text-title: 20px;
  --text-xl: 24px;
  --text-reading: 28px;
  --text-display: 64px;
  --radius-card: 12px;
  --radius-input: 8px;
  --radius-pill: 8px;
  --rule-fine: 1px;
  --dur-short: 150ms;
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in: cubic-bezier(0.7, 0, 0.84, 0);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
}
```

### DTCG

```json
{
  "color": {
    "paper": {
      "$type": "color",
      "$value": "oklch(19.446% 0.01802 259.705)"
    },
    "paper-2": {
      "$type": "color",
      "$value": "oklch(24.905% 0.02636 255.767)"
    },
    "paper-3": {
      "$type": "color",
      "$value": "oklch(30.109% 0.03191 254.301)"
    },
    "rule": {
      "$type": "color",
      "$value": "oklch(40.408% 0.03747 254.554)"
    },
    "control-outline": {
      "$type": "color",
      "$value": "oklch(60.752% 0.03902 254.963)"
    },
    "accent": {
      "$type": "color",
      "$value": "oklch(78.585% 0.10855 260.544)"
    },
    "accent-light": {
      "$type": "color",
      "$value": "oklch(85.975% 0.06894 261.350)"
    },
    "accent-surface": {
      "$type": "color",
      "$value": "oklch(36.138% 0.06649 257.201)"
    },
    "accent-ink": {
      "$type": "color",
      "$value": "oklch(25.653% 0.05866 258.037)"
    },
    "ink": {
      "$type": "color",
      "$value": "oklch(95.909% 0.00967 252.811)"
    },
    "ink-2": {
      "$type": "color",
      "$value": "oklch(80.978% 0.02617 255.580)"
    },
    "muted": {
      "$type": "color",
      "$value": "oklch(74.212% 0.03222 254.364)"
    },
    "success": {
      "$type": "color",
      "$value": "oklch(82.338% 0.11092 163.327)"
    },
    "warning": {
      "$type": "color",
      "$value": "oklch(85.249% 0.11335 69.090)"
    },
    "caution": {
      "$type": "color",
      "$value": "oklch(87.067% 0.10559 96.826)"
    },
    "danger": {
      "$type": "color",
      "$value": "oklch(79.459% 0.11988 13.697)"
    },
    "danger-surface": {
      "$type": "color",
      "$value": "oklch(31.672% 0.04536 359.947)"
    },
    "speed": {
      "$type": "color",
      "$value": "oklch(81.939% 0.10690 42.718)"
    },
    "electric": {
      "$type": "color",
      "$value": "oklch(83.177% 0.07947 199.764)"
    }
  },
  "font": {
    "body": {
      "$type": "fontFamily",
      "$value": "sans-serif"
    }
  },
  "space": {
    "xxs": {
      "$type": "dimension",
      "$value": {
        "value": 4,
        "unit": "px"
      }
    },
    "xs": {
      "$type": "dimension",
      "$value": {
        "value": 8,
        "unit": "px"
      }
    },
    "sm": {
      "$type": "dimension",
      "$value": {
        "value": 12,
        "unit": "px"
      }
    },
    "md": {
      "$type": "dimension",
      "$value": {
        "value": 16,
        "unit": "px"
      }
    },
    "lg": {
      "$type": "dimension",
      "$value": {
        "value": 24,
        "unit": "px"
      }
    },
    "xl": {
      "$type": "dimension",
      "$value": {
        "value": 32,
        "unit": "px"
      }
    },
    "touch": {
      "$type": "dimension",
      "$value": {
        "value": 48,
        "unit": "px"
      }
    }
  }
}
```

### shadcn/ui CSS variables

```css
:root {
  --background: var(--color-paper);
  --foreground: var(--color-ink);
  --primary: var(--color-accent);
  --primary-foreground: var(--color-accent-ink);
  --muted: var(--color-paper-3);
  --muted-foreground: var(--color-ink-2);
  --border: var(--color-rule);
  --input: var(--color-rule);
  --ring: var(--color-focus);
  --radius: 8px;
}
```
