"""Verify the visible grid order, active page, swipe rebound and persisted tab group."""
import re
import subprocess
import time
from xml.etree import ElementTree


def verify_card_drag(adb, shell, label):
    import cv2
    import numpy as np
    shell("uiautomator", "dump", "/sdcard/terra-order.xml")
    root = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-order.xml"))
    cards = []
    for node in root.iter("node"):
        descendants = list(node.iter("node"))
        if (node.get("clickable") == "true" and any(n.get("text") == label for n in descendants)
                and any(n.get("content-desc") == "关闭标签页" for n in descendants)):
            cards.append(tuple(map(int, re.findall(r"\d+", node.get("bounds")))))
    l, t, r, b = min(cards, key=lambda rect: (rect[2]-rect[0])*(rect[3]-rect[1]))
    def frame():
        raw = np.frombuffer(adb("exec-out", "screencap", "-p"), np.uint8)
        return cv2.imdecode(raw, cv2.IMREAD_GRAYSCALE)
    before = frame()
    width = r-l
    # The title's letters give the template a distinct horizontal and vertical position.
    sample = before[t+9:t+65, l+12:l+width//2].copy()
    def position():
        img = frame()
        region = img[t:t+85, max(0,l-width//2):r]
        corr = cv2.matchTemplate(region, sample, cv2.TM_CCOEFF_NORMED)
        _, score, _, (x,y) = cv2.minMaxLoc(corr)
        assert score > .80, (score, label)
        return max(0,l-width//2)+x, t+y
    x, y = l+width*3//4, t+(b-t)//2
    original = position()
    shell("input", "motionevent", "DOWN", x, y)
    dest = x-width//3
    try:
        shell("input", "motionevent", "MOVE", x-width//8, y)
        shell("input", "motionevent", "MOVE", dest, y)
        time.sleep(.2)
        shifted = position()
        dx = original[0]-shifted[0]
        assert abs(dx-(x-dest)) < width*.10 and abs(original[1]-shifted[1]) <= 2, (original, shifted, width)
        # Pass the close threshold, reverse direction, then rest before lifting.
        shell("input", "motionevent", "MOVE", x-width*3//5, y)
        dest = x-width//6
        shell("input", "motionevent", "MOVE", dest, y)
        time.sleep(.25)
    finally:
        shell("input", "motionevent", "UP", dest, y)
    time.sleep(.7)
    assert position() == original
    print("PASS held card follows the finger and a reversed swipe springs back", dx, "px", flush=True)


def verify_arrangement(adb, shell, tap, locate, labels, hold, base, port, prefix, movie, manual=False):
    name = f"Arrange{port}"
    titles = {key: f"Overview {key} {port}" for key in "ABCD"}

    def overview():
        tap("标签页，", True)

    def order(expected):
        until = time.monotonic() + 12
        actual = []
        while time.monotonic() < until:
            shell("uiautomator", "dump", "/sdcard/terra-order.xml")
            root = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-order.xml"))
            items = []
            for node in root.iter("node"):
                text = node.get("text", "")
                if text in titles.values():
                    x, y, _, _ = map(int, re.findall(r"\d+", node.get("bounds")))
                    items.append((y, x, text))
            actual = [label for _, _, label in sorted(items)]
            if actual == [titles[k] for k in expected]:
                return
        raise AssertionError((expected, actual))

    overview()
    hold(locate(titles["B"]))
    tap("移至标签组")
    tap("新建标签组")
    locate("android.widget.EditText")
    shell("input", "text", name)
    tap("保存")
    tap("完成")
    for key in "DCA":
        url = (base.replace("127.0.0.1", "localhost") if key == "C" else base) + key
        shell("am", "start", "-W", "-n", "app.terra.browser/.MainActivity",
              "-a", "android.intent.action.VIEW", "-d", url)
        locate(titles[key])
    overview()
    hold(locate(titles["A"]))
    tap("固定标签页")
    locate("已固定，" + titles["A"])
    order("ABDC")
    tap(titles["D"])
    locate(titles["D"])
    overview()
    if manual:
        from tab_manual_order import verify_edge_scroll, verify_manual_order
        verify_manual_order(adb, shell, tap, locate, order, titles, prefix, movie)
        verify_edge_scroll(adb, shell, tap, locate, base, port)
        return
    recording = None
    if movie:
        recording = subprocess.Popen(prefix + ["shell", "screenrecord", "--time-limit", "24", "/sdcard/terra-order.mp4"])
        time.sleep(.5)
    try:
        tap("排列标签页")
        tap("按标题排列")
        order("ABCD")
        tap("完成")
        locate(titles["D"])
        overview()
        tap("排列标签页")
        tap("按网站排列")
        order("ABDC")
    finally:
        if recording:
            recording.wait(timeout=26)
            adb("pull", "/sdcard/terra-order.mp4", str(movie))
    print("PASS title and site arrangement, pinned position, stable same-site order and active page", flush=True)
    tap("完成")
    locate(titles["D"])
    shell("am", "force-stop", "app.terra.browser")
    shell("am", "start", "-W", "-n", "app.terra.browser/.MainActivity")
    locate(titles["D"])
    overview()
    locate(name)
    order("ABDC")
    locate("已固定，" + titles["A"])
    tap("标签组")
    tap("普通标签")
    assert titles["D"] not in labels()
    tap("标签组")
    tap(name)
    order("ABDC")
    verify_card_drag(adb, shell, titles["D"])
    tap("完成")
    locate(titles["D"])
    print("PASS saved order, active tab and group isolation after restart", flush=True)
