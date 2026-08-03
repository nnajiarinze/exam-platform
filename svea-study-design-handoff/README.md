# Svea Study — Stitch Design Handoff

This package consolidates the latest Stitch-generated design references for the existing Svea Study mobile application.

## Source of truth

- `design-system/SVEA_STUDY_DESIGN_SYSTEM.md` — visual tokens and design rules.
- `screens/*/reference.png` — visual target for each screen.
- `screens/*/reference.html` — Stitch HTML/CSS reference. This is not production React Native code; use it to extract structure, spacing, typography, colors, and component intent.
- `docs/CODEX_IMPLEMENTATION_PROMPT.md` — implementation prompt for Codex.
- `docs/SCREEN_MAPPING.md` — mapping between design references and likely app flows.

## Screen order

1. Home dashboard
2. Study topics
3. Practice question
4. Mock exam setup
5. Results summary
6. Progress analytics

The `99-progress-analytics-original-superseded` folder is retained only for traceability. Implement the fixed version in `06-progress-analytics`.

## Important constraints

- Preserve the existing backend contracts, navigation, authentication, progress tracking, practice flows, mock-exam flows, localization behavior, and state management.
- Rebuild the visual layer using native React Native components and the project's existing libraries.
- Do not copy Tailwind CDN, browser-only CSS, Material Symbols web fonts, or HTML directly into the app.
- Treat screenshots as visual guidance, not pixel-perfect requirements where they conflict with accessibility, device sizes, safe areas, localization, or existing functionality.
