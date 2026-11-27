"""Verify WebView find highlights, navigation, entry points and the inline keyboard toolbar."""
import re
import time
from xml.etree import ElementTree


def fixture(handler):
    sections = "".join(
        f"<section style='background:{color}'><p>{number}. Coastal <b>transect</b></p></section>"
        for number, color in enumerate(("#d9e9ef", "#c4dfc8", "#e7cfc1"), start=1))
    data = ("""<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>
        <title>Coastal samples</title><style>*{box-sizing:border-box}body{margin:0}section{height:1050px;
        padding:16vh 5vw;font:26px sans-serif;color:#17212f}p{margin:0}output{position:fixed;right:8px;top:10px;
        font:14px sans-serif;background:white}</style><output>Scroll 0</output>""" + sections +
        "<script>onscroll=()=>document.querySelector('output').textContent='Scroll '+Math.round(scrollY)</script>").encode()
    handler.send_response(200)
    handler.send_header("Content-Type","text/html;charset=utf-8")
    handler.send_header("Content-Length",str(len(data)))
    handler.end_headers()
    handler.wfile.write(data)


def verify(adb, shell, locate, shot, port, tablet):
    def nodes():
        shell("uiautomator","dump","/sdcard/terra-find.xml")
        return list(ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-find.xml")).iter("node"))
    def tap(label):
        shell("input","tap",*locate(label))
    def scroll_offset():
        return int(next(n.get("text").split()[1] for n in nodes() if n.get("text","").startswith("Scroll ")))
    def buttons_enabled(expected):
        tree = nodes()
        for label in ("上一个匹配","下一个匹配"):
            node = next(n for n in tree if n.get("clickable") == "true" and
                        any(child.get("content-desc")==label for child in n.iter("node")))
            assert (node.get("enabled")=="true")==expected, (label,node.attrib)
    def ime():
        return b"mInputShown=true" in shell("dumpsys","input_method")
    def highlight_pixels(image):
        # Only the page uses orange in this fixture. Ignore toolbars and IME candidates.
        area = (0,bar_y+65,width,height//2) if tablet else (0,60,width,bar_y-65)
        page = image.crop(area)
        return sum(n for n,(r,g,b) in page.getcolors(page.width*page.height)
                   if r>225 and 90<g<200 and b<95)
    def shortcut(*keys):
        shell("input","keycombination",*keys)

    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/find-coast")
    tap("页面菜单")
    tap("在页面中查找")
    _, bar_y = locate("结束查找")
    assert ime(), "on-screen keyboard did not open"
    buttons_enabled(False)
    shell("input","text","transect")
    locate("1/3")
    first=scroll_offset()
    frame=shot("find-first")
    width,height=frame.size
    assert highlight_pixels(frame)>200
    sample_x, sample_y = locate("下一个匹配")
    sample = (sample_x, sample_y-24)
    first_glass = frame.getpixel(sample)
    assert bar_y<height*.8 and (bar_y<height*.18 if tablet else bar_y>height*.28),(frame.size,bar_y)
    buttons_enabled(True)
    tap("下一个匹配")
    locate("2/3")
    second=scroll_offset()
    frame=shot("find-second")
    assert highlight_pixels(frame)>200
    if not tablet:
        second_glass=frame.getpixel(sample)
        assert first_glass[2]-second_glass[2]>10, (first_glass,second_glass)
    assert second-first>300,(first,second)
    shortcut("KEYCODE_CTRL_LEFT","KEYCODE_G")
    locate("3/3")
    third=scroll_offset()
    assert third-second>300,(second,third)
    tap("下一个匹配")
    locate("1/3")
    assert scroll_offset()<second
    tap("上一个匹配")
    locate("3/3")
    print("PASS keyboard visibility, count and actual WebView scrolling through three results in both directions",flush=True)
    tap("清空查找文字")
    buttons_enabled(False)
    shell("input","text","unmatchedword")
    locate("无结果")
    assert highlight_pixels(shot("find-no-result"))<30
    buttons_enabled(False)
    tap("清空查找文字")
    shell("input","text","2.")
    locate("1/1")
    buttons_enabled(False)
    shell("input","keyevent","KEYCODE_ESCAPE")
    locate("页面菜单")
    shortcut("KEYCODE_CTRL_LEFT","KEYCODE_F")
    locate("结束查找")
    buttons_enabled(False)
    assert not any(n.get("text") in ("无结果","1/1","2.") for n in nodes() if n.get("class")!="android.webkit.WebView")
    tap("结束查找")
    locate("页面菜单")
    print("PASS no-result and single-result states, Escape, Ctrl+F and a clean new search",flush=True)
    tap("不安全 · 127.0.0.1")
    tap("清空输入")
    shell("input","text","coastal")
    tap("在此页查找")
    locate("结束查找")
    # The current match may start at the previous reading position.
    assert any(re.fullmatch(r"[1-3]/3",n.get("text","")) for n in nodes())
    shot("find-from-address")
    tap("结束查找")
    locate("页面菜单")
    assert not ime()
    print("PASS address search passes the query into the current page and dismisses the keyboard",flush=True)
