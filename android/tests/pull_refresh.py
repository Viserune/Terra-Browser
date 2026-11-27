"""Exercise pull-to-refresh with real input, measured page pixels and an HTTP reload counter."""
import argparse
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import os
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
from xml.etree import ElementTree
from PIL import Image


class Pages(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/receipt":
            self.send_error(404)
            return
        self.rfile.read(int(self.headers.get("Content-Length",0)))
        self.server.requests["/receipt"] += 1
        self.page("<h1>Receipt %d</h1>" % self.server.requests["/receipt"])

    def page(self, markup):
        body=("<meta name=viewport content='width=device-width,initial-scale=1'>"+markup).encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html;charset=utf-8")
        self.send_header("Content-Length",str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        name = self.path.split("?")[0]
        if name == "/form":
            self.page("<form method=post action=/receipt><button>Send sample</button></form>")
            return
        if name not in ("/article","/short","/nested","/blocked","/custom"):
            self.send_error(404)
            return
        number = self.server.requests[name] + 1
        self.server.requests[name] = number
        if name == "/article" and number == 2:
            self.server.resume.wait(35)
        style = "html{overscroll-behavior-y:none}" if name == "/blocked" else ""
        nested = "<div id='list'><div style='height:2800px'>Inner list</div></div>" if name == "/nested" else ""
        script = {"/nested": "const listBox=document.getElementById('list');listBox.scrollTop=600;listBox.onscroll=()=>statusBox.value='Inner '+Math.round(listBox.scrollTop)",
                  "/custom": "addEventListener('touchmove',e=>{e.preventDefault();statusBox.value='Handled drag'},{passive:false})"}.get(name,"")
        height = "75vh" if name == "/short" else "260vh"
        body = f"""<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'><title>Coastal log</title>
        <style>body{{margin:0;background:#326f88;color:white;font:20px sans-serif}}main{{height:{height};padding-top:64px}}
        hr{{margin:0;border:0;height:9px;background:#db5881}}h2{{margin:20px}}#list{{position:fixed;inset:20% 12% 26%;background:#163e68;
        overflow:auto}}output{{position:fixed;background:#173241;right:0;bottom:22%}}{style}</style>
        <main><hr><h2>Coastal log {number}</h2>{nested}</main><output id=statusBox>Ready</output>
        <script>onscroll=()=>statusBox.value='Page '+Math.round(scrollY);{script}</script>""".encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html;charset=utf-8")
        self.send_header("Cache-Control","no-store")
        self.send_header("Content-Length",str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self,*args):
        pass


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--record",type=Path)
    parser.add_argument("--form",action="store_true",help="verify cancel and confirm on a POST response")
    args=parser.parse_args()
    exe=shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe"))
    prefix=[exe,"-s",args.serial]
    def adb(*cmd):
        return subprocess.check_output(prefix+list(map(str,cmd)),timeout=20)
    def shell(*cmd):
        return adb("shell",*cmd)
    def nodes():
        shell("uiautomator","dump","/sdcard/terra-pull.xml")
        return ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-pull.xml")).iter("node")
    def labels():
        return {v for n in nodes() for v in (n.get("text"),n.get("content-desc")) if v}
    def tap(label):
        for n in nodes():
            if label in (n.get("text"),n.get("content-desc")):
                l,t,r,b=map(int,re.findall(r"-?\d+",n.get("bounds")))
                if r>l and b>t:
                    shell("input","tap",(l+r)//2,(t+b)//2)
                    return
        raise AssertionError(label)
    def frame():
        return Image.open(BytesIO(adb("exec-out","screencap","-p"))).convert("RGB")
    def stripe():
        im=frame()
        x=im.width//5
        return next((y for y in range(im.height) if im.getpixel((x,y))==(219,88,129)),-1)
    def wait(predicate, timeout=11):
        deadline=time.monotonic()+timeout
        while time.monotonic()<deadline:
            if predicate(): return
            time.sleep(.12)
        raise AssertionError("Timed out: "+str(predicate))
    density=int(re.findall(r"(\d+)",shell("wm","density").decode())[-1])/160
    def position():
        w,h=frame().size
        return w,h,w//2,int(h*.45)
    w,h,x,y=position()
    def touch(action,offset):
        shell("input","motionevent",action,x,round(y+offset*density))
    def begin(distance):
        # Several moves let the web renderer acknowledge the scroll before the finger stops.
        touch("DOWN",0)
        for step in (12,25,40,distance): touch("MOVE",step)
        time.sleep(.1)
    with ThreadingHTTPServer(("127.0.0.1",0),Pages) as server:
        server.requests=Counter()
        server.resume=threading.Event()
        threading.Thread(target=server.serve_forever,daemon=True).start()
        port=f"tcp:{server.server_port}"
        adb("reverse",port,port)
        def visit(name):
            nonlocal w,h,x,y
            shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",
                  f"http://127.0.0.1:{server.server_port}/{name}")
            wait(lambda: server.requests["/"+name]==1 and "Coastal log 1" in labels(),timeout=45)
            time.sleep(.35)
            w,h,x,y=position()
        recorder=None
        try:
            if args.form:
                shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                      "-d",f"http://127.0.0.1:{server.server_port}/form")
                wait(lambda: "Send sample" in labels(),timeout=45)
                w,h,x,y=position()
                tap("Send sample")
                wait(lambda: "Receipt 1" in labels())
                for choice in ("取消","重新提交"):
                    begin(160)
                    touch("UP",160)
                    wait(lambda: "重新提交表单？" in labels())
                    assert server.requests["/receipt"]==1
                    tap(choice)
                    wait(lambda: "重新提交表单？" not in labels())
                    if choice=="取消": assert server.requests["/receipt"]==1
                wait(lambda: server.requests["/receipt"]==2 and "Receipt 2" in labels())
                print("PASS cancelled form refresh kept one POST and confirmed refresh sent exactly one more",flush=True)
                return
            visit("article")
            base=stripe()
            assert base>0,base
            if args.record:
                recorder=subprocess.Popen(prefix+["shell","screenrecord","--time-limit","20","/sdcard/terra-pull.mp4"])
                time.sleep(.5)
            begin(60)
            wait(lambda: "下拉刷新" in labels())
            partial=stripe()
            assert base+8*density<partial<base+60*density,(base,partial)
            print("Short pull marker",base,partial,flush=True)
            touch("UP",60)
            wait(lambda: abs(stripe()-base)<=2)
            begin(155)
            wait(lambda: "松开刷新" in labels())
            held=stripe()
            assert held>partial+10*density,(partial,held)
            touch("MOVE",32)
            touch("UP",32)
            wait(lambda: abs(stripe()-base)<=2)
            assert server.requests["/article"]==1
            begin(155)
            touch("CANCEL",155)
            wait(lambda: abs(stripe()-base)<=2)
            assert server.requests["/article"]==1
            begin(155)
            touch("UP",155)
            wait(lambda: server.requests["/article"]==2)
            requested = time.monotonic()
            assert "正在刷新" in labels()
            begin(150)
            touch("UP",150)
            assert server.requests["/article"]==2
            time.sleep(max(0,9-(time.monotonic()-requested)))
            assert "正在刷新" in labels(), "Loading indicator ended before the server responded"
            server.resume.set()
            wait(lambda: "Coastal log 2" in labels() and abs(stripe()-base)<=2)
            assert server.requests["/article"]==2
            print("PASS progressive page displacement, short release, disarm, single HTTP reload, loading indicator and return",flush=True)
            shell("input","swipe",x,int(h*.72),x,int(h*.22),320)
            time.sleep(.45)
            shell("input","swipe",x,y,x,round(y+165*density),420)
            assert server.requests["/article"]==2
            print("PASS downward scroll inside a long document does not reload it",flush=True)
            for name in ("nested","blocked","custom"):
                visit(name)
                if name=="nested": wait(lambda: "Inner 600" in labels())
                begin(155)
                touch("UP",155)
                time.sleep(.5)
                assert server.requests["/"+name]==1,name
                if name=="nested": assert any(re.fullmatch(r"Inner \d+",s) and int(s[6:])<600 for s in labels())
                if name=="custom": assert "Handled drag" in labels()
            print("PASS nested scrolling, site opt-out and page-owned touch handling",flush=True)
            visit("short")
            begin(155)
            touch("UP",155)
            wait(lambda: server.requests["/short"]==2)
            print("PASS refresh on a document shorter than the viewport",flush=True)
        finally:
            server.resume.set()
            if recorder:
                recorder.wait(timeout=23)
                adb("pull","/sdcard/terra-pull.mp4",str(args.record))
            adb("reverse","--remove",port)
            server.shutdown()


if __name__=="__main__":
    main()


