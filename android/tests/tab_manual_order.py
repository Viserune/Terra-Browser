"""Exercise one continuous touch across the overview window and its card menu."""
import re
import subprocess
import time
from xml.etree import ElementTree


def verify_manual_order(adb, shell, tap, locate, order, titles, prefix, movie):
    def point(key):
        return locate(titles[key])

    def touch(phase, pos):
        shell("input", "motionevent", phase, *pos)

    def move_between(start, end, steps=5):
        for i in range(1, steps+1):
            touch("MOVE", tuple(round(a + (b-a)*i/steps) for a,b in zip(start,end)))

    def positions(key):
        shell("uiautomator", "dump", "/sdcard/terra-drag.xml")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-drag.xml"))
        return [tuple(map(int, re.findall(r"\d+", n.get("bounds")))) for n in tree.iter("node")
                if n.get("text") == titles[key]]

    source, target = point("D"), point("B")
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","18","/sdcard/terra-reorder.mp4"])
        time.sleep(.4)
    try:
        # Wait for the actual long-press menu. Moving away from its rows picks up the card.
        touch("DOWN",source)
        try:
            locate("固定标签页")
            middle = tuple(round(a+(b-a)*.82) for a,b in zip(source,target))
            move_between(source, middle)
            time.sleep(.25)
            rects = positions("D")
            assert any(abs((l+r)//2-middle[0])<25 and abs((t+b)//2-middle[1])<22
                       for l,t,r,b in rects), (source,middle,rects)
            move_between(middle, target)
            time.sleep(.35)
            rects = positions("D")
            assert any(abs((l+r)//2-target[0])<25 and abs((t+b)//2-target[1])<22
                       for l,t,r,b in rects), (source,target,rects)
        finally:
            touch("UP", target)
        order("ADBC")
        print("PASS moved a held card sideways after the long-press menu appeared",flush=True)

        start, end = point("D"), point("C")
        touch("DOWN",start)
        time.sleep(.52)
        move_between(start,end)
        touch("CANCEL",end)
        order("ADBC")
        print("PASS interrupted cross-row drag restores the original order",flush=True)

        touch("DOWN",start)
        time.sleep(.52)
        move_between(start,end)
        touch("UP",end)
        order("ABCD")
    finally:
        if recording:
            recording.wait(timeout=20)
            adb("pull","/sdcard/terra-reorder.mp4",str(movie))
    tap("完成")
    locate(titles["D"])
    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n","app.terra.browser/.MainActivity")
    locate(titles["D"])
    tap("标签页，4 个")
    order("ABCD")
    locate("已固定，"+titles["A"])
    # Slide directly to an action while still holding the card.
    start = point("B")
    pin = start
    touch("DOWN", start)
    try:
        pin = locate("固定标签页")
        touch("MOVE", pin)
    finally:
        touch("UP", pin)
    locate("已固定，"+titles["B"])
    start, end = point("B"), point("A")
    touch("DOWN", start)
    time.sleep(.48)
    move_between(start, end)
    touch("UP", end)
    order("ABCD" if start[1] < end[1] else "BACD")
    print("PASS held menu selection coexists with dragging two pinned cards",flush=True)
    tap("完成")
    locate(titles["D"])
    print("PASS cross-row drop persists through restart with the same pinned and selected tabs",flush=True)

def verify_edge_scroll(adb, shell, tap, locate, base, port):
    def title(key):
        return f"Overview {key} {port}"
    for number in range(12):
        url = base + "scroll-" + str(number)
        shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",url)
    last = title("scroll-11")
    locate(last)
    tap("标签页，16 个")
    start = locate(last)
    _, heading = locate("搜索标签页")
    edge = (start[0], heading+60)
    shell("uiautomator","dump","/sdcard/terra-edge.xml")
    tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-edge.xml"))
    assert not any(n.get("text") == title("A") for n in tree.iter("node"))
    shell("input","motionevent","DOWN",*start)
    released = False
    try:
        time.sleep(.48)
        shell("input","motionevent","MOVE", start[0], start[1]-35)
        shell("input","motionevent","MOVE",*edge)
        locate("已固定，"+title("A"))
        time.sleep(.6)
        # The first ordinary slot is one card-height row below the two pinned headers.
        shell("uiautomator","dump","/sdcard/terra-edge.xml")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-edge.xml"))
        pins = [tuple(map(int,re.findall(r"\d+",n.get("bounds")))) for n in tree.iter("node")
                if n.get("content-desc") in {"已固定，"+title("A"), "已固定，"+title("B")}]
        assert len(pins)==2, pins
        l,t,r,b = max(pins, key=lambda rect: rect[3])
        dest = (l+(r-l)//4,b+64)
        shell("input","motionevent","MOVE",*dest)
        time.sleep(.25)
        shell("input","motionevent","UP",*dest)
        released = True
    finally:
        if not released: shell("input","motionevent","CANCEL",*edge)
    time.sleep(.8)
    last_xy = locate(last)
    first_xy = locate(title("C"))
    assert last_xy[0] < first_xy[0] and abs(last_xy[1]-first_xy[1]) < 8, (last_xy,first_xy)
    tap("完成")
    locate(last)
    print("PASS holding at the grid edge scrolls from tab 16 to the first ordinary slot",flush=True)
