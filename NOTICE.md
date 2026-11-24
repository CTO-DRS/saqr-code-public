# Third-party notices

Saqr Code's own source is © 2026 ctodrs. It bundles or downloads the following:

| Component | License | How it is used | Source |
|---|---|---|---|
| PRoot 5.1.107 (Termux build) → `libproot.so`, `libproot-loader.so` | GPL-2.0 | Separate executable launched as a process (not linked into the app) | https://github.com/termux/proot |
| talloc 2.5.0 → `libtalloc.so` | LGPL-3.0 | Shared library loaded by proot | https://www.samba.org/ftp/talloc/ |
| libandroid-shmem 0.7 → `libandroid-shmem.so` | BSD-3-Clause | Shared library loaded by proot | https://github.com/termux/libandroid-shmem |
| Alpine Linux minirootfs 3.24.4 | Various (mostly GPL/MIT) | Downloaded at runtime from dl-cdn.alpinelinux.org, not shipped in the APK | https://alpinelinux.org |
| IBM Plex Sans Arabic | SIL OFL 1.1 | Bundled font | https://github.com/IBM/plex |
| Eclipse JGit 5.13 | EDL 1.0 (BSD-3) | Library | https://www.eclipse.org/jgit/ |
| OkHttp 4.12 | Apache-2.0 | Library | https://square.github.io/okhttp/ |
| AndroidX / Jetpack Compose / Material 3 / Security-Crypto | Apache-2.0 | Libraries | https://developer.android.com/jetpack |
| Kotlin, kotlinx.coroutines | Apache-2.0 | Language/runtime | https://kotlinlang.org |
| Google Play Billing Library 8.0.0 (billing-ktx) | Apache-2.0 | Library (optional purchases; products `saqr_pro_monthly`, `saqr_student_monthly`, `saqr_lifetime`) | https://developer.android.com/google/play/billing |

### Licensing model (v0.11) · نموذج الترخيص

- Paid plans are enforced with two channels: **Google Play Billing** (the strong, tamper-proof
  channel — Play's own purchase state is the proof) and **offline license tokens** (`SQ1.…`,
  HMAC-SHA256 over a small JSON payload) for the Moyasar/manual path.
- The offline token key ships in the APK as `BuildConfig.SAQR_LICENSE_KEY`. The **public fallback
  key is intentionally non-secret** (dev builds); production builds take the real key from
  `keystore.properties` → `licenseKey`, which stays in the private repository. Offline tokens are
  convenience licensing, not cryptographic enforcement — the app's source is open, and anyone who
  patches the app can bypass any client-side check. That is accepted: Play Billing is the
  enforcement channel, tokens exist so merchants can sell through Moyasar (mada / Apple Pay /
  STC Pay) without a backend.
- The Moyasar **secret key** is only ever entered by the merchant on their own device
  (Settings → Billing → merchant tools, stored in SecureStore) and never ships in the app.
  End users only ever see the public hosted payment page URL.

The proot binaries were taken unmodified from the Termux apt repository (aarch64), except that `patchelf` was used to rename the `libtalloc.so.2` dependency to `libtalloc.so` and drop the Termux RUNPATH so Android's package installer extracts them. Per GPL-2.0 §3, the corresponding source is available at the URL above (tag matching 5.1.107).

### Added in v0.2.0
| Component | License | How it is used | Source |
|---|---|---|---|
| JetBrains Mono 2.304 (NL variant: Regular/Medium/Bold) | SIL OFL 1.1 | Bundled font for code, paths and numbers | https://github.com/JetBrains/JetBrainsMono |
| IBM Plex Sans Arabic SemiBold | SIL OFL 1.1 | Bundled font weight | https://github.com/IBM/plex |
| Lucide icons 0.460 (path data, converted to Compose `ImageVector`s in `ui/icons/Lucide.kt`) | ISC | UI icon set | https://lucide.dev |
| Robolectric 4.18 + Compose UI test | MIT / Apache-2.0 | Test-only (JVM screenshot rendering), not shipped | https://robolectric.org |

File-type glyphs, provider monograms, the falcon app icon and all illustrations are original artwork for Saqr Code. No PolyDev assets or code are used.
