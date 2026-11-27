"""Restore two separately closed tabs with identical current URLs after a process restart."""
import argparse
import json
from urllib.parse import unquote
import os
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
import re
import shutil
import subprocess
import threading
import time
from xml.etree import ElementTree


class Trail(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/state?"):
            observation=json.loads(unquote(self.path.partition("?")[2]))
            self.server.state[observation["path"]]=observation
            self.server.sessions[(observation["path"],observation["desktop"])]=observation
            self.send_response(204)
            self.end_headers()
            return
        tag=str(self.server.server_port)
        if self.path in ("/blue","/green","/private"):
            title=self.path[1:].capitalize()+" coast "+tag
            main="<a href='/notes'>Read survey notes</a>"
        elif self.path=="/notes":
            title="Survey notes "+tag
            main="""<p><a href='/end'>Open appendix</a></p>
            <button onclick='window.scrollTo(0,470)'>First position</button>
            <button onclick='window.scrollTo(0,930)'>Second position</button>
            <div style='height:2700px'></div><output id=pos>Offset 0</output>
            <script>onscroll=()=>pos.value='Offset '+Math.round(scrollY)</script>"""
        elif self.path=="/end":
            title="Survey appendix "+tag
            main="<p>End of the survey</p>"
        else:
            self.send_error(404)
            return
        body=(f"<meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>{title}</title>"
              "<style>body{font:20px sans-serif;background:#e0ece6;color:#163a33;padding:20px}button,a{padding:10px;font-size:18px}"
              "output{position:fixed;top:32%;right:10px;background:white;padding:8px}</style>"
              f"<h1>{title}</h1>{main}" + """<script>
              function report(){const links={};for(const el of document.querySelectorAll('a,button')){
              let r=el.getBoundingClientRect();links[el.textContent]=[(r.left+r.right)/2,(r.top+r.bottom)/2];}
              fetch('/state?'+encodeURIComponent(JSON.stringify({path:location.pathname,y:Math.round(scrollY),
              desktop:navigator.userAgent.includes('X11; Linux x86_64'),width:visualViewport.width,links}))).catch(()=>{});}
              addEventListener('pageshow',report);addEventListener('resize',report);addEventListener('scroll',report);
              </script>""").encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html;charset=utf-8")
        self.send_header("Content-Length",str(len(body)))
        self.end_headers()
        self.wfile.write(body)
    def log_message(self,*args):
        pass


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--private",action="store_true",help="check private history never enters Recently Closed")
    args=parser.parse_args()
    exe=shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe"))
    prefix=[exe,"-s",args.serial]
    def adb(*cmd):
        return subprocess.check_output(prefix+list(map(str,cmd)),timeout=25)
    def shell(*cmd):
        return adb("shell",*cmd)
    def rows():
        if b"dumped to" not in shell("uiautomator","dump","/sdcard/terra-closed.xml"):return []
        return list(ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-closed.xml")).iter("node"))
    def points(label,starts=False):
        matches=set()
        for node in rows():
            values=(node.get("content-desc",""),node.get("text",""))
            if label not in values and not (starts and any(v.startswith(label) for v in values)):continue
            x,y,r,b=map(int,re.findall(r"-?\d+",node.get("bounds")))
            if r>x and b>max(0,y):matches.add(((x+r)//2,(max(0,y)+b)//2))
        return sorted(matches,key=lambda xy:xy[1])
    def point(label,starts=False,count=1):
        deadline=time.monotonic()+35
        while time.monotonic()<deadline:
            found=points(label,starts)
            if len(found)>=count:return found[0]
        raise AssertionError((label,count))
    def tap(label,starts=False):
        shell("input","tap",*point(label,starts))
    def hold(label,starts=False):
        xy=point(label,starts)
        shell("input","swipe",*xy,*xy,750)
    def closed_list():
        hold("标签页，",True)
        tap("最近关闭")
    with ThreadingHTTPServer(("127.0.0.1",0),Trail) as site:
        site.state={}
        site.sessions={}
        def view_box(title):
            deadline=time.monotonic()+28
            while time.monotonic()<deadline:
                for node in rows():
                    if node.get("class")=="android.webkit.WebView" and node.get("text")==title:
                        return tuple(map(int,re.findall(r"-?\d+",node.get("bounds"))))
            raise AssertionError(title)
        def page_tap(path,title,label):
            left,top,right,_=view_box(title)
            time.sleep(.5)
            found=points(label)
            if found:
                shell("input","tap",*found[0]);return
            state=site.state[path]

            px,py=state["links"][label]
            scale=(right-left)/state["width"]
            shell("input","tap",round(left+px*scale),round(top+py*scale))
        def offset(expected,desktop):
            view_box(title)
            deadline=time.monotonic()+12
            while time.monotonic()<deadline:
                measured=site.sessions.get(("/notes",desktop),{}).get("y",-1)
                if abs(measured-expected)<=2:return
                time.sleep(.15)
            raise AssertionError(("offset",expected,measured))
        threading.Thread(target=site.serve_forever,daemon=True).start()
        port=str(site.server_port)
        adb("reverse","tcp:"+port,"tcp:"+port)
        title="Survey notes "+port
        try:
            if args.private:
                shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                      "-d",f"http://127.0.0.1:{port}/blue")
                view_box("Blue coast "+port)
                hold("标签页，",True)
                tap("关闭标签页")
                hold("标签页，",True)
                tap("新建无痕标签页")
                point("无痕浏览")
                tap("搜索或输入网址")
                point("网址或搜索词")
                shell("input","text",f"http://127.0.0.1:{port}/private")
                shell("input","keyevent","KEYCODE_ENTER")
                view_box("Private coast "+port)
                shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_SHIFT_LEFT","KEYCODE_T")
                view_box("Private coast "+port)
                hold("标签页，",True)
                tap("关闭标签页")
                point("页面菜单")
                shell("am","force-stop","app.terra.browser")
                shell("am","start","-W","-n","app.terra.browser/.MainActivity")
                closed_list()
                point("Blue coast "+port)
                assert not points("Private coast "+port)
                print("PASS private shortcut preserves isolation and the private tab is absent after restart",flush=True)
                return
            for origin,slot,y in (("blue","First position",470),("green","Second position",930)):
                shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                      "-d",f"http://127.0.0.1:{port}/{origin}")
                if origin=="green":
                    view_box("Green coast "+port)
                    tap("页面菜单")
                    tap("请求桌面网站")
                    deadline=time.monotonic()+12
                    while not site.state.get("/green",{}).get("desktop") and time.monotonic()<deadline:time.sleep(.12)
                    assert site.state["/green"]["desktop"]
                page_tap("/"+origin,origin.capitalize()+" coast "+port,"Read survey notes")
                page_tap("/notes",title,"Open appendix")
                view_box("Survey appendix "+port)
                tap("后退")
                page_tap("/notes",title,slot)
                offset(y,origin=="green")
                hold("标签页，",True)
                tap("关闭标签页")
                point("页面菜单")
            print("PASS closed two independent trails at the same URL with forward entries and distinct offsets",flush=True)
            shell("am","force-stop","app.terra.browser")
            shell("am","start","-W","-n","app.terra.browser/.MainActivity")
            closed_list()
            site.sessions.pop(("/notes",True),None)
            point("来自 Green coast "+port)
            point("来自 Blue coast "+port)
            shell("input","tap",*point(title,count=2))
            offset(930,True)
            tap("前进")
            view_box("Survey appendix "+port)
            tap("后退")
            tap("后退")
            view_box("Green coast "+port)
            tap("前进")
            view_box(title)
            print("PASS newer tab restored at its offset, forward to the appendix and back to its own origin",flush=True)
            tap("标签页，",True)
            hold("新建标签页")
            site.sessions.pop(("/notes",False),None)
            tap(title)
            offset(470,False)
            tap("后退")
            view_box("Blue coast "+port)
            print("PASS overview plus-button menu restored the older copy and its independent navigation history",flush=True)
            hold("标签页，",True)
            tap("关闭标签页")
            point("页面菜单")
            shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_SHIFT_LEFT","KEYCODE_T")
            view_box("Blue coast "+port)
            tap("前进")
            view_box(title)
            print("PASS keyboard shortcut reopens the latest tab with forward history",flush=True)
        finally:
            adb("reverse","--remove","tcp:"+port)
            site.shutdown()


if __name__=="__main__":
    main()

