"""Measure distinct SurfaceFlinger presentations during eight scrolls of a local test article.
Run on an Android test device with USB debugging and Terra installed: python scroll_pacing.py SERIAL.
"""
import argparse
from http.server import ThreadingHTTPServer
import math
import os
from pathlib import Path
import re
import shutil
import statistics
import subprocess
from threading import Thread
import time
from uuid import uuid4

from reading_toolbar import Article


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    args = parser.parse_args()
    exe = shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name=="nt" else "adb"))
    prefix = [exe,"-s",args.serial]
    def adb(*parts):
        return subprocess.check_output(prefix+list(map(str,parts)),timeout=15,text=True,encoding="utf-8")

    def shell(*parts):
        return adb("shell",*parts)

    component = "app.terra.browser/.MainActivity"
    def presentations(layer):
        lines = shell("dumpsys","SurfaceFlinger","--latency",layer).splitlines()
        if not lines or not lines[0].strip().isdigit():
            return 0,set()
        period = int(lines[0])
        frames = set()
        for line in lines[1:]:
            fields = line.split()
            if len(fields)==3:
                # FrameTracker reports desired presentation, actual presentation, ready time.
                actual = int(fields[1])
                if 0 < actual < (1<<63)-1:
                    frames.add(actual)
        return period, frames

    with ThreadingHTTPServer(("127.0.0.1",0),Article) as site:
        Thread(target=site.serve_forever,daemon=True).start()
        site.position = 0
        port = site.server_port
        adb("reverse",f"tcp:{port}",f"tcp:{port}")
        try:
            shell("am","start","-W","-n",component,"-a","android.intent.action.VIEW",
                  "-d",f"http://127.0.0.1:{port}/{uuid4().hex}")
            width,height = map(int,re.findall(r"(\d+)x(\d+)",shell("wm","size"))[-1])
            x = width//2
            low,high = int(height*.65),int(height*.42)
            deadline = time.monotonic()+20
            while time.monotonic()<deadline:
                shell("uiautomator","dump","/sdcard/terra-pacing.xml")
                if 'text="Morning"' in adb("exec-out","cat","/sdcard/terra-pacing.xml"):
                    break
            else:
                raise RuntimeError("The test article did not finish loading")
            shell("input","swipe",x,low,x,high,600)
            time.sleep(.45)
            if site.position==0:
                raise RuntimeError("The test document did not report scrolling")
            listing = shell("dumpsys","SurfaceFlinger","--list")
            package,activity = component.split("/")
            pattern = re.escape(package+"/"+package+activity)+r"(?:#\d+)?(?=[ }\r\n]|$)"
            candidates = re.findall(pattern,listing)
            layer = next((name for name in reversed(candidates) if all(presentations(name))),None)
            if layer is None:
                raise RuntimeError("SurfaceFlinger did not expose presentation timestamps for the browser window")
            intervals = []
            for index in range(8):
                y1,y2 = (high,low) if index%2==0 else (low,high)
                previous = site.position
                _,before = presentations(layer)
                shell("input","swipe",x,y1,x,y2,600)
                time.sleep(.22)
                period,after = presentations(layer)
                frames = sorted(after-before)
                deadline = time.monotonic()+1.5
                while site.position==previous and time.monotonic()<deadline:
                    time.sleep(.05)
                if len(frames)<3 or site.position==previous:
                    raise RuntimeError(f"Swipe {index+1} did not produce a measured scroll")
                intervals.extend((b-a)/1e6 for a,b in zip(frames,frames[1:]))
                time.sleep(.4)
            intervals.sort()
            print(f"{len(intervals)} within-swipe intervals, display period {period/1e6:.2f} ms")
            print(f"Median {statistics.median(intervals):.2f} ms, P90 {intervals[math.ceil(.9*len(intervals))-1]:.2f} ms, max {intervals[-1]:.2f} ms")
        finally:
            adb("reverse","--remove",f"tcp:{port}")
            site.shutdown()


if __name__=="__main__":
    main()
