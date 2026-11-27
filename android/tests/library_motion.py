"""Exercise Terra's LiquidKit library sheet on a dedicated portrait phone emulator."""
import re
import subprocess
import time
from xml.etree import ElementTree


def verify_sheet(shell, locate, adb, shot, port, prefix, movie):
    base = f"http://127.0.0.1:{port}"
    def visit(path):
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",base+path)
    def tap(label):
        shell("input","tap",*locate(label))
    def drag(y, delta, duration):
        shell("input","swipe",cx,y,cx,y+delta,duration)
        time.sleep(.55)
    def header_y():
        return locate("完成")[1]
    def history_rows():
        shell("uiautomator","dump","/sdcard/terra-library.xml")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-library.xml"))
        return {n.get("text") for n in tree.iter("node") if n.get("text","").startswith(f"Sheet {port} /")}

    visit("/glass-coast")
    locate("浏览峡谷")
    width, height = shot("sheet-page").size
    cx = width//2
    tap("书签")
    medium = header_y()
    assert .42*height < medium < .66*height, (medium,height)
    # A touch held midway through the drag must already move the pinned header.
    shell("input","motionevent","DOWN",cx,medium)
    up = medium-int(.16*height)
    try:
        for i in range(1,9):
            shell("input","motionevent","MOVE",cx,medium+(up-medium)*i//8)
        held = header_y()
        assert .10*height < medium-held < .21*height, (medium,held)
        shot("sheet-held")
    finally:
        shell("input","motionevent","UP",cx,up)
    time.sleep(.65)
    assert abs(header_y()-medium) < height*.02, "Short drag should spring back"
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","10","/sdcard/terra-library-motion.mp4"])
        time.sleep(.65)
    drag(medium,-int(.39*height),520)
    large = header_y()
    assert large < .18*height, (medium,large)
    shot("sheet-large")
    drag(large,int(.38*height),560)
    assert abs(header_y()-medium) < height*.02
    drag(medium,int(.39*height),480)
    locate("浏览峡谷")
    if recording:
        recording.wait(timeout=15)
        adb("pull","/sdcard/terra-library-motion.mp4",str(movie))
    print("PASS held drag, short release rebound, medium/large detents and interactive dismissal",flush=True)

    visit("/sheet-list/1")
    locate(f"Sheet {port} / 24")
    tap("页面菜单")
    x,y = locate("页面缩放")
    shell("input","swipe",cx,int(.88*height),cx,y,440)
    tap("历史记录")
    medium = header_y()
    first = f"Sheet {port} / 24"
    second = f"Sheet {port} / 23"
    def swipe_row(label, distance):
        # A half-height panel can expose the underlying WebView's identically titled heading.
        shell("uiautomator","dump","/sdcard/terra-sheet-row.xml")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-sheet-row.xml"))
        bounds = [list(map(int,re.findall(r"\d+", n.get("bounds"))))
                  for n in tree.iter("node") if n.get("text") == label]
        y = next((t+b)//2 for _,t,_,b in bounds if t > medium)
        shell("input", "swipe", int(.88*width), y, int((.88-distance)*width), y, 440)
        return y
    first_y = swipe_row(first, .31)
    locate("共享")
    assert abs(locate("删除")[1] - first_y) < height*.05
    # Opening another row closes the previous row. Only remove entries created by this fixture.
    second_y = swipe_row(second, .31)
    assert abs(locate("删除")[1] - second_y) < height*.05
    tap("删除")
    visible = history_rows()
    assert first in visible and second not in visible, visible
    swipe_row(f"Sheet {port} / 22", .78)
    time.sleep(.45)
    visible = history_rows()
    assert first in visible and f"Sheet {port} / 22" not in visible, visible
    assert abs(header_y()-medium) < height*.02
    print("PASS horizontal row actions transfer between rows, tapped and full-swipe delete act only on the chosen history entries",flush=True)
    drag(int(.86*height),-int(.45*height),570)
    assert header_y()<.18*height, "Upward list gesture must expand the sheet"
    before = history_rows()
    assert f"Sheet {port} / 24" in before, before
    drag(int(.84*height),-int(.48*height),390)
    after = history_rows()
    assert after and after != before and f"Sheet {port} / 24" not in after, (before,after)
    drag(int(.40*height),int(.18*height),650)
    assert header_y()<.18*height, "Downward list scroll collapsed a scrolled sheet"
    shell("input","keyevent","BACK")
    locate(f"Sheet {port} / 24")
    print("PASS list drag expands first, scrolls older history and keeps the large sheet while returning toward the top",flush=True)

def verify_resize(shell, locate, shot, port):
    """The Android emulator can cross the compact-width boundary without changing tasks."""
    def override(kind):
        match = re.search(r"Override [^:]+:\s*(\S+)", shell("wm",kind).decode())
        return match[1] if match else "reset"

    if shell("getprop","ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("This test changes display overrides and requires a portrait emulator")
    size, density = override("size"), override("density")
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/glass-coast")
    locate("浏览峡谷")
    height = shot("phone-page").height
    shell("input","tap",*locate("书签"))
    initial = locate("完成")[1]
    assert initial > height*.4, "Start on a compact portrait display"
    try:
        shell("wm","size","820x1180")
        shell("wm","density",160)
        time.sleep(1.2)
        x,y = locate("完成")
        assert 255<x<370 and y<125,(x,y)
        shot("library-sidebar")
    finally:
        shell("wm","size",size)
        shell("wm","density",density)
    time.sleep(1.2)
    _, restored = locate("完成")
    assert abs(restored-initial) < height*.03, (restored,initial)
    shell("input","tap",*locate("完成"))
    locate("浏览峡谷")
    print("PASS open library survives live phone/tablet layout and density changes",flush=True)
