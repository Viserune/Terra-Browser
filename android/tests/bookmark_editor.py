"""Exercise the add/edit and Favorites menu flows with bookmarks created by this test."""
import time
from xml.etree import ElementTree


def replace_field(shell, locate, label, value):
    shell("input","tap",*locate("清除 " + label))
    shell("input","tap",*locate(label))
    shell("input","text",value)
    locate(value)

def verify_bookmarks(shell, locate, adb, shot, port):
    base = f"http://127.0.0.1:{port}"
    first, second = f"Coast{port}", f"Garden{port}"
    def visit(path):
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",base+path)
    def tap(label):
        shell("input","tap",*locate(label))
    def replace(label, value):
        replace_field(shell, locate, label, value)
    def entry(label):
        tap("页面菜单")
        tap(label)
        locate("书签名称")
    def labels():
        shell("uiautomator","dump","/sdcard/terra-bookmark.xml")
        nodes=ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-bookmark.xml")).iter("node")
        return {n.get("text") for n in nodes}
    def swipe_edit(label):
        done_x,_ = locate("完成")
        _,y = locate(label)
        shell("input","swipe",done_x-8,y,int(done_x*.42),y,490)
        tap("编辑")

    visit("/glass-coast")
    locate("浏览峡谷")
    entry("添加书签")
    shot("save-bookmark")
    replace("书签名称",first+"Cancel")
    tap("取消")
    entry("添加书签")
    replace("书签名称",first)
    tap("起始页收藏开关")
    tap("保存")
    tap("书签")
    locate(first)
    assert first+"Cancel" not in labels()
    tap("完成")
    visit("/dune")
    locate("Dune")
    entry("添加书签")
    replace("书签名称",second)
    replace("书签网址",base+"/glass-coast")
    tap("保存")
    locate("此网址已有书签")
    replace("书签网址","ftp://example.com")
    tap("保存")
    locate("请输入有效的 HTTP 或 HTTPS 网址")
    replace("书签网址","mailto:info@example.com")
    tap("保存")
    locate("请输入有效的 HTTP 或 HTTPS 网址")
    replace("书签网址",base+"/garden")
    tap("保存")
    tap("书签")
    locate(first)
    swipe_edit(second)
    replace("书签名称",second+"Saved")
    tap("保存")
    locate(second+"Saved")
    tap(second+"Saved")
    locate("Garden")
    print("PASS cancel, title edit, address edit, invalid scheme, duplicate URL and reopen",flush=True)

    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n","app.terra.browser/.MainActivity")
    locate("Garden")
    tap("页面菜单")
    # The new-tab action is below the page actions on compact phones.
    x,y = locate("页面缩放")
    h = shot("page-menu").height
    shell("input","swipe",x,int(h*.88),x,y,460)
    tap("新建标签页")
    locate(second+"Saved")
    assert first not in labels(), "A library-only bookmark appeared on the start page"
    tap("书签")
    swipe_edit(first)
    tap("起始页收藏开关")
    tap("保存")
    tap("完成")
    xy=locate(first)
    shell("input","swipe",*xy,*xy,750)
    tap("删除书签")
    tap("删除")
    locate(second+"Saved")
    assert first not in labels()
    print("PASS changing Favorites visibility and deleting a selected test bookmark",flush=True)
    xy=locate(second+"Saved")
    shell("input","swipe",*xy,*xy,750)
    tap("编辑书签")
    locate(base+"/garden")
    shot("favorite-editor")
    replace("书签名称",second+"Home")
    tap("保存")
    locate(second+"Home")
    xy=locate(second+"Home")
    shell("input","swipe",*xy,*xy,750)
    tap("删除书签")
    tap("取消")
    locate(second+"Home")
    tap(second+"Home")
    locate("Garden")
    print("PASS saved edit survives restart and Favorites supports edit, canceled deletion and navigation",flush=True)


def verify_bookmark_menu(shell, locate, adb, shot, port, prefix, movie, tablet=False):
    import subprocess
    title = f"Tide{port}"
    base = f"http://127.0.0.1:{port}"
    def tap(label):
        shell("input","tap",*locate(label))
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",base+"/glass-coast")
    locate("浏览峡谷")
    tap("页面菜单")
    tap("添加书签")
    replace_field(shell, locate, "书签名称", title)
    tap("保存")
    try:
        if tablet:
            tap("显示侧栏")
            shell("uiautomator","dump","/sdcard/terra-bookmark.xml")
            nodes = list(ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-bookmark.xml")).iter("node"))
            if not any(n.get("text") == title for n in nodes):
                if any(n.get("content-desc") == "返回侧栏" for n in nodes):
                    tap("返回侧栏")
                tap("书签")
        else:
            tap("书签")
    except AssertionError:
        shot("after-save-failure")
        raise
    start = locate(title)
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","12","/sdcard/terra-bookmark-menu.mp4"])
        time.sleep(.6)
    shell("input","motionevent","DOWN",*start)
    release = start
    try:
        item = locate("编辑书签")
        shot("bookmark-actions")
        shell("input","motionevent","MOVE",*item)
        release = item
        time.sleep(.25)
    finally:
        shell("input","motionevent","UP",*release)
    locate("书签网址")
    locate(base+"/glass-coast")
    shot("bookmark-editor")
    tap("取消")
    locate(title)
    if recording:
        recording.wait(timeout=15)
        adb("pull","/sdcard/terra-bookmark-menu.mp4",str(movie))
    tap("完成")
    locate("浏览峡谷")
    print("PASS one held press on a saved bookmark opens the editor and cancel returns to the original library",flush=True)

def verify_editor_glass(shell, locate, shot, site):
    """Use the same web-page color transition with and without an opaque intervening library."""
    import threading
    from PIL import ImageStat

    site.sheet_change = threading.Event()
    address = f"http://127.0.0.1:{site.server_port}/live-sheet"
    title = f"Atlas{site.server_port}"
    def visit():
        shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",address)
        locate("Live glass")
    def tap(label):
        shell("input","tap",*locate(label))
    def sample(name):
        _, y = locate("取消")
        frame = shot(name)
        x = int(frame.width*.28)
        return ImageStat.Stat(frame.crop((x-4,y-4,x+4,y+4))).mean

    visit()
    tap("页面菜单")
    tap("添加书签")
    blue = sample("direct-blue")
    site.sheet_change.set()
    time.sleep(1)
    orange = sample("direct-orange")
    print("Uncovered editor samples",blue,orange,flush=True)
    replace_field(shell, locate, "书签名称", title)
    tap("保存")
    site.sheet_change.clear()
    visit()
    tap("书签")
    _, y = locate("完成")
    width,height = shot("medium").size
    shell("input","swipe",width//2,y,width//2,y-int(height*.4),480)
    assert locate("完成")[1] < .18*height
    point = locate(title)
    shell("input","swipe",*point,*point,750)
    tap("编辑书签")
    covered = sample("library-blue")
    site.sheet_change.set()
    time.sleep(1)
    updated = sample("library-orange")
    assert blue[2]-blue[0]>8 and orange[0]-orange[2]>8,(blue,orange,covered,updated)
    assert max(abs(a-b) for a,b in zip(covered,updated))<6 and max(covered)-min(covered)<15,(covered,updated)
    tap("取消")
    tap("完成")
    locate("Live glass")
    print("PASS editor follows the live page when directly above it and samples the intervening full library when stacked",blue,orange,covered,updated,flush=True)
