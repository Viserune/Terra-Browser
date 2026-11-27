"""Run via safari_motion.py --menu-drag on a dedicated test emulator."""
import time
import subprocess
import re
from xml.etree import ElementTree


def verify_menu_drag(shell, locate, shot, port, record, prefix, adb):
    shell("am", "start", "-a", "android.intent.action.VIEW", "-n", "app.terra.browser/.MainActivity",
          "-d", f"http://127.0.0.1:{port}/sky")
    link = locate("Open garden")
    def finger(action, point):
        shell("input", "motionevent", action, *point)
    def begin():
        finger("DOWN", link)
        return locate("打开")
    recording = None
    if record:
        recording = subprocess.Popen(prefix + ["shell", "screenrecord", "--time-limit", "12", "/sdcard/terra-menu.mp4"])
        time.sleep(.5)
    target = begin()
    alternate = locate("在新标签中打开")
    finger("MOVE", alternate)
    time.sleep(.35)
    finger("MOVE", target)
    time.sleep(.3)
    finger("UP", target)
    locate("Garden")
    if recording:
        recording.wait(timeout=16)
        adb("pull", "/sdcard/terra-menu.mp4", str(record))
    print("PASS one continuous press on a link slides to Open and loads the target", flush=True)
    shell("input", "keyevent", "BACK")
    locate("Sky")
    link = locate("Open garden")
    target = begin()
    before = shot("menu-before-drag")
    finger("MOVE", target)
    time.sleep(.2)
    selected = shot("menu-drag-selected")
    width,height = before.size
    x,y = target
    box = (x+int(width*.1),y-4,x+int(width*.15),y+4)
    from PIL import ImageChops, ImageStat
    contrast = max(ImageStat.Stat(ImageChops.difference(before.crop(box),selected.crop(box))).mean)
    assert contrast > 8, ("Menu row did not highlight", contrast)
    outside = (width-3, height//2)
    finger("MOVE", outside)
    finger("UP", outside)
    locate("Open garden")
    locate("Sky")
    print("PASS row highlights under the held finger and sliding out cancels without navigating", flush=True)
    target = begin()
    finger("UP", link)
    finger("DOWN", target)
    finger("UP", target)
    locate("Garden")
    print("PASS releasing in place leaves the menu available for a separate tap", flush=True)
    shell("am", "start", "-a", "android.intent.action.VIEW", "-n", "app.terra.browser/.MainActivity",
          "-d", f"http://127.0.0.1:{port}/tab-menu")
    title = f"Menu {port}"
    locate(title)
    shell("input", "tap", *locate("标签页，", True))
    finger("DOWN", locate(title))
    pin = locate("固定标签页")
    finger("MOVE", pin)
    finger("UP", pin)
    pinned = locate("已固定，"+title)
    finger("DOWN", pinned)
    unpin = locate("取消固定")
    finger("MOVE", unpin)
    finger("UP", unpin)
    locate("完成")
    shell("input","tap",*locate(title))
    locate("页面菜单")
    print("PASS the overview window supports one-press pin and unpin, then opens the original tab", flush=True)


def verify_menu_history(shell, locate, adb, port):
    shell("am", "start", "-a", "android.intent.action.VIEW", "-n", "app.terra.browser/.MainActivity",
          "-d", f"http://127.0.0.1:{port}/trail/0")
    next_link = locate("Continue")
    for step in range(1, 25):
        shell("input", "tap", *next_link)
        locate(f"Trail {step}")
    back = locate("后退")
    release_point = back
    shell("input", "motionevent", "DOWN", *back)
    try:
        first = locate("Trail 23")
        tree = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-motion.xml"))
        scroller = next(n for n in tree.iter("node") if n.get("scrollable") == "true")
        l,t,r,b = map(int, re.findall(r"\d+", scroller.get("bounds")))
        # Initially the oldest entry is clipped below the menu.
        assert not any(n.get("text") == "Trail 0" and n.get("bounds") not in ("[0,0][0,0]","")
                       for n in tree.iter("node"))
        shell("input", "motionevent", "MOVE", *first)
        shell("input", "motionevent", "MOVE", (l+r)//2, b-6)
        locate("Trail 0")
        time.sleep(.25)
        release_point = locate("Trail 0")
        shell("input", "motionevent", "MOVE", *release_point)
    finally:
        shell("input", "motionevent", "UP", *release_point)
    locate("页面菜单")
    locate("Trail 0")
    print("PASS holding at the lower edge scrolls 24 history entries and release navigates to the oldest page", flush=True)


def verify_tablet_menu(shell, locate, shot, adb, port):
    from link_groups import preview_bounds
    shell("am", "start", "-a", "android.intent.action.VIEW", "-n", "app.terra.browser/.MainActivity",
          "-d", f"http://127.0.0.1:{port}/sky")
    press = locate("Open garden")
    shell("input", "swipe", *press, *press, 800)
    locate("隐藏预览")
    locate("打开")
    tree = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-motion.xml"))
    bounds = [list(map(int, re.findall(r"\d+",n.get("bounds")))) for n in tree.iter("node")
              if n.get("clickable") == "true" and any(c.get("text") == "打开" for c in n.iter("node"))]
    row = min(bounds, key=lambda box: (box[2]-box[0])*(box[3]-box[1]))
    image = shot("tablet-link-menu")
    density = int(re.findall(r"(\d+)",shell("wm","density").decode())[-1]) / 160
    assert abs(image.width / density - 820) < 2 and image.height > image.width, "Use an 820dp portrait test device"
    l,t,r,b = preview_bounds(image)
    # Scan the empty middle of the header above the green WebView. This
    # measures the screenshot independently of the application's layout constants.
    mid = (l+r)//2
    top = t-1
    header_color = image.getpixel((mid,top))
    while top > 0 and max(abs(a-b) for a,b in zip(header_color,image.getpixel((mid,top-1)))) < 6:
        top -= 1
    width, height, header = (r-l)/density, (b-top)/density, (t-top)/density
    gap = (row[0]-r)/density - 9
    assert abs(width-335) <= 2 and abs(height-476) <= 3 and abs(header-44) <= 2 and abs(gap-28) <= 2, (width,height,header,gap)
    shell("input","tap",*locate("在标签页组中打开"))
    locate("新建标签组")
    child = preview_bounds(shot("tablet-link-groups"))
    assert child and max(abs(a-b) for a,b in zip(child,(l,t,r,b))) <= 2, child
    shell("input","tap",*locate("返回链接菜单"))
    shell("input","tap",*locate("隐藏预览"))
    locate("显示预览")
    shell("input","tap",*locate("显示预览"))
    shell("input","tap",*locate("打开"))
    locate("Garden")
    print(f"PASS portrait preview {width:.0f} × {height:.0f} dp, panel gap {gap:.0f} dp, group submenu keeps preview anchored, hide/show/open", flush=True)
