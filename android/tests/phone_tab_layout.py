"""Verify the three iPhone-style toolbar layouts using the public controls of an Android test device."""

import argparse
import os
import re
import shutil
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
from pathlib import Path
from threading import Thread
from xml.etree import ElementTree

from PIL import Image


class Pages(BaseHTTPRequestHandler):
    def do_GET(self):
        light = self.path.startswith("/lighthouse")
        title = "Lighthouse" if light else "Harbor notebook"
        color = "#d39266" if light else "#39788a"
        other = "" if light else "<a href='/lighthouse'>Open lighthouse</a>"
        data = f"""<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
        <title>{title}</title><style>
        *{{box-sizing:border-box}}body{{margin:0;font:18px system-ui;color:white;background:{color}}}
        section{{min-height:106vh;padding:8vh 8vw}}h1{{font:600 32px Georgia,serif}}a{{color:white}}
        </style><section><h1>{title}</h1>{other}</section>
        <section style='background:#6d8266'><h1>Tide tables</h1></section>""".encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


layouts = {"compact": "紧凑", "bottom": "底部", "top": "顶部"}


class Device:
    def __init__(self, serial, folder):
        sdk = Path(os.environ.get("ANDROID_HOME", "."))
        binary = shutil.which("adb") or sdk / "platform-tools" / (
            "adb.exe" if os.name == "nt" else "adb"
        )
        self.adb = [str(binary), "-s", serial]
        self.folder = folder
        if folder:
            folder.mkdir(parents=True, exist_ok=True)
        self.w, self.h = map(
            int, re.findall(r"(\d+)x(\d+)", self.shell("wm", "size").decode())[-1]
        )
        self.density = (
            int(re.findall(r"\d+", self.shell("wm", "density").decode())[-1]) / 160
        )

    def run(self, *args):
        return subprocess.check_output(self.adb + list(map(str, args)), timeout=25)

    def shell(self, *args):
        return self.run("shell", *args)

    def screen(self, name):
        if self.folder:
            data = self.run("exec-out", "screencap", "-p")
            image = Image.open(BytesIO(data))
            assert image.size == (self.w, self.h)
            image.save(self.folder / (name + ".png"))

    def tree(self):
        if (
            b"dumped to"
            not in self.shell(
                "uiautomator", "dump", "/sdcard/terra-layouts.xml"
            ).lower()
        ):
            return {}
        root = ElementTree.fromstring(
            self.run("exec-out", "cat", "/sdcard/terra-layouts.xml")
        )
        items = {}
        for node in root.iter("node"):
            l, t, r, b = map(int, re.findall(r"-?\d+", node.get("bounds")))
            if r <= l or b <= t or min(r, b) <= 0:
                continue
            for key in ("content-desc", "text", "class"):
                label = node.get(key)
                if label and (key != "class" or label == "android.widget.EditText"):
                    items.setdefault(
                        label, ((max(0, l) + r) // 2, (max(0, t) + b) // 2)
                    )
        return items

    def wait(self, *labels, absent=None):
        end = time.monotonic() + 22
        while time.monotonic() < end:
            items = self.tree()
            if all(k in items for k in labels) and (
                absent is None or absent not in items
            ):
                return items
        raise AssertionError((labels, absent, list(items)[-35:]))

    def tap(self, point):
        self.shell("input", "tap", *point)

    def button(self, label):
        self.tap(self.wait(label)[label])

    def launch(self, url):
        self.shell(
            "am",
            "start",
            "-n",
            "app.terra.browser/.MainActivity",
            "-a",
            "android.intent.action.VIEW",
            "-d",
            url,
        )

    def geometry(self, expected=None):
        items = self.wait("页面菜单", "后退", "重新载入")
        menu = items["页面菜单"]
        back = items["后退"]
        if "更多" in items:
            layout = "compact"
            assert back[0] < menu[0] < items["重新载入"][0] < items["更多"][0]
            assert (
                max(abs(items[k][1] - menu[1]) for k in ("后退", "重新载入", "更多"))
                < 6 * self.density
            )
            assert "书签" not in items and menu[1] > self.h * 0.68
        else:
            layout = "top" if menu[1] < self.h * 0.35 else "bottom"
            assert "书签" in items and "前进" in items and back[1] > self.h * 0.7
            assert (
                menu[1] < self.h * 0.22
                if layout == "top"
                else back[1] - menu[1] > 28 * self.density
            )
        if expected:
            assert expected == layout, (expected, layout, items)
        return layout, items

    def choose(self, layout, through_more=False):
        self.button("更多" if through_more else "页面菜单")
        self.button("标签栏布局")
        choices = self.wait("紧凑", "底部", "顶部", "完成")
        assert choices["紧凑"][0] < choices["底部"][0] < choices["顶部"][0]
        self.screen("layout-choices")
        self.tap(choices[layouts[layout]])
        time.sleep(0.45)
        return self.geometry(layout)[1]


def verify_compact_gestures(device, url):
    device.button("更多")
    device.button("新建标签页")
    device.wait("android.widget.EditText")
    device.shell("input", "text", url)
    device.shell("input", "keyevent", "ENTER")
    device.wait("Open lighthouse")
    for direction, heading in ((1, "Lighthouse"), (-1, "Harbor notebook")):
        nodes = device.geometry("compact")[1]
        x, y = next(point for label, point in nodes.items() if "127.0.0.1" in label)
        device.shell("input", "swipe", x, y, x + direction * device.w // 3, y, 340)
        device.wait(heading)
    nodes = device.geometry("compact")[1]
    x, y = next(point for label, point in nodes.items() if "127.0.0.1" in label)
    device.shell("input", "swipe", x, y, x, y - device.h // 4, 300)
    device.wait("标签组")
    device.shell("input", "keyevent", "BACK")
    device.wait("Harbor notebook")
    device.shell("input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_W")
    device.wait("Lighthouse")
    print(
        "PASS new tab, horizontal address swipes and upward swipe to overview",
        flush=True,
    )


def verify_private_menu(device, url):
    device.launch(url)
    device.wait("Open lighthouse")
    original = device.geometry()[0]
    device.choose("compact")
    try:
        for action, home in (
            ("新建无痕标签页", "无痕起始页"),
            ("新建标签页", "起始页"),
        ):
            device.button("更多")
            device.button(action)
            device.wait("android.widget.EditText")
            device.shell("input", "keyevent", "KEYCODE_ESCAPE")
            device.wait(home, "更多")
        print(
            "PASS private and regular commands focus a new tab in the chosen profile",
            flush=True,
        )
    finally:
        device.launch(url)
        device.wait("Open lighthouse")
        if device.geometry()[0] != original:
            device.choose(original)


def verify(d, url, initial):
    d.launch(url)
    d.wait("Open lighthouse")
    original = d.geometry(initial)[0]
    print("PASS initial layout", original, flush=True)
    try:
        exercise_layouts(d, url)
    finally:
        d.launch(url)
        if d.geometry()[0] != original:
            d.choose(original)


def exercise_layouts(d, url):
    d.choose("compact")
    d.screen("compact")
    d.button("Open lighthouse")
    d.wait("Lighthouse")
    nodes = d.geometry("compact")[1]
    assert "前进" not in nodes
    d.tap(nodes["后退"])
    d.wait("Harbor notebook", "前进")
    nodes = d.geometry("compact")[1]
    assert nodes["后退"][0] < nodes["前进"][0] < nodes["页面菜单"][0]
    d.screen("compact-forward")
    d.tap(nodes["前进"])
    d.wait("Lighthouse", absent="前进")
    d.button("更多")
    menu = d.wait("新建标签页", "新建无痕标签页", "共享", "书签", "所有标签页")
    d.screen("more")
    d.tap(menu["共享"])
    d.wait("选项 ›")
    d.shell("input", "keyevent", "BACK")
    d.geometry("compact")
    d.button("更多")
    d.button("所有标签页")
    d.wait("标签组")
    d.shell("input", "keyevent", "BACK")
    d.geometry("compact")
    print("PASS compact history buttons, More, share and tab overview", flush=True)
    verify_compact_gestures(d, url)
    for layout in ("top", "bottom"):
        d.choose(layout, through_more=(layout == "top"))
        # Start each scroll at the beginning of its own document.
        d.launch(url.rsplit("/", 1)[0] + "/lighthouse?layout=" + layout)
        d.wait("Lighthouse")
        nodes = d.geometry(layout)[1]
        d.screen(layout)
        address = next(v for k, v in nodes.items() if "127.0.0.1" in k)
        d.tap(address)
        field = d.wait("android.widget.EditText")["android.widget.EditText"]
        assert field[1] < d.h * 0.22 if layout == "top" else field[1] > d.h * 0.36, (
            layout,
            field,
        )
        d.screen(layout + "-address")
        d.shell("input", "keyevent", "KEYCODE_ESCAPE")
        d.geometry(layout)
        d.shell(
            "input", "swipe", d.w // 2, int(d.h * 0.73), d.w // 2, int(d.h * 0.29), 470
        )
        compact = d.wait("显示工具栏", absent="页面菜单")["显示工具栏"]
        assert compact[1] < d.h * 0.19 if layout == "top" else compact[1] > d.h * 0.7, (
            layout,
            compact,
        )
        d.screen(layout + "-reading")
        d.tap(compact)
        d.geometry(layout)
        print(
            "PASS",
            layout,
            "field, history controls, reading collapse and restore",
            flush=True,
        )
    d.choose("top")
    d.shell("am", "force-stop", "app.terra.browser")
    d.launch(url)
    d.geometry("top")
    print(
        "PASS layout choice persists after a process restart",
        flush=True,
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--initial", choices=layouts)
    parser.add_argument("--screens", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--private",
        action="store_true",
        help="Exercise private and regular tabs on a WebView with profile support",
    )
    mode.add_argument(
        "--inspect-motion",
        action="store_true",
        help="Track the moving glass in both directions on a dedicated test device",
    )
    args = parser.parse_args()
    device = Device(args.serial, args.screens)
    assert device.w / device.density < 650, "Use a phone-sized test device"
    with ThreadingHTTPServer(("127.0.0.1", 0), Pages) as server:
        Thread(target=server.serve_forever, daemon=True).start()
        port = server.server_port
        device.run("reverse", f"tcp:{port}", f"tcp:{port}")
        try:
            url = f"http://127.0.0.1:{port}/harbor"
            if args.inspect_motion:
                from phone_layout_motion import verify_layout_motion

                verify_layout_motion(device, url)
            elif args.private:
                verify_private_menu(device, url)
            else:
                verify(device, url, args.initial)
        finally:
            device.run("reverse", "--remove", f"tcp:{port}")
            server.shutdown()


if __name__ == "__main__":
    main()
