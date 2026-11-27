"""Exercise the address bar using Android touch input. Run on a Terra test device:
    python android/tests/address_gestures.py SERIAL
Opens four local tabs. Distances use the device's current display density.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
import xml.etree.ElementTree as ET


class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path not in ("/start", "/middle", "/one", "/two"):
            self.send_error(404)
            return
        title = "Gesture page " + self.path[1:]
        body = f'<meta name="viewport" content="width=device-width"><title>{title}</title><h1>{title}</h1>'.encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    serial = parser.parse_args().serial
    platform_tools = Path(os.environ.get("ANDROID_HOME", ""), "platform-tools")
    exe = shutil.which("adb") or str(platform_tools / ("adb.exe" if os.name == "nt" else "adb"))

    def adb(*args):
        return subprocess.check_output([exe, "-s", serial, *map(str, args)], timeout=25)

    def shell(*args):
        return adb("shell", *args)

    def wait_for(label):
        deadline = time.monotonic() + 15
        labels = []
        while time.monotonic() < deadline:
            if b"dumped to" not in shell("uiautomator", "dump", "/sdcard/terra-gesture-test.xml"):
                continue
            tree = ET.fromstring(adb("exec-out", "cat", "/sdcard/terra-gesture-test.xml"))
            labels = []
            for node in tree.iter("node"):
                values = (node.get("text"), node.get("content-desc"), node.get("class"))
                if label in values:
                    x,y,r,b = map(int,re.findall(r"\d+",node.get("bounds")))
                    return (x+r)//2,(y+b)//2
                labels.extend(value for value in values[:2] if value)
        raise AssertionError(f"Expected {label!r}, visible labels: {labels}")

    density = int(re.findall(rb"density: (\d+)", shell("wm","density"))[-1])/160
    one, two = "Gesture page one", "Gesture page two"
    with ThreadingHTTPServer(("127.0.0.1", 0), Fixture) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        port = server.server_port
        adb("reverse", f"tcp:{port}", f"tcp:{port}")
        try:
            for path in ("/start","/middle","/one","/two"):
                shell("am","start","-a","android.intent.action.VIEW","-d",f"http://127.0.0.1:{port}{path}",
                      "-n","app.terra.browser/.MainActivity")
                wait_for("Gesture page " + path[1:])
            x,y = wait_for("不安全 · 127.0.0.1")

            def swipe(dx,dy,ms):
                shell("input","swipe",x,y,x+round(dx*density),y+round(dy*density),ms)
                time.sleep(.32)

            swipe(35,0,420)
            wait_for(two)
            swipe(35,0,50)
            wait_for(one)
            swipe(-35,0,50)
            wait_for(two)
            print("PASS same 35dp travel stays put when slow and switches both ways when flicked", flush=True)
            for direction, target in ((1,"Gesture page start"),(-1,two)):
                for _ in range(3):
                    shell("input","swipe",x,y,x+round(direction*70*density),y,100)
                wait_for(target)
            print("PASS three successive swipes reach the fourth tab in both directions", flush=True)
            swipe(-75,0,220)
            wait_for(two)
            swipe(55,-54,220)
            wait_for(two)
            shell("input","motionevent","DOWN",x,y)
            shell("input","motionevent","MOVE",x+round(85*density),y)
            shell("input","motionevent","MOVE",x+round(5*density),y)
            shell("input","motionevent","UP",x+round(5*density),y)
            wait_for(two)
            print("PASS last-tab boundary, diagonal and reversed drag preserve the current page", flush=True)
            swipe(90,0,550)
            wait_for(one)
            swipe(0,-85,220)
            wait_for("标签页")
            shell("input","tap",*wait_for("完成"))
            shell("input","tap",*wait_for("不安全 · 127.0.0.1"))
            wait_for("android.widget.EditText")
            print("PASS deliberate long drag, swipe up and address editing", flush=True)
        finally:
            adb("reverse","--remove",f"tcp:{port}")
            server.shutdown()


if __name__ == "__main__":
    main()
