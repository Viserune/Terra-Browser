"""Run against Terra on a test device: python android/tests/fullscreen_video.py SERIAL.

Serves a video element backed by a live canvas stream over an adb loopback tunnel.
Uses page events to distinguish leaving fullscreen from navigating the WebView.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import queue
import re
import shutil
import subprocess
import threading
import time
import xml.etree.ElementTree as ET


PLAYER = b"""<meta name=viewport content="width=device-width,initial-scale=1">
<title>Video fixture</title><h1>Video fixture</h1>
<button id=enter style="font-size:24px">Play full screen</button>
<style>#leave {display:none} #player:fullscreen #leave {display:block;font-size:24px}</style>
<div id=player><video id=video muted playsinline style="width:100%"></video>
<button id=leave onclick="document.exitFullscreen()">Exit player</button></div>
<script>
const canvas = document.createElement('canvas');
canvas.width=160; canvas.height=90;
const ctx = canvas.getContext('2d');
setInterval(() => {
  ctx.fillStyle='navy'; ctx.fillRect(0,0,160,90);
  ctx.fillStyle='white'; ctx.fillRect((performance.now()/12)%140,30,20,20);
}, 65);
video.srcObject=canvas.captureStream(15);
const report = event => fetch('/event/'+event,{cache:'no-store'});
document.addEventListener('fullscreenchange',() => report(document.fullscreenElement?'entered':'left'));
enter.onclick = () => { video.play(); player.requestFullscreen(); };
setInterval(() => {
  if(document.fullscreenElement && video.currentTime > .2 && !video.paused)
    report('playing');
}, 350);
</script>"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--edge-back", action="store_true", help="Exercise the edge gesture on a gesture-navigation device")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_HOME", ""), "platform-tools")
    adb_path = shutil.which("adb") or str(sdk / ("adb.exe" if os.name == "nt" else "adb"))
    events = queue.Queue()

    class Fixture(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path.startswith("/event/"):
                events.put(self.path.rsplit("/", 1)[-1])
                self.send_response(204)
                self.end_headers()
                return
            pages = {"/start": b'<meta name=viewport content="width=device-width"><a href="/video">Open video</a>',
                     "/video": PLAYER, "/incoming": b"<h1>Incoming page</h1>"}
            body = pages.get(self.path)
            if body is None:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type","text/html")
            self.send_header("Content-Length",str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    def adb(*command):
        return subprocess.check_output([adb_path, "-s", args.serial, *command], timeout=20)

    def point(label):
        end = time.monotonic() + 30
        while time.monotonic() < end:
            dump = adb("shell", "uiautomator", "dump", "/sdcard/terra-player-test.xml")
            if b"dumped to" in dump:
                tree = ET.fromstring(adb("exec-out", "cat", "/sdcard/terra-player-test.xml"))
                if tree.find(".//node[@resource-id='android:id/immersive_cling_title']") is not None:
                    ok = tree.find(".//node[@resource-id='android:id/ok']")
                    if ok is not None:
                        l,t,r,b = map(int, re.findall(r"\d+", ok.get("bounds")))
                        adb("shell", "input", "tap", str((l+r)//2), str((t+b)//2))
                        continue
                for node in tree.iter("node"):
                    if label in (node.get("text"), node.get("content-desc")):
                        l,t,r,b = map(int, re.findall(r"\d+", node.get("bounds")))
                        return str((l+r)//2), str((t+b)//2)
        raise AssertionError("Missing visible element: "+label)

    def tap(label):
        adb("shell", "input", "tap", *point(label))

    def check_bars(visible):
        # Android 15 exposes display-wide InsetsSource visibility in window diagnostics.
        if adb("shell", "getprop", "ro.build.version.sdk").strip() != b"35":
            return
        end = time.monotonic() + 8
        while time.monotonic() < end:
            state = adb("shell","dumpsys","window").decode(errors="replace")
            bars = dict(re.findall(r"InsetsSource id=\w+ type=(statusBars|navigationBars) frame=[^\n]*? visible=(true|false)",state))
            if len(bars) == 2 and all(value == str(visible).lower() for value in bars.values()):
                return
            time.sleep(.3)
        raise AssertionError(f"Expected bars visible={visible}, found {bars}")

    def saw_event(expected, timeout):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                if events.get(timeout=max(.01, end-time.monotonic())) == expected:
                    return True
            except queue.Empty:
                break
        return False

    def wait_event(expected):
        assert saw_event(expected,12), "Missing WebView event: "+expected

    def back():
        if args.edge_back:
            assert adb("shell","settings","get","secure","navigation_mode").strip() == b"2", "Enable gesture navigation first"
            sizes = re.findall(rb"(\d+)x(\d+)", adb("shell","wm","size"))
            width, height = map(int, sizes[-1])
            adb("shell","input","swipe","3",str(height//2),str(width//2),str(height//2),"220")
        else:
            adb("shell","input","keyevent","BACK")

    with ThreadingHTTPServer(("127.0.0.1",0),Fixture) as server:
        port = server.server_port
        threading.Thread(target=server.serve_forever, daemon=True).start()
        adb("reverse",f"tcp:{port}",f"tcp:{port}")
        def open_page(path):
            adb("shell","am","start","-n","app.terra.browser/.MainActivity",
                "-a","android.intent.action.VIEW","-d",f"http://127.0.0.1:{port}"+path)
        try:
            open_page("/start")
            tap("Open video")
            tap("Play full screen")
            wait_event("entered")
            wait_event("playing")
            check_bars(False)
            tap("Exit player")
            wait_event("left")
            check_bars(True)
            print("PASS player exit control and immersive system bars", flush=True)
            tap("Play full screen")
            wait_event("entered")
            wait_event("playing")
            if args.edge_back:
                # Android immersive mode first reveals transient navigation from the top edge.
                check_bars(False)
                size = re.findall(rb"(\d+)x(\d+)", adb("shell","wm","size"))[-1]
                center = str(int(size[0])//2)
                adb("shell","input","swipe",center,"1",center,str(int(size[1])//5),"220")
                deadline = time.monotonic() + 2.5
                while time.monotonic() < deadline:
                    system_ui = adb("shell","dumpsys","activity","service","com.android.systemui/.SystemUIService")
                    if b"mIsNavBarShownTransiently=true" in system_ui:
                        break
                else:
                    raise AssertionError("Android did not reveal transient gesture navigation")
            back()
            wait_event("left")
            point("Video fixture")
            back()
            point("Open video")
            print("PASS fullscreen plays video; first Back exits fullscreen, second Back navigates",flush=True)
            tap("Open video")
            tap("Play full screen")
            wait_event("entered")
            wait_event("playing")
            open_page("/incoming")
            point("Incoming page")
            check_bars(True)
            tap("页面菜单")
            point("添加书签")
            print("PASS external URL reveals the new page and functional browser controls",flush=True)
        finally:
            adb("reverse","--remove",f"tcp:{port}")
            server.shutdown()


if __name__ == "__main__":
    main()
