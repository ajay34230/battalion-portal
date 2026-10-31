# Design system

App tokens (`ui/Theme.kt`): background #040A14, cyan #00E5FF, blue #2979FF, violet #8E5CFF, mint #3DFFC0, text #E6F3FF, muted #8FA8C4; glass cards (`Modifier.glass`), 18–28 dp radii, shared components in `ui/Components.kt` (ScreenFrame, InfoCard, Pill, SectionLabel, EmptyState).
Per-event colours: bill orange, birthday pink, anniversary violet, medicine mint, reminder cyan.
Generated websites: templates define CSS custom properties for colour, spacing, radius and a `prefers-color-scheme` dark mode (dashboard template). Icon system, dialogs/menus tokens, elevation scale: not formalised.
