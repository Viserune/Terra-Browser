"""Two marked documents make translation, edge shading and live rendering measurable."""
from http import HTTPStatus
from itertools import groupby
import json
from pathlib import Path
import subprocess
import threading
import time


COAST = (64, 130, 147)
CANYON = (204, 127, 75)
PULSE = (207, 59, 154)


def serve_layers(handler):
    name = handler.path.lstrip("/")
    if name not in ("edge-coast", "edge-canyon", "edge-pulse", "edge-painted", "edge-chapters"):
        return False
    if name in ("edge-pulse", "edge-painted"):
        if name == "edge-pulse":
            handler.server.pulse.wait(35)
        else:
            handler.server.painted.set()
        handler.send_response(HTTPStatus.NO_CONTENT)
        handler.end_headers()
        return True

    coast = name == "edge-coast"
    title = "Coast" if coast else "Canyon"
    marker = 53 if coast else 24
    color = bytes(COAST if coast else CANYON).hex()
    link = "<a href='/edge-canyon'>Open canyon</a>" if coast else ""
    script = "" if coast else """<script>
    fetch('/edge-pulse').then(() => {
      document.querySelector('aside').style.background = '#cf3b9a';
      requestAnimationFrame(() => requestAnimationFrame(() => fetch('/edge-painted')));
    });
    </script>"""
    html = f"""<!doctype html><meta name=viewport content="width=device-width,initial-scale=1">
    <title>{title}</title><style>
    body {{margin:0; background:#{color}; font:20px system-ui}}
    main {{padding:22px}} i {{position:fixed; top:0; bottom:0; left:{marker}vw; width:4vw; background:white}}
    aside {{position:fixed; top:62%; height:7%; width:100%}}
    </style><main><h1>{title}</h1>{link}</main><i></i><aside></aside>{script}"""
    if name == "edge-chapters":
        html = """<!doctype html><meta name=viewport content="width=device-width"><title>Chapters</title>
        <style>body{margin:0;background:#408293}body.second{background:#cc7f4b}
        button{margin:24px;padding:16px}</style>
        <button onclick="history.pushState({second:true},'',location.pathname);show()">Second chapter</button>
        <script>function show(){document.body.classList.toggle('second',!!history.state?.second)}
        onpopstate=show;show()</script>"""
    data = html.encode()
    handler.send_response(HTTPStatus.OK)
    handler.send_header("Content-Type", "text/html;charset=utf-8")
    handler.send_header("Content-Length", str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)
    return True


def close(color, target, tolerance=14):
    return max(abs(a-b) for a,b in zip(color,target)) <= tolerance


def markers(image, y):
    flags = [min(image.getpixel((x, y))) > 184 and
             max(image.getpixel((x, y))) - min(image.getpixel((x, y))) < 12
             for x in range(image.width)]
    spans = []
    start = 0
    for white, points in groupby(flags):
        width = sum(1 for _ in points)
        if white and width > image.width * .018:
            spans.append((start, start+width))
        start += width
    return spans


def check_layers(server, adb, run, locate, touch, frame, screens, recording):
    server.pulse = threading.Event()
    server.painted = threading.Event()
    uri = f"http://127.0.0.1:{server.server_port}/edge-coast"
    run("shell","am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",uri)
    run("shell","input","tap",*locate("Open canyon"))
    locate("Canyon")
    base = frame()
    w,h = base.size
    y = h//2
    left,right = round(w*.015),round(w*.985)
    if screens:
        screens.mkdir(parents=True,exist_ok=True)
    def save(name, image=None):
        image = frame() if image is None else image
        if screens: image.save(screens/(name+".png"))
        return image
    def settled(name, color):
        until = time.monotonic()+6
        expected = .53 if color == COAST else .24
        while time.monotonic()<until:
            image = frame()
            spans = markers(image,y)
            if (all(close(image.getpixel((x,y)),color,5) for x in (w//50,w//2,w-1-w//50)) and
                len(spans)==1 and abs(spans[0][0]-expected*w)<=2):
                return save(name,image)
            time.sleep(.08)
        raise AssertionError(name+" did not settle")
    def move(start, stop):
        for i in range(1,6):
            touch("MOVE",round(start+(stop-start)*i/5),y)
    def layers(name, start, end):
        touch("DOWN", start, y)
        move(start,end)
        time.sleep(.12)
        image = save(name)
        points = markers(image,y)
        assert len(points)==2, (name,points)
        assert close(image.getpixel((w//16,y)),COAST,45), (name,"left page")
        assert close(image.getpixel((15*w//16,y)),CANYON,18), (name,"right page")
        # Both marker positions are measured from their left edge at rest.
        offsets = [(p[0]-f*w)/w for p,f in zip(points,(.53,.24))]
        boundary = (end-start)/w if start < end else 1-(start-end)/w
        assert abs(offsets[1]-boundary)<.018, (name,"foreground marker",offsets,boundary)
        assert abs(offsets[0]+.23*(1-boundary))<.018, (name,"background marker",offsets,boundary)
        print(name, "marker offsets", [round(delta,3) for delta in offsets],flush=True)
        return image, points

    recorder = None
    events = []
    def mark(name):events.append([name,time.monotonic()-started])
    try:
        save("canyon",base)
        if recording:
            recorder = subprocess.Popen(adb+["shell","screenrecord","--bit-rate","9000000","--time-limit","35","/sdcard/terra-layers.mp4"])
            time.sleep(.55)
        started = time.monotonic()
        back = locate("后退")
        mark("button-back")
        run("shell","input","tap",*back)
        settled("button-back",COAST)
        forward = locate("前进")
        expanded = save("forward-available")
        mark("button-forward")
        run("shell","input","tap",*forward)
        settled("button-forward",CANYON)
        time.sleep(.55)
        collapsed = save("back-available")
        for image,(x,yc),light in [(expanded,forward,True),(collapsed,back,False)]:
            glyph = image.crop((x-8,yc-10,x+9,yc+11))
            count = sum(1 for pixel in glyph.getdata() if (min(pixel)>224 if light else max(pixel)<70))
            assert count >= 6, ("Arrow contrast",light,count)
        if w/h < .6:
            assert back[1] > h*.7 and forward[1] == back[1]
        print("PASS the toolbar controls navigate and use contrasting ink on both page colors",flush=True)
        mid = round(.61*w)
        mark("cancel-down")
        held,points = layers("cancel-held",left,mid)
        server.pulse.set()
        assert server.painted.wait(5), "The currently displayed document did not update"
        until = time.monotonic()+4
        while time.monotonic()<until:
            image = save("live-held")
            if any(close(image.getpixel((w*4//5,py)),PULSE) for py in range(h//2,4*h//5,3)):
                break
        else:raise AssertionError("The foreground page froze while the finger was down")
        assert markers(image,y)==points, "The page moved while the finger was held still"
        move(mid, left+round(.05*w))
        mark("cancel-release")
        touch("UP",left+round(.05*w),y)
        settled("cancelled",CANYON)
        print("PASS live document repainted at a stationary drag position and cancellation kept the current entry",flush=True)
        mark("back-down")
        layers("back-held",left,mid)
        mark("back-release")
        touch("UP",mid,y)
        settled("back",COAST)
        time.sleep(.4)
        mark("forward-down")
        stop = round(.45*w)
        layers("forward-held",right,stop)
        mark("forward-release")
        touch("UP",stop,y)
        settled("forward",CANYON)
        assert locate("Canyon")
        print("PASS held back and forward expose both page markers and land on the requested history entry",flush=True)
        run("shell","am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
            "-d",uri.replace("edge-coast","edge-chapters"))
        run("shell","input","tap",*locate("Second chapter"))
        time.sleep(.5)
        for start,end,expected in ((left,mid,COAST),(right,stop,CANYON)):
            touch("DOWN",start,y)
            move(start,end)
            time.sleep(.12)
            image = frame()
            assert close(image.getpixel((w//8,y)),COAST,40) and close(image.getpixel((7*w//8,y)),CANYON), "Same-URL entries lost their individual colors"
            touch("UP",end,y)
            until = time.monotonic()+4
            while time.monotonic()<until:
                image = frame()
                if all(close(image.getpixel((x,y)),expected) for x in (w//50,w//2,49*w//50)):break
            else:raise AssertionError("Same-document history did not reach the requested entry")
            time.sleep(.35)
        print("PASS two pushState entries sharing a URL retain different previews and both restore",flush=True)
    finally:
        server.pulse.set()
        if screens: (screens/"events.json").write_text(json.dumps(events),encoding="utf-8")
        if recorder:
            recorder.wait(timeout=40)
            if recorder.returncode:raise AssertionError("Android screenrecord failed")
            run("pull","/sdcard/terra-layers.mp4",str(Path(recording)))
