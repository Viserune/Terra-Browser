"""Exercise the public link menu on a dedicated phone emulator."""
import re
import time


def verify_windows(shell, locate, shot, port, edit_conflict=False):
    base = f"http://127.0.0.1:{port}"
    def tap(label, prefix=False):
        shell("input", "tap", *locate(label, prefix))
    def task(component):
        state = shell("dumpsys", "activity", "activities").decode()
        row = next(line for line in state.splitlines() if "topResumedActivity=" in line)
        assert component in row, row
        return int(re.search(r" t(\d+)", row)[1])
    def focus(task_id):
        shell("am", "task", "focus", task_id)
    def open_window(label):
        xy = locate(label)
        shell("input", "swipe", *xy, *xy, 800)
        tap("在新窗口中打开")

    shell("am", "start", "-W", "-a", "android.intent.action.VIEW", "-n",
          "app.terra.browser/.MainActivity", "-d", base + "/window-source")
    locate("Source window")
    primary = task("/.MainActivity")
    image = shot("source-top")
    x, height = image.width // 2, image.height
    shell("input", "swipe", x, height*2//3, x, height//3, 540)
    time.sleep(.5)
    anchor = locate("Window destination")
    open_window("Window destination")
    locate(f"Destination {port}")
    child = task("/.BrowserWindowActivity")
    assert child != primary
    locate("标签页，1 个")
    tap("页面菜单")
    tap("添加书签")
    tap("保存")
    focus(primary)
    restored = locate("Window destination")
    assert abs(restored[1] - anchor[1]) <= 3, (restored, anchor)
    assert task("/.MainActivity") == primary
    # Folded toolbars reappear after a reverse scroll.
    shell("input", "swipe", x, height//3, x, height*2//3, 500)
    tap("书签")
    locate("完成")
    locate(f"Destination {port}")
    if edit_conflict:
        _, row_y = locate(f"Destination {port}")
        action_x,_ = locate("完成")
        shell("input","swipe",action_x-8,row_y,int(.42*action_x),row_y,490)
        tap("编辑")
        locate("书签名称")
        focus(child)
        tap("页面菜单")
        tap("编辑书签")
        from bookmark_editor import replace_field
        replace_field(shell, locate, "书签名称", f"Updated{port}")
        tap("保存")
        focus(primary)
        tap("保存")
        locate("这条书签已在其他窗口更改，请重新打开")
        tap("取消")
        locate(f"Updated{port}")
        print("PASS a stale editor cannot overwrite a newer change from the other window",flush=True)
    shell("input", "keyevent", "BACK")
    focus(child)
    locate(f"Destination {port}")
    assert task("/.BrowserWindowActivity") == child
    print("PASS independent Android tasks, original scroll position and live shared bookmark", flush=True)
    shell("input", "keyevent", "HOME")
    time.sleep(1.3)
    shell("am", "kill", "app.terra.browser")
    time.sleep(.7)
    assert not shell("pidof app.terra.browser || true").strip(), "Process survived idle kill"
    focus(child)
    locate(f"Destination {port}")
    locate("标签页，1 个")
    assert task("/.BrowserWindowActivity") == child
    focus(primary)
    locate("Source window")
    assert task("/.MainActivity") == primary
    print("PASS both task sessions restore after process death without duplicating destination", flush=True)

def verify_private_window(shell, locate, port):
    def tap(s, prefix=False):
        shell("input", "tap", *locate(s, prefix))
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/sky")
    locate("Sky")
    tabs = locate("标签页，", True)
    shell("input","swipe",*tabs,*tabs,750)
    tap("新建无痕标签页")
    tap("搜索或输入网址")
    locate("网址或搜索词")
    shell("input","text",f"http://127.0.0.1:{port}/private-source")
    shell("input","keyevent","ENTER")
    xy = locate("Private link")
    shell("input","swipe",*xy,*xy,750)
    tap("在新窗口中打开")
    locate("Private destination")
    state = shell("dumpsys","activity","activities").decode()
    row = next(line for line in state.splitlines() if "topResumedActivity=" in line)
    assert "/.BrowserWindowActivity" in row, row
    child = int(re.search(r" t(\d+)", row)[1])
    tap("标签页，",True)
    locate("无痕浏览")
    tap("完成")
    recents = shell("dumpsys","activity","recents")
    assert b"/private-destination" not in recents and b"/private-source" not in recents
    shell("input","keyevent","HOME")
    time.sleep(1.4)
    shell("am","kill","app.terra.browser")
    time.sleep(.7)
    assert not shell("pidof app.terra.browser || true").strip()
    shell("am","task","focus",child)
    locate("无痕起始页")
    print("PASS private link opened in a private task, addresses stayed out of Recents and vanished on process death",flush=True)

def verify_library_glass(shell, locate, adb, shot, port, prefix, movie):
    import subprocess
    from PIL import ImageStat
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
          "-d",f"http://127.0.0.1:{port}/glass-coast")
    locate("浏览峡谷")
    bookmarks = locate("书签")
    # Measure the actual positions once before recording so UIAutomator doesn't pause the animation.
    shell("input","tap",*bookmarks)
    done = locate("完成")
    shell("input","tap",*done)
    locate("浏览峡谷")
    recording = None
    if movie:
        recording = subprocess.Popen(prefix+["shell","screenrecord","--time-limit","18","/sdcard/terra-library-glass.mp4"])
        time.sleep(.7)
    samples = []
    for page in ("coast","canyon"):
        shell("input","tap",*bookmarks)
        time.sleep(.7)
        frame = shot("bookmarks-"+page)
        x, y = done
        sample_x = x*2//3
        samples.append(ImageStat.Stat(frame.crop((sample_x-4,y-4,sample_x+4,y+4))).mean)
        time.sleep(.5)
        shell("input","tap",*done)
        time.sleep(.45)
        if page == "coast":
            # The sheet transition can move the web content on a tall phone.
            shell("input","tap",*locate("浏览峡谷"))
            locate("浏览海岸")
    locate("浏览海岸")
    if recording:
        recording.wait(timeout=23)
        adb("pull","/sdcard/terra-library-glass.mp4",str(movie))
    blue, orange = samples
    assert blue[2]-blue[0] > 5 and orange[0]-orange[2] > 5, samples
    print("PASS same bookmark glass over blue and orange documents", samples, flush=True)
