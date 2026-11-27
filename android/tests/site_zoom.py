"""Test webpage text sizing, site scope and privacy on a dedicated Terra test device.
The full run restarts Terra to check persistence. --layout-only checks the rendered controls and text.
"""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import os
from pathlib import Path
import re
import shutil
import subprocess
from threading import Thread
import time
from xml.etree import ElementTree
from PIL import Image, ImageChops


class Article(BaseHTTPRequestHandler):
    def do_GET(self):
        marker = "Second tab" if self.path.endswith("/second") else "First tab"
        html = f"""<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
        <title>Type specimen</title><style>body{{margin:0;background:#fbf7ee;color:#343331}}
        main{{position:absolute;left:8%;top:30%;right:8%}} h1{{color:#24629a;font:normal 28px Georgia,serif;margin:0}}
        p{{font:18px/1.5 sans-serif}}</style><main><h1>Earthrise</h1><p>月面上的地球</p>
        <a href='http://localhost:{self.server.server_port}/other'>Other site</a><p>{marker}</p></main>""".encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html; charset=utf-8")
        self.send_header("Content-Length",str(len(html)))
        self.end_headers()
        self.wfile.write(html)

    def log_message(self,*args):
        pass


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--layout-only",action="store_true")
    parser.add_argument("--tablet",action="store_true")
    parser.add_argument("--record",type=Path)
    args=parser.parse_args()
    exe=shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name=="nt" else "adb"))
    prefix=[exe,"-s",args.serial]
    def adb(*cmd):
        return subprocess.check_output(prefix+list(map(str,cmd)),timeout=20)

    def shell(*cmd):
        return adb("shell",*cmd)

    def locate(label,starts=False):
        end=time.monotonic()+22
        while time.monotonic()<end:
            if b"dumped to" not in shell("uiautomator","dump","/sdcard/terra-zoom.xml"):
                continue
            nodes=ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-zoom.xml")).iter("node")
            for n in nodes:
                values=[n.get(k,"") for k in ("text","content-desc","class")]
                if label in values or starts and any(v.startswith(label) for v in values):
                    x,y,r,b=map(int,re.findall(r"\d+",n.get("bounds")))
                    if r>x and b>y: return ((x+r)//2,(y+b)//2)
        raise AssertionError(label)

    def tap(label,starts=False):
        shell("input","tap",*locate(label,starts))

    def panel():
        anchor=locate("页面菜单")
        shell("input","tap",*anchor)
        zoom=locate("页面缩放")
        if args.tablet:
            tree=ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-zoom.xml"))
            rows=[list(map(int,re.findall(r"\d+",n.get("bounds")))) for n in tree.iter("node")
                  if n.get("clickable")=="true" and any(c.get("text")=="页面缩放" for c in n.iter("node"))]
            l,t,r,b=min(rows,key=lambda rect:(rect[2]-rect[0])*(rect[3]-rect[1]))
            density=int(re.findall(r"\d+",shell("wm","density").decode())[-1])/160
            assert l<=anchor[0]<=r and 0<t-anchor[1]<155*density and r-l<=352*density,(anchor,(l,t,r,b))
        shell("input","tap",*zoom)
        locate("完成缩放")
        return anchor,zoom

    def glyph_height():
        img=Image.open(BytesIO(adb("exec-out","screencap","-p"))).convert("RGB")
        img=img.crop((0,img.height//4,img.width,img.height*3//4))
        # Chromium can rasterize the same glyph with a different mix of solid
        # and antialiased pixels. Include its blue edges, excluding link blue.
        r,g,b=img.split()
        blue=ImageChops.subtract(b,r).point(lambda v:255 if v>20 else 0)
        green=ImageChops.subtract(g,r).point(lambda v:255 if v>10 else 0)
        matches=ImageChops.darker(blue,green)
        box=matches.getbbox()
        return box[3]-box[1] if box else 0

    def rendered_height(expected):
        deadline=time.monotonic()+7
        while time.monotonic()<deadline:
            actual=glyph_height()
            if abs(actual-expected)<=2:
                return actual
            time.sleep(.1)
        raise AssertionError(("Rendered glyph height",actual,"expected",expected))

    with ThreadingHTTPServer(("127.0.0.1",0),Article) as server:
        Thread(target=server.serve_forever,daemon=True).start()
        port=server.server_port
        url=f"http://127.0.0.1:{port}/article"
        adb("reverse",f"tcp:{port}",f"tcp:{port}")
        recorder=None
        try:
            def launch(address=url):
                shell("am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",address)
                locate("Earthrise")

            def open_private():
                tap("标签页，",True)
                tap("无痕浏览")
                tap("新建无痕标签页")
                tap("搜索或输入网址")
                locate("android.widget.EditText")
                shell("input","text",url)
                shell("input","keyevent","ENTER")
                locate("Earthrise")
                panel()

            launch()
            if not args.layout_only and not args.tablet:
                launch(url+"/second")
                locate("Second tab")
            menu_btn,zoom_choice=panel()
            tap("恢复网页字号至 100%")
            locate("100%")
            time.sleep(.55)
            normal=glyph_height()
            assert normal>12,"Initial text was not rendered"
            up=locate("放大网页字号")
            reset=locate("恢复网页字号至 100%")
            if args.record:
                tap("完成缩放")
                locate("页面菜单")
                recorder=subprocess.Popen(prefix+["shell","screenrecord","--time-limit","11","/sdcard/terra-type.mp4"])
                time.sleep(.6)
                shell("input","tap",*menu_btn)
                time.sleep(.8)
                shell("input","tap",*zoom_choice)
                time.sleep(.7)
            for _ in range(3):
                shell("input","tap",*up)
                time.sleep(.25)
            locate("150%")
            large=rendered_height(normal*1.5)
            shell("input","tap",*reset)
            locate("100%")
            rendered_height(normal)
            if recorder:
                recorder.wait(timeout=12)
                adb("pull","/sdcard/terra-type.mp4",str(args.record))
            print(f"PASS rendered letter height {normal} -> {large} -> {normal} pixels",flush=True)
            tap("缩小网页字号")
            locate("85%")
            rendered_height(normal*.85)
            tap("恢复网页字号至 100%")
            if args.layout_only:
                for _ in range(8): shell("input","tap",*up)
                locate("300%")
                down=locate("缩小网页字号")
                for _ in range(12): shell("input","tap",*down)
                locate("50%")
                tap("恢复网页字号至 100%")
                tap("完成缩放")
                print("PASS both zoom limits and reset",flush=True)
                return
            for _ in range(3): shell("input","tap",*up)
            locate("150%")
            tap("完成缩放")
            if not args.tablet:
                x,y=locate("不安全 · 127.0.0.1")
                width=int(re.findall(r"(\d+)x\d+",shell("wm","size").decode())[-1])
                shell("input","swipe",x,y,x+width//3,y,300)
                locate("First tab")
                rendered_height(large)
                print("PASS an already-open same-site tab reflows immediately",flush=True)
            tap("Other site")
            locate("不安全 · localhost")
            panel()
            locate("100%")
            rendered_height(normal)
            tap("完成缩放")
            tap("后退")
            locate("不安全 · 127.0.0.1")
            panel()
            locate("150%")
            rendered_height(large)
            tap("完成缩放")
            shell("am","force-stop","app.terra.browser")
            launch()
            panel()
            locate("150%")
            rendered_height(large)
            tap("完成缩放")
            print("PASS another hostname uses 100%, back navigation and process restart restore 150%",flush=True)
            open_private()
            locate("100%")
            tap("缩小网页字号")
            locate("85%")
            tap("完成缩放")
            tap("标签页，1 个")
            tap("关闭标签页")
            tap("普通标签")
            tap("完成")
            panel()
            locate("150%")
            tap("完成缩放")
            open_private()
            locate("100%")
            rendered_height(normal)
            tap("完成缩放")
            tap("标签页，1 个")
            tap("关闭标签页")
            tap("普通标签")
            tap("完成")
            panel()
            tap("恢复网页字号至 100%")
            tap("完成缩放")
            print("PASS private zoom is isolated and expires when the last private tab closes",flush=True)
        finally:
            if recorder and recorder.poll() is None: recorder.wait(timeout=12)
            adb("reverse","--remove",f"tcp:{port}")
            server.shutdown()


if __name__=="__main__":
    main()
