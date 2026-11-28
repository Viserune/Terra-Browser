"""Check the progress line inside the address capsule during a controlled document load."""
import time
from threading import Event
import numpy as np


def fixture(handler):
    first=b"""<!doctype html><meta name=viewport content='width=device-width'><title>Loading sample</title>
        <body style='background:#d6ddd9'><h1>Loading sample</h1>""" + b" " * 4096
    last=b"<p>Finished</p></body>"
    handler.send_response(200)
    handler.send_header("Content-Type","text/html;charset=utf-8")
    handler.send_header("Cache-Control","no-store")
    handler.send_header("Content-Length",str(len(first)+len(last)))
    handler.end_headers()
    try:
        handler.wfile.write(first)
        handler.wfile.flush()
        handler.server.started.set()
        handler.server.finish_document.wait(55)
        handler.wfile.write(last)
    except (BrokenPipeError,ConnectionResetError): pass


def verify(shell,locate,shot,server):
    server.started=Event()
    server.finish_document=Event()
    old=shell("cmd","uimode","night").decode().strip().split()[-1]
    assert old in ("yes","no","auto")
    try:
        shell("cmd","uimode","night","no")
        shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
              "-d",f"http://127.0.0.1:{server.server_port}/load-bar/page")
        assert server.started.wait(10),"Fixture document was not requested"
        shot("address-partial-document")
        _,cy=locate("停止载入")
        def line_length(name):
            pixels=np.array(shot(name)).astype(int)
            height,width=pixels.shape[:2]
            # The stop button is vertically centered in the glass field.
            band=pixels[cy+12:min(height,cy+32),width//10:width*9//10]
            blue=(band[:,:,0]<45)&(band[:,:,1]>65)&(band[:,:,1]<140)&(band[:,:,2]>155)
            lengths=[]
            for row in blue:
                edges=np.diff(np.pad(row.astype(int),(1,1)))
                lengths.extend(np.flatnonzero(edges==-1)-np.flatnonzero(edges==1))
            return max(lengths,default=0)
        assert line_length("address-loading")>12,"Progress line not painted along the address field"
        server.finish_document.set()
        locate("重新载入")
        time.sleep(.6)
        assert line_length("address-loaded")<9,"Progress line survived the completed load"
        print("PASS stop changes to reload and the address field progress line disappears after completion",flush=True)
    finally:
        server.finish_document.set()
        shell("cmd","uimode","night",old)
