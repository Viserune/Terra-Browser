"""Exercise address search in a separate browser window with known ordinary and private tabs."""
import subprocess
import time
import re
from xml.etree import ElementTree
from PIL import ImageStat


def fixture(handler):
    private = handler.path == "/suggest-private"
    if not private: handler.server.note_loads += 1
    title = ("LunarStudy" if private else "TideLog") + str(handler.server.server_port)
    page = f"""<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
    <title>{title}</title><style>body{{margin:0;background:#245a76;color:#fff;font:18px sans-serif;
    padding:6vh 8vw;min-height:2200px}}input,button{{font:inherit;padding:.5em}}button{{display:block;margin:1em 0}}
    output{{position:fixed;bottom:28vh;left:8vw}}</style><h1>{title}</h1>
    <label for='draft'>观测草稿</label><br><input id='draft'><button onclick='window.scrollTo(0,900)'>阅读下一节</button>
    <output id='state'></output><script>function sync(){{state.textContent='草稿 '+draft.value+' · 位置 '+Math.round(scrollY)}}
    onscroll=sync;draft.oninput=sync;sync()</script>""".encode()
    handler.send_response(200)
    handler.send_header("Content-Type","text/html;charset=utf-8")
    handler.send_header("Cache-Control","no-store")
    handler.send_header("Content-Length",str(len(page)))
    handler.end_headers()
    handler.wfile.write(page)


def verify(adb,shell,locate,shot,site,prefix,movie,tablet):
    site.note_loads = 0
    site.orange = False
    site.strip_theme = None
    port = site.server_port
    base = f"http://127.0.0.1:{port}"
    note,private,group = (name+str(port) for name in ("TideLog","LunarStudy","Notes"))
    def tap(label):
        shell("input","tap",*locate(label))
    def hold(label):
        p = locate(label)
        shell("input","swipe",*p,*p,680)
    def ctrl(*keys):
        shell("input","keycombination","KEYCODE_CTRL_LEFT",*("KEYCODE_"+k for k in keys))
    def key(code):
        shell("input","keyevent","KEYCODE_"+code)
    def type_text(text):
        locate("网址或搜索词")
        shell("input","text",text)
    def visit(path):
        ctrl("L")
        type_text(base+path)
        key("ENTER")
    def enter_query(text):
        ctrl("L")
        type_text(text)
    def labels():
        for _ in range(5):
            if b"dumped to" in shell("uiautomator","dump","/sdcard/terra-suggest.xml"):
                nodes = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-suggest.xml")).iter("node")
                return {v for n in nodes for v in (n.get("text",""),n.get("content-desc",""))}
        raise AssertionError("could not read the active window")
    def position():
        label = "草稿 salt · 位置 "
        locate(label,True)
        return int(next(s[len(label):] for s in labels() if s.startswith(label)))
    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",base+"/glass-coast")
    hold("浏览峡谷")
    tap("在新窗口中打开")
    locate("标签页，1 个")
    visit("/suggest-note")
    tap("观测草稿")
    shell("input","text","salt")
    locate("草稿 salt · 位置 ",True)
    key("BACK")
    tap("阅读下一节")
    old_y = position()
    assert old_y>700,old_y
    ctrl("T")
    type_text(base+"/strip-document")
    key("ENTER")
    locate("潮线与岩层")
    enter_query(note)
    detail = "切换到标签页 · 普通标签 · "+base.split("://")[1]+"/suggest-note"
    locate(detail)
    tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-motion.xml"))
    rows = [list(map(int,re.findall(r"\d+",n.get("bounds")))) for n in tree.iter("node")
            if n.get("clickable")=="true" and any(ch.get("text")==detail for ch in n.iter("node"))]
    l,t,r,b = min(rows,key=lambda box:(box[2]-box[0])*(box[3]-box[1]))
    field = next(n for n in tree.iter("node") if n.get("class")=="android.widget.EditText")
    fl,ft,fr,fb = map(int,re.findall(r"\d+",field.get("bounds")))
    assert (fb <= t if tablet else ft >= b), (ft,fb,t,b)
    centers = [(r-20,t+8),(fr-15,(ft+fb)//2)]
    def sample(image):
        return [ImageStat.Stat(image.crop((x-3,y-3,x+4,y+4))).mean for x,y in centers]
    bounds_frame = shot("suggest-initial")
    blue = sample(bounds_frame)
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","15","/sdcard/terra-suggest.mp4"])
        time.sleep(.5)
    site.orange = True
    limit = time.monotonic()+8
    while True:
        orange = sample(shot("suggest-warm"))
        if all(c[0]-c[2]>6 for c in orange) or time.monotonic()>limit: break
        time.sleep(.2)
    assert all(c[2]-c[0]>6 for c in blue) and all(c[0]-c[2]>6 for c in orange),(centers,blue,orange)
    key("DPAD_DOWN")
    shot("suggest-selected")
    key("ENTER")
    new_y = position()
    assert abs(new_y-old_y)<=32 and site.note_loads==1,(old_y,new_y,site.note_loads)
    print("PASS keyboard selection returns to the original form and scroll position without another GET",old_y,new_y,flush=True)
    print("PASS suggestion glass follows the live page while typing",blue,orange,flush=True)
    if movie:
        recording.wait(timeout=18)
        adb("pull","/sdcard/terra-suggest.mp4",str(movie))
    ctrl("T")
    type_text(base+"/suggest-note")
    key("ENTER")
    tap("观测草稿")
    shell("input","text","moon")
    locate("草稿 moon · 位置 ",True)
    key("BACK")
    locate("草稿 moon · 位置 0")
    ctrl("2")
    locate("潮线与岩层")
    enter_query(note)
    first = detail.replace("普通标签","普通标签 · 第 1 个标签")
    third = detail.replace("普通标签","普通标签 · 第 3 个标签")
    locate(first)
    tap(third)
    locate("草稿 moon · 位置 0")
    ctrl("W")
    ctrl("2")
    locate("潮线与岩层")
    enter_query(note)
    tap(detail)
    duplicate_y = position()
    assert abs(duplicate_y-old_y)<=32 and site.note_loads==2,(old_y,duplicate_y,site.note_loads)
    print("PASS same-address tabs retain separate drafts and can be picked by their tab positions",flush=True)
    ctrl("SHIFT_LEFT","BACKSLASH")
    hold(note)
    tap("移至标签组")
    tap("新建标签组")
    locate("标签组名称")
    shell("input","text",group)
    tap("保存")
    locate(group)
    tap("标签组")
    tap("普通标签")
    tap("海岸观测")
    locate("潮线与岩层")
    enter_query("suggest-note")
    tap(detail.replace("普通标签",group))
    grouped_y = position()
    assert abs(grouped_y-old_y)<=32 and site.note_loads==2,(old_y,grouped_y,site.note_loads)
    ctrl("SHIFT_LEFT","BACKSLASH")
    locate(group)
    tap("完成")
    print("PASS a URL fragment in the address field finds a tab in another named group",flush=True)
    if "显示工具栏" in labels(): tap("显示工具栏")
    hold("标签页，1 个")
    tap("新建无痕标签页")
    locate("无痕起始页")
    visit("/suggest-private")
    locate(private)
    ctrl("T")
    type_text(note)
    locate("前往")
    assert not any(v.startswith("切换到标签页") for v in labels())
    ctrl("L")
    type_text(private)
    tap("切换到标签页 · 无痕浏览 · "+base.split("://")[1]+"/suggest-private")
    locate("观测草稿")
    ctrl("SHIFT_LEFT","BACKSLASH")
    tap("普通标签")
    tap("完成")
    locate("草稿 salt · 位置 ",True)
    enter_query(private)
    locate("前往")
    assert not any(v.startswith("切换到标签页") for v in labels())
    key("ESCAPE")
    if "显示工具栏" in labels(): tap("显示工具栏")
    point = locate("标签页，",True)
    shell("input","swipe",*point,*point,680)
    tap("关闭窗口")
    print("PASS ordinary and private search results stay in their own browsing mode",flush=True)
