"""Exercise link and image long presses on a connected Terra test emulator.

    python android/tests/page_context_menu.py emulator-5554

Uses a temporary loopback fixture. Opens test tabs, downloads one small image,
and restarts Terra to verify recently closed tab recovery.
"""
import argparse
import base64
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import xml.etree.ElementTree as ET


PIXEL = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=")
IMAGE_NAME = f"terra-menu-{time.time_ns()}.png"
HTML = f"""<meta name="viewport" content="width=device-width"><title>Context menu fixture</title>
<style>img {{width:100px;height:70px;background:#346;display:block}} a {{display:block;margin:24px}}</style>
<a href="/destination">Text target</a>
<a href="/destination"><img alt="Linked image target" src="/{IMAGE_NAME}"></a>
<img alt="Standalone image target" src="/{IMAGE_NAME}">
<a href="/middle">Go to middle</a>
<a href="#bottom">Jump down</a><div style="height:160vh"></div>
<a id="bottom" href="/destination">Lower target</a>""".encode()


class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        responses = {
            "/": ("text/html", HTML),
            "/destination": ("text/html", b"<title>Linked destination</title><h1>Reached link destination</h1>"),
            "/middle": ("text/html", b"<meta name=viewport content='width=device-width'><title>Middle page</title><a href='/destination'>Go to last</a>"),
            "/" + IMAGE_NAME: ("image/png", PIXEL),
        }
        if self.path not in responses:
            self.send_error(404)
            return
        mime, body = responses[self.path]
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    serial = parser.parse_args().serial
    sdk = Path(os.environ.get("ANDROID_HOME", ""), "platform-tools")
    executable = shutil.which("adb") or str(sdk / ("adb.exe" if os.name == "nt" else "adb"))

    def adb(*args):
        return subprocess.check_output([executable, "-s", serial, *args], timeout=20)

    def point(label, *, prefix=False):
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            out = adb("shell", "uiautomator", "dump", "/sdcard/terra-menu-test.xml")
            if b"dumped to" in out:
                for node in ET.fromstring(adb("exec-out", "cat", "/sdcard/terra-menu-test.xml")).iter("node"):
                    values = (node.get("text", ""), node.get("content-desc", ""))
                    if label in values or (prefix and any(value.startswith(label) for value in values)):
                        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                        return str((x1 + x2) // 2), str((y1 + y2) // 2)
        raise AssertionError(f"Could not find {label}")

    def hold(label, *, prefix=False):
        x, y = point(label, prefix=prefix)
        adb("shell", "input", "swipe", x, y, x, y, "750")

    def tap(label):
        adb("shell", "input", "tap", *point(label))

    with ThreadingHTTPServer(("127.0.0.1", 0), Fixture) as server:
        port = server.server_port
        threading.Thread(target=server.serve_forever, daemon=True).start()
        adb("reverse", f"tcp:{port}", f"tcp:{port}")
        try:
            def open_fixture():
                adb("shell", "am", "start", "-n", "app.terra.browser/.MainActivity",
                    "-a", "android.intent.action.VIEW", "-d", f"http://127.0.0.1:{port}/")

            open_fixture()
            point("Text target")
            hold("标签页，", prefix=True)
            point("关闭标签页")
            open_fixture()
            hold("Text target")
            print("PASS opening an external link dismisses the previous native menu", flush=True)
            tap("在新标签中打开")
            point("Reached link destination")
            print("PASS text link", flush=True)

            open_fixture()
            hold("Linked image target")
            point("在新标签中查看图片")
            tap("在新标签中打开")
            point("Reached link destination")
            print("PASS linked image opens anchor href", flush=True)

            for label in ("Linked image target", "Standalone image target"):
                open_fixture()
                hold(label)
                tap("下载图片")
                point(IMAGE_NAME)
                if label == "Linked image target":
                    tap("取消")
                else:
                    tap("保存到下载目录")
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                if adb("exec-out", "cat", "/sdcard/Download/" + IMAGE_NAME) == PIXEL:
                    print("PASS linked and standalone image target; image download bytes match", flush=True)
                    break
                time.sleep(0.3)
            else:
                raise AssertionError("Downloaded file did not match the fixture image")
            tap("Go to middle")
            tap("Go to last")
            point("Reached link destination")
            hold("后退")
            point("Middle page")
            tap("Context menu fixture")
            point("Go to middle")
            hold("前进")
            point("Middle page")
            tap("Linked destination")
            point("Reached link destination")
            tap("后退")
            point("Go to last")
            tap("前进")
            point("Reached link destination")
            print("PASS long press jumps two history entries in both directions; single tap moves one", flush=True)
            hold("标签页，", prefix=True)
            tap("新建无痕标签页")
            point("无痕浏览")
            hold("标签页，", prefix=True)
            tap("关闭标签页")
            point("Reached link destination")
            hold("标签页，", prefix=True)
            tap("新建标签页")
            point("起始页")
            print("PASS tab menu creates regular and private tabs; closing returns to previous page", flush=True)
            open_fixture()
            tap("Go to middle")
            point("Go to last")
            hold("标签页，", prefix=True)
            tap("关闭标签页")
            point("起始页")
            adb("shell", "am", "force-stop", "app.terra.browser")
            adb("shell", "am", "start", "-n", "app.terra.browser/.MainActivity")
            hold("标签页，", prefix=True)
            tap("最近关闭")
            tap("Middle page")
            point("Go to last")
            print("PASS recently closed tab survives restart and reopens its final URL", flush=True)
            open_fixture()
            tap("Jump down")
            original_position = point("Lower target")
            hold("Lower target")
            tap("在后台打开")
            assert point("Lower target") == original_position, "Source page moved"
            hold("标签页，", prefix=True)
            tap("关闭标签页")
            point("Reached link destination")
            print("PASS background tab preserves scroll position and opens the selected destination", flush=True)
        finally:
            adb("reverse", "--remove", f"tcp:{port}")
            server.shutdown()


if __name__ == "__main__":
    main()
