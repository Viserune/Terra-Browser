"""Exercise the tablet sidebar over a changing WebView in a separate browser window."""
import subprocess
import time
from PIL import ImageChops, ImageStat


def verify(adb, shell, locate, shot, server, prefix, movie):
    def tap(name):
        shell("input", "tap", *locate(name))

    def hold(name):
        xy = locate(name)
        shell("input", "swipe", *xy, *xy, 680)

    def ctrl(*keys):
        shell("input", "keycombination", "KEYCODE_CTRL_LEFT", *("KEYCODE_"+key for key in keys))

    def sample(frame, x, y):
        return tuple(round(c) for c in ImageStat.Stat(frame.crop((x-4,y-4,x+4,y+4))).mean)

    base = f"http://127.0.0.1:{server.server_port}"
    server.strip_theme = None
    server.orange = False
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",base+"/glass-coast")
    hold("浏览峡谷")
    tap("在新窗口中打开")
    locate("标签页，1 个")
    ctrl("T")
    locate("网址或搜索词")
    shell("input","text",base+"/strip-document")
    shell("input","keyevent","KEYCODE_ENTER")
    locate("标签页，2 个")
    assert server.strip_theme in ("light","dark"), "test page has not loaded"
    tap("显示侧栏")
    locate("资料库")
    right, header = locate("完成")
    blue = shot("sidebar-blue")
    outside = (int(.85*blue.width), int(.80*blue.height))
    points = [(right//2, int(.80*blue.height)), (int(.84*right),locate("阅读列表")[1]), outside]
    before = [sample(blue,*p) for p in points]
    server.orange = True
    deadline = time.monotonic()+9
    while True:
        orange = shot("sidebar-orange")
        after = [sample(orange,*p) for p in points]
        if all(r-b > 5 for r,_,b in after) or time.monotonic() > deadline:
            break
        time.sleep(.25)
    assert all(b-r > 5 for r,_,b in before) and all(r-b > 5 for r,_,b in after), (before,after)
    print("PASS live page color reaches the open sidebar",before,after,flush=True)
    tap("书签")
    tap("返回侧栏")
    tap("历史记录")
    locate("清空历史记录")
    shell("input","keyevent","KEYCODE_BACK")
    tap("最近关闭")
    tap("返回侧栏")
    tap("阅读列表")
    locate("全部")
    tap("完成")
    tap("显示侧栏")
    locate("未读")
    print("PASS four library sections, hardware back and reopening the last section",flush=True)

    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","16","/sdcard/terra-sidebar.mp4"])
    rest = shot("sidebar-reading")
    start, end = int(.30*right), int(.65*right)
    shell("input","motionevent","DOWN",start,header)
    try:
        for x in range(start+4,end,12):
            shell("input","motionevent","MOVE",x,header)
        time.sleep(.3)
        held = shot("sidebar-held")
        shell("input","motionevent","MOVE",start+2,header)
        time.sleep(.25)
    finally:
        shell("input","motionevent","UP",start+2,header)
    locate("全部")
    region = (20,header-20,right,header+150)
    delta = ImageStat.Stat(ImageChops.difference(rest.crop(region),held.crop(region))).mean
    assert max(delta)>2,delta
    shell("input","swipe",start,header,int(1.03*right),header,460)
    locate("资料库")
    shell("input","tap",*outside)
    locate("标签页，2 个")
    print("PASS held swipe changes the drawn header, reversal cancels, full swipe returns and outside tap dismisses",flush=True)
    if movie:
        recording.wait(timeout=20)
        adb("pull","/sdcard/terra-sidebar.mp4",str(movie))

    ctrl("SHIFT_LEFT","BACKSLASH")
    hold("峡谷")
    tap("移至标签组")
    tap("新建标签组")
    locate("标签组名称")
    group = "Sidebar"+str(server.server_port)
    shell("input","text",group)
    tap("保存")
    locate(group)
    tap("完成")
    tap("显示侧栏")
    tap(group)
    locate("浏览海岸")
    tap("显示侧栏")
    tap("无痕浏览")
    locate("无痕浏览")
    tap("显示侧栏")
    tap("普通标签")
    locate("海岸观测")
    hold("标签页，1 个")
    tap("关闭窗口")
    print("PASS switching from named to private to ordinary tabs restores the selected document",flush=True)
