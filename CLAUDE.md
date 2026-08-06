# Atebits

A native Android client for X/Twitter (Jetpack Compose, Material 3), built for
feature parity with the official client — starting with its scroll smoothness.

## Scroll performance rules — a tale of caution

The timeline once stuttered at 2.89% janky frames with 48ms frame spikes while
the native client scrolled like butter. The causes were five ordinary-looking
patterns that each felt harmless when written. Fixing them (Aug 2026) got a
release build to 0.17% janky with p95 inside the 120Hz frame budget. Do not
let them creep back:

1. **Never build or release an ExoPlayer inside a list item.** Every
   player allocates hardware AVC/AAC codecs and surfaces; doing that as items
   cross the viewport made logcat wall-to-wall codec churn and was the single
   biggest jank source. Acquire/release leases from `VideoPlayerPool` (slots
   are rebound, codecs reused) and gate *creation* on scroll settle via
   `LocalListScrollInProgress` — new list-bearing screens must provide it from
   their `LazyListState`. The pool's mediaId binding is also the
   inline↔fullscreen handoff: drop the lease without releasing to hand off.

2. **Every lazy list passes `key` and `contentType`.** Without keys, a
   refresh prepend shifts all item state (expansion, video visibility) onto
   the wrong tweets, the viewport jumps, and slot reuse degrades. Keys come
   from `UiTweet.id`; fixed slots use non-numeric string keys.

3. **Never load a raw `media_url_https` into a card.** That's a ~2048px
   decode for a ~900px slot. Cards use `previewUrl("medium")` (timeline) or
   `"small"` (quoted/threaded/posters); only fullscreen viewers take
   `fullSizeUrl()` — pass it through the click handler. Row avatars use
   `smallAvatarUrl()` (`_bigger`, 73px), never `_400x400`.

4. **Row composables read, they don't compute.** Date parsing, display-text
   assembly (codepoint slicing, t.co swaps, unescapes), and count formatting
   happen once in `TweetResult.toUi()` at ingestion. UI code takes
   `UiTweet` (stable, `@Immutable`) — never raw `TweetResult`. If a row needs
   a new derived value, add a `UiTweet` field and compute it in the mapper.

5. **Judge smoothness only on a release build.** Debug measured ~10x worse
   on identical code (no R8, no AOT, Compose debug checks). Protocol:
   `gfxinfo(reset=True)` → ~10 timeline flings → `gfxinfo()` (MCP tool), watch
   janky % and p95/p99 against the 8.3ms/120Hz budget. After touching models
   or the network layer, smoke-test `assembleRelease` on device — R8 breaks
   kotlinx-serialization *silently* (empty timeline, no crash).

Supporting facts:

- The baseline profile is committed at
  `app/src/release/generated/baselineProfiles/` and embeds in every release
  build. Regenerate after major hot-path changes with
  `./gradlew :app:generateReleaseBaselineProfile` — the run **logs the device
  out** (instrumentation uninstall drops app data), so log in first and again
  after; a logged-out run silently produces a login-screen profile.
- Release signing: `keystore.properties` + `release.keystore` at the repo root
  (gitignored; also stored in GitHub Actions secrets — KEYSTORE_BASE64 etc.).
  Without them, release falls back to debug signing so any checkout builds.
  Losing the keystore means a new app identity for every install — keep it
  backed up.
- Releases ship by pushing a `v*` tag: the Release workflow builds the signed
  APK (versionName from the tag, versionCode from the run number) and attaches
  it to a GitHub Release; a `-` in the tag (v0.5.0-beta1) marks it pre-release.
- MCP debug-server screenshots are downscaled vs the device's 1080x2404 input
  space — scale tap coordinates up by ~1.2x or small targets (tab rows) miss.
