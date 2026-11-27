"""Optical assertions for tab_overview.py --glass (phone portrait)."""
from io import BytesIO
import subprocess
import time

from PIL import Image, ImageStat


def verify_glass(adb, shell, locate, tap, base, port, screens, prefix, record):
    # Adjacent pairs give both columns the same document regardless of whether
    # the first new tab lands in the right or the left column.
    for key in ("A", "A", "B", "B", "A", "A", "B", "B"):
        shell("am", "start", "-a", "android.intent.action.VIEW",
              "-n", "app.terra.browser/.MainActivity", "-d", base + key)
        locate(f"Overview {key} {port}")
    tabs = locate("标签页，", True)
    remote_movie = "/sdcard/terra-overview-glass.mp4"
    recording = None
    if record:
        recording = subprocess.Popen(prefix + ["shell", "screenrecord", "--time-limit", "18", remote_movie])
        time.sleep(.6)
    shell("input", "tap", *tabs)
    x, y = locate("新建标签页")
    time.sleep(.5)

    def shot():
        return Image.open(BytesIO(adb("exec-out", "screencap", "-p"))).convert("RGB")

    image = shot()
    width, height = image.size
    # Sample beside the plus glyph but inside the lens. The reference patch
    # lies outside the lens on the same card and the same scanline.
    offset = round(width * .032)
    def color(image, px):
        return ImageStat.Stat(image.crop((px-3, y-3, px+4, y+4))).mean

    found = {}
    for _ in range(3):
        start = height // 4
        shell("input", "motionevent", "DOWN", width//2, start)
        finger = start
        try:
            for step in range(1, 14):
                finger = start + height * step // 26
                shell("input", "motionevent", "MOVE", width//2, finger)
                time.sleep(.1)
                image = shot()
                glass = color(image, x + offset)
                card = color(image, x + 3*offset)
                for name, sign in (("blue", -1), ("orange", 1)):
                    if name in found or sign*(card[0]-card[2]) < 70:
                        continue
                    if sign*(glass[0]-glass[2]) > 10 and max(abs(a-b) for a,b in zip(glass,card)) > 3:
                        found[name] = tuple(round(c) for c in glass)
                        if screens:
                            screens.mkdir(parents=True, exist_ok=True)
                            image.save(screens / f"overview-{name}.png")
                if len(found) == 2:
                    break
        finally:
            shell("input", "motionevent", "UP", width//2, finger)
        if len(found) == 2:
            break
    assert len(found) == 2, f"Expected both card colors through the add button, saw {found}"
    tap("完成")
    locate(f"Overview B {port}")
    if recording:
        recording.wait(timeout=20)
        adb("pull", remote_movie, str(record))
    print(f"PASS moving blue and orange cards tint the same floating button: {found}", flush=True)
