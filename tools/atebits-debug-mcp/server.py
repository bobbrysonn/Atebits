# /// script
# requires-python = ">=3.11"
# dependencies = ["mcp>=1.2.0", "websockets>=13.0"]
# ///
"""MCP server exposing adb + WebView DevTools debugging for the Atebits app.

Registered in the repo's .mcp.json; Claude Code launches it with
`uv run --script tools/atebits-debug-mcp/server.py`.
"""

import asyncio
import json
import os
import subprocess
import urllib.request

from mcp.server import MCPServer
from mcp.server.mcpserver import Image

PACKAGE = os.environ.get("ATEBITS_PACKAGE", "dev.bobbrysonn.atebits")
MAIN_ACTIVITY = f"{PACKAGE}/.MainActivity"
DEVTOOLS_PORT = int(os.environ.get("ATEBITS_DEVTOOLS_PORT", "9222"))

mcp = MCPServer("atebits-debug")


def _run(*cmd: str, timeout: int = 30) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    out = (result.stdout + result.stderr).strip()
    if result.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed ({result.returncode}): {out[:500]}")
    return out


def _physical_id(serial: str) -> str:
    # Wireless-debugging entries embed the USB serial: adb-<serial>-<suffix>._adb-tls-connect._tcp
    if serial.startswith("adb-"):
        return serial.removeprefix("adb-").split("._", 1)[0].rsplit("-", 1)[0]
    return serial


def _serial() -> str:
    """Pick the target device, tolerating the same phone being connected over
    both USB and wireless debugging at once (adb lists it twice)."""
    if override := os.environ.get("ATEBITS_SERIAL"):
        return override
    serials = [
        line.split()[0]
        for line in _run("adb", "devices").splitlines()[1:]
        if len(line.split()) >= 2 and line.split()[1] == "device"
    ]
    if not serials:
        raise RuntimeError("no adb device connected (or unauthorized)")
    if len(serials) == 1:
        return serials[0]
    if len({_physical_id(s) for s in serials}) == 1:
        # One physical device, multiple transports: prefer USB (faster screencap).
        return next((s for s in serials if not s.startswith("adb-")), serials[0])
    raise RuntimeError(
        f"multiple devices connected: {serials}; set ATEBITS_SERIAL to pick one"
    )


def _adb(*args: str, timeout: int = 30) -> str:
    return _run("adb", "-s", _serial(), *args, timeout=timeout)


def _pid() -> str:
    return _adb("shell", "pidof", "-s", PACKAGE).strip()


def _forward_devtools() -> None:
    _adb("forward", f"tcp:{DEVTOOLS_PORT}", f"localabstract:webview_devtools_remote_{_pid()}")


def _devtools_targets() -> list[dict]:
    _forward_devtools()
    with urllib.request.urlopen(f"http://127.0.0.1:{DEVTOOLS_PORT}/json", timeout=10) as resp:
        return json.load(resp)


async def _cdp(method: str, params: dict) -> dict:
    import websockets

    targets = _devtools_targets()
    page = next((t for t in targets if t["type"] == "page"), None)
    if page is None:
        raise RuntimeError(f"No WebView page target; targets: {[t['type'] for t in targets]}")
    async with websockets.connect(page["webSocketDebuggerUrl"], max_size=50_000_000) as ws:
        await ws.send(json.dumps({"id": 1, "method": method, "params": params}))
        while True:
            msg = json.loads(await ws.recv())
            if msg.get("id") == 1:
                if "error" in msg:
                    raise RuntimeError(f"CDP error: {msg['error']}")
                return msg.get("result", {})


@mcp.tool()
def devices() -> str:
    """List connected adb devices."""
    return _run("adb", "devices", "-l")


@mcp.tool()
def launch_app(clear_state: bool = False) -> str:
    """Force-stop and relaunch the Atebits app. clear_state=True also wipes app data
    (SharedPreferences session, cookies) for a from-scratch login test."""
    _adb("shell", "am", "force-stop", PACKAGE)
    if clear_state:
        _adb("shell", "pm", "clear", PACKAGE)
    return _adb("shell", "am", "start", "-n", MAIN_ACTIVITY)


@mcp.tool()
def install_and_launch() -> str:
    """Build + install the debug APK via Gradle, then relaunch the app."""
    repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    result = subprocess.run(
        ["./gradlew", ":app:installDebug", "-q"],
        cwd=repo_root, capture_output=True, text=True, timeout=600,
        # AGP respects ANDROID_SERIAL, so installDebug targets the same device
        env={**os.environ, "ANDROID_SERIAL": _serial()},
    )
    if result.returncode != 0:
        return f"BUILD FAILED:\n{(result.stdout + result.stderr)[-3000:]}"
    return launch_app()


@mcp.tool()
def screenshot() -> Image:
    """Capture the device screen as a PNG."""
    result = subprocess.run(
        ["adb", "-s", _serial(), "exec-out", "screencap", "-p"],
        capture_output=True, timeout=30,
    )
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError(f"screencap failed: {result.stderr.decode()[:300]}")
    return Image(data=result.stdout, format="png")


@mcp.tool()
def logcat(lines: int = 150, grep: str = "") -> str:
    """Dump recent logcat output for the app's process. Optional case-insensitive
    substring filter via `grep`."""
    out = _adb("logcat", "-d", f"--pid={_pid()}")
    rows = out.splitlines()
    if grep:
        rows = [r for r in rows if grep.lower() in r.lower()]
    return "\n".join(rows[-lines:]) or "(no matching log lines)"


@mcp.tool()
def logcat_clear() -> str:
    """Clear the logcat buffer (useful before reproducing an issue)."""
    _adb("logcat", "-c")
    return "cleared"


@mcp.tool()
def webview_pages() -> str:
    """List the app's WebView DevTools targets (page URL + title). Requires a debug
    build with WebView.setWebContentsDebuggingEnabled(true)."""
    targets = _devtools_targets()
    return json.dumps(
        [{"type": t["type"], "title": t.get("title", ""), "url": t.get("url", "")} for t in targets],
        indent=1,
    )


@mcp.tool()
async def webview_eval(expression: str) -> str:
    """Evaluate a JavaScript expression in the app's WebView page and return the
    result. Objects are JSON-serialized (wrap in JSON.stringify for complex data)."""
    result = await _cdp("Runtime.evaluate", {"expression": expression, "returnByValue": True})
    inner = result.get("result", {})
    if "value" in inner:
        value = inner["value"]
        return value if isinstance(value, str) else json.dumps(value, indent=1, ensure_ascii=False)
    if result.get("exceptionDetails"):
        return f"JS exception: {json.dumps(result['exceptionDetails'])[:1000]}"
    return json.dumps(inner)[:2000]


@mcp.tool()
async def webview_cdp(method: str, params_json: str = "{}") -> str:
    """Send a raw Chrome DevTools Protocol command to the WebView page target,
    e.g. method='Page.getLayoutMetrics' or 'Network.clearBrowserCookies'."""
    result = await _cdp(method, json.loads(params_json))
    return json.dumps(result, indent=1)[:8000]


@mcp.tool()
def tap(x: int, y: int) -> str:
    """Tap the device screen at physical pixel coordinates (screenshot scale)."""
    _adb("shell", "input", "tap", str(x), str(y))
    return f"tapped {x},{y}"


@mcp.tool()
def type_text(text: str) -> str:
    """Type text into the focused field on the device."""
    _adb("shell", "input", "text", text.replace(" ", "%s"))
    return "typed"


@mcp.tool()
def long_press(x: int, y: int, duration_ms: int = 600) -> str:
    """Long-press the screen at physical pixel coordinates (screenshot scale)."""
    _adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), str(duration_ms))
    return f"long-pressed {x},{y} for {duration_ms}ms"


@mcp.tool()
def swipe(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 300) -> str:
    """Swipe/drag between physical pixel coordinates (screenshot scale). Covers
    scrolling: e.g. swipe from lower to upper screen to scroll a list down."""
    _adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), str(duration_ms))
    return f"swiped {x1},{y1} -> {x2},{y2}"


@mcp.tool()
def scroll(direction: str = "down", amount: float = 0.6) -> str:
    """Scroll the screen content. direction is up/down/left/right (down means
    reveal content further down the page). amount is the fraction of the screen
    to travel (0-1)."""
    size = _adb("shell", "wm", "size").rsplit(" ", 1)[-1]  # e.g. "1080x2404"
    w, h = (int(v) for v in size.split("x"))
    amount = min(amount, 0.8)  # keep swipe endpoints away from screen-edge gestures
    cx, cy = w // 2, h // 2
    dx, dy = 0, 0
    if direction == "down":
        dy = -int(h * amount)
    elif direction == "up":
        dy = int(h * amount)
    elif direction == "left":
        dx = -int(w * amount)
    elif direction == "right":
        dx = int(w * amount)
    else:
        raise ValueError(f"unknown direction {direction!r}; use up/down/left/right")
    x1, y1 = cx - dx // 2, cy - dy // 2
    x2, y2 = cx + dx // 2, cy + dy // 2
    _adb("shell", "input", "swipe", str(x1), str(y1), str(x2), str(y2), "300")
    return f"scrolled {direction} by {amount} of screen"


@mcp.tool()
def key(name: str) -> str:
    """Press a hardware/navigation key. Common names: BACK, HOME, ENTER, TAB,
    DEL, APP_SWITCH, DPAD_UP/DOWN/LEFT/RIGHT, VOLUME_UP/DOWN, POWER, WAKEUP.
    Any android KEYCODE_* suffix works."""
    _adb("shell", "input", "keyevent", f"KEYCODE_{name.upper().removeprefix('KEYCODE_')}")
    return f"pressed {name.upper()}"


if __name__ == "__main__":
    mcp.run()
