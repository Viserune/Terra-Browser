"""Reader mode regression with an article, anchored sections and an actual local PNG."""
from io import BytesIO
import re
import subprocess
import time
from xml.etree import ElementTree
from PIL import Image


def fixture(handler):
    if handler.path in ("/reader-figure.png","/reader-blue.png","/reader-orange.png"):
        buf = BytesIO()
        width, height, color = {
            "/reader-figure.png": (270,72,(29,148,122)),
            "/reader-blue.png": (520,1350,(31,95,174)),
            "/reader-orange.png": (520,1350,(183,92,38))
        }[handler.path]
        Image.new("RGB",(width,height),color).save(buf,"PNG")
        data = buf.getvalue()
        content_type = "image/png"
    elif handler.path == "/reader-optics":
        data = b"""<meta name=viewport content='width=device-width,initial-scale=1'><title>Survey plates</title><article><h1>Survey plates</h1>
        <p>Two exposures from the coastal survey.</p><figure><img src='/reader-blue.png' alt='Blue plate'></figure>
        <figure><img src='/reader-orange.png' alt='Orange plate'></figure><p>End of plates.</p></article>"""
        content_type = "text/html;charset=utf-8"
    elif handler.path == "/reader-article":
        paragraphs = "".join(f"<p>Field note {i}. The samples from the valley were recorded in sequence.</p>"
                             for i in range(24))
        data = f"""<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>
        <title>Dispatch | Valley Journal</title><style>body{{margin:12px;font:18px sans-serif;background:#d7e1ef}}
        output{{position:fixed;top:5px;right:8px;background:white;z-index:2}}img{{max-width:100%}}</style>
        <output>Source offset 0</output><nav>Subscription navigation</nav>
        <article><p>Short unrelated item</p></article>
        <article><header><h1>Valley dispatch</h1><p>By Rowan</p></header>
        <figure><img src='/reader-figure.png' alt='Valley map'><figcaption>Ravine transect</figcaption></figure>
        <p><a href='#field-notes'>Jump to notes</a></p><p><a href='/garden'>Open specimen</a></p>
        <aside>Promotional sidebar</aside><p hidden>Hidden annotation</p>
        <blockquote>Water marks tell a story.</blockquote><ol start=3><li>East bank</li><li>West bank</li></ol>
        <h2>Survey route</h2>{paragraphs}<h2 id='field-notes'>Observations</h2><p>End of the transect.</p>{paragraphs}
        </article><script>window.onscroll=()=>document.querySelector('output').textContent='Source offset '+Math.round(scrollY);
        window.onload=()=>setTimeout(()=>scrollTo(0,540),90)</script>""".encode()
        content_type = "text/html;charset=utf-8"
    else:
        return False
    handler.send_response(200)
    handler.send_header("Content-Type",content_type)
    handler.send_header("Content-Length",str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)
    return True


def verify_reader(adb,shell,locate,shot,port,prefix,movie):
    def tree():
        shell("uiautomator","dump","/sdcard/terra-reader.xml")
        return list(ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-reader.xml")).iter("node"))
    def tap(label):
        shell("input","tap",*locate(label))
    def enter():
        tap("页面菜单")
        tap("显示阅读器")
        locate("显示原网页")
    def rect(label):
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            boxes = [tuple(map(int,re.findall(r"-?\d+",n.get("bounds")))) for n in tree() if n.get("text")==label]
            visible = [b for b in boxes if b[2]>b[0] and b[3]>max(0,b[1])]
            if visible:
                return min(visible,key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
        raise AssertionError((label,boxes))
    def offset():
        label=next(n.get("text") for n in tree() if n.get("text","").startswith("Source offset "))
        return int(label.split()[-1])
    def options():
        tap("阅读器外观")
        locate("放大正文字号")
    def open_source():
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
              "-d",f"http://127.0.0.1:{port}/reader-article")
        locate("Source offset ",True)
        time.sleep(.35)

    open_source()
    start=offset()
    assert start>400, start
    enter()
    locate("By Rowan")
    shot("reader-entry")
    labels={n.get("text") for n in tree()}
    locate("Ravine transect")
    assert not labels.intersection({"Subscription navigation","Short unrelated item","Promotional sidebar","Hidden annotation"})
    options()
    tap("白色")
    tap("恢复正文字号")
    tap("衬线体")
    tap("完成")
    locate("Valley dispatch")
    before=rect("Valley dispatch")
    frame=shot("reader-white")
    assert sum(n for n,c in frame.getcolors(frame.width*frame.height) if c==(29,148,122))>1000
    assert frame.getpixel((10,frame.height//2))==(255,255,255)
    recording=None
    if movie:
        recording=subprocess.Popen(prefix+["shell","screenrecord","--time-limit","24","/sdcard/terra-reader.mp4"])
    try:
        options()
        tap("放大正文字号")
        tap("放大正文字号")
        tap("无衬线体")
        tap("纸色")
        tap("完成")
        locate("Valley dispatch")
        after=rect("Valley dispatch")
        page=shot("reader-sepia")
        assert after[3]-after[1]>before[3]-before[1],(before,after)
        assert page.getpixel((10,page.height//2))==(243,232,210)
    finally:
        if recording:
            recording.wait(timeout=26)
            adb("pull","/sdcard/terra-reader.mp4",str(movie))
    print("PASS extracted article title, byline, image and caption; real type size and paper color changed",flush=True)
    tap("文章目录")
    tap("Survey route")
    time.sleep(.8)
    assert rect("Survey route")[1]<page.height*.28
    tap("文章目录")
    tap("Observations")
    time.sleep(.8)
    assert rect("Observations")[1]<page.height*.28
    # Reopen at the top to exercise an authored same-document link as well.
    shell("input","swipe",page.width//2,int(page.height*.2),page.width//2,int(page.height*.86),70)
    for _ in range(8):
        candidates=[n for n in tree() if n.get("content-desc")=="Jump to notes" and n.get("bounds")!="[0,0][0,0]"]
        if candidates: break
        shell("input","swipe",page.width//2,int(page.height*.2),page.width//2,int(page.height*.86),100)
    tap("Jump to notes")
    time.sleep(.4)
    locate("Observations")
    note_y=rect("Observations")[1]
    assert note_y<page.height*.28, note_y
    tap("显示原网页")
    locate("Source offset ",True)
    assert abs(offset()-start)<=3,(start,offset())
    enter()
    locate("Observations")
    assert abs(rect("Observations")[1]-note_y)<20
    print("PASS fragment link, original webpage position and reopened Reader position",flush=True)
    shell("am","force-stop","app.terra.browser")
    open_source()
    enter()
    locate("Valley dispatch")
    page=shot("reader-restart")
    assert page.getpixel((10,page.height//2))==(243,232,210)
    options()
    locate("125%")
    selected = {child.get("text") for n in tree()
                if n.get("selected") == "true" or n.get("checked") == "true"
                for child in n.iter("node")}
    assert {"纸色","无衬线体"} <= selected, selected
    tap("完成")
    tap("Open specimen")
    locate("Garden")
    tap("页面菜单")
    locate("页面缩放")
    time.sleep(.6)
    assert not any(n.get("text")=="显示阅读器" for n in tree())
    tap("完成")
    print("PASS Reader appearance survives restart and links open in the original browser tab",flush=True)

def verify_reader_glass(shell,locate,shot,port):
    from PIL import ImageStat
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/reader-optics")
    shell("input","tap",*locate("页面菜单"))
    shell("input","tap",*locate("显示阅读器"))
    _,y=locate("显示原网页")
    time.sleep(.4)
    img=shot("reader-blue-plate")
    w,h=img.size
    # Sample within the capsule between the two controls; the reference lies in the open margin beside it.
    def patch(frame,x,y):
        return ImageStat.Stat(frame.crop((x-3,y-3,x+4,y+4))).mean
    def measured(frame):
        return patch(frame,int(w*.61),y-22), patch(frame,int(w*.61),y-96)
    blue,blue_ref=measured(img)
    assert blue_ref[2]-blue_ref[0]>80 and blue[2]-blue[0]>7,(blue,blue_ref)
    for _ in range(8):
        shell("input","swipe",w//2,h*3//5,w//2,h//3,440)
        time.sleep(.35)
        img=shot("reader-orange-plate")
        orange,orange_ref=measured(img)
        if orange_ref[0]-orange_ref[2]>80: break
    assert orange_ref[0]-orange_ref[2]>80 and orange[0]-orange[2]>7,(orange,orange_ref)
    assert locate("显示原网页")[1]==y
    shell("input","tap",*locate("显示原网页"))
    print("PASS blue and orange plates refract through a stationary Reader control",blue,orange,flush=True)
