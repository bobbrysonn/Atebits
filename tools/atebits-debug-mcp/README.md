# atebits-debug MCP server

Project-scoped MCP server that lets Claude Code debug the Atebits app on a
device connected via adb. Registered in the repo's `.mcp.json`; launched
automatically by Claude Code with `uv run --script` (dependencies are inline,
nothing to install).

## Tools

| Tool | Purpose |
|---|---|
| `devices` | List connected adb devices |
| `launch_app(clear_state)` | Force-stop + relaunch; optionally wipe app data |
| `install_and_launch` | `gradlew :app:installDebug` then relaunch |
| `screenshot(delay_ms)` | Screen capture as PNG, optionally after a settle delay |
| `tap_and_screenshot(x, y, delay_ms)` | Tap, wait, capture — tight timing for transition checks |
| `logcat(lines, grep)` / `logcat_clear` | App-process log dump / clear buffer |
| `webview_pages` | List WebView DevTools targets (URL + title) |
| `webview_eval(expression)` | Run JS in the login WebView, return the value |
| `webview_cdp(method, params_json)` | Raw Chrome DevTools Protocol command |
| `tap(x, y)` / `long_press(x, y, duration_ms)` | Touch injection |
| `swipe(x1, y1, x2, y2, duration_ms)` | Swipe/drag between points |
| `scroll(direction, amount)` | Scroll up/down/left/right by screen fraction |
| `type_text(text)` / `key(name)` | Keyboard input / hardware keys (BACK, ENTER, …) |

## Requirements

- `adb` on PATH with the device authorized for USB debugging
- `uv` on PATH
- Debug build of the app (WebView remote debugging is enabled in debug builds
  via `WebView.setWebContentsDebuggingEnabled` in `MainActivity`)

Env overrides: `ATEBITS_PACKAGE` (default `dev.bobbrysonn.atebits`),
`ATEBITS_DEVTOOLS_PORT` (default `9222`), `ATEBITS_SERIAL` (pin a device).

## Device selection

All adb calls pin `-s <serial>`, resolved per call: if one device is attached,
it's used; if the same phone is attached over both USB and wireless debugging
(adb lists it twice), the USB transport is preferred; genuinely different
devices raise an error asking for `ATEBITS_SERIAL`.
