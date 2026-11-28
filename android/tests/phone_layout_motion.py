"""Check that the moving phone address bar keeps sampling the document between screen edges."""

import subprocess
import time
from pathlib import Path
from tempfile import TemporaryDirectory

import cv2
import numpy as np


def glass_positions(movie, density, upward):
    video = cv2.VideoCapture(str(movie))
    positions = []
    try:
        while True:
            ok, frame = video.read()
            if not ok:
                break
            height, width = frame.shape[:2]
            pixels = frame.astype(np.int16)
            # The fixture has a solid background across each row. A moving
            # glass capsule tints a wide band, including the space around its text.
            edge = np.median(pixels[:, 1 : max(3, round(width * 0.016))], axis=1)
            middle = pixels[:, round(width * 0.23) : round(width * 0.77)]
            contrast = np.abs(middle - edge[:, None]).mean(axis=2)
            rows = (contrast > 14).mean(axis=1) > 0.74
            runs = np.flatnonzero(np.diff(np.r_[False, rows, False])).reshape(-1, 2)
            for first, last in runs:
                y = (first + last) / (2 * height)
                if not (
                    16 * density <= last - first <= 60 * density and 0.15 < y < 0.84
                ):
                    continue
                travel = (
                    (positions[-1] - y if upward else y - positions[-1])
                    if positions
                    else 1
                )
                if travel > 0.01:
                    positions.append(y)
    finally:
        video.release()
    return positions


def verify_layout_motion(device, url):
    device.launch(url.rsplit("/", 1)[0] + "/lighthouse?motion")
    device.wait("Lighthouse")
    original = device.geometry()[0]
    scale_key = "animator_duration_scale"
    old_scale = device.shell("settings", "get", "global", scale_key).decode().strip()
    try:
        device.choose("top")
        device.shell("settings", "put", "global", scale_key, "3")
        with TemporaryDirectory() as temporary:
            folder = device.folder or Path(temporary)
            for target, label in (("bottom", "底部"), ("top", "顶部")):
                device.button("页面菜单")
                device.button("标签栏布局")
                point = device.wait(label, "完成")[label]
                remote = f"/sdcard/terra-layout-{time.time_ns()}.mp4"
                movie = folder / f"address-to-{target}.mp4"
                try:
                    recorder = subprocess.Popen(
                        device.adb
                        + [
                            "shell",
                            "screenrecord",
                            "--bit-rate",
                            "9000000",
                            "--time-limit",
                            "5",
                            remote,
                        ]
                    )
                    try:
                        time.sleep(0.8)
                        device.tap(point)
                    finally:
                        recorder.wait(timeout=15)
                    assert recorder.returncode == 0, "Android screen recording failed"
                    device.run("pull", remote, movie)
                finally:
                    device.shell("rm", "-f", remote)
                device.geometry(target)
                points = glass_positions(
                    movie, device.density, upward=(target == "top")
                )
                assert len(points) >= 4 and abs(points[-1] - points[0]) > 0.38, (
                    target,
                    points,
                )
                print(
                    "PASS illuminated glass moving to",
                    target,
                    "at",
                    len(points),
                    "intermediate positions",
                    flush=True,
                )
    finally:
        if old_scale == "null":
            device.shell("settings", "delete", "global", scale_key)
        else:
            device.shell("settings", "put", "global", scale_key, old_scale)
        if device.geometry()[0] != original:
            device.choose(original)
