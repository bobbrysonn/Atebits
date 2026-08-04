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
| `screenshot` | Screen capture as PNG |
| `logcat(lines, grep)` / `logcat_clear` | App-process log dump / clear buffer |
| `webview_pages` | List WebView DevTools targets (URL + title) |
| `webview_eval(expression)` | Run JS in the login WebView, return the value |
| `webview_cdp(method, params_json)` | Raw Chrome DevTools Protocol command |
| `tap(x, y)` / `type_text(text)` | Basic input injection |

## Requirements

- `adb` on PATH with the device authorized for USB debugging
- `uv` on PATH
- Debug build of the app (WebView remote debugging is enabled in debug builds
  via `WebView.setWebContentsDebuggingEnabled` in `MainActivity`)

Env overrides: `ATEBITS_PACKAGE` (default `dev.bobbrysonn.atebits`),
`ATEBITS_DEVTOOLS_PORT` (default `9222`).
