"""Check both edges of the live glass surface during tablet address editing."""
from pathlib import Path
import subprocess
import tempfile
import time
import re
from xml.etree import ElementTree


def verify(adb, shell, locate, shot, port, prefix, movie):
    import cv2
    import numpy as np

    if shell("getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("This motion test changes the animation scale of a test emulator")
    url = f"http://127.0.0.1:{port}/garden"
    shell("am","force-stop","app.terra.browser")
    shell("am","start","-W","-a","android.intent.action.VIEW","-n","app.terra.browser/.MainActivity","-d",url)
    locate("Garden")
    address = "不安全 · 127.0.0.1"
    x, y = locate(address)
    tree = ElementTree.fromstring(adb("exec-out","cat","/sdcard/terra-motion.xml"))
    field = next(node for node in tree.iter("node") if node.get("content-desc")==address)
    _,top,_,bottom = map(int,re.findall(r"-?\d+",field.get("bounds")))
    sample_y = top+(bottom-top)*.25
    resting = shot("address-rest")
    w, h = resting.size
    assert y < h * .15, "Use a tablet-sized emulator for this test"
    # Compare identical locations as the edges pass over them. The field text,
    # icons, system bars and software keyboard are outside the sampled regions.
    center = w // 2
    columns = np.r_[int(w*.15):int(w*.275), int(w*.725):int(w*.85)]
    remote = "/sdcard/terra-address-motion.mp4"
    record_size = f"800x{2*round(400*h/w)}"

    def escape():
        shell("input","keyevent","KEYCODE_ESCAPE")

    def open_field():
        shell("input","tap",x,y)

    def record(path, seconds, settle):
        recording = subprocess.Popen(prefix+["shell","screenrecord","--size",record_size,
            "--bit-rate","5500000","--time-limit",str(seconds),remote])
        try:
            time.sleep(.7)
            open_field()
            time.sleep(settle)
            escape()
            time.sleep(settle)
        finally:
            recording.wait(timeout=seconds+8)
        adb("pull",remote,str(path))
        locate(address)

    def profile(frame):
        fh,fw = frame.shape[:2]
        row = round(sample_y*fh/h)
        return np.median(frame[row-2:row+3,(columns*fw/w).astype(int),:],axis=0)

    old_scale = shell("settings","get","global","animator_duration_scale").decode().strip()
    try:
        open_field()
        locate("网址或搜索词")
        expanded = shot("address-expanded")
        escape()
        locate(address)
        if movie:
            record(movie,6,1.3)
        shell("settings","put","global","animator_duration_scale","6")
        time.sleep(.6)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/"address-motion.mp4"
            record(path,12,4.4)
            video = cv2.VideoCapture(str(path))
            try:
                begin = profile(np.asarray(resting)[:,:,::-1])
                end = profile(np.asarray(expanded)[:,:,::-1])
                delta = end-begin
                energy = np.sum(delta*delta,axis=1)
                usable = energy > 12**2
                masks = [(columns<center)&usable,(columns>center)&usable]
                assert all(m.sum()>20 for m in masks),"Insufficient optical contrast at field boundaries"
                samples = []
                spreading = []
                while True:
                    ok, frame = video.read()
                    if not ok: break
                    progress = np.sum((profile(frame)-begin)*delta,axis=1)/np.maximum(energy,1)
                    samples.append([float(np.mean(progress[m]>.55)) for m in masks])
                    distance = np.abs(columns-center)/w
                    inner = usable & (distance<.26)
                    outer = usable & (distance>.32)
                    spreading.append(np.mean(progress[inner]>.55)>.8 and np.mean(progress[outer]>.55)<.2)
            finally:
                video.release()
            opened = [i for i,row in enumerate(samples) if min(row)>.85]
            assert opened and max(samples[0])<.18 and max(samples[-1])<.18, (len(samples),max(samples,key=min))
            def intermediate(rows):
                return [s for s in rows if .1<min(s) and max(s)<.9 and abs(s[0]-s[1])<.25]
            entering = intermediate(samples[:opened[0]])
            leaving = intermediate(samples[opened[-1]+1:])
            assert entering and leaving and any(spreading[:opened[0]]) and any(spreading[opened[-1]+1:]), (entering,leaving)
            print("PASS centered field expands and contracts with stable glass contrast",
                  len(entering),"intermediate opening frames,",len(leaving),"intermediate closing frames",flush=True)
        # Immediately reverse a dismissal, then check that input and dismissal
        # still operate on the current document.
        shell("settings","put","global","animator_duration_scale","1")
        open_field()
        locate("网址或搜索词")
        escape()
        shell("input","keycombination","KEYCODE_CTRL_LEFT","KEYCODE_L")
        locate("网址或搜索词")
        shell("input","text",f"motion{port}")
        locate("在此页查找")
        escape()
        locate("Garden")
        print("PASS editing works after reopening and Escape returns to the original page",flush=True)
        shell("settings","put","global","animator_duration_scale","0")
        open_field()
        locate("网址或搜索词")
        shell("input","tap",round(w*.96),round(h*.24))
        locate(address)
        print("PASS outside tap restores the toolbar with system animations disabled",flush=True)
    finally:
        if old_scale=="null": shell("settings","delete","global","animator_duration_scale")
        else: shell("settings","put","global","animator_duration_scale",old_scale)
