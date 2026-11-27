"""Verify the wide glass tab bar in an isolated test window on a tablet emulator."""
import time
from urllib.parse import parse_qs, urlsplit
from PIL import ImageStat


def fixture(handler):
    request = urlsplit(handler.path)
    if request.path == "/strip-status":
        handler.server.strip_theme = parse_qs(request.query)["theme"][0]
        data = b"orange" if handler.server.orange else b"blue"
        kind = "text/plain"
    else:
        data = """<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
        <title>海岸观测</title><style>:root{color-scheme:light dark}body{background:#255694;color:white;
        padding:10vh 7vw;font:21px sans-serif}body.orange{background:#ae612c}</style>
        <h1>海岸观测</h1><p>潮线与岩层</p><script>
        const media=matchMedia('(prefers-color-scheme:dark)');
        async function observe(){let r=await fetch('/strip-status?theme='+(media.matches?'dark':'light'));
        document.body.classList.toggle('orange',(await r.text())==='orange');}
        setInterval(observe,300);observe();</script>""".encode()
        kind = "text/html;charset=utf-8"
    handler.send_response(200)
    handler.send_header("Cache-Control","no-store")
    handler.send_header("Content-Type",kind)
    handler.send_header("Content-Length",str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


def verify(shell,locate,shot,server):
    def tap(name):
        shell("input","tap",*locate(name))
    def hold(name):
        xy=locate(name)
        shell("input","swipe",*xy,*xy,680)
    def ctrl(key):
        shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_"+key)
    def wait_theme(expected):
        limit=time.monotonic()+12
        while time.monotonic()<limit and server.strip_theme!=expected: time.sleep(.2)
        assert server.strip_theme==expected,(server.strip_theme,expected)
    def patches(frame,y):
        return [ImageStat.Stat(frame.crop((x-4,y-4,x+4,y+4))).mean
                for x in (frame.width//8,frame.width*7//8)]
    server.strip_theme=None
    server.orange=False
    base=f"http://127.0.0.1:{server.server_port}"
    previous=shell("cmd","uimode","night").decode().strip().split()[-1]
    assert previous in ("yes","no","auto"),previous
    try:
        shell("cmd","uimode","night","no")
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
              "-d",base+"/glass-coast")
        hold("浏览峡谷")
        tap("在新窗口中打开")
        locate("标签页，1 个")
        ctrl("T")
        locate("网址或搜索词")
        shell("input","text",base+"/strip-document")
        shell("input","keyevent","KEYCODE_ENTER")
        locate("标签页，2 个")
        wait_theme("light")
        _,y=locate("峡谷")
        assert y<shot("tablet-blue").height*.20,y
        blue=patches(shot("tablet-blue"),y)
        server.orange=True
        deadline=time.monotonic()+8
        while True:
            orange=patches(shot("tablet-orange"),y)
            if all(c[0]-c[2]>8 for c in orange) or time.monotonic()>deadline: break
            time.sleep(.2)
        assert all(c[2]-c[0]>8 for c in blue) and all(c[0]-c[2]>8 for c in orange),(blue,orange)
        print("PASS active and inactive tab glass sample the changing web page",blue,orange,flush=True)
        shell("cmd","uimode","night","yes")
        wait_theme("dark")
        locate("标签页，2 个")
        shot("tablet-night")
        shell("cmd","uimode","night","no")
        wait_theme("light")
        print("PASS same open document observes dark and light system appearance",flush=True)
        hold("标签页，2 个")
        tap("关闭窗口")
    finally:
        shell("cmd","uimode","night",previous)
