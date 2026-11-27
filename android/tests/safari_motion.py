"""Check that a held address-bar drag moves the WebViews and that link previews render.
Run with Python and Pillow on an Android test emulator. An optional output directory keeps screenshots.
"""
import argparse
from functools import partial
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
from PIL import Image, ImageStat


class Pages(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/preview-origin":
            page = f"""<meta name=viewport content="width=device-width"><title>Coast</title>
<body style="background:#4488cc"><h1>Coast</h1>
<a style="position:absolute;left:42%;top:58%;padding:18px" href="http://127.0.0.1:{self.server.server_port}/garden">Preview garden</a>"""
            self.send_response(200)
            self.send_header("Content-Type","text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(page.encode())
            return
        if self.path == "/link-drop":
            html = b"""<meta name=viewport content="width=device-width"><title>Link drag</title>
<a style="position:absolute;left:41%;top:46%;padding:18px" href="/garden">Garden link</a>
<div contenteditable style="position:absolute;left:5%;top:73%;padding:24px;background:#aec">Drop link here</div>
<output>Waiting for drop</output><script>
const target = document.querySelector('div');
target.ondragover = event => event.preventDefault();
target.ondrop = event => {
  event.preventDefault();
  const url = event.dataTransfer.getData('text/uri-list') || event.dataTransfer.getData('text/plain');
  document.querySelector('output').textContent = url.trim() === location.origin + '/garden' ? 'Garden link received' : 'Unexpected drop';
};
</script>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html)
            return
        if self.path == "/panel-paper":
            html = b"<meta name=viewport content='width=device-width'><title>Paper</title><body style='background:#b2cbd2'><p style='margin-top:68vh'>Panel sample</p>"
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(html)
            return
        if self.path.startswith("/suggest-"):
            from address_suggestions import fixture
            return fixture(self)
        if self.path.startswith("/transfer-"):
            from downloads import fixture
            return fixture(self)
        if self.path == "/find-coast":
            from page_find import fixture
            return fixture(self)
        if self.path == "/keys-editor":
            from browser_keys import editor
            return editor(self)
        if self.path.startswith("/strip-"):
            from tablet_strip import fixture
            return fixture(self)
        if self.path.startswith("/reader-"):
            from reader_mode import fixture
            if fixture(self): return
        colors = {"/sky": "#4488cc", "/dune": "#cc8844", "/garden": "#408058"}
        if self.path == "/glass":
            html = b"""<meta name=viewport content='width=device-width,initial-scale=1'><title>Glass</title>
            <style>body{margin:0}section{height:120vh;background-image:repeating-linear-gradient(90deg,
            transparent 0 36px,white 36px 42px,transparent 42px 80px)}</style>
            <section style='background-color:#275b9b'>First band</section><section style='background-color:#b46029'>Second band</section>"""
        elif self.path in ("/glass-coast", "/glass-canyon", "/reading-coast", "/reading-canyon"):
            coast = self.path.endswith("-coast")
            title, next_title = ("海岸", "峡谷") if coast else ("峡谷", "海岸")
            document_title = f"{title}手记 · {self.server.server_port}" if self.path.startswith("/reading-") else title
            color = "#28669c" if coast else "#ac602d"
            next_url = "/glass-canyon" if coast else "/glass-coast"
            html = f"""<meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
            <title>{document_title}</title><style>*{{box-sizing:border-box}}body{{margin:0;color:white;background:{color};
            font:18px sans-serif}}main{{min-height:100vh;padding:9vh 7vw; background:
            repeating-radial-gradient(ellipse at 104% 56%,transparent 0px 46px,#ffffff50 47px 49px,transparent 50px 64px)}}
            h1{{font:700 64px serif;margin:.25em 0}}a{{display:inline-block;padding:14px 20px;margin-top:12px;
            color:inherit;background:#172d3c77;border-radius:28px}}p{{max-width:16em;line-height:1.6}}</style>
            <main><small>Terra · 地貌图集</small><h1>{title}</h1><p>{'潮水褪去，海岸的轮廓逐渐显现。' if coast else '沿河而行，层层岩壁留下峡谷的年轮。'}</p>
            <a href='{next_url}'>浏览{next_title}</a></main>""".encode()
        elif self.path == "/live-sheet":
            html = b"""<meta name=viewport content='width=device-width'><title>Live glass</title>
            <body style='background:#285e99;color:white;height:100vh'><h1>Live glass</h1>
            <script>fetch('/change-sheet-color').then(r=>r.text()).then(color=>document.body.style.background=color)</script>"""
        elif self.path == "/change-sheet-color" and hasattr(self.server, "sheet_change"):
            self.server.sheet_change.wait(getattr(self.server,"sheet_timeout",45))
            html = b"#b16225"
        elif re.fullmatch(r"/sheet-list/\d+", self.path):
            step = int(self.path.rsplit("/", 1)[1])
            title = f"Sheet {self.server.server_port} / {step}"
            onward = f"<script>setTimeout(() => location.href='/sheet-list/{step+1}', 450)</script>" if step < 24 else ""
            html = f"<meta name=viewport content='width=device-width'><title>{title}</title><h1>{title}</h1>{onward}".encode()
        elif self.path == "/private-source":
            html = b"<meta name=viewport content='width=device-width'><h1>Private source</h1><a href='/private-destination'>Private link</a>"
        elif self.path == "/private-destination":
            html = b"<meta name=viewport content='width=device-width'><h1>Private destination</h1>"
        elif self.path == "/window-target":
            name = f"Destination {self.server.server_port}"
            html = f"<meta name=viewport content='width=device-width'><title>{name}</title><h1>{name}</h1>".encode()
        elif self.path == "/window-source":
            html = b"""<meta name=viewport content='width=device-width'><title>Source window</title>
            <body style='margin:0;background:#365b78;color:white;font:22px sans-serif'>
            <h1>Source window</h1><div style='height:78vh'></div>
            <a style='color:white' href='/window-target'>Window destination</a><div style='height:54vh'></div></body>"""
        elif self.path in ("/tab-menu","/group-link") or re.fullmatch(r"/trail/\d+", self.path):
            if self.path in ("/tab-menu","/group-link"):
                title = f"Menu {self.server.server_port}"
                link = "/garden"
            else:
                step = int(self.path.split("/")[-1])
                title = f"Trail {step}"
                link = f"/trail/{step+1}"
            spacing = "margin-top:47vh" if self.path == "/group-link" else ""
            html = f"<meta name=viewport content='width=device-width'><title>{title}</title><body style='padding:32px;background:#467365;color:white'><h1>{title}</h1><div style='{spacing}'><a href='{link}'>Continue</a></div></body>".encode()
        elif self.path in colors:
            title = self.path[1:].title()
            html = f"""<meta name=viewport content="width=device-width"><title>{title}</title>
            <body style="margin:0;padding:36px 22px;background:{colors[self.path]};color:white;font:20px sans-serif">
            <h1>{title}</h1><a href="/garden" style="color:white">Open garden</a></body>""".encode()
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/html;charset=utf-8")
        self.send_header("Content-Length", str(len(html)))
        self.end_headers()
        self.wfile.write(html)

    def log_message(self, *args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--screens", type=Path)
    parser.add_argument("--tablet", action="store_true")
    parser.add_argument("--menu-drag", action="store_true", help="verify selection during the original long press")
    parser.add_argument("--menu-history", action="store_true", help="reach a hidden history entry with one held press")
    parser.add_argument("--bookmark-conflict", action="store_true", help="verify concurrent edits in separate browser tasks")
    parser.add_argument("--bookmark-glass", action="store_true", help="check editor glass over the live web page and full bookmark sheet")
    parser.add_argument("--bookmark-menu", action="store_true", help="hold and drag to Edit from a phone sheet or tablet sidebar")
    parser.add_argument("--bookmarks", action="store_true", help="edit bookmarks from the page menu, library and Favorites")
    parser.add_argument("--library-resize", action="store_true", help="change the portrait emulator to tablet width and back with bookmarks open")
    parser.add_argument("--live-sheet", action="store_true", help="sample a web page changing color under an already-open sheet")
    parser.add_argument("--library-motion", action="store_true", help="exercise the phone sheet detents and list scroll handoff")
    parser.add_argument("--library-glass", action="store_true", help="check that bookmarks refract the current page")
    parser.add_argument("--private-window", action="store_true", help="verify private window handoff and cold restart")
    parser.add_argument("--new-window", action="store_true", help="verify independent document tasks and shared bookmarks")
    parser.add_argument("--link-groups", action="store_true", help="open a link in a new or existing tab group and verify isolation")
    parser.add_argument("--tablet-menu", action="store_true", help="measure the side-by-side menu on an 820dp portrait test tablet")
    parser.add_argument("--menu-record",type=Path,help="record the interaction for --menu-drag, --link-groups, --library-glass or --library-motion or --bookmark-menu")
    parser.add_argument("--reader",action="store_true",help="verify Reader typography, article links and reading position")
    parser.add_argument("--reader-glass",action="store_true",help="sample moving article images beneath the Reader toolbar")
    parser.add_argument("--find",action="store_true",help="test on-page find with a real keyboard and WebView")
    parser.add_argument("--browser-keys",action="store_true",help="test browser shortcuts in a separate test window")
    parser.add_argument("--tablet-strip",action="store_true",help="measure the tablet tab strip and system light/dark changes")
    parser.add_argument("--tablet-sidebar",action="store_true",help="verify the tablet sidebar navigation and live glass")
    parser.add_argument("--tablet-popup-motion",action="store_true",help="measure page menu growth and sidebar dimming on a tablet emulator")
    parser.add_argument("--address-menu",action="store_true",help="check held address actions, live glass, exact URLs and the compact bar")
    parser.add_argument("--open-tab-search",action="store_true",help="test live tab suggestions with a retained form and reading position")
    parser.add_argument("--address-motion",action="store_true",help="measure the tablet address field opening and closing on an emulator")
    parser.add_argument("--address-search",action="store_true",help="verify a pasted search phrase offline on a dedicated emulator")
    parser.add_argument("--downloads",action="store_true",help="test real transfers, cancellation and opening the downloaded file")
    parser.add_argument("--download-retry",action="store_true",help="test a failed transfer and two files with the same name")
    parser.add_argument("--reading-list",action="store_true",help="verify unread filtering and held and swiped actions")
    parser.add_argument("--reading-list-glass",action="store_true",help="test glass over a fully expanded reading list")
    parser.add_argument("--upgrade-apk",type=Path,help="with --reading-list, save an item on the installed build before upgrading")
    args = parser.parse_args()
    adb = shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name=="nt" else "adb"))
    prefix = [adb, "-s", args.serial]

    def run(*command):
        return subprocess.check_output(prefix + list(map(str, command)), timeout=20)

    shell = partial(run, "shell")

    def locate(label, prefix_match=False):
        until = time.monotonic() + 22
        while time.monotonic() < until:
            if b"dumped to" not in shell("uiautomator","dump","/sdcard/terra-motion.xml"):
                continue
            nodes = ElementTree.fromstring(run("exec-out","cat","/sdcard/terra-motion.xml")).iter("node")
            for node in nodes:
                values = (node.get("text",""),node.get("content-desc",""))
                if label in values or prefix_match and any(v.startswith(label) for v in values):
                    x,y,r,b = map(int,re.findall(r"-?\d+",node.get("bounds")))
                    if r > x and b > y and r > 0 and b > 0:
                        return (max(0,x)+r)//2, (max(0,y)+b)//2
        raise AssertionError(label)

    def shot(name):
        img = Image.open(BytesIO(run("exec-out","screencap","-p"))).convert("RGB")
        if args.screens:
            args.screens.mkdir(parents=True,exist_ok=True)
            img.save(args.screens / (name+".png"))
        return img

    def count(img, rgb):
        return sum(n for n,c in img.getcolors(img.width * img.height) if c == rgb)

    with ThreadingHTTPServer(("127.0.0.1",0),Pages) as site:
        threading.Thread(target=site.serve_forever,daemon=True).start()
        port = site.server_port
        run("reverse",f"tcp:{port}",f"tcp:{port}")
        try:
            if args.address_motion:
                from address_motion import verify
                verify(run,shell,locate,shot,port,prefix,args.menu_record)
                return
            if args.open_tab_search:
                from address_suggestions import verify
                verify(run,shell,locate,shot,site,prefix,args.menu_record,args.tablet)
                return
            if args.address_menu:
                from address_menu import verify
                verify(shell,locate,shot,site,args.tablet,prefix,args.menu_record)
                return
            if args.address_search:
                from address_menu import verify_search
                verify_search(run,shell,locate,site.server_port)
                return
            if args.downloads or args.download_retry:
                from downloads import verify, verify_retry
                if args.download_retry: verify_retry(run,shell,locate,site)
                else: verify(run,shell,locate,shot,site,args.tablet,prefix,args.menu_record)
                return
            if args.tablet_sidebar:
                from tablet_sidebar import verify
                verify(run,shell,locate,shot,site,prefix,args.menu_record)
                return
            if args.tablet_popup_motion:
                from tablet_popup_motion import verify
                verify(run,shell,locate,shot,port,prefix)
                return
            if args.tablet_strip:
                from tablet_strip import verify
                verify(shell,locate,shot,site)
                return
            if args.browser_keys:
                from browser_keys import verify
                verify(run,shell,locate,port,prefix,args.menu_record)
                return
            if args.reading_list or args.reading_list_glass:
                from reading_list import verify_reading_list
                verify_reading_list(run,shell,locate,port,prefix,args.upgrade_apk,args.menu_record,
                                    (site,shot) if args.reading_list_glass else None)
                return
            if args.find:
                from page_find import verify
                verify(run,shell,locate,shot,port,args.tablet)
                return
            if args.reader_glass:
                from reader_mode import verify_reader_glass
                verify_reader_glass(shell,locate,shot,port)
                return
            if args.reader:
                from reader_mode import verify_reader
                verify_reader(run,shell,locate,shot,port,prefix,args.menu_record)
                return
            if args.bookmark_glass:
                from bookmark_editor import verify_editor_glass
                verify_editor_glass(shell, locate, shot, site)
                return
            if args.bookmark_menu:
                from bookmark_editor import verify_bookmark_menu
                verify_bookmark_menu(shell, locate, run, shot, port, prefix, args.menu_record, args.tablet)
                return
            if args.bookmarks:
                from bookmark_editor import verify_bookmarks
                verify_bookmarks(shell, locate, run, shot, port)
                return
            if args.library_resize:
                from library_motion import verify_resize
                verify_resize(shell, locate, shot, port)
                return
            if args.live_sheet:
                site.sheet_change = threading.Event()
                shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
                      "-d",f"http://127.0.0.1:{port}/live-sheet")
                locate("Live glass")
                shell("input","tap",*locate("书签"))
                x,y = locate("完成")
                patch = (x*2//3-4,y-4,x*2//3+4,y+4)
                blue = ImageStat.Stat(shot("live-sheet-blue").crop(patch)).mean
                site.sheet_change.set()
                until = time.monotonic()+6
                while True:
                    orange = ImageStat.Stat(shot("live-sheet-orange").crop(patch)).mean
                    if orange[0]-orange[2] > 10 or time.monotonic()>until:
                        break
                    time.sleep(.18)
                assert blue[2]-blue[0]>10 and orange[0]-orange[2]>10, (blue, orange)
                assert locate("完成") == (x,y)
                shell("input","tap",x,y)
                print("PASS the web document recolors a stationary glass sheet",blue,orange,flush=True)
                return
            if args.library_motion:
                from library_motion import verify_sheet
                verify_sheet(shell, locate, run, shot, port, prefix, args.menu_record)
                return
            if args.library_glass:
                from browser_windows import verify_library_glass
                verify_library_glass(shell, locate, run, shot, port, prefix, args.menu_record)
                return
            if args.private_window:
                from browser_windows import verify_private_window
                verify_private_window(shell, locate, port)
                return
            if args.new_window or args.bookmark_conflict:
                from browser_windows import verify_windows
                verify_windows(shell, locate, shot, port, args.bookmark_conflict)
                return
            if args.link_groups:
                from link_groups import verify_link_groups
                verify_link_groups(shell, locate, run, shot, port, prefix, args.menu_record)
                return
            if args.tablet_menu:
                from menu_tracking import verify_tablet_menu
                verify_tablet_menu(shell, locate, shot, run, port)
                return
            if args.menu_drag:
                from menu_tracking import verify_menu_drag
                verify_menu_drag(shell, locate, shot, port, args.menu_record, prefix, run)
                return
            if args.menu_history:
                from menu_tracking import verify_menu_history
                verify_menu_history(shell, locate, run, port)
                return
            for path in ("sky","dune"):
                shell("am","start","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
                      "-d",f"http://127.0.0.1:{port}/{path}")
                locate(path.title())
            initial = shot("initial")
            x = initial.width // 2
            _, y = locate("页面菜单")
            if args.tablet:
                assert y < initial.height / 3, "Tablet address bar should be at the top"
            else:
                assert y > initial.height / 2
                shell("input","motionevent","DOWN",x,y)
                end = x + initial.width // 3
                try:
                    shell("input","motionevent","MOVE",x+initial.width//9,y)
                    shell("input","motionevent","MOVE",end,y)
                    middle = shot("drag-held")
                    for rgb in ((68,136,204),(204,136,68)):
                        assert count(middle,rgb) > middle.width*middle.height*.08, "Both pages should be visible before release"
                finally:
                    shell("input","motionevent","UP",end,y)
                locate("Sky")
                print("PASS both page images follow the held gesture and settle to the adjacent live tab", flush=True)
            x,y=locate("Open garden")
            shell("input","swipe",x,y,x,y,800)
            locate("隐藏预览")
            preview = shot("link-preview")
            green = (64,128,88)
            assert count(preview,green) > 3000, "Target pixels missing from preview"
            shell("input","tap",*locate("隐藏预览"))
            locate("显示预览")
            assert count(shot("hidden-preview"),green) < 300, "Hidden preview is still visible"
            shell("input","tap",*locate("显示预览"))
            locate("隐藏预览")
            assert count(shot("restored-preview"),green) > 3000
            shell("input","tap",*locate("打开"))
            locate("Garden")
            print("PASS preview displays the destination, collapses, restores and opens it", flush=True)
            # A context menu can land outside the toolbar's normal sampling band.
            menu_colors = []
            for path in ("sky","dune"):
                shell("am","start","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
                      "-d",f"http://127.0.0.1:{port}/{path}")
                locate(path.title())
                x,y=locate("Open garden")
                shell("input","swipe",x,y,x,y,800)
                locate("打开")
                tree=ElementTree.fromstring(run("exec-out","cat","/sdcard/terra-motion.xml"))
                rows=[list(map(int,re.findall(r"\d+",n.get("bounds")))) for n in tree.iter("node")
                      if n.get("clickable")=="true" and any(c.get("text")=="打开" for c in n.iter("node"))]
                l,t,r,b=min(rows,key=lambda box:(box[2]-box[0])*(box[3]-box[1]))
                x,y=l+(r-l)*4//5,(t+b)//2
                img=shot("context-"+path)
                menu_colors.append(ImageStat.Stat(img.crop((x-3,y-3,x+3,y+3))).mean)
                shell("input","keyevent","BACK")
            blue,orange=menu_colors
            assert blue[2]-blue[0]>8 and orange[0]-orange[2]>8,(blue,orange)
            print("PASS the floating context menu samples the document away from the toolbar",flush=True)
            shell("am","start","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
                  "-d",f"http://127.0.0.1:{port}/glass")
            locate("First band")
            cx, cy = locate("页面菜单")
            first = shot("glass-blue")
            w,h = first.size
            patch = (cx+int(w*.065),cy-4,cx+int(w*.106),cy+4)
            for _ in range(3 if args.tablet else 1):
                shell("input","swipe",w//2,int(h*.76),w//2,int(h*.19),490)
            time.sleep(.65)
            shell("input","tap",*locate("显示工具栏"))
            locate("页面菜单")
            second = shot("glass-orange")
            blue = ImageStat.Stat(first.crop(patch)).mean
            orange = ImageStat.Stat(second.crop(patch)).mean
            assert blue[2]-blue[0]>8 and orange[0]-orange[2]>8, (patch,blue,orange)
            print("PASS the same glass surface samples blue and orange while the document scrolls",flush=True)
        finally:
            run("reverse","--remove",f"tcp:{port}")
            site.shutdown()


if __name__ == "__main__":
    main()
