"""Run from tab_overview.py --search-tabs. Destructive actions target this run's fixture group only."""
import re
import subprocess
import time


def verify_search(adb,shell,tap,locate,labels,hold,first,outsider,base,port,screenshot,record_path,prefix):
    group=f"Search{port}"
    third=f"Overview C {port}"
    fourth=f"Overview D {port}"

    def search(term):
        tap("搜索标签页")
        locate("android.widget.EditText")
        shell("input","text",term.replace(" ","%s"))

    def submit():
        shell("input","keyevent","ENTER")
        time.sleep(.3)

    tap("标签页，",True)
    hold(locate(first))
    tap("移至标签组")
    tap("新建标签组")
    locate("android.widget.EditText")
    shell("input","text",group)
    tap("保存")
    tap("完成")
    locate(first)
    for path,title in (("C",third),("D",fourth)):
        shell("am","start","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",base+path)
        locate(title)
    tap("标签页，3 个")
    locate(group)
    x,y=locate(first)
    density=int(shell("wm","density").decode().split()[-1])/160
    # Begin in a preview, away from navigation buttons and the system's screen edge.
    y+=round(48*density)
    shell("input","swipe",x,y,x,y+round(30*density),300)
    locate(group)
    assert "取消" not in labels(), "Short drag unexpectedly opened search"
    recording = None
    if record_path:
        width,height = map(int,re.findall(r"(\d+)x(\d+)",shell("wm","size").decode())[-1])
        scale = min(1, 1520 / max(width,height), 720 / min(width,height))
        resolution = f"{int(width*scale)//2*2}x{int(height*scale)//2*2}"
        recording = subprocess.Popen(prefix+["shell","screenrecord","--size",resolution,"--time-limit","12","/sdcard/terra-tab-search.mp4"])
        time.sleep(.6)
    try:
        shell("input","swipe",x,y,x,y+round(116*density),690)
        locate("android.widget.EditText")
        shell("input","text",f"overview%sc%s{port}")
        locate(third)
    finally:
        if recording:
            if recording.wait(timeout=16) != 0:
                raise AssertionError("Device screen recording failed")
            adb("pull","/sdcard/terra-tab-search.mp4",str(record_path))
    assert first not in labels() and fourth not in labels()
    tap("清除搜索")
    shell("input","text",f":{port}/A")
    locate(first)
    assert third not in labels()
    submit()
    if screenshot:
        screenshot.write_bytes(adb("exec-out","screencap","-p"))
    tap(first)
    locate("页面菜单")
    locate(first)
    print("PASS short pull cancels, long pull reveals search, mixed-case title and URL filtering open the existing page",flush=True)

    tap("标签页，3 个")
    search(outsider)
    locate("没有匹配的标签页")
    submit()
    tap("无痕浏览")
    locate("无痕标签页")
    assert group not in labels() and outsider not in labels()
    tap("普通标签")
    locate(group)
    hold(locate(first))
    tap("固定标签页")
    locate("已固定，"+first)
    search(f":{port}/")
    submit()
    hold(locate("完成"))
    tap("关闭 2 个匹配标签页")
    tap("取消")
    locate(third)
    locate(fourth)
    hold(locate("完成"))
    tap("关闭 2 个匹配标签页")
    tap("关闭")
    locate("已固定，"+first)
    assert third not in labels() and fourth not in labels()
    hold(locate("新建标签页"))
    tap(third)
    locate("标签页，2 个")
    locate(third)
    print("PASS current-group and private isolation, confirmed close excludes pinned tabs, and holding plus reopens a page in its group",flush=True)

    tap("标签页，2 个")
    search("nothing-matches-this")
    locate("没有匹配的标签页")
    tap("取消")
    locate(group)
    tap("标签组")
    tap("移除标签组")
    tap("删除组并关闭标签页")
    tap("完成")
    locate(outsider)
    print("PASS cancel restores the unfiltered group and the outside tab stays open",flush=True)
