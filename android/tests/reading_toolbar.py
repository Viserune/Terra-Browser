"""Exercise scrolling chrome, recovery and reading position on an installed Terra test device."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import re
import shutil
import subprocess
from threading import Thread
import time
from xml.etree import ElementTree


class Article(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/offset/"):
            self.server.position = int(self.path.split("/")[-1])
            self.send_response(204)
            self.end_headers()
            return
        sections = ["<section style='background:%s'><h1>%s</h1></section>" % pair
                    for pair in [("#285b88","Morning"),("#b96542","Afternoon"),("#557e65","Evening"),("#424a72","Night")]*4]
        body = ("<meta name=viewport content='width=device-width,initial-scale=1'><title>Observatory</title>"
                "<style>body{margin:0;color:white;font:20px Georgia,serif}section{padding:18vh 8vw;height:78vh;"
                "box-sizing:border-box}h1{font-size:38px}</style>" +
                ("<h1>Short page</h1>" if self.path=="/short" else "".join(sections)) +
                "<script>let t;onscroll=()=>{clearTimeout(t);t=setTimeout(()=>fetch('/offset/'+Math.round(scrollY)),80)}</script>").encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html")
        self.send_header("Content-Length",str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self,*args):
        pass


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--tablet",action="store_true")
    parser.add_argument("--record",type=Path)
    args=parser.parse_args()
    exe=shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name=="nt" else "adb"))
    prefix=[exe,"-s",args.serial]

    def adb(*cmd):
        return subprocess.check_output(prefix+list(map(str,cmd)),timeout=20)

    def shell(*cmd):
        return adb("shell",*cmd)

    def controls():
        shell("uiautomator","dump","/sdcard/terra-reading.xml")
        nodes=ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-reading.xml")).iter("node")
        result={}
        for node in nodes:
            x,y,r,b=map(int,re.findall(r"\d+",node.get("bounds")))
            for key in ("content-desc","text"):
                if node.get(key):
                    result[node.get(key)]=((x+r)//2,(y+b)//2)
        return result

    def expect(visible,absent=None):
        end=time.monotonic()+10
        while time.monotonic()<end:
            nodes=controls()
            if visible in nodes and (not absent or absent not in nodes):
                return nodes[visible]
        raise AssertionError((visible,absent,list(nodes)[-35:]))

    with ThreadingHTTPServer(("127.0.0.1",0),Article) as server:
        Thread(target=server.serve_forever,daemon=True).start()
        server.position=0
        port=server.server_port
        adb("reverse",f"tcp:{port}",f"tcp:{port}")
        recorder=None
        try:
            def launch(path):
                shell("am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                      "-d",f"http://127.0.0.1:{port}/"+path)
            launch("short")
            expect("Short page")
            launch("article")
            w,h=map(int,re.findall(r"(\d+)x(\d+)",shell("wm","size").decode())[-1])
            density=int(re.findall(r"(\d+)",shell("wm","density").decode())[-1])/160
            x,y=w//2,h//2
            expect("Morning")
            _,expandedY=expect("页面菜单","显示工具栏")
            shell("input","motionevent","DOWN",x,y)
            for dp in (10,20,35,50):
                shell("input","motionevent","MOVE",x,y-round(dp*density))
            try:
                nodes=controls()
                assert "页面菜单" in nodes, ("Missing intermediate state",list(nodes)[-24:])
                moved=nodes["页面菜单"][1]-expandedY
                assert (moved if not args.tablet else -moved)>3*density, ("Toolbar did not follow the held drag",expandedY,moved)
            finally:
                shell("input","motionevent","UP",x,y-round(50*density))
            _,bar=expect("显示工具栏","页面菜单")
            assert (bar<h//2)==args.tablet
            print("PASS toolbar follows a held scroll, then settles at the reading edge",flush=True)
            shell("input","swipe",x,int(h*.72),x,int(h*.3),550)
            pill=expect("显示工具栏","页面菜单")
            if not args.tablet:
                shell("input","swipe",*pill,pill[0],pill[1]-round(90*density),300)
                expect("排列标签页")
                shell("input","tap",*expect("完成"))
                pill=expect("显示工具栏","页面菜单")
                shell("input","swipe",*pill,pill[0]+round(90*density),pill[1],250)
                expect("Short page")
                address=expect("不安全 · 127.0.0.1")
                shell("input","swipe",*address,address[0]-round(90*density),address[1],250)
                pill=expect("显示工具栏","页面菜单")
                print("PASS compact address swipes can open overview and switch away and back",flush=True)
            before=server.position
            assert before>60
            shell("input","tap",*pill)
            expect("页面菜单","显示工具栏")
            assert abs(before-server.position)<=2,(before,server.position)
            print("PASS tap restores all controls without changing document scroll position",flush=True)
            shell("input","swipe",x,int(h*.72),x,int(h*.40),450)
            expect("显示工具栏","页面菜单")
            shell("input","swipe",x,int(h*.40),x,int(h*.58),450)
            expect("页面菜单","显示工具栏")
            print("PASS reversing the scroll brings the expanded toolbar back",flush=True)
            shell("input","tap",*expect("页面菜单"))
            shell("input","tap",*expect("收起工具栏"))
            expect("显示工具栏","页面菜单")
            shell("input","swipe",x,int(h*.38),x,int(h*.60),450)
            shell("input","tap",*expect("显示工具栏","页面菜单"))
            expect("页面菜单","显示工具栏")
            launch("short")
            expect("Short page")
            shell("input","swipe",x,int(h*.7),x,int(h*.35),450)
            expect("页面菜单","显示工具栏")
            print("PASS manual hiding survives scrolling, and an unscrollable page keeps its controls",flush=True)
            if args.record:
                launch("article")
                expect("Morning")
                expect("页面菜单","显示工具栏")
                recorder=subprocess.Popen(prefix+["shell","screenrecord","--time-limit","8","/sdcard/terra-reading.mp4"])
                time.sleep(.7)
                for start,end in ((.66,.41),(.42,.60),(.66,.41)):
                    shell("input","swipe",x,int(h*start),x,int(h*end),700)
                    time.sleep(.65)
                shell("input","tap",w//2,bar)
                recorder.wait(timeout=15)
                adb("pull","/sdcard/terra-reading.mp4",str(args.record))
        finally:
            if recorder and recorder.poll() is None: recorder.wait(timeout=15)
            adb("reverse","--remove",f"tcp:{port}")
            server.shutdown()


if __name__=="__main__":
    main()
