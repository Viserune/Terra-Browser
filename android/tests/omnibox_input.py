"""Regression for hardware-key input racing recomposition and moving the cursor.

Run on a test emulator with Terra installed:
    python android/tests/omnibox_input.py emulator-5554
The test opens one tab and edits its address without submitting it.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as XML

PACKAGE = "app.terra.browser"
ADDRESS = "http://127.0.0.1:18175/private-only"


def main():
    args = argparse.ArgumentParser(description=__doc__)
    args.add_argument("serial")
    serial = args.parse_args().serial
    sdk = os.environ.get("ANDROID_HOME", "")
    adb = shutil.which("adb") or str(Path(sdk, "platform-tools", "adb.exe" if os.name == "nt" else "adb"))

    def shell(*command):
        return subprocess.check_output([adb, "-s", serial, "shell", *command], timeout=15)

    def wait_for(predicate):
        end = time.monotonic() + 45
        while time.monotonic() < end:
            result = shell("uiautomator", "dump", "/sdcard/terra-omnibox.xml")
            if b"dumped to" in result:
                root = XML.fromstring(shell("cat", "/sdcard/terra-omnibox.xml"))
                for node in root.iter("node"):
                    if predicate(node):
                        return node
            time.sleep(0.2)
        raise AssertionError("Expected address-bar state was not reached")

    shell("am", "start", "-a", "android.intent.action.VIEW", "-d", "https://example.org/",
          "-n", PACKAGE + "/.MainActivity")
    label = wait_for(lambda n: n.get("text") == "example.org")
    left, top, right, bottom = map(int, re.findall(r"\d+", label.get("bounds")))
    shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))
    wait_for(lambda n: n.get("class") == "android.widget.EditText" and n.get("focused") == "true")
    # The existing address must be selected. Send one burst before Compose can settle between keys.
    shell("input", "text", ADDRESS)
    wait_for(lambda n: n.get("class") == "android.widget.EditText" and n.get("text") == ADDRESS)
    shell("input", "keyevent", "KEYCODE_BACK")
    print("PASS: rapid typing replaced the entire address in order")


if __name__ == "__main__":
    main()

