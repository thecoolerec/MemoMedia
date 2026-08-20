<!-- Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V4 -->
# Design — MemoMedia

MemoMedia is a local Android photo organizer. This document is the visual and
interaction source of truth for every app screen; Compose theme tokens in
`app/src/main/java/com/example/ui/theme/` are the executable implementation.

## Genre

Modern-minimal, with a utilitarian content-first tone.

## Product hierarchy

- Photos: compact app bar, lightweight filters, date-grouped edge-to-edge grid.
- Organize: source-group workbench; one clear classification action per group.
- Albums: cover-led two-column grid; rules are a secondary tab, not a peer page.
- Settings: native grouped rows with restrained surfaces and clear state labels.

## Theme

- Background: near-white neutral; near-black in dark mode.
- Surface: white/light neutral layers with no decorative elevation.
- Accent: one MemoMedia blue, reserved for selection and primary actions.
- Category colours: small icon, badge, or status accents only; never page-sized fills.
- Dividers: subtle semantic outline tokens; avoid shadows as section separators.

## Typography

- Android system font for reliable OEM rendering and Chinese glyph coverage.
- App-bar title: 19sp semibold.
- Section title: 16sp semibold.
- Body: 15–16sp regular.
- Supporting metadata: 12–13sp, never below 11sp.

## Spacing and shape

- 4dp base rhythm; primary spacing steps are 8, 12, 16, 24, and 32dp.
- Standard screen gutter: 16dp. Media-grid gap: 2dp.
- Cards: 14–16dp radius. Controls: 10–12dp radius.
- Every touch target is at least 48dp where layout permits; compact chips remain
  Material components with their built-in minimum target semantics.

## Navigation

- Four stable destinations: Photos, Organize, Albums/Categories, Settings.
- Bottom navigation is a compact, solid system-safe surface with zero shadow,
  no top divider, and no floating or tonal indicator capsule.
- The selected destination uses the accent colour, a filled icon, and medium
  label weight; unselected destinations use outline icons and quieter labels.
- Root Scaffold owns bottom safe-area spacing. Child screens must not add a
  second navigation-bar inset or a hard-coded 96dp bottom workaround.

## Motion and feedback

- Use platform ripple/pressed feedback and 150–220ms state transitions.
- Animate opacity or content removal only; never move surrounding layout on press.
- Classification is optimistic and confirmed with a concise snackbar.
- Loading longer than 300ms shows an inline progress indicator or skeleton.

## Accessibility

- Interactive targets: 48x48dp on Android.
- Body contrast: at least 4.5:1; large glyphs and controls: at least 3:1.
- Icon-only actions always have content descriptions.
- Layout must remain usable with large system font and in landscape.

## Anti-patterns

- No oversized iOS-style large titles or duplicated platform chrome.
- No floating bottom pill, decorative tab shadow, glass blur, or gradient wash.
- No wall of cards when a divider or whitespace establishes hierarchy.
- No invented product metrics, fake AI features, or ornamental category colours.
