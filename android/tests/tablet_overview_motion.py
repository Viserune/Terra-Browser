"""Verify tablet overview geometry and both directions of the card transition on a portrait emulator."""
from pathlib import Path
import subprocess
import tempfile
import time

import cv2
import numpy as np

PAGES = [
    ("Night", (44,49,78)), ("Pine", (65,110,74)), ("Slate", (75,91,116)),
    ("Paper", (196,162,168)), ("Coast", (64,130,147)), ("Canyon", (204,127,75)),
]


def fixture(handler):
    try:
        title, rgb = PAGES[int(handler.path.rsplit("/",1)[1])]
    except (ValueError, IndexError):
        handler.send_error(404)
        return
    html=f"""<meta name=viewport content='width=device-width'><title>Terra {title}</title>
<body style='background:rgb{rgb};color:white'><h1>Terra {title}</h1>""".encode()
    handler.send_response(200)
    handler.send_header("Content-Type","text/html;charset=utf-8")
    handler.send_header("Content-Length",str(len(html)))
    handler.end_headers()
    handler.wfile.write(html)


def color_box(rgb, color, tolerance=13):
    target = np.array(color,dtype=int)
    mask = cv2.inRange(rgb,np.maximum(0,target-tolerance).astype("uint8"),
                       np.minimum(255,target+tolerance).astype("uint8"))
    mask[:round(rgb.shape[0]*.076)] = 0
    count,_,stats,_ = cv2.connectedComponentsWithStats(mask,8)
    return tuple(map(int,stats[1+np.argmax(stats[1:,4])][:4])) if count>1 else (0,0,0,0)


def verify(adb, shell, locate, shot, port, prefix, movie=None, inspect_frames=False):
    def tap(label):
        shell("input","tap",*locate(label))
    def ctrl(key):
        shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_"+key)
    def wait_page(index):
        expected=np.asarray(PAGES[index][1])
        end=time.monotonic()+10
        while time.monotonic()<end:
            frame=np.asarray(shot("document"))
            if np.max(abs(frame[frame.shape[0]*3//4,frame.shape[1]//2].astype(int)-expected))<6:
                return
            time.sleep(.2)
        raise AssertionError("Did not display "+PAGES[index][0])
    def visit(index):
        locate("网址或搜索词")
        shell("input","text",f"http://127.0.0.1:{port}/overview-tile/{index}")
        shell("input","keyevent","KEYCODE_ENTER")
        wait_page(index)
    shmode=shell("cmd","uimode","night").decode().strip().split()[-1]
    assert shmode in ("yes","no","auto")
    recording=None
    pid=None
    on_disk=f"/sdcard/terra-overview-{port}.mp4"
    original_task=None
    scale=shell("settings","get","global","animator_duration_scale").decode().strip()
    if inspect_frames:
        assert shell("getprop","ro.kernel.qemu").strip()==b"1","Use a test emulator for frame inspection"
    with tempfile.TemporaryDirectory() as tmp:
        video=Path(movie) if movie else Path(tmp)/"overview.mp4"
        try:
            shell("cmd","uimode","night","no")
            shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity",
                  "-d",f"http://127.0.0.1:{port}/glass-coast")
            xy=locate("浏览峡谷")
            import re
            state=shell("dumpsys","activity","activities").decode(errors="replace")
            primary=re.search(r"topResumedActivity=.*?\.MainActivity t(\d+)",state)
            if primary: original_task=primary[1]
            shell("input","swipe",*xy,*xy,680)
            tap("在新窗口中打开")
            locate("标签页，1 个")
            for index in range(6):
                ctrl("L" if index==0 else "T")
                visit(index)
            badge="标签页，6 个"
            trigger=locate(badge)
            time.sleep(.4)
            shot("overview-source")
            print("Six controlled tabs ready",flush=True)
            if inspect_frames:
                shell("settings","put","global","animator_duration_scale","4")
                time.sleep(.45)
            command=f"echo $$; exec screenrecord --bit-rate 12000000 --time-limit 150 {on_disk}"
            recording=subprocess.Popen(prefix+["shell",command],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            pid=recording.stdout.readline().strip().decode()
            assert pid.isdigit(),pid
            time.sleep(.6)
            shell("input","tap",*trigger)
            locate("完成")
            end=time.monotonic()+6
            while True:
                grid=np.asarray(shot("overview-grid"))
                h,w=grid.shape[:2]
                boxes=[color_box(grid,color) for _,color in PAGES]
                if all(.28*w<bw<.33*w for _,_,bw,_ in boxes): break
                assert time.monotonic()<end,("The six previews did not reach the grid",boxes)
                time.sleep(.15)
            assert h>w and w/h>.60,(w,h)
            print("Painted card bounds",boxes,flush=True)
            for i,(x,y,width,height) in enumerate(boxes):
                assert .28*w < width < .33*w and 1.20 < height/width < 1.38,(i,boxes)
                assert abs(x/w-(.02+(i%3)*.326))<.028,(i,boxes)
                assert abs(y/h-(.078 if i<3 else .385))<.045,(i,boxes)
            assert locate("完成")[1]<.095*h and locate("新建标签页")[1]<.095*h
            print("PASS three columns, portrait previews, tablet toolbar above the grid",flush=True)
            x,y,bw,bh=boxes[5]
            shell("input","tap",x+bw//2,y+bh//2)
            locate(badge)
            wait_page(5)
            shot("overview-returned")
            tap(badge)
            tap("新建标签页")
            locate("网址或搜索词")
            shot("overview-start-field")
            locate("隐私")
            visit(4)
            tap("标签页，7 个")
            locate("完成")
            third=np.asarray(shot("overview-added"))
            # Two coast cards exist now. The new card starts the last row.
            bottom=third[int(.62*h):]
            est=color_box(bottom,PAGES[4][1])
            assert est[2]>.25*w,est
            tap("搜索标签页")
            locate("搜索当前组的标签页")
            shell("input","text","Night")
            locate("Terra Night")
            shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_SHIFT_LEFT","KEYCODE_BACKSLASH")
            locate("标签页，7 个")
            wait_page(4)
            # Close Paper from the fully visible second row. Canyon must move one column left.
            tap("标签页，7 个")
            locate("完成")
            rect=color_box(np.asarray(shot("before-close")),PAGES[3][1])
            shell("input","tap",rect[0]+rect[2]-16,rect[1]+15)
            end=time.monotonic()+5
            while True:
                after=np.asarray(shot("overview-closed"))
                next_box=color_box(after,PAGES[5][1])
                if color_box(after,PAGES[3][1])[2]<12 and abs(next_box[0]-boxes[1][0])<8: break
                assert time.monotonic()<end,("Card did not settle in the vacated column",next_box,boxes)
                time.sleep(.15)
            tap("完成")
            locate(badge)
            xy=locate(badge)
            shell("input","swipe",*xy,*xy,690)
            tap("关闭窗口")
            print("PASS new card opens a focused start page, closing a card fills its grid position",flush=True)
            print("PASS leaving filtered overview returns to the current page outside the results",flush=True)
        finally:
            if inspect_frames:
                shell("settings","put","global","animator_duration_scale","1" if scale=="null" else scale)
                if scale=="null":
                    time.sleep(.45)
                    shell("settings","delete","global","animator_duration_scale")
            if recording is not None:
                if pid and pid.isdigit() and recording.poll() is None: shell("kill","-2",pid)
                recording.wait(timeout=20)
                video.parent.mkdir(parents=True,exist_ok=True)
                adb("pull",on_disk,str(video))
                shell("rm","-f",on_disk)
            if original_task: shell("am","task","focus",original_task)
            shell("cmd","uimode","night",shmode)
        cap=cv2.VideoCapture(str(video))
        travel=[]
        try:
            while True:
                ok,bgr=cap.read()
                if not ok: break
                rgb=cv2.cvtColor(cv2.resize(bgr,(328,472)),cv2.COLOR_BGR2RGB)
                x,y,width,height=color_box(rgb,PAGES[5][1],24)
                if width>0: travel.append((x,y,width,height))
        finally:
            cap.release()
        small=next(i for i,b in enumerate(travel) if 85 < b[2] < 110 and b[0]>195)
        full=next(i for i in range(small+1,len(travel)) if travel[i][2]>320)
        widths_per_leg=[]
        for leg in (travel[:small],travel[small:full]):
            widths={b[2] for b in leg if 115<b[2]<310 and b[0]>8}
            widths_per_leg.append(sorted(widths))
            # Screenrecord only emits changes. Inspect mode stretches the animation clock to expose its path.
            assert len(widths)>=(2 if inspect_frames else 1),("Missing moving card between the document and its grid cell",sorted(widths))
        print("PASS intermediate card widths during entry and exit:",widths_per_leg,flush=True)
