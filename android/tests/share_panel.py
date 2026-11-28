"""Check the actual share window, system chooser and origin after dismissal on a test emulator."""
import subprocess
import time
from pathlib import Path
import numpy as np


def verify(adb, shell, locate, shot, port, prefix, video, tablet):
    url = f"http://127.0.0.1:{port}/panel-paper"
    old_mode = shell("cmd","uimode","night").decode().strip().split()[-1]
    assert old_mode in ("yes","no","auto")
    recording = None
    pid = None
    remote = f"/sdcard/terra-share-{port}.mp4"
    def tap(label):
        shell("input","tap",*locate(label))
    def back():
        shell("input","keyevent","4")
    def finish_recording():
        nonlocal recording
        if recording is None:return
        if pid and pid.isdigit() and recording.poll() is None:shell("kill","-2",pid)
        recording.wait(timeout=25)
        Path(video).parent.mkdir(parents=True,exist_ok=True)
        adb("pull",remote,str(video))
        shell("rm","-f",remote)
        recording=None
    def chooser():
        until = time.monotonic()+10
        while time.monotonic()<until:
            info=shell("dumpsys","activity","activities").decode(errors="replace")
            resumed=[s for s in info.splitlines() if "ResumedActivity" in s or "topResumedActivity" in s]
            if any("ChooserActivity" in s or "IntentResolver" in s for s in resumed):return
            time.sleep(.15)
        raise AssertionError(("System chooser did not open",resumed))
    try:
        shell("cmd","uimode","night","no")
        shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW","-d",url)
        locate("Panel sample")
        share=locate("共享")
        rest=np.asarray(shot("share-rest")).astype(int)
        h,w=rest.shape[:2]
        if video:
            command=f"echo $$; exec screenrecord --bit-rate 10000000 --time-limit 150 {remote}"
            recording=subprocess.Popen(prefix+["shell",command],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            pid=recording.stdout.readline().strip().decode()
            time.sleep(.5)
        shell("input","tap",*share)
        options=locate("选项 ›")
        more=locate("查看更多")
        copy=locate("拷贝")
        compact=np.asarray(shot("share-compact")).astype(int)
        if tablet:
            assert options[0]>.5*w and options[1]<.25*h and copy[1]<.56*h,(share,options,copy)
            patch=np.s_[int(h*.33):int(h*.54),15:int(w*.36),:]
            assert np.mean(np.abs(compact[patch]-rest[patch]))<2,"Web page dimmed outside the popover"
        else:
            assert copy[1]>.7*h,(h,copy)
        if tablet:
            shell("input","tap",*more)
        else:
            handle=locate("共享面板把手")
            shell("input","swipe",*handle,handle[0],max(35,handle[1]-130),340)
        locate("打印或存为 PDF")
        shot("share-more")
        expanded_options=locate("选项 ›")
        if tablet:
            assert abs(expanded_options[1]-options[1])<8,(options,expanded_options)
        else:
            assert expanded_options[1]<options[1]-40,(options,expanded_options)
        tap("查看更少")
        tap("选项 ›")
        tap("标题和链接")
        locate("网页归档")
        shot("share-options")
        back()
        locate("拷贝")
        tap("系统推荐")
        chooser()
        shot("share-android-chooser")
        back()
        tap("共享")
        tap("选项 ›")
        tap("网页归档")
        tap("关闭共享选项")
        tap("系统推荐")
        chooser()
        back()
        tap("共享")
        tap("添加到书签")
        locate("添加书签")
        tap("取消")
        tap("共享")
        tap("拷贝")
        locate("共享")
        tap("共享")
        locate("拷贝")
        if tablet:
            shell("input","tap",int(w*.12),int(h*.71))
        else:
            handle=locate("共享面板把手")
            shell("input","swipe",*handle,handle[0],min(h-50,handle[1]+145),320)
        locate("共享")
        end=np.asarray(shot("share-return")).astype(int)
        region=np.s_[int(.25*h):int(.65*h),int(.08*w):int(.91*w),:]
        assert np.abs(rest[region]-end[region]).mean()<2,"Source document changed when sharing was dismissed"
        print("PASS share open, expansion, collapse, format options and dismissal",flush=True)
        print("PASS text and web archive reach the system chooser, bookmark draft and copy return to the source",flush=True)
        finish_recording()
        if tablet:
            shell("am","start","-W","-n","app.terra.browser/.MainActivity","-a","android.intent.action.VIEW",
                  "-d",f"http://127.0.0.1:{port}/group-link")
            xy=locate("Continue")
            shell("input","swipe",*xy,*xy,750)
            locate("在标签页组中打开")
            tap("共享")
            tap("拷贝")
            locate("Continue")
            xy=locate("不安全 · 127.0.0.1")
            shell("input","swipe",*xy,*xy,750)
            tap("粘贴并前往")
            locate("Garden")
            print("PASS sharing from a held link copies its destination and opens it on paste",flush=True)
    finally:
        shell("cmd","uimode","night",old_mode)
        finish_recording()
