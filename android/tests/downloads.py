"""Serve unique attachments and verify the in-app downloads UI on a dedicated Android emulator."""
import hashlib
import subprocess
from threading import Event
import time
from PIL import ImageStat


def payload(port):
    return (f"Terra field notes {port}\n" + "Coast observations and tidal measurements.\n"*18000).encode()


def fixture(handler):
    port = handler.server.server_port
    if handler.path == "/transfer-theme":
        data = b"warm" if getattr(handler.server,"warm",False) else b"cool"
        handler.send_response(200)
        handler.send_header("Cache-Control","no-store")
        handler.send_header("Content-Length",str(len(data)))
        handler.end_headers()
        handler.wfile.write(data)
        return
    if handler.path == "/transfer-index":
        data = f"""<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
        <title>海岸手记</title><style>body{{margin:0;padding:12vh 7vw;min-height:90vh;color:white;
        font:18px sans-serif;background:linear-gradient(155deg,#174c70,#357fab 45%,#76adc2)}}
        body.warm{{background:linear-gradient(155deg,#933e24,#ba7137 45%,#d5a15b)}}
        a{{display:block;color:white;margin:1.5em 0}}</style><h1>海岸手记</h1>
        <a href='/transfer-coast'>下载观测记录</a><a href='/transfer-tides'>下载潮汐记录</a>
        <a href='/transfer-retry'>下载勘测日记</a><script>setInterval(async()=>{{
        const r=await fetch('/transfer-theme');document.body.classList.toggle('warm',(await r.text())==='warm')
        }},350)</script>""".encode()
        handler.send_response(200)
        handler.send_header("Content-Type","text/html;charset=utf-8")
        handler.send_header("Content-Length",str(len(data)))
        handler.end_headers()
        handler.wfile.write(data)
        return
    names = {"/transfer-coast":"Coast","/transfer-tides":"Tides","/transfer-retry":"Survey"}
    name = names[handler.path]
    if name == "Survey" and getattr(handler.server,"reject",False):
        handler.send_error(404)
        return
    data = payload(port)
    handler.send_response(200)
    handler.send_header("Content-Type","text/plain")
    handler.send_header("Content-Disposition",f'attachment; filename="{name}-{port}.txt"')
    handler.send_header("Content-Length",str(len(data)))
    handler.end_headers()
    try:
        chunk = len(data)//8
        for i in range(4):
            handler.wfile.write(data[i*chunk:(i+1)*chunk])
            handler.wfile.flush()
            time.sleep(.4)
        offset = 4*chunk
        deadline = time.monotonic()+160
        while not handler.server.gates[name].wait(.5):
            if time.monotonic()>deadline: return
            handler.wfile.write(data[offset:offset+1024])
            handler.wfile.flush()
            offset += 1024
        handler.wfile.write(data[offset:])
    except (BrokenPipeError,ConnectionResetError,ConnectionAbortedError):
        pass


def verify(adb,shell,locate,shot,server,tablet,prefix,movie):
    server.gates = {key:Event() for key in ("Coast","Tides")}
    port = server.server_port
    coast, tides = (f"{name}-{port}.txt" for name in ("Coast","Tides"))
    url = f"http://127.0.0.1:{port}/transfer-index"
    def tap(label):
        shell("input","tap",*locate(label))
    def visit():
        shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",url)
        locate("下载观测记录")
    def begin(link,name):
        tap(link)
        locate(name)
        tap("保存到下载目录")
    def saved_file():
        return adb("exec-out","cat","/sdcard/Download/"+coast)
    def assert_absent(label):
        deadline = time.monotonic()+15
        while time.monotonic()<deadline:
            if b"dumped to" in shell("uiautomator","dump","/sdcard/terra-downloads.xml"):
                if label.encode() not in adb("exec-out","cat","/sdcard/terra-downloads.xml"): return
            time.sleep(.2)
        raise AssertionError(label)

    try:
        visit()
        begin("下载观测记录",coast)
        if movie:
            recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","22","/sdcard/terra-transfers.mp4"])
            time.sleep(.6)
        tap("查看下载")
        locate("下载中 · ",True)
        frame = shot("transfer-running")
        x,y = locate(coast)
        assert (y < frame.height*.40 if tablet else y > frame.height*.43),(x,y,frame.size)
        dx,dy = locate("打开系统下载目录")
        patch = (dx+90,dy-3,dx+97,dy+4)
        blue = ImageStat.Stat(frame.crop(patch)).mean
        server.warm = True
        deadline = time.monotonic()+8
        while True:
            orange = ImageStat.Stat(shot("transfer-warm").crop(patch)).mean
            if orange[0]-orange[2]>5 or time.monotonic()>deadline: break
            time.sleep(.3)
        assert blue[2]-blue[0]>5 and orange[0]-orange[2]>5,(patch,blue,orange)
        print("PASS open download glass follows the underlying web page",blue,orange,flush=True)
        server.gates["Coast"].set()
        locate("移除记录 "+coast)
        assert hashlib.sha256(saved_file()).digest() == hashlib.sha256(payload(port)).digest()
        if movie:
            recording.wait(timeout=25)
            adb("pull","/sdcard/terra-transfers.mp4",str(movie))
        print("PASS a real transfer reports progress and saves every byte",flush=True)
        tap(coast)
        time.sleep(.8)
        focus = shell("dumpsys","activity","activities").decode(errors="replace")
        assert any("htmlviewer" in line.lower() and "resumedactivity" in line.lower() for line in focus.splitlines()), "text viewer did not open"
        shell("input","keyevent","KEYCODE_BACK")
        time.sleep(.9)
        locate(coast)
        tap("完成")
        begin("下载潮汐记录",tides)
        tap("查看下载")
        tap("取消下载 "+tides)
        tap("取消")
        locate("取消下载 "+tides)
        tap("取消下载 "+tides)
        tap("停止下载")
        locate(coast)
        assert_absent(tides)
        server.gates["Tides"].set()
        tap("完成")
        shell("am","force-stop","app.terra.browser")
        visit()
        tap("查看下载")
        locate(coast)
        assert_absent(tides)
        assert tides not in shell("ls","/sdcard/Download").decode().splitlines()
        shot("transfer-complete")
        tap("完成")
        point = locate("标签页，",True)
        shell("input","swipe",*point,*point,680)
        tap("新建无痕标签页")
        locate("无痕起始页")
        assert_absent("查看下载")
        shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_L")
        locate("网址或搜索词")
        shell("input","text",url)
        shell("input","keyevent","KEYCODE_ENTER")
        begin("下载潮汐记录",tides)
        tap("查看下载")
        locate("移除记录 "+tides)
        locate(tides)
        assert_absent(coast)
        tap("完成")
        point = locate("标签页，1 个")
        shell("input","swipe",*point,*point,680)
        tap("关闭标签页")
        locate("下载观测记录")
        tap("查看下载")
        locate(coast)
        assert_absent(tides)
        tap("移除记录 "+coast)
        assert saved_file() == payload(port)
        tap("完成")
        print("PASS open, cancel confirmation, restart recovery and forgetting a record while preserving its file",flush=True)
        print("PASS private files stay out of the ordinary download list",flush=True)
    finally:
        for gate in server.gates.values(): gate.set()


def verify_retry(adb,shell,locate,server):
    port = server.server_port
    original = f"Survey-{port}.txt"
    duplicate = f"Survey-{port} (2).txt"
    server.gates = {"Survey":Event()}
    server.gates["Survey"].set()
    def tap(label):
        shell("input","tap",*locate(label))
    shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
          "-d",f"http://127.0.0.1:{port}/transfer-index")
    tap("下载勘测日记")
    locate(original)
    server.reject = True
    tap("保存到下载目录")
    tap("查看下载")
    locate("下载失败 · 点按重试",True)
    server.reject = False
    tap(original)
    tap("保存到下载目录")
    locate("移除记录 "+original)
    assert adb("exec-out","cat","/sdcard/Download/"+original) == payload(port)
    tap("完成")
    tap("下载勘测日记")
    tap("保存到下载目录")
    tap("查看下载")
    locate("移除记录 "+duplicate)
    for name in (duplicate,original):
        assert adb("exec-out","cat","/sdcard/Download/"+name) == payload(port)
        tap("移除记录 "+name)
    tap("完成")
    print("PASS retry after HTTP failure and preserve both same-name downloads byte for byte",flush=True)
