"""Verify the address bar with an actual held finger, clipboard round trip and a changing page."""
import time
import subprocess
from urllib.parse import parse_qs, urlsplit
from xml.etree import ElementTree
from PIL import ImageStat


def verify(shell,locate,shot,site,tablet,prefix,movie):
    base = f"http://127.0.0.1:{site.server_port}"
    target = base+"/strip-document?section=coast#tide"
    site.strip_theme = None
    site.orange = False
    address = "不安全 · 127.0.0.1"

    def tap(label):
        shell("input","tap",*locate(label))

    def visit(url):
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",url)

    def hold(label):
        xy = locate(label)
        shell("input","swipe",*xy,*xy,720)

    def drag_to(label):
        start = locate(address)
        shell("input","motionevent","DOWN",*start)
        point = start
        try:
            point = locate(label)
            shell("input","motionevent","MOVE",*point)
            time.sleep(.25)
        finally:
            shell("input","motionevent","UP",*point)

    visit(target)
    locate("潮线与岩层")
    drag_to("复制网址")
    locate(address)
    visit(base+"/garden")
    locate("Garden")
    drag_to("粘贴并前往")
    locate("潮线与岩层")
    hold(address)
    tap("编辑地址")
    locate(target)
    shell("input","keyevent","KEYCODE_ESCAPE")
    _,anchor_y = locate(address)
    print("PASS a single held gesture copies and opens the full URL including query and fragment",flush=True)

    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","16","/sdcard/terra-address-menu.mp4"])
        time.sleep(.6)
    hold(address)
    x,y = locate("编辑地址")
    assert (y > anchor_y if tablet else y < anchor_y), (y,anchor_y)
    # Sample the empty space past the label, inside the same row of glass.
    x += 83
    patch = (x-3,y-3,x+4,y+4)
    blue = ImageStat.Stat(shot("address-blue").crop(patch)).mean
    site.orange = True
    limit = time.monotonic()+7
    while True:
        orange = ImageStat.Stat(shot("address-orange").crop(patch)).mean
        if orange[0]-orange[2]>7 or time.monotonic()>limit: break
        time.sleep(.25)
    assert blue[2]-blue[0]>7 and orange[0]-orange[2]>7,(patch,blue,orange)
    shell("input","keyevent","KEYCODE_BACK")
    locate(address)
    if movie:
        recording.wait(timeout=20)
        subprocess.run(prefix+["pull","/sdcard/terra-address-menu.mp4",str(movie)],check=True)
    print("PASS open address menu refracts live page changes",blue,orange,flush=True)

    start = locate(address)
    shell("input","motionevent","DOWN",*start)
    try:
        locate("粘贴并前往")
        shell("input","motionevent","MOVE",2,anchor_y)
    finally:
        shell("input","motionevent","UP",2,anchor_y)
    locate("潮线与岩层")
    print("PASS releasing outside the menu returns to the same document",flush=True)

    tap("页面菜单")
    tap("收起工具栏")
    hold("显示工具栏")
    tap("编辑地址")
    locate(target)
    shell("input","keyevent","KEYCODE_ESCAPE")
    print("PASS address actions remain accessible on the compact toolbar",flush=True)


def verify_search(adb,shell,locate,port):
    if shell("getprop","ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("The offline search check requires a dedicated emulator")
    airplane = shell("cmd","connectivity","airplane-mode").decode().strip()
    wifi = shell("cmd","wifi","status").decode().splitlines()[0]
    assert airplane in ("enabled","disabled") and wifi in ("Wifi is enabled","Wifi is disabled")
    component = "app.terra.browser/.MainActivity"
    local_url = f"http://127.0.0.1:{port}/sky"
    def visit():
        shell("am","start","-W","-n",component,"-a","android.intent.action.VIEW","-d",local_url)
    def ctrl(key):
        shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_"+key)
    visit()
    locate("Sky")
    ctrl("L")
    locate("网址或搜索词")
    phrase = "Terra offline address check"
    shell("input","text",phrase.replace(" ","%s"))
    locate(phrase)
    ctrl("A")
    ctrl("C")
    shell("input","keyevent","KEYCODE_ESCAPE")
    try:
        shell("cmd","connectivity","airplane-mode","enable")
        shell("svc","wifi","disable")
        deadline = time.monotonic()+10
        while time.monotonic()<deadline:
            try: shell("ip","route","get","1.1.1.1")
            except subprocess.CalledProcessError: break
            time.sleep(.25)
        else: raise AssertionError("Emulator still has an external route")
        xy = locate("不安全 · 127.0.0.1")
        shell("input","swipe",*xy,*xy,720)
        shell("input","tap",*locate("粘贴并前往"))
        locate("网页载入失败")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-motion.xml"))
        engines = {"www.bing.com","www.google.com","duckduckgo.com"}
        domain = next(n.get("content-desc") for n in tree.iter("node") if n.get("content-desc") in engines)
        shell("input","tap",*locate(domain))
        locate("网址或搜索词")
        tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-motion.xml"))
        field = next(n for n in tree.iter("node") if n.get("class")=="android.widget.EditText")
        dest = urlsplit(field.get("text",""))
        assert dest.hostname == domain and parse_qs(dest.query).get("q")==[phrase]
        shell("input","keyevent","KEYCODE_ESCAPE")
        visit()
    finally:
        if airplane=="disabled": shell("cmd","connectivity","airplane-mode","disable")
        shell("svc","wifi","enable" if wifi=="Wifi is enabled" else "disable")
    print("PASS pasted words form the configured engine's search URL while offline",flush=True)
