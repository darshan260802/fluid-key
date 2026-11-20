## FluidKey v1.0.0

The first release of FluidKey: an Android keyboard with **iOS-style adaptive hit targets**. It predicts the next letter and quietly enlarges that letter's touch area, and anchored key cores keep every key reachable.

**Install:** download `FluidKey-v1.0.0.apk`, open it on your phone, then open FluidKey and follow **1 · Enable** → **2 · Select**. Requires Android 8.0+.

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
