/* Optional visual/export check: requires Playwright and Chromium/Edge, not a runtime dependency. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { chromium } = require('playwright');

(async () => {
    const [input, output] = process.argv.slice(2);
    if (!input || !output) throw new Error('Usage: node manuscript-math-browser-check.cjs MANUSCRIPT_HTML NEW_OUTPUT_DIRECTORY');
    fs.mkdirSync(output, { recursive: true });
    const browser = await chromium.launch({ headless: true,
        ...(process.env.MANUSCRIPT_BROWSER ? { executablePath: process.env.MANUSCRIPT_BROWSER } : {}) });
    try {
        const page = await browser.newPage({ viewport: { width: 1200, height: 1000 } });
        const remote = [], errors = [];
        await page.route(/^https?:/, route => { remote.push(route.request().url()); return route.abort(); });
        page.on('pageerror', error => errors.push(error.message));
        await page.goto(pathToFileURL(path.resolve(input)).href);
        await page.waitForFunction(() => document.documentElement.dataset.manuscriptMath === 'complete');
        await page.evaluate(() => document.fonts.ready);
        const checks = await page.evaluate(() => {
            const equations = [...document.querySelectorAll('.manuscript-math')];
            return {
                equations: equations.length,
                complete: equations.every(element => element.dataset.mathStatus === 'complete'),
                fractions: document.querySelectorAll('math mfrac').length,
                roots: document.querySelectorAll('math msqrt').length,
                sums: [...document.querySelectorAll('math mo')].filter(element => element.textContent === '∑').length,
                properComparisons: document.body.textContent.includes('P ≤ 0.05 and N ≥ 10'),
                unclipped: equations.every(element => element.dataset.display === 'true'
                    ? element.scrollWidth <= element.clientWidth + 1
                    : element.getBoundingClientRect().right <= document.documentElement.clientWidth)
            };
        });
        assert.ok(checks.equations > 0 && checks.complete && checks.fractions > 0 && checks.sums > 0 && checks.roots > 0);
        assert.ok(checks.properComparisons && checks.unclipped);
        assert.deepEqual(remote, []); assert.deepEqual(errors, []);
        await page.pdf({ path: path.join(output, 'manuscript-math-check.pdf'), format: 'A4', printBackground: true,
            margin: { top: '16mm', right: '16mm', bottom: '16mm', left: '16mm' } });
        await page.locator('.manuscript-math-display').first().screenshot({ path: path.join(output, 'equation.png') });
        // Invalid notation stays visible and blocks a successful equation-rendering status.
        await page.evaluate(() => {
            for (const tex of ['\\unknowncommand{x}', '\\href{https://example.org}{x}', '\\frac{1}']) {
                const element = document.createElement('span');
                element.className = 'manuscript-math'; element.dataset.tex = tex;
                element.dataset.display = 'false'; element.textContent = tex;
                document.body.appendChild(element);
            }
        });
        await page.addScriptTag({ content: fs.readFileSync(path.resolve(__dirname,
            '../../src/main/resources/org/jlinalg/study/math/render.js'), 'utf8') });
        assert.equal(await page.locator('.manuscript-math[data-math-status="error"]').count(), 3);
        assert.equal(await page.locator('html').getAttribute('data-manuscript-math'), 'error');
        assert.deepEqual(remote, []); assert.deepEqual(errors, []);
        const result = { ...checks, invalidEquationsVisible: true, externalRequests: remote.length, browser: browser.version() };
        fs.writeFileSync(path.join(output, 'browser-verification.json'), JSON.stringify(result, null, 2) + '\n');
        console.log(JSON.stringify(result, null, 2));
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
