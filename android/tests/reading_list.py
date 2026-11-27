import re
import subprocess
import threading
import time
from xml.etree import ElementTree


def verify_reading_list(adb, shell, locate, port, prefix, apk, movie, glass):
    first, second = (f"{region}手记 · {port}" for region in ("海岸","峡谷"))
    component = "app.terra.browser/.MainActivity"

    def nodes():
        for _ in range(4):
            if b"dumped to" in shell("uiautomator", "dump", "/sdcard/terra-reading-list.xml"):
                return list(ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-reading-list.xml")).iter("node"))
        raise AssertionError("reading list window unavailable")

    def bounds(node):
        return tuple(map(int, re.findall(r"-?\d+", node.get("bounds"))))

    def tap(label):
        shell("input", "tap", *locate(label))

    def selection():
        return {n.get("text") for n in nodes() if n.get("text") in (first, second) and bounds(n)[3] > bounds(n)[1]}

    def visit(coast):
        path = "reading-coast" if coast else "reading-canyon"
        shell("am", "start", "-W", "-n", component, "-a", "android.intent.action.VIEW",
              "-d", f"http://127.0.0.1:{port}/{path}")
        locate("浏览峡谷" if coast else "浏览海岸")

    def page_menu(label):
        tap("页面菜单")
        for _ in range(4):
            items = nodes()
            for node in items:
                if node.get("text") != label: continue
                x, y, r, b = bounds(node)
                if r > x and b - y > 12:
                    shell("input", "tap", (x+r)//2, (y+b)//2)
                    return
            scroll = next((n for n in items if n.get("scrollable") == "true"), None)
            if scroll is not None:
                x, y, r, b = bounds(scroll)
                shell("input", "swipe", (x+r)//2, b-20, (x+r)//2, y+30, 420)
        raise AssertionError(label)

    def mark_while_held(title, label):
        point = locate(title)
        shell("input", "motionevent", "DOWN", *point)
        try:
            point = locate(label)
            shell("input", "motionevent", "MOVE", *point)
            time.sleep(.25)
        finally:
            shell("input", "motionevent", "UP", *point)
        locate("全部")

    visit(coast=True)
    page_menu("加入阅读列表")
    locate("页面菜单")
    if glass:
        from PIL import ImageStat
        server, shot = glass
        server.sheet_change = threading.Event()
        server.sheet_timeout = 150
        shell("am","start","-W","-n",component,"-a","android.intent.action.VIEW","-d",f"http://127.0.0.1:{port}/live-sheet")
        locate("Live glass")
        clear = shot("reading-uncovered-blue")
        blue = clear.getpixel((clear.width//2,clear.height//2))
        assert blue[2]-blue[0] > 30, blue
        page_menu("阅读列表")
        x,y = locate("完成")
        frame = shot("reading-half")
        shell("input","swipe",frame.width//2,y,frame.width//2,y-round(frame.height*.48),480)
        assert locate("完成")[1] < frame.height*.18
        point = locate(first)
        shell("input","swipe",*point,*point,650)
        x,y = locate("标记为已读")
        patch = (x-8,y-23,x,y-17)
        before = ImageStat.Stat(shot("reading-menu-blue").crop(patch)).mean
        server.sheet_change.set()
        time.sleep(1)
        after = ImageStat.Stat(shot("reading-menu-orange").crop(patch)).mean
        assert max(abs(a-b) for a,b in zip(before,after)) < 6 and max(before)-min(before) < 16,(before,after)
        tap("删除")
        tap("完成")
        locate("Live glass")
        page = shot("reading-uncovered")
        pixel = page.getpixel((page.width//2,page.height//2))
        assert pixel[0]-pixel[2]>30,pixel
        print("PASS expanded library shields the glass menu from the changing page",before,after,pixel,flush=True)
        return
    if apk:
        assert b"Success" in adb("install", "-r", str(apk))
    visit(coast=False)
    page_menu("加入阅读列表")
    page_menu("阅读列表")
    tap("未读")
    assert selection() == {first, second}, "new and previously saved pages must both start unread"
    print("PASS saved entries visible in Unread",flush=True)
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","35","/sdcard/terra-reading-list.mp4"])
    mark_while_held(first, "标记为已读")
    assert selection() == {second}, "held action should immediately remove the selected row from Unread"
    print("PASS held action updated the filtered list",flush=True)
    tap("全部")
    assert selection() == {first, second}
    # A full right swipe on the previously read article must reverse its status.
    tree = nodes()
    row = next(n for n in tree if n.get("text") == first)
    _, top, _, bottom = bounds(row)
    parent = {child: node for node in tree for child in node}
    target = row
    while target.get("clickable") != "true":
        target = parent[target]
    left, _, right, _ = bounds(target)
    y = (top+bottom)//2
    start = left+(right-left)//12
    end = right-(right-left)//40
    print("Dragging reading row",start,end,y,flush=True)
    shell("input","motionevent","DOWN",start,y)
    try:
        for step in range(1,11):
            shell("input","motionevent","MOVE",start+(end-start)*step//10,y)
        time.sleep(.2)
    finally:
        shell("input","motionevent","UP",end,y)
    tap("未读")
    assert selection() == {first, second}, "full leading swipe should mark the article unread again"
    print("PASS full leading swipe marked the article unread",flush=True)
    tap(second)
    locate("浏览海岸")
    page_menu("阅读列表")
    assert selection() == {first}, "opening an item from the list should mark it read"
    tap("完成")
    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n",component)
    page_menu("阅读列表")
    tap("未读")
    assert selection() == {first}, "status changed across restart"
    tap("完成")
    page_menu("加入阅读列表")
    page_menu("阅读列表")
    assert selection() == {first, second}, "saving a read page again must return it to Unread"
    tap("全部")
    rows = [n.get("text") for n in nodes() if n.get("text") in (first,second)]
    assert rows == [second,first], ("duplicate or stale order after saving the same URL",rows)
    for title in (first, second):
        mark_while_held(title, "删除")
    assert selection() == set()
    tap("完成")
    if recording:
        recording.wait(timeout=40)
        adb("pull","/sdcard/terra-reading-list.mp4",str(movie))
    print("PASS reading list: migration or fresh save, held action, full right swipe, filtering, open, restart and deletion",flush=True)
