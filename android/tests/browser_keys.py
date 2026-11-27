"""Exercise real key events in a separate browser window with its own tab order."""
import subprocess
import re
import time
from xml.etree import ElementTree


def editor(handler):
    count = getattr(handler.server, "editor_requests", 0) + 1
    handler.server.editor_requests = count
    html = f"""<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'>
      <title>观测笔记</title><style>body{{margin:8%;background:#e4eadf;color:#263f3c;font:20px sans-serif}}
      input{{font:20px sans-serif;padding:12px;max-width:80%}}</style><h1>观测笔记</h1>
      <p>载入次数 {count}</p><input aria-label='观测记录' oninput='statusLine.textContent="记录 "+this.value'>
      <p id=statusLine></p>""".encode()
    handler.send_response(200)
    handler.send_header("Cache-Control","no-store")
    handler.send_header("Content-Type","text/html;charset=utf-8")
    handler.send_header("Content-Length",str(len(html)))
    handler.end_headers()
    handler.wfile.write(html)


def verify(adb,shell,locate,port,prefix,movie):
    base = f"http://127.0.0.1:{port}"
    def document(title, input_field=False):
        deadline = time.monotonic()+30
        while time.monotonic()<deadline:
            if b"dumped to" not in shell("uiautomator","dump","/sdcard/terra-keys.xml"): continue
            tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-keys.xml"))
            for node in tree.iter("node"):
                if node.get("class")!="android.webkit.WebView" or node.get("text")!=title: continue
                if not input_field: return
                for child in node.iter("node"):
                    if child.get("class")=="android.widget.EditText":
                        x,y,r,b = map(int,re.findall(r"-?\d+",child.get("bounds")))
                        return (x+r)//2,(y+b)//2
        raise AssertionError(("active web document",title))

    def tap(label):
        shell("input","tap",*locate(label))
    def hold(label):
        xy = locate(label)
        shell("input","swipe",*xy,*xy,700)
    def chord(*keys):
        shell("input","keycombination",*["KEYCODE_"+k for k in keys])
    def ctrl(*keys):
        chord("CTRL_LEFT",*keys)
    def type_address(path):
        locate("网址或搜索词")
        shell("input","text",base+path)
    def escape():
        shell("input","keyevent","KEYCODE_ESCAPE")
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",base+"/glass-coast")
    hold("浏览峡谷")
    tap("在新窗口中打开")
    document("峡谷")
    locate("标签页，1 个")
    tap("浏览海岸")
    document("海岸")
    ctrl("LEFT_BRACKET")
    document("峡谷")
    ctrl("RIGHT_BRACKET")
    document("海岸")
    print("PASS Ctrl page history",flush=True)
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","32","/sdcard/terra-keys.mp4"])
    ctrl("SHIFT_LEFT","BACKSLASH")
    locate("搜索标签页")
    tap("搜索标签页")
    locate("取消")
    escape()
    locate("搜索标签页")
    ctrl("SHIFT_LEFT","BACKSLASH")
    document("海岸")
    print("PASS keyboard overview opens, cancels search and returns to the same document",flush=True)
    ctrl("T")
    type_address("/keys-editor")
    ctrl("L")
    type_address("/glass-canyon")
    shell("input","keyevent","KEYCODE_ENTER")
    document("峡谷")
    locate("标签页，2 个")
    ctrl("L")
    type_address("/keys-editor")
    ctrl("ENTER")
    document("峡谷")
    locate("标签页，3 个")
    ctrl("9")
    locate("载入次数 1")
    shell("input","tap",*document("观测笔记",input_field=True))
    shell("input","text","tide")
    locate("记录 tide")
    ctrl("A")
    shell("input","text","coast")
    locate("记录 coast")
    chord("CTRL_LEFT","ALT_LEFT","L")
    shell("input","text","line")
    locate("记录 coastline")
    ctrl("R")
    locate("载入次数 2")
    print("PASS address replacement, background tab, editing shortcuts, AltGr and reload",flush=True)
    ctrl("TAB")
    document("海岸")
    ctrl("SHIFT_LEFT","TAB")
    document("观测笔记")
    ctrl("1")
    document("海岸")
    ctrl("SHIFT_LEFT","RIGHT_BRACKET")
    document("峡谷")
    ctrl("L")
    type_address("/keys-editor")
    escape()
    document("峡谷")
    ctrl("L")
    type_address("/keys-editor")
    ctrl("SHIFT_LEFT","ENTER")
    document("观测笔记")
    locate("标签页，4 个")
    ctrl("W")
    locate("标签页，3 个")
    ctrl("SHIFT_LEFT","T")
    document("观测笔记")
    locate("标签页，4 个")
    print("PASS group-relative tab selection, wraparound, Escape, foreground open, close and reopen",flush=True)
    ctrl("2")
    document("峡谷")
    ctrl("SHIFT_LEFT","BACKSLASH")
    locate("搜索标签页")
    hold("峡谷")
    tap("移至标签组")
    tap("新建标签组")
    locate("标签组名称")
    shell("input","text","Keys"+str(port))
    tap("保存")
    locate("Keys"+str(port))
    tap("完成")
    locate("标签页，1 个")
    ctrl("TAB")
    ctrl("9")
    document("峡谷")
    ctrl("T")
    type_address("/glass-coast")
    shell("input","keyevent","KEYCODE_ENTER")
    locate("标签页，2 个")
    ctrl("1")
    document("峡谷")
    print("PASS numbered and cyclic shortcuts stay inside the named group",flush=True)
    hold("标签页，2 个")
    tap("新建无痕标签页")
    locate("无痕浏览")
    ctrl("T")
    type_address("/glass-coast")
    shell("input","keyevent","KEYCODE_ENTER")
    document("海岸")
    locate("标签页，2 个")
    ctrl("SHIFT_LEFT","T")
    locate("标签页，2 个")
    ctrl("1")
    locate("无痕浏览")
    hold("标签页，2 个")
    tap("关闭窗口")
    document("海岸")
    if recording:
        recording.wait(timeout=36)
        adb("pull","/sdcard/terra-keys.mp4",str(movie))
    print("PASS private new tab and selection remain inside the private session",flush=True)
