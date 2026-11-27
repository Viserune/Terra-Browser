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
    from PIL import ImageStat
    shell("am", "start", "-a", "android.intent.action.VIEW", "-n", "app.terra.browser/.MainActivity",
          "-d", f"http://127.0.0.1:{port}/sky")
    press = locate("Open garden")
    rest = shot("tablet-link-rest")
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
    assert abs(width-342) <= 2 and abs(height-476) <= 3 and abs(header-44) <= 2 and abs(gap-20) <= 2, (width,height,header,gap)
    patch = (int(image.width*.88),int(image.height*.80),int(image.width*.94),int(image.height*.83))
    before = ImageStat.Stat(rest.crop(patch)).mean
    after = ImageStat.Stat(image.crop(patch)).mean
    factors = [b/a for a,b in zip(before,after) if a>30]
    assert factors and all(.48 < value < .56 for value in factors), (before,after)
    shell("input","tap",*locate("在标签页组中打开"))
    locate("新建标签组")
    child = preview_bounds(shot("tablet-link-groups"))
    assert child and max(abs(a-b) for a,b in zip(child,(l,t,r,b))) <= 2, child
    shell("input","tap",*locate("返回链接菜单"))
    shell("input","tap",*locate("隐藏预览"))
    locate("显示预览")
    shell("input","tap",*locate("显示预览"))
    first = locate("打开")
    second = locate("在后台打开")
    outside = (image.width-3,image.height//2)

    def slide(start, via, end):
        shell("input","motionevent","DOWN",*start)
        try:
            shell("input","motionevent","MOVE",*via)
            time.sleep(.24)
            shell("input","motionevent","MOVE",*end)
        finally:
            shell("input","motionevent","UP",*end)

    slide(first, second, outside)
    locate("Sky")
    press = locate("Open garden")
    shell("input","swipe",*press,*press,800)
    slide(locate("在后台打开"),first,first)
    locate("Garden")
    print(f"PASS portrait preview {width:.0f} × {height:.0f} dp, gap {gap:.0f} dp, background brightness {sum(factors)/len(factors):.2f}", flush=True)
    print("PASS group submenu stays anchored, hide/show works, a fresh slide cancels outside or opens the row under the finger", flush=True)
    shell("am","start","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/link-drop")
    source = locate("Garden link")
    target = locate("Drop link here")
    shell("input","motionevent","DOWN",*source)
    try:
        locate("隐藏预览")
        l,t,r,b = preview_bounds(shot("tablet-link-held"))
        middle = (l+(r-l)//4,t+(b-t)//3)
        for step in range(1,7):
            x = round(source[0]+(middle[0]-source[0])*step/6)
            y = round(source[1]+(middle[1]-source[1])*step/6)
            shell("input","motionevent","MOVE",x,y)
        shell("input","motionevent","MOVE",target[0],target[1]-8)
        shell("input","motionevent","MOVE",*target)
        shot("tablet-link-over-drop-target")
    finally:
        shell("input","motionevent","UP",*target)
    locate("Garden link received")
    print("PASS the original held link lifts into a system drag and the receiving webpage gets its URL", flush=True)
