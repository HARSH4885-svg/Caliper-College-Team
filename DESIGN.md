# CodeSentinel design system ("Signal")

Operate-mode product UI for instructors and students. Tagline (pending approval): "Every submission, read fairly."

## Colour
- Ground `#f3f5f8`; ink (harbour navy) `#0a1f3c`; signal blue `#1b4fe0` / `#1238a8`; signal yellow `#f2b705`; teal `#0e8f7e`; violet for course banners.
- Red is reserved for real errors. Similarity states never use red; they use a three-tick "watch level".

## Type
- Display: Bricolage Grotesque. Body: Hanken Grotesk. Code: JetBrains Mono. All self-hosted via `@fontsource-variable/*` (CSP allows `font-src 'self' data:`).

## Brand mark
- A swallow-tail pennant on a mast with a lookout lens at the hoist: the same pennant shape the verdicts use, so mark and product speak one language. Signal yellow flag, navy lens with a blue iris; mast white on dark, navy on light. Favicon is the same mark on a navy tile.
- Home masthead: a string of signal pennants (bunting) across a navy band.

## Signature elements
- Course banners: deterministic signal-flag SVGs (9 geometries x 6 palettes) seeded from the course.
- Verdicts: swallow-tail pennants; the result page unfurls a large pennant (disabled under `prefers-reduced-motion`).
- Top bar carries a 4px three-colour flag stripe.

## Layout
- Classroom-style: class cards with banners; "To do" (student) and "Review desk" (instructor) side panels; course page with Classwork / People / Manage tabs; assignment page with a sticky "Your work" panel; mobile bottom tab bar.

## Rules kept
- No gradient text, no eyebrow kickers above headings, no emoji icons, no side-stripe accents, no hero-metric template.
- Wording: similarity is "an investigation signal, not a finding"; only the instructor records a decision.

## Themes
- Light (default) and dark, switchable from the top bar, remembered per browser, follows the OS setting until chosen.

## Not done
- No automated accessibility audit (axe/Lighthouse) was run; contrast and keyboard order were checked by eye only.
