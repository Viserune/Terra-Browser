"""Verify the tab overview on an installed test emulator. --record saves an actual device capture."""
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


class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        entries = {"A": "#185491", "B": "#bb7134", "C": "#236954", "D": "#695886", "Private": "#285c39"}
        key = self.path.lstrip("/")
        if re.fullmatch(r"scroll-\d+", key):
            entries[key] = "#447060"
        if key not in entries:
            self.send_error(404)
            return
        title = f"Overview {key} {self.server.server_port}"
        color = entries[key]
        if hasattr(self.server, "page_color"):
            color = self.server.page_color(key, color)
        html = f"<meta name=viewport content='width=device-width'><title>{title}</title><body style='background:{color};color:white;padding:16vh 8vw'><h1>{title}</h1></body>".encode()
        if key == "A" and getattr(self.server, "long_article", False):
            html = f"""<meta name=viewport content='width=device-width,initial-scale=1'><title>{title}</title>
            <style>*{{box-sizing:border-box}} body{{margin:0;font:20px sans-serif;color:white}}section{{padding:12vh 8vw}}
            output{{position:fixed;right:5vw;top:4vh}}</style>
            <section style='height:72vh;background:#844541'><h1>{title}</h1></section>
            <section style='height:420vh;background:{color}'><h2>{title}</h2></section>
            <output id='offset'>Offset 0</output><script>onscroll=()=>offset.textContent='Offset '+Math.round(scrollY)</script>""".encode()
        self.send_response(200)
        self.send_header("Content-Type","text/html; charset=utf-8")
        self.send_header("Content-Length",str(len(html)))
        self.end_headers()
        self.wfile.write(html)

    def log_message(self,*args):
        pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--record",type=Path)
    parser.add_argument("--pins",action="store_true",help="verify pinned cards, persistence and private isolation")
    parser.add_argument("--glass",action="store_true",help="verify that moving card previews tint the floating add button")
    parser.add_argument("--restore-previews",action="store_true",help="check the pixels and opening behavior of a cold-restored tab")
    parser.add_argument("--inspect-preview-cache",action="store_true",help="also check private isolation and deletion with root on a dedicated test emulator")
    parser.add_argument("--glass-screens",type=Path,help="keep the two measured overview screenshots during --glass")
    parser.add_argument("--tablet-pins",action="store_true",help="verify pinning through the tablet tab strip")
    parser.add_argument("--groups",action="store_true",help="verify named tab groups and session restore")
    parser.add_argument("--tablet-groups",action="store_true",help="verify moving a tab from the tablet tab strip")
    parser.add_argument("--group-screen",type=Path,help="save a screenshot of the group selector during --groups or --group-selection")
    parser.add_argument("--group-selection",action="store_true",help="verify per-group active tabs after a cold restart")
    parser.add_argument("--search-tabs",action="store_true",help="verify search gestures, scoped closing and reopening in a test group")
    parser.add_argument("--search-screen",type=Path,help="save the filtered tab overview during --search-tabs")
    parser.add_argument("--arrange", action="store_true", help="verify title and website arrangement in a new tab group")
    parser.add_argument("--manual-order", action="store_true", help="exercise long-press dragging and cancellation in the tab grid")
    args = parser.parse_args()
    exe = shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name == "nt" else "adb"))
    prefix = [exe,"-s",args.serial]

    def adb(*cmd):
        return subprocess.check_output(prefix+list(map(str,cmd)),timeout=22)

    def shell(*cmd):
        return adb("shell",*cmd)

    def locate(label,prefix_match=False,topmost=False):
        end = time.monotonic()+25
        seen = []
        while time.monotonic()<end:
            if b"dumped to" not in shell("uiautomator","dump","/sdcard/terra-overview.xml"):
                continue
            seen = []
            matches = []
            nodes = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-overview.xml")).iter("node")
            for node in nodes:
                vals = (node.get("text",""),node.get("content-desc",""),node.get("class",""))
                if vals[0] or vals[1]: seen.append(vals[:2])
                if label in vals or prefix_match and any(v.startswith(label) for v in vals):
                    l,t,r,b = map(int,re.findall(r"\d+",node.get("bounds")))
                    if r>l and b>t:
                        point = ((l+r)//2,(t+b)//2)
                        if not topmost: return point
                        matches.append(point)
            if matches: return min(matches,key=lambda point:point[1])
        raise AssertionError((label,seen[-30:]))

    def tap(label,prefix_match=False):
        shell("input","tap",*locate(label,prefix_match))

    def labels():
        shell("uiautomator","dump","/sdcard/terra-overview.xml")
        root = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-overview.xml"))
        return {n.get("text","") for n in root.iter("node")}

    with ThreadingHTTPServer(("127.0.0.1",0),Fixture) as site:
        Thread(target=site.serve_forever,daemon=True).start()
        port = site.server_port
        base = f"http://127.0.0.1:{port}/"
        a,b,private = (f"Overview {key} {port}" for key in ("A","B","Private"))
        adb("reverse",f"tcp:{port}",f"tcp:{port}")
        recording = None
        try:
            if args.groups or args.tablet_groups or args.group_selection or args.search_tabs:
                shell("am","force-stop","app.terra.browser")
                shell("am","start","-n","app.terra.browser/.MainActivity")
                tap("标签页，",True)
                if "无痕标签页" in labels():
                    tap("普通标签")
                tap("标签组")
                tap("普通标签")
                tap("完成")
            for key,label in (("A",a),("B",b)):
                site.long_article = args.restore_previews or args.inspect_preview_cache
                shell("am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",base+key)
                locate(label)
                if site.long_article and key == "A":
                    from restored_previews import scroll_article
                    site.saved_offset = scroll_article(adb, shell)
            def hold(point):
                shell("input","swipe",*point,*point,780)

            if args.arrange or args.manual_order:
                from tab_arrangement import verify_arrangement
                verify_arrangement(adb, shell, tap, locate, labels, hold, base, port, prefix, args.record, manual=args.manual_order)
                return
            if args.restore_previews or args.inspect_preview_cache:
                from restored_previews import verify_restore, verify_private_cache
                verify_restore(adb, shell, tap, locate, a, b, args.record, prefix, site)
                if args.inspect_preview_cache:
                    verify_private_cache(adb, shell, tap, locate, hold, base, a, b, private)
                return
            if args.glass:
                from overview_glass import verify_glass
                verify_glass(adb, shell, locate, tap, base, port, args.glass_screens, prefix, args.record)
                return
            if args.search_tabs:
                from tab_search import verify_search
                verify_search(adb,shell,tap,locate,labels,hold,a,b,base,port,args.search_screen,args.record,prefix)
                return
            if args.group_selection:
                from tab_groups import verify_selection
                verify_selection(shell, tap, locate, hold, a, base, port, adb, args.group_screen)
                return
            if args.tablet_groups:
                from tab_groups import verify_tablet
                verify_tablet(shell, tap, locate, labels, hold, a, b, port)
                return
            if args.groups:
                from tab_groups import verify_groups
                verify_groups(shell, tap, locate, labels, hold, a, b, base, port, adb, args.group_screen)
                return

            if args.tablet_pins:
                # Touch the tab in the top strip, above the document with the same title.
                before = locate(b,topmost=True)
                hold(before)
                tap("固定标签页")
                pinned = locate("已固定，"+b)
                assert pinned[0] < before[0] and abs(pinned[1]-before[1]) < 8,(pinned,before)
                hold(pinned)
                tap("取消固定")
                hold(locate(b,topmost=True))
                locate("固定标签页")
                shell("input","keyevent","BACK")
                print("PASS tablet strip pins at the leading edge and unpins via long press",flush=True)
                return

            tabs = locate("标签页，",True)
            if args.pins:
                shell("input","tap",*tabs)
                locate("普通标签")
                hold(locate(b))
                if args.record:
                    recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","10","/sdcard/terra-pin.mp4"])
                    time.sleep(.5)
                tap("固定标签页")
                pin_label = "已固定，"+b
                x,y = locate(pin_label)
                header = locate("标签页")
                first_close = locate("关闭标签页")
                assert header[1] < y < first_close[1], (header,y,first_close)
                shell("input","swipe",x,y,x//5,y,300)
                locate(pin_label)
                tap("完成")
                locate(b)
                if recording:
                    recording.wait(timeout=15)
                    adb("pull","/sdcard/terra-pin.mp4",str(args.record))
                shell("am","force-stop","app.terra.browser")
                shell("am","start","-n","app.terra.browser/.MainActivity")
                locate(b)
                tap("标签页，",True)
                locate(pin_label)
                tap("无痕浏览")
                assert b not in labels()
                tap("新建无痕标签页")
                tap("搜索或输入网址")
                locate("android.widget.EditText")
                shell("input","text",base+"Private")
                shell("input","keyevent","ENTER")
                locate(private)
                tap("标签页，1 个")
                hold(locate(private))
                tap("固定标签页")
                locate("已固定，"+private)
                tap("普通标签")
                locate(pin_label)
                assert private not in labels()
                tap("无痕浏览")
                hold(locate("已固定，"+private))
                tap("关闭标签页")
                assert private not in labels()
                tap("普通标签")
                hold(locate(pin_label))
                tap("取消固定")
                assert locate(b)[1] >= locate(a)[1]
                tap(b)
                locate(b)
                print("PASS pin placement, swipe protection, active tab and pin survive restart, private pins stay isolated, close and unpin",flush=True)
                return
            # Obtain the second cell's real position first, including large restored sessions.
            shell("input","tap",*tabs)
            locate("无痕浏览")
            picked = locate(a)
            tap("完成")
            locate(b)
            if args.record:
                recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","7","/sdcard/terra-overview-demo.mp4"])
                time.sleep(.8)
            shell("input","tap",*tabs)
            time.sleep(1.3)
            shell("input","tap",*picked)
            locate(a)
            print("PASS overview focuses the current card and opens the chosen page",flush=True)
            if recording:
                recording.wait(timeout=12)
                adb("pull","/sdcard/terra-overview-demo.mp4",str(args.record))
            tap("标签页，",True)
            tap("无痕浏览")
            tap("新建无痕标签页")
            tap("搜索或输入网址")
            locate("android.widget.EditText")
            shell("input","text",base+"Private")
            shell("input","keyevent","ENTER")
            locate(private)
            tap("标签页，1 个")
            locate(private)
            assert a not in labels() and b not in labels()
            tap("普通标签")
            tap(a)
            locate(a)
            tap("标签页，",True)
            locate(a)
            assert private not in labels()
            tap("无痕浏览")
            tap(private)
            locate(private)
            x,y=locate("不安全 · 127.0.0.1")
            shell("input","swipe",x,y,x+120,y,180)
            locate(private)
            print("PASS private and regular overviews stay separate and the address gesture respects the group",flush=True)
            tap("标签页，1 个")
            locate(private)
            shell("uiautomator","dump","/sdcard/terra-overview.xml")
            root = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-overview.xml"))
            cards = []
            for node in root.iter("node"):
                if node.get("clickable") != "true":
                    continue
                descendants = list(node.iter("node"))
                if any(n.get("text")==private for n in descendants) and any(n.get("content-desc")=="关闭标签页" for n in descendants):
                    cards.append(tuple(map(int,re.findall(r"\d+",node.get("bounds")))))
            l,t,r,bottom = min(cards,key=lambda box:(box[2]-box[0])*(box[3]-box[1]))
            x = l + (r-l)*4//5
            y = t + (bottom-t)*2//3
            shell("input","swipe",x,y,x-(r-l)//5,y,450)
            locate("无痕标签页")
            assert private in labels()
            shell("input","swipe",x,y,l+(r-l)//8,y,280)
            time.sleep(.5)
            assert private not in labels()
            print("PASS short card swipe returns and deliberate left swipe closes",flush=True)
            tap("普通标签")
            tap(a)
            locate(a)
            print("PASS closing the last private tab returns to a regular page",flush=True)
        finally:
            if recording and recording.poll() is None: recording.wait(timeout=10)
            adb("reverse","--remove",f"tcp:{port}")
            site.shutdown()


if __name__ == "__main__":
    main()
