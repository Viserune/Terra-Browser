(() => {
    const root = document.scrollingElement || document.documentElement;
    const lines = [], figures = [];
    const range = document.createRange();
    const walker = document.createTreeWalker(document.body || root, NodeFilter.SHOW_TEXT);
    for (let node; (node = walker.nextNode());) {
        if (!node.textContent.trim() || !node.parentElement) continue;
        const style = getComputedStyle(node.parentElement);
        if (style.visibility !== 'visible' || Number(style.opacity) === 0) continue;
        range.selectNodeContents(node);
        if (!range.getClientRects().length) continue;
        const text = node.data;
        let start = 0;
        while (start < text.length) {
            range.setStart(node, start);
            range.setEnd(node, start + 1);
            const first = range.getBoundingClientRect();
            let low = start + 1, high = text.length;
            // Find the last character on this physical line. A bidi run can have several rectangles.
            while (low < high) {
                const mid = Math.ceil((low + high) / 2);
                range.setEnd(node, mid);
                if (range.getBoundingClientRect().height <= first.height + 1) low = mid;
                else high = mid - 1;
            }
            range.setEnd(node, low);
            const rect = range.getBoundingClientRect();
            const value = text.slice(start, low).replace(/\s+/g, ' ').trim();
            if (value && rect.width > 0 && rect.height > 0)
                lines.push([rect.left + scrollX, rect.top + scrollY, rect.width, rect.height, value]);
            start = low;
        }
    }
    for (const element of document.querySelectorAll('img,svg,canvas,tr')) {
        const rect = element.getBoundingClientRect();
        if (rect.width > 0 && rect.height > 0) figures.push([rect.top + scrollY, rect.bottom + scrollY]);
    }
    return {width: root.scrollWidth, height: root.scrollHeight, viewport: innerWidth, lines, figures};
})()
