"""Record Safari-style format selection and verify files received by a separate Android app."""
from contextlib import contextmanager
from io import BytesIO
import json
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import time
from xml.etree import ElementTree

import numpy as np
from PIL import Image, ImageDraw


COLORS = ["#1d947a", "#d28b34", "#224fba"]
NAV = "#713c9c"
FOOTER = "#813b50"
RECEIVER = "app.terra.test.sharereceiver"
READER = "阅读器视图版 PDF"


def fixture(request):
    if request.path.endswith(".png"):
        index = int(Path(request.path).stem.rsplit("-", 1)[1])
        img = Image.new("RGB", (400, 130), COLORS[index])
        ImageDraw.Draw(img).line((12, 112, 124, 48, 207, 77, 288, 31, 388, 60), fill="white", width=3)
        output = BytesIO()
        img.save(output, format="PNG")
        content, mime = output.getvalue(), "image/png"
    elif request.path == "/share-article-short":
        content = """<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
        <title>Terra field notes</title><h1>Short field note</h1><p>一页河岸观测。</p>""".encode("utf-8")
        mime = "text/html; charset=utf-8"
    else:
        paragraphs = "".join(f"<p>Field entry {n}. Along the riverbank we measured the water level, mapped the changing "
                             "channel and recorded the direction of the current. 河岸观测，水位和流向均已记录。</p>"
                             for n in range(1, 8))
        sections = "".join(f"<h2>{title}</h2><figure><img src='/share-article-{n}.png' width='400' height='130'>"
                           f"<figcaption>Plate {n+1} — 河岸观测</figcaption></figure>{paragraphs}"
                           for n, title in enumerate(["First plate", "Middle plate", "Last plate"]))
        content = f"""<!doctype html><html lang="zh"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Terra field notes</title><style>*{{box-sizing:border-box}}body{{margin:0;background:#fbf8ef;color:#19342f;font:18px/1.55 sans-serif}}
        article{{margin:auto;max-width:680px;padding:20px}}nav,footer{{padding:25px;color:white}}nav{{background:{NAV}}}footer{{background:{FOOTER}}}
        figure{{margin:0}}img{{max-width:100%;height:auto}}h1,h2{{font-family:serif}}</style>
        <nav>Site navigation</nav><article><h1>Terra field notes</h1><p>Notes from three field stations on the same river.</p>
        {sections}<p>End of field notes.</p></article><footer>Survey footer</footer></html>""".encode("utf-8")
        mime = "text/html; charset=utf-8"
    if mime.startswith("text/html"):
        title = f"<title>Terra field notes {request.server.server_port}</title>"
        content = content.replace(b"<title>Terra field notes</title>", title.encode())
    request.send_response(200)
    request.send_header("Content-Type", mime)
    request.send_header("Content-Length", str(len(content)))
    request.end_headers()
    request.wfile.write(content)


@contextmanager
def recording(adb, shell, prefix, target, port):
    if not target:
        yield
        return
    remote = f"/sdcard/terra-share-format-{port}.mp4"
    command = f"echo $$; exec screenrecord --bit-rate 10000000 --time-limit 100 {remote}"
    rec = subprocess.Popen(prefix + ["shell", command], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    pid = rec.stdout.readline().strip().decode()
    try:
        assert pid.isdigit(), pid
        time.sleep(.45)
        yield
    finally:
        if rec.poll() is None and pid.isdigit():
            shell("kill", "-2", pid)
        rec.wait(timeout=25)
        Path(target).parent.mkdir(parents=True, exist_ok=True)
        adb("pull", remote, target)
        shell("rm", "-f", remote)


def verify_pdf(path, reader):
    import pymupdf
    with pymupdf.open(path) as document:
        assert len(document) >= 2, (path, len(document))
        pages, words = [], []
        for page in document:
            pix = page.get_pixmap(alpha=False)
            rgb = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, 3).astype(np.int16)
            assert np.count_nonzero((rgb.min(axis=2) < 140)) > 400, ("blank page", path, page.number)
            pages.append(rgb)
            words.append(page.get_text())
        positions, areas = {}, {}
        for color in COLORS + [NAV, FOOTER]:
            target = tuple(bytes.fromhex(color[1:]))
            counts = [np.count_nonzero((np.abs(p - target) <= 5).all(axis=2)) for p in pages]
            expected = color in COLORS or not reader
            assert (max(counts) > 300) == expected, (path, color, counts)
            if expected:
                positions[color] = int(np.argmax(counts))
                areas[color] = sum(counts)
        assert positions[COLORS[0]] == 0 and positions[COLORS[-1]] > 0
        assert [positions[c] for c in COLORS] == sorted(positions[c] for c in COLORS)
        for color in COLORS[1:]:
            assert .94 < areas[color] / areas[COLORS[0]] < 1.06, ("missing or duplicated illustration", areas)
        if not reader:
            assert positions[FOOTER] == len(document) - 1
        fulltext = "".join(words)
        assert "End of" in words[-1], (path, words[-1][-150:])
        assert "Terra" in fulltext and "Last" in fulltext and "河岸" in fulltext, (path, fulltext[:300])
        assert ("Site navigation" in fulltext) != reader, (path, fulltext[:300])
        print(f"PASS {path.name}: {len(document)} pages, three illustrations, selectable English and Chinese", flush=True)


def verify(adb, shell, locate, shot, port, prefix, screens, video):
    from share_receiver.build import build
    url = f"http://127.0.0.1:{port}/share-article"
    title = f"Terra field notes {port}"
    mode = shell("cmd", "uimode", "night").decode().strip().split()[-1]
    assert mode in ("yes", "no", "auto")
    if shell("pm", "list", "packages", RECEIVER).strip():
        raise RuntimeError(f"The test receiver {RECEIVER} is already installed on this device")
    def tap(label):
        shell("input", "tap", *locate(label))
    def back():
        shell("input", "keyevent", "4")
    def options():
        tap("共享")
        tap("选项 ›")
    def format_sheet(label):
        options()
        tap(label)
        tap("关闭共享选项")
    def save_pdf(folder):
        format_sheet("PDF")
        tap("保存到文件")
        def document_node(test):
            until = time.monotonic() + 20
            labels = []
            while time.monotonic() < until:
                shell("uiautomator", "dump", "/sdcard/terra-motion.xml")
                labels = []
                for node in ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-motion.xml")).iter("node"):
                    if "documentsui" not in node.get("package", ""):
                        continue
                    label = node.get("text") or node.get("content-desc")
                    if label:
                        labels.append(label)
                    if test(node):
                        x,y,r,b = map(int, re.findall(r"-?\d+", node.get("bounds")))
                        if r > x and b > y:
                            return (x+r)//2, (y+b)//2
            raise AssertionError(("Missing system file picker control", labels[:24]))
        def choose(test):
            shell("input", "tap", *document_node(test))
        choose(lambda n: n.get("content-desc") in ("Show roots", "显示根目录", "显示根文件夹"))
        choose(lambda n: n.get("text") in ("Downloads", "下载内容", "下载") and not n.get("resource-id","").endswith("breadcrumb_text"))
        document_node(lambda n: n.get("class") == "android.widget.EditText" and title in n.get("text", ""))
        shot("format-save-picker")
        choose(lambda n: n.get("resource-id", "").endswith("/button_create") or n.get("text") in ("SAVE", "Save", "保存"))
        remote = "/sdcard/Download/" + title + ".pdf"
        path = folder / "saved.pdf"
        until = time.monotonic() + 10
        while time.monotonic() < until:
            try:
                adb("pull", remote, path)
                data = path.read_bytes()
            except subprocess.CalledProcessError:
                data = b""
            if b"%%EOF" in data[-64:]:
                try:
                    verify_pdf(path, reader=False)
                finally:
                    shell("rm", "-f", shlex.quote(remote))
                locate("共享")
                return
            time.sleep(.25)
        raise AssertionError("The PDF selected in the system file picker was not written")
    def assert_selected(expected):
        shell("uiautomator", "dump", "/sdcard/terra-motion.xml")
        nodes = ElementTree.fromstring(adb("exec-out", "cat", "/sdcard/terra-motion.xml"))
        choices = {n.get("text") for node in nodes.iter("node") if node.get("checked") == "true" or node.get("selected") == "true"
                   for n in node.iter("node") if n.get("text")}
        labels = {"自动", "PDF", READER, "网页归档"}
        assert choices & labels == {expected}, (expected, choices)
        bounds = {n.get("text"): n.get("bounds") for n in nodes.iter("node") if n.get("text") in labels}
        assert set(bounds) == labels
        return bounds
    def receive(folder, name, mime):
        tap("Terra File Check")
        locate("Terra file received")
        receipt = json.loads(adb("exec-out", "run-as", RECEIVER, "cat", "files/receipt.json"))
        data = adb("exec-out", "run-as", RECEIVER, "cat", "files/payload")
        path = folder / name
        path.write_bytes(data)
        path.with_suffix(".receipt.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
        assert receipt["name"] == title + "." + name.rsplit(".", 1)[1] and receipt["mime"] == mime, receipt
        assert receipt["subject"] == title, receipt
        assert receipt["bytes"] == len(data) and receipt["clipMatches"] and receipt["readGrant"], receipt
        if mime == "application/pdf":
            assert receipt["providerMime"] == mime and data.startswith(b"%PDF-"), receipt
        back()
        locate("共享")
        return path
    with tempfile.TemporaryDirectory(prefix="terra-receiver-") as tmp:
        tmp = Path(tmp)
        folder = screens or tmp
        folder.mkdir(exist_ok=True, parents=True)
        apk = build(tmp)
        assert b"Success" in adb("install", "--no-streaming", apk)
        try:
            shell("cmd", "uimode", "night", "no")
            shell("am", "start", "-W", "-n", "app.terra.browser/.MainActivity", "-a", "android.intent.action.VIEW", "-d", url)
            locate("Site navigation")
            rest = np.asarray(shot("format-browser")).astype(int)
            save_pdf(folder)
            with recording(adb, shell, prefix, video, port):
                options()
                rows = assert_selected("自动")
                shot("format-auto")
                for name, image in (("PDF", "pdf"), (READER, "reader"), ("网页归档", "archive"), ("PDF", "pdf-again")):
                    tap(name)
                    assert assert_selected(name) == rows, "The format rows moved when the checkmark changed"
                    shot("format-" + image)
                tap("关闭共享选项")
                locate("PDF 文稿")
                locate("保存到文件")
                shot("format-pdf-panel")
            page = receive(folder, "page.pdf", "application/pdf")
            format_sheet(READER)
            reader = receive(folder, "reader.pdf", "application/pdf")
            verify_pdf(page, reader=False)
            verify_pdf(reader, reader=True)
            format_sheet("PDF")
            tap("标记")
            tap("下一页")
            locate("标记画布，第 2 页")
            tap("上一页")
            tap("红色画笔")
            x, y = locate("标记画布，第 1 页")
            shell("input", "swipe", x-42, y, x+45, y+29, 400)
            tap("撤销")
            tap("重做")
            shot("format-markup")
            tap("完成")
            marked = receive(folder, "marked.pdf", "application/pdf")
            import pymupdf
            with pymupdf.open(page) as original, pymupdf.open(marked) as annotated:
                assert len(original) == len(annotated)
                for num in range(len(original)):
                    a, b = (np.frombuffer(doc[num].get_pixmap().samples, dtype=np.uint8).reshape(-1, 3).astype(np.int16)
                            for doc in (original, annotated))
                    assert (np.count_nonzero((np.abs(a-b) > 12).any(axis=1)) > 50) == (num == 0)
                    if num == 0:
                        assert np.count_nonzero((np.abs(b - (237, 59, 54)) < 6).all(axis=1)) > 20
            print("PASS markup survives undo/redo and handoff, ink changes only the selected PDF page", flush=True)
            format_sheet("网页归档")
            archive = receive(folder, "page.mht", "multipart/related").read_bytes()
            assert b"multipart/related" in archive[:1500] and b"image/png" in archive
            current = np.asarray(shot("format-browser-return")).astype(int)
            h, w = rest.shape[:2]
            area = np.s_[int(h*.28):int(h*.65), int(w*.08):int(w*.92), :]
            assert np.abs(rest[area] - current[area]).mean() < 2, "The source moved during PDF export"
            print("PASS file permissions, real MHTML attachment and unchanged browser position", flush=True)
            shell("am", "start", "-W", "-n", "app.terra.browser/.MainActivity", "-a", "android.intent.action.VIEW",
                  "-d", url + "-short")
            locate("Short field note")
            # Auto must supply a PDF to a target which accepts files but does not accept plain links.
            tap("共享")
            compact = receive(folder, "short.pdf", "application/pdf")
            with pymupdf.open(compact) as short:
                assert len(short) == 1 and "河岸" in short[0].get_text()
            print("PASS Auto targets a PDF-only app and a short page fits one sheet", flush=True)
        finally:
            shell("cmd", "uimode", "night", mode)
            adb("uninstall", RECEIVER)
