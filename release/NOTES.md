## FluidKey v1.1.0: frosted glass

**A full visual redesign of the keyboard and the app.**

- **Frosted-glass keys:**
  - Translucent caps with an inner sheen, light hairline edges and soft shadows, over an aurora
    backdrop.
  - Pressed keys sink, brighten and spring back.
  - Glass preview bubbles, and an animated selection pill in long-press popups.
- **Glass toolbar and panels:** the active panel's chip is highlighted, panels fade and slide in,
  the emoji bar has a sliding category indicator, and clipboard items are a card grid.
- **Re-tuned themes:** Midnight, Daylight, AMOLED (still pure black), Ocean, Forest, Sunset,
  Lavender, Rose, plus Material You.
- **A new app** (Jetpack Compose, Material 3):
  - guided setup;
  - a **live preview keyboard** you can type on, which reflects your settings instantly;
  - a visual theme carousel;
  - grouped settings cards.
- Your existing settings carry over, and the update installs over v1.0.x.

---

The first release of FluidKey: an Android keyboard with **iOS-style adaptive hit targets**. It predicts the next letter and quietly enlarges that letter's touch area, and anchored key cores keep every key reachable.

**Install:** download `FluidKey-v1.1.0.apk`, open it on your phone, then open FluidKey and tap **Enable**, then **Select**. Requires Android 8.0+.

### Highlights
- Adaptive hit targets: a Bayesian spatial and language model with anchored targets, on-device learning of your aim, adjustable strength, and a hit-map debug view
- Themes: Follow system, AMOLED black, Light, Dark, Material You, Ocean, Forest, Sunset, Lavender, Rose; optional borderless keys
- Keyboard height 75–130% and one-handed mode
- Number row toggle; long-press symbols with an adjustable delay (100–800 ms)
- Clipboard history with pinning and a quick-paste chip
- Hold-space cursor seek; swipe-left-on-backspace word delete
- Emoji panel with recents

See [docs/ADAPTIVE_TOUCH.md](https://github.com/darshan260802/fluid-key/blob/HEAD/docs/ADAPTIVE_TOUCH.md) for how it works.

The APK is signed with the repository's public sideload key (`keystore/fluidkey-release.jks`), so future builds install as updates.
