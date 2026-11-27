(() => {
    const root = document.querySelector("article") || document.querySelector("main") || document.body;
    if (!root) return "";
    let length = 0;
    const paragraphs = [];
    for (const element of root.querySelectorAll("h1,h2,h3,p,blockquote,pre,li")) {
        if (element.closest("nav,header,footer,aside,[hidden],[aria-hidden=true]")) continue;
        if (element.querySelector("p,li") || element.getClientRects().length === 0) continue;
        const text = element.innerText.trim();
        if (text.length === 0) continue;
        paragraphs.push(text);
        length += text.length;
        if (length >= 150000) break;
    }
    return paragraphs.join("\n\n").slice(0,150000);
})()
