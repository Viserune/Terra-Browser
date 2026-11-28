"""Build the isolated file receiver with the Android SDK and the local Android debug key."""
import os
from pathlib import Path
import subprocess
import zipfile


def build(directory):
    sdk = Path(os.environ["ANDROID_HOME"])
    tools = sdk / "build-tools" / "36.0.0"
    platform = sdk / "platforms" / "android-37.0" / "android.jar"
    source = Path(__file__).resolve().parent
    classes, dex = directory / "classes", directory / "dex"
    classes.mkdir()
    dex.mkdir()
    win = os.name == "nt"
    binary = ".exe" if win else ""
    batch = ".bat" if win else ""
    subprocess.run([tools / ("aapt2" + binary), "link", "-o", directory / "base.apk",
                    "--manifest", source / "AndroidManifest.xml", "-I", platform,
                    "--min-sdk-version", "29", "--target-sdk-version", "35"], check=True)
    subprocess.run(["javac", "--release", "8", "-encoding", "UTF-8",
                    "-classpath", platform, "-d", classes, source / "ReceiveActivity.java"], check=True)
    subprocess.run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--lib", platform,
                    "--min-api", "29", "--output", dex, *classes.rglob("*.class")], check=True)
    with zipfile.ZipFile(directory / "base.apk", "a") as apk:
        apk.write(dex / "classes.dex", "classes.dex")
    subprocess.run([tools / ("zipalign" + binary), "-f", "4", directory / "base.apk", directory / "aligned.apk"], check=True)
    apk = directory / "receiver.apk"
    subprocess.run([tools / ("apksigner" + batch), "sign", "--ks", Path.home() / ".android/debug.keystore",
                    "--ks-pass", "pass:android", "--key-pass", "pass:android", "--ks-key-alias", "androiddebugkey",
                    "--out", apk, directory / "aligned.apk"], check=True)
    return apk
