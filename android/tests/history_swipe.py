"""Verify same-tab Safari-style edge navigation with Android touch events and actual page pixels."""
import argparse
from http.server import ThreadingHTTPServer
from io import BytesIO
import os
from pathlib import Path
import re
import shutil
import subprocess
from threading import Thread
import time
from xml.etree import ElementTree

from PIL import Image
from safari_motion import Pages


class EdgePages(Pages):
    def do_GET(self):
        if self.path != "/garden":
            return super().do_GET()
        html = b"""<meta name=viewport content='width=device-width'><title>Garden</title>
        <style>body{margin:0}section{height:100vh;padding:20px;box-sizing:border-box}</style>
        <section style='background:#408058'><h1>Garden</h1></section>
        <section style='height:150vh;background:#164658'>Lower garden</section>"""
        self.send_response(200)
        self.send_header("Content-Type","text/html")
        self.send_header("Content-Length",str(len(html)))
        self.end_headers()
        self.wfile.write(html)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("serial")
    parser.add_argument("--record",type=Path)
    args = parser.parse_args()
    binary = shutil.which("adb") or str(Path(os.environ["ANDROID_HOME"],"platform-tools","adb.exe" if os.name=="nt" else "adb"))
    adb = [binary,"-s",args.serial]

    def run(*cmd):
        return subprocess.check_output(adb+list(map(str,cmd)),timeout=20)

    def touch(kind,x,y):
        run("shell","input","motionevent",kind,x,y)

    def locate(label):
        end = time.monotonic()+20
        while time.monotonic()<end:
            run("shell","uiautomator","dump","/sdcard/terra-edge.xml")
            tree = ElementTree.fromstring(run("exec-out","cat","/sdcard/terra-edge.xml"))
            for n in tree.iter("node"):
                if label in (n.get("text"),n.get("content-desc")):
                    l,t,r,b=map(int,re.findall(r"\d+",n.get("bounds")))
                    return (l+r)//2,(t+b)//2
        raise AssertionError(label)

    def frame():
        return Image.open(BytesIO(run("exec-out","screencap","-p"))).convert("RGB")

    def near(pixel,target):
        return max(abs(a-b) for a,b in zip(pixel,target))<40

    sky,garden=(68,136,204),(64,128,88)
    with ThreadingHTTPServer(("127.0.0.1",0),EdgePages) as server:
        Thread(target=server.serve_forever,daemon=True).start()
        port=server.server_port
        run("reverse",f"tcp:{port}",f"tcp:{port}")
        recorder=None
        try:
            run("shell","am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                "-d",f"http://127.0.0.1:{port}/sky")
            run("shell","input","tap",*locate("Open garden"))
            locate("Garden")
            image=frame()
            w,h=image.size
            y=h//2
            assert near(image.getpixel((w//2,y)),garden)
            if args.record:
                recorder=subprocess.Popen(adb+["shell","screenrecord","--time-limit","11","/sdcard/terra-edge.mp4"])
                time.sleep(.6)
            def held(edge,first,mid):
                touch("DOWN",edge,y)
                touch("MOVE",first,y)
                touch("MOVE",mid,y)
                time.sleep(.15)
                image=frame()
                left=image.getpixel((w//5,y))
                right=image.getpixel((w*4//5,y))
                assert near(left,sky) and near(right,garden),(left,right)

            left,right=4,w-5
            held(left,w//7,w//2)
            touch("MOVE",w//14,y)
            touch("UP",w//14,y)
            time.sleep(.4)
            assert near(frame().getpixel((w//5,y)),garden)
            print("PASS inward drag reveals the preceding page and reversing it leaves history untouched",flush=True)
            held(left,w//7,w//2)
            touch("UP",w//2,y)
            time.sleep(.9)
            assert near(frame().getpixel((w*4//5,y)),sky)
            held(right,w*6//7,w//2)
            touch("UP",w//2,y)
            time.sleep(.9)
            locate("Garden")
            assert near(frame().getpixel((w//5,y)),garden)
            print("PASS opposite edges go back then forward within one live WebView",flush=True)
            run("shell","input","swipe",left,y,left+2,int(h*.17),420)
            time.sleep(.7)
            assert near(frame().getpixel((w//2,int(h*.72))),(22,70,88)), "Vertical edge scroll was intercepted"
            print("PASS a vertical swipe starting at the edge still scrolls the document",flush=True)
            run("shell","input","swipe",left,y,w*3//4,y,350)
            locate("Sky")
            touch("DOWN",right,y)
            touch("MOVE",w*6//7,y)
            touch("MOVE",w//2,y)
            time.sleep(.15)
            pixel=frame().getpixel((w*4//5,int(h*.72)))
            assert near(pixel,(22,70,88)), ("Scrolled preview lost its position",pixel)
            touch("UP",w//2,y)
            time.sleep(1)
            pixel=frame().getpixel((w//2,int(h*.72)))
            assert near(pixel,(22,70,88)), ("Live page lost its position",pixel)
            print("PASS the forward preview and restored live page both keep the vertical scroll position",flush=True)
            if recorder:
                recorder.wait(timeout=15)
                run("pull","/sdcard/terra-edge.mp4",str(args.record))
        finally:
            if recorder and recorder.poll() is None: recorder.wait(timeout=15)
            run("reverse","--remove",f"tcp:{port}")
            server.shutdown()


if __name__=="__main__":
    main()
