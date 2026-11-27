"""Cold session and card-pixel checks for tab_overview.py --restore-previews."""
from io import BytesIO
import hashlib
import subprocess
from threading import Event
import time
from xml.etree import ElementTree

from PIL import Image


def offset(adb, shell):
    shell("uiautomator", "dump", "/sdcard/terra-position.xml")
    for node in ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-position.xml")).iter("node"):
        for value in (node.get("text", ""), node.get("content-desc", "")):
            if value.startswith("Offset "):
                return int(value.split()[-1])
    raise AssertionError("The article did not expose its reading position")


def scroll_article(adb, shell):
    w,h = Image.open(BytesIO(adb("exec-out", "screencap", "-p"))).size
    for _ in range(2):
        shell("input", "swipe", w//2, 3*h//4, w//2, h//4, 760)
    time.sleep(.6)
    before = offset(adb, shell)
    assert before > 400, before
    return before


def verify_restore(adb, shell, tap, locate, first, second, movie, prefix, site):
    requested = Event()
    release = Event()
    def page_color(key, original):
        if key != "A":
            return original
        requested.set()
        release.wait(timeout=8)
        return "#236954"
    site.page_color = page_color
    shell("am", "force-stop", "app.terra.browser")
    shell("am", "start", "-n", "app.terra.browser/.MainActivity")
    locate(second)
    tap("标签页，", True)
    x, y = locate(first)
    deadline = time.monotonic() + 5
    while True:
        shot = Image.open(BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")
        # Sample inside A's card below its title, before the restored WebView is opened.
        w = shot.width
        crop = shot.crop((x-w//14, y+w//7, x+w//14, y+w//4))
        blue = sum(1 for r,g,b in crop.getdata() if b-r > 65 and b-g > 25)
        if blue > crop.width * crop.height * .8:
            break
        assert time.monotonic() < deadline, "Inactive card lost its blue document after cold restart"
        time.sleep(.15)
    print("PASS cold overview contains the inactive document's original blue pixels", flush=True)
    assert not requested.is_set(), "Merely opening the overview loaded the background document"
    recording = None
    if movie:
        recording = subprocess.Popen(prefix + ["shell", "screenrecord", "--time-limit", "6", "/sdcard/terra-restore.mp4"])
        time.sleep(.5)
    shell("input", "tap", x, y)
    try:
        assert requested.wait(timeout=8), "Tapping the restored card did not request its page"
        time.sleep(.65)
        pending = Image.open(BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")
        w, h = pending.size
        patch = pending.crop((w//4, h//2, 3*w//4, 3*h//5))
        assert sum(1 for r,g,b in patch.getdata() if b-r > 65) > patch.width*patch.height*.9, "Blank frame while waiting for the restored page"
    finally:
        release.set()
    locate("页面菜单")
    restored = offset(adb, shell)
    assert abs(restored - site.saved_offset) <= 8, (site.saved_offset, restored)
    fresh = Image.open(BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")
    r,g,b = fresh.getpixel((w//2, h//2))
    assert g-r > 45 and g-b > 12, (r,g,b)
    if recording:
        recording.wait(timeout=12)
        adb("pull", "/sdcard/terra-restore.mp4", str(movie))
    print("PASS selected card stays blue during the delayed response and gives way to the fresh green document", flush=True)
    print(f"PASS old and restored live reading offsets: {site.saved_offset} and {restored} CSS pixels", flush=True)


def verify_private_cache(adb, shell, tap, locate, hold, base, first, second, private):
    folder = "/data/data/app.terra.browser/cache/tab-previews"
    def files():
        return set(shell("su", "0", "ls", folder).decode().split())
    tap("标签页，", True)
    tap(second)
    locate(second)
    target = hashlib.sha256((base + "A").encode()).digest()
    names = [name for name in files() if adb("exec-out", "su", "0", "head", "-c", "32", f"{folder}/{name}") == target]
    assert len(names) == 1, names
    tap("标签页，", True)
    tap("无痕浏览")
    tap("新建无痕标签页")
    tap("搜索或输入网址")
    locate("android.widget.EditText")
    shell("input", "text", base + "Private")
    shell("input", "keyevent", "ENTER")
    locate(private)
    before = files()
    shell("input", "keyevent", "HOME")
    time.sleep(.5)
    assert files() == before, "Putting the private tab in the background wrote a preview"
    shell("am", "start", "-n", "app.terra.browser/.MainActivity")
    locate(private)
    tap("标签页，", True)
    tap("普通标签")
    tap(second)
    locate(second)
    assert files() == before, "Leaving the private tab wrote a preview"
    tap("标签页，", True)
    hold(locate(first))
    tap("关闭标签页")
    deadline = time.monotonic() + 3
    while names[0] in files():
        assert time.monotonic() < deadline, "Closed tab's preview is still present"
        time.sleep(.1)
    print("PASS private backgrounding and switching created no preview file; closing the ordinary card removed its file", flush=True)


if __name__ == "__main__":
    import argparse
    from itertools import groupby
    import cv2

    parser = argparse.ArgumentParser(description="Inspect the --restore-previews recording for single-frame flashes.")
    parser.add_argument("movie")
    video = cv2.VideoCapture(parser.parse_args().movie)
    frames = []
    while True:
        ok, frame = video.read()
        if not ok:
            break
        height, width = frame.shape[:2]
        center = frame[height//2:3*height//5, width//4:3*width//4].astype("int16")
        b,g,r = center.transpose(2, 0, 1)
        if ((b-r > 80) & (b-g > 32)).mean() > .9:
            frames.append("blue")
        elif ((g-r > 42) & (g-b > 12)).mean() > .9:
            frames.append("green")
        else:
            frames.append("other")
    video.release()
    print("Recorded center patch", [(label, len(list(group))) for label,group in groupby(frames)])
    start = frames.index("blue")
    end = frames.index("green", start)
    assert "other" not in frames[start:end], "Another surface showed through after card expansion"
    assert all(label == "green" for label in frames[end:]), "Cached page reappeared after live paint"
