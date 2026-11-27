"""Exercise link preview dismissal and the original tab's real navigation."""
from statistics import median
import threading
import time
from urllib.parse import urlsplit

from link_groups import preview_bounds
from PIL import ImageStat


def fixture(handler):
    site = handler.server
    path = urlsplit(handler.path).path
    if path == "/handoff-source":
        html = """<meta name=viewport content="width=device-width,initial-scale=1">
<body style="background:#4488cc"><h1>Trail notebook</h1>
<label for=notes>Trail notes</label><input id=notes name=notes type=text><output></output>
<a href="/handoff-cancel" style="position:absolute;top:38%;left:42%;padding:12px">Inspect route</a>
<a href="/handoff-route" style="position:absolute;top:58%;left:42%;padding:12px">Visit route</a>
<a href="#last-stop" style="position:absolute;top:70%;left:42%;padding:12px">Last stop link</a>
<section id="last-stop" style="position:absolute;top:180vh;left:0;width:100%;height:100vh;background:#bc7040">Last stop</section>
<script>
const field = document.querySelector('input');
field.oninput = () => {
  sessionStorage.setItem('trail-note',field.value);
  document.querySelector('output').textContent = field.value;
};
</script>"""
    elif path in ("/handoff-cancel", "/handoff-route"):
        with site.handoff_lock:
            site.handoff_count[path] += 1
            count = site.handoff_count[path]
        if path == "/handoff-route" and count == 2:
            site.handoff_requested.set()
            site.handoff_release.wait(14)
        html = """<meta name=viewport content="width=device-width,initial-scale=1">
<body style="background:#408058"><h1>Trail map</h1><p id=note></p>
<button onclick="document.body.style.background='#d8a640';this.textContent='Trail marked'">Mark trail</button>
<script>document.querySelector('#note').textContent='Notes: '+sessionStorage.getItem('trail-note')</script>"""
    else:
        return handler.send_error(404)
    data = html.encode()
    handler.send_response(200)
    handler.send_header("Cache-Control","private, max-age=120" if path == "/handoff-source" else "no-store")
    handler.send_header("Content-Type","text/html; charset=utf-8")
    handler.send_header("Content-Length",str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


def verify(shell, locate, shot, site):
    site.handoff_lock = threading.Lock()
    site.handoff_count = {"/handoff-cancel": 0, "/handoff-route": 0}
    site.handoff_requested = threading.Event()
    site.handoff_release = threading.Event()
    tap = lambda point: shell("input","tap",*point)

    def open_menu(label):
        point = locate(label)
        shell("input","motionevent","DOWN",*point)
        try:
            time.sleep(.9)
            shot("handoff-held-"+label.split()[0])
        finally:
            shell("input","motionevent","UP",*point)
        locate("隐藏预览")
        end = time.monotonic()+8
        while time.monotonic() < end:
            image = shot("handoff-card")
            bounds = preview_bounds(image)
            if bounds:
                l,t,r,b = bounds
                return image, ((l+r)//2,(t+b)//2)
        raise AssertionError("The previewed document did not paint")

    def color_at(image, fraction_x, fraction_y):
        return image.getpixel((int(image.width*fraction_x),int(image.height*fraction_y)))

    def restore_speed(previous):
        if previous == "null":
            # WindowManager retains the last effective native animator scale
            # if an unset key is simply deleted. Apply the platform default
            # before restoring the absence of the setting.
            shell("settings","put","global","animator_duration_scale","1")
            time.sleep(.5)
            shell("settings","delete","global","animator_duration_scale")
        else:
            shell("settings","put","global","animator_duration_scale",previous)

    url = f"http://127.0.0.1:{site.server_port}/handoff-source"
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",url)
    tap(locate("Trail notes"))
    shell("input","text","Harbor72")
    shell("input","keyevent","4")
    locate("Harbor72")
    initial = shot("handoff-source")
    cx,cy = locate("页面菜单")
    glass_patch = (cx+int(initial.width*.065),cy-4,cx+int(initial.width*.106),cy+4)
    def colors(image):
        return color_at(image,.05,.45),ImageStat.Stat(image.crop(glass_patch)).median[2]
    open_menu("Inspect route")
    shell("input","keyevent","4")
    locate("Visit route")
    locate("Harbor72")
    assert site.handoff_count["/handoff-cancel"] == 1,site.handoff_count
    assert max(abs(a-b) for a,b in zip(color_at(shot("handoff-cancelled"),.04,.46),(68,136,204))) < 7
    print("PASS cancelling a loaded preview keeps the source document and sends no tab request",flush=True)

    opened, card = open_menu("Visit route")
    samples = []
    for x in (.025,.07,.13,.82,.92,.975):
        for y in (.30,.46,.56,.72):
            rest = color_at(initial,x,y)
            shade = color_at(opened,x,y)
            if max(abs(a-b) for a,b in zip(rest,(68,136,204)))<7 and .44<sum(shade)/sum(rest)<.64:
                samples.append((x,y,rest))
    assert len(samples) >= 3,"No exposed source beside the menu"
    speed = shell("settings","get","global","animator_duration_scale").decode().strip()
    fade_samples = []
    try:
        # Stretch only the dismissal so an adb screenshot can observe the
        # real request and the fading menu in the same frame on slow hosts.
        shell("settings","put","global","animator_duration_scale","8")
        time.sleep(.25)
        tap(card)
        assert site.handoff_requested.wait(5),"The committed tab did not start its own navigation"
        during = shot("handoff-request-in-flight")
        brightness = median(sum(color_at(during,x,y))/sum(rest) for x,y,rest in samples)
        assert .43 < brightness < .93,("The source had already undimmed before the tab request",brightness)
        print(f"PASS the live tab requests the destination during menu dismissal (background ratio {brightness:.2f})",flush=True)
        site.handoff_release.set()
        until = time.monotonic()+5
        while time.monotonic()<until:
            frame = colors(shot(f"handoff-fade-{len(fade_samples):02}"))
            fade_samples.append(frame)
            if frame[0] == (64,128,88): break
    finally:
        site.handoff_release.set()
        restore_speed(speed)

    locate("Notes: Harbor72")
    target = shot("handoff-target")
    _,start_blue = colors(initial)
    _,end_blue = colors(target)
    assert start_blue-end_blue>12,(glass_patch,start_blue,end_blue)
    # Dimming affects all three channels. During the actual document blend
    # green stays near 128 while blue moves from 204 to 88.
    matching = []
    for rgb,blue in fade_samples:
        progress = (204-rgb[2])/116
        if 122<=rgb[1]<=140 and .22<progress<.87:
            material = (start_blue-blue)/(start_blue-end_blue)
            if .08<material<.96 and abs(material-progress)<.38:
                matching.append((round(progress,2),round(material,2)))
    assert matching,("LiquidKit did not follow the document during its fade",start_blue,end_blue,fade_samples)
    print("PASS the toolbar glass takes an intermediate color during the actual page blend",matching,flush=True)
    tap(locate("Mark trail"))
    locate("Trail marked")
    assert max(abs(a-b) for a,b in zip(color_at(shot("handoff-real-tap"),.4,.6),(216,166,64)))<7
    tap(locate("后退"))
    locate("Trail notebook")
    assert max(abs(a-b) for a,b in zip(color_at(shot("handoff-back"),.4,.6),(68,136,204)))<7
    tap(locate("前进"))
    locate("Notes: Harbor72")
    forward = color_at(shot("handoff-forward"),.4,.6)
    assert any(max(abs(a-b) for a,b in zip(forward,color))<7 for color in ((64,128,88),(216,166,64))),forward
    print("PASS native navigation retains the tab's sessionStorage, responds to a webpage button, and renders Back and Forward",flush=True)

    tap(locate("后退"))
    point = locate("Last stop link")
    shell("input","motionevent","DOWN",*point)
    try:
        head = locate("隐藏预览")
    finally:
        shell("input","motionevent","UP",*point)
    tap((head[0]-50,head[1]+85))
    started = time.monotonic()
    while time.monotonic()-started < 2.1:
        actual = color_at(shot("handoff-fragment"),.34,.56)
        if max(abs(a-b) for a,b in zip(actual,(188,112,64))) < 7:
            break
    else:
        raise AssertionError("The same-document fragment remained covered after the menu closed")
    print("PASS opening a fragment exposes its actual scrolled region without waiting for a new document",flush=True)
    tap(locate("后退"))
    locate("Visit route")
    assert max(abs(a-b) for a,b in zip(color_at(shot("handoff-fragment-back"),.4,.6),(68,136,204)))<7
