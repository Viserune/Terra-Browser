(() => {
    const omit = "nav,aside,footer,form,button,input,textarea,select,script,style,noscript,template,svg,iframe,canvas,video,audio,[hidden],[aria-hidden=true],[role=navigation],[role=banner],[role=complementary]";
    const visible = el => el.getClientRects().length && getComputedStyle(el).visibility !== "hidden";
    const candidates = Array.from(document.querySelectorAll("article,[itemprop=articleBody]")).slice(0, 32)
        .filter(el => !el.closest(omit) && visible(el));
    const score = el => [...el.querySelectorAll("p")].slice(0, 160)
        .reduce((sum, p) => sum + Math.min(1500, p.textContent.trim().length), 0);
    const root = candidates.map(el => [score(el), el]).sort((a,b) => b[0]-a[0])[0]?.[1]
        || document.querySelector("main,[role=main]") || document.body;
    if (!root) return null;
    const heading = [...root.querySelectorAll("h1")].find(el => !el.closest(omit) && visible(el));
    const title = (heading?.innerText || document.querySelector('meta[property="og:title"]')?.content || document.title).trim().slice(0,512);
    const allowed = new Set("p h1 h2 h3 h4 h5 h6 blockquote ul ol li pre code strong b em i a sub sup br hr figure figcaption img table thead tbody tr th td time".split(" "));
    const blocks = new Set("div section article header main address dl dt dd".split(" "));
    const events = [];
    let remaining = 180000, visited = 0, prose = 0, truncated = false;
    function address(value, link) {
        if (!value) return "";
        try {
            const u = new URL(value, document.baseURI);
            if (!["https:", "http:"].includes(u.protocol)) return "";
            if (link && u.hash && u.href.split("#")[0] === location.href.split("#")[0]) {
                return "#" + decodeURIComponent(u.hash.slice(1)).slice(0,256);
            }
            return u.href.length <= 4096 ? u.href : "";
        } catch { return ""; }
    }
    function visit(node, depth, countsAsProse=true) {
        if (depth > 48 || remaining <= 0 || ++visited > 9000) { truncated = true; return; }
        if (node.nodeType === Node.TEXT_NODE) {
            const text = node.data.slice(0,remaining);
            if (text.length < node.data.length) truncated = true;
            remaining -= text.length;
            if (countsAsProse) prose += text.trim().length;
            if (text) events.push(["text", text]);
            return;
        }
        if (node.nodeType !== Node.ELEMENT_NODE || node === heading || node.matches(omit)) return;
        const tag = node.localName;
        if (tag === "header" && root === document.body) return;
        const style = getComputedStyle(node);
        if (style.display === "none" || style.visibility === "hidden") return;
        if (tag === "img") {
            const url = [node.currentSrc, node.dataset.src, node.getAttribute("src")].map(src => address(src, false)).find(Boolean);
            if (url && node.width !== 1 && node.height !== 1)
                events.push(["img", url, node.alt.slice(0,512), node.naturalWidth || node.width, node.naturalHeight || node.height]);
            return;
        }
        if (tag === "br" || tag === "hr") { events.push([tag]); return; }
        const wrap = allowed.has(tag) ? tag : blocks.has(tag) || node.id ? "div" : "";
        if (wrap) events.push(["open",wrap,node.id.slice(0,256),tag === "a" ? address(node.getAttribute("href"), true) : tag === "ol" ? String(node.start) : ""]);
        for (const child of node.childNodes) {
            if (remaining <= 0 || visited >= 9000) { truncated = true; break; }
            visit(child, depth+1, countsAsProse && !/^(a|h[1-6]|figcaption)$/.test(tag));
        }
        if (wrap) events.push(["close",wrap]);
    }
    visit(root,0);
    if (prose < (candidates.length ? 1 : 75)) return null;
    return {title, lang:(root.closest("[lang]")?.lang || "").slice(0,32), events, truncated};
})()
