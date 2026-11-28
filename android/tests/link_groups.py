"""Exercise the link menu through safari_motion.py --link-groups."""
import re
import subprocess
import time
from xml.etree import ElementTree
from PIL import Image, ImageChops


def preview_bounds(image):
    diff = ImageChops.difference(image, Image.new("RGB", image.size, (64,128,88)))
    r,g,b = [channel.point(lambda x: 255 if x <= 1 else 0) for channel in diff.split()]
    return ImageChops.multiply(ImageChops.multiply(r,g),b).getbbox()


def verify_link_groups(shell, locate, adb, shot, port, prefix, movie):
    base = f"http://127.0.0.1:{port}"
    source = f"Menu {port}"
    name = f"Links{port}"
    branch = "在标签页组中打开"
    def tap(label, prefix=False):
        shell("input", "tap", *locate(label, prefix))
    def labels():
        shell("uiautomator", "dump", "/sdcard/terra-links.xml")
        tree = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-links.xml"))
        return {value for node in tree.iter("node") for value in (node.get("text",""), node.get("content-desc",""))}
    def count():
        return next(s for s in labels() if re.fullmatch(r"标签页，\d+ 个", s))
    def open_menu():
        xy = locate("Continue")
        shell("input", "swipe", *xy, *xy, 750)
        locate(branch)
    def overview():
        tap("标签页，", True)
    def painted_preview(label):
        end=time.monotonic()+8
        while time.monotonic()<end:
            bounds=preview_bounds(shot(label))
            if bounds: return bounds
            time.sleep(.15)
        raise AssertionError("The Garden preview did not paint in "+label)

    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n","app.terra.browser/.MainActivity")
    overview()
    tap("标签组")
    tap("普通标签")
    tap("完成")
    shell("am","start","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",base+"/group-link")
    locate(source)
    before = count()
    open_menu()
    tap(branch)
    tap("新建标签组")
    locate("标签组名称")
    shell("input","text",name+"Cancel")
    tap("关闭标签组面板")
    locate("返回链接菜单")
    assert name+"Cancel" not in labels()
    shell("input","keyevent","BACK")
    locate(branch)
    shell("input","keyevent","BACK")
    locate("Continue")
    assert count() == before
    print("PASS canceling name entry and backing out keeps the source and tab count", flush=True)

    open_menu()
    tap(branch)
    tap("新建标签组")
    locate("标签组名称")
    shell("input","text",name)
    tap("保存")
    locate("Garden")
    assert count() == "标签页，1 个"
    overview()
    locate(name)
    assert source not in labels()
    tap("标签组")
    tap("普通标签")
    tap(source)
    locate("Continue")
    assert count() == before
    print("PASS new group contains the link and the original tab stays in its group", flush=True)

    start = locate("Continue")
    recording = None
    if movie:
        recording = subprocess.Popen(prefix + ["shell","screenrecord","--time-limit","12","/sdcard/terra-link-group.mp4"])
        time.sleep(.5)
    shell("input","motionevent","DOWN",*start)
    release = start
    try:
        item = locate(branch)
        original = painted_preview("link-root-menu")
        shell("input","motionevent","MOVE",*item)
        release = locate(name)
        opened = painted_preview("link-group-menu")
        assert original and opened and max(abs(a-b) for a,b in zip(original,opened)) <= 2, (original,opened)
        shell("input","motionevent","MOVE",*release)
        time.sleep(.2)
    finally:
        shell("input","motionevent","UP",*release)
    locate("Garden")
    assert count() == "标签页，2 个"
    if recording:
        recording.wait(timeout=15)
        adb("pull","/sdcard/terra-link-group.mp4",str(movie))
    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-n","app.terra.browser/.MainActivity")
    locate("Garden")
    overview()
    locate(name)
    tap("无痕浏览")
    tap("新建无痕标签页")
    tap("搜索或输入网址")
    locate("网址或搜索词")
    shell("input","text", base+"/tab-menu")
    locate(base+"/tab-menu")
    shell("input","keyevent","ENTER")
    # The inline address keyboard is retracting after Enter. Use the final link position.
    xy = locate("Continue")
    for _ in range(5):
        settled = locate("Continue")
        if settled == xy: break
        xy = settled
    shell("input","swipe",*xy,*xy,750)
    locate("打开")
    assert branch not in labels() and name not in labels()
    shell("input","keyevent","BACK")
    overview()
    tap("普通标签")
    locate(name)
    tap("标签组")
    tap("移除标签组")
    tap("删除组并关闭标签页")
    tap("完成")
    locate(source)
    print("PASS single held press opens an existing group, selection survives restart, private menu excludes normal groups", flush=True)
