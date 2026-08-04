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


if __name__ == "__main__":
    mcp.run()
