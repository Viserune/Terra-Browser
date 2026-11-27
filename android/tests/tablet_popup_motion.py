"""Check modal geometry and composited video frames over a solid test page."""
import re
import subprocess
import tempfile
import time
from pathlib import Path


def verify(adb, shell, locate, shot, port, prefix):
    import cv2
    import numpy as np

    if shell("getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("Use a test emulator for animation-scale checks")
    density = int(re.findall(r"\d+", shell("wm", "density").decode())[-1]) / 160
    shell("am", "start", "-W", "-a", "android.intent.action.VIEW", "-n",
          "app.terra.browser/.MainActivity", "-d", f"http://127.0.0.1:{port}/panel-paper")
    locate("Panel sample")
    sidebar = locate("显示侧栏")
    menu = locate("页面菜单")
    rest = np.asarray(shot("paper-rest").convert("RGB"), dtype=float)
    h, w = rest.shape[:2]
    assert w / density >= 650, "Tablet layout required"
    outside = (round(w * .92), round(h * .69))

    def tap(point):
        shell("input", "tap", *point)

    def patch(image, x, y):
        return np.median(image[y-4:y+5, x-4:x+5], axis=(0, 1))

    tap(sidebar)
    locate("完成")
    side = np.asarray(shot("paper-sidebar"), dtype=float)[:, :, :3]
    paper = patch(rest, *outside)
    shaded = patch(side, *outside)
    assert np.max(abs(shaded / paper - .8)) < .035, (paper, shaded)
    # On both sides of the nominal trailing edge, sample the same featureless paper.
    y = round(.79*h)
    inside = patch(side, round(305*density), y)
    beyond = patch(side, round(335*density), y)
    assert max(abs(inside-shaded)) > 12 and max(abs(beyond-shaded)) < 4, (inside, beyond)
    assert max(abs(patch(side, round(3*density), y)-inside)) < 6, "Left margin in the sidebar"
    tap(outside)
    locate("显示侧栏")
    tap(menu)
    locate("页面缩放")
    ready = np.asarray(shot("paper-menu"), dtype=float)[:, :, :3]
    assert np.max(abs(patch(ready,*outside)-paper)) < 4, "Page menu darkened the document"
    tap(outside)
    locate("显示侧栏")
    print("PASS edge-to-edge 320dp sidebar, 20% dim, undimmed menu", flush=True)

    old = shell("settings","get","global","animator_duration_scale").decode().strip()
    remote = "/sdcard/terra-tablet-popup.mp4"
    try:
        shell("settings","put","global","animator_duration_scale","5")
        with tempfile.TemporaryDirectory() as temp:
            local = Path(temp)/"panel.mp4"
            capture = subprocess.Popen(prefix+["shell","screenrecord","--size",f"720x{2*round(360*h/w)}",
                                       "--time-limit","10",remote])
            try:
                time.sleep(1)
                tap(menu)
                time.sleep(4.4)
                tap(outside)
            finally:
                capture.wait(timeout=16)
            adb("pull",remote,str(local))
            cap = cv2.VideoCapture(str(local))
            trace = []
            background = None
            while True:
                ok, frame = cap.read()
                if not ok:
                    break
                fh, fw = frame.shape[:2]
                left = round(fw*(menu[0]-5*density)/w)
                right = round(fw*(menu[0]+14*density)/w)
                top = round(fh*(menu[1]+32*density)/h)
                bottom = min(fh, round(fh*(menu[1]+590*density)/h))
                strip = frame[top:bottom,left:right].astype(float)
                if background is None:
                    background = strip
                rows = (np.max(abs(strip-background),axis=2)>12).mean(axis=1)>.5
                trace.append(int(np.flatnonzero(rows)[-1]+1) if rows.any() else 0)
            cap.release()
            assert trace and max(trace)>120, "Popover unavailable in the recording"
            values = np.asarray(trace)/max(trace)
            settled = np.flatnonzero(values>.94)
            assert min(values[:settled[0]+1]) < .1 and values[-1]<.1, "Popover failed to return to its origin"
            for leg in (values[:settled[0]], values[settled[-1]+1:]):
                assert any((leg>.18)&(leg<.76)), "Menu jumped directly between small and full size"
            print("PASS intermediate menu sizes on entrance and dismissal", flush=True)
    finally:
        if old=="null":
            shell("settings","delete","global","animator_duration_scale")
        else:
            shell("settings","put","global","animator_duration_scale",old)

    from bookmark_editor import replace_field
    name = f"Panel{port}"
    tap(locate("页面菜单"))
    tap(locate("添加书签"))
    replace_field(shell, locate, "书签名称", name)
    tap(locate("保存"))
    shell("am", "start", "-W", "-a", "android.intent.action.VIEW", "-n",
          "app.terra.browser/.MainActivity", "-d", f"http://127.0.0.1:{port}/garden")
    locate("Garden")
    tap(locate("不安全 · 127.0.0.1"))
    locate("个人收藏")
    tap(locate(name))
    locate("Panel sample")
    tap(locate("不安全 · 127.0.0.1"))
    point = locate(name)
    shell("input","swipe",*point,*point,680)
    tap(locate("删除书签"))
    tap(locate("删除"))
    locate("个人收藏")
    assert name.encode() not in adb("exec-out","cat","/sdcard/terra-motion.xml"), "Deleted favorite still present"
    shell("input","keyevent","KEYCODE_ESCAPE")
    locate("Panel sample")
    print("PASS favorite opens the saved page and supports its long-press menu", flush=True)
