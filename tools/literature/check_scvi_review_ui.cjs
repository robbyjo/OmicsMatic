// Run against the local source-site server; no external model or study data is loaded.
const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {chromium} = require(path.join(process.argv[2], 'playwright'));
const root = path.resolve(__dirname, '../..'), out = process.argv[4];
fs.mkdirSync(out, {recursive: true});
const rows = JSON.parse(fs.readFileSync(path.join(root, 'tools/literature/scvi-use-reviews.json'), 'utf8'));
const papers = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/papers.json'), 'utf8'));
const checks = [], screenshots = [], plain = s => s.replace(/\s+/g, ' ').trim();
function check(name, fn) { fn(); checks.push(name); }
(async () => {
  const browser = await chromium.launch({executablePath: process.argv[3], headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}}), errors = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    const item = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/tool-inventory.json'), 'utf8')).find(t => t.id === 'scvi');
    check('Scoped original adapter available', () => {assert.equal(item.implementation_status, 'upstream-adapter-validated'); assert.equal(item.source_roles_reviewed, 11); assert.equal(item.whole_paper_reproduced, false);});
    for (const row of rows) {
      const paper = papers.find(p => p.paper_id === row.paper_id);
      await page.locator('#query').fill(paper.doi);
      assert.equal(await page.locator('#results article').count(), 1);
      const tr = page.locator('#' + row.paper_id + ' td:first-child').filter({hasText: /^scVI(?:reported\b|\s|$)/}).locator('..');
      assert.equal(await tr.count(), 1);
      const text = plain(await tr.innerText());
      check(row.paper_id + ' reviewed use and scope', () => {assert.ok(text.includes(plain(row.review.use_summary))); assert.ok(text.includes(plain(row.review.implementation_scope_note)));});
      check(row.paper_id + ' available scoped status', () => assert.match(text, /upstream-adapter-validated/));
      const name = plain(await tr.locator('td').first().innerText());
      check(row.paper_id + ' exact reported version', () => row.review.version_reported_near_name ? assert.ok(name.includes('reported ' + row.review.version_reported_near_name)) : assert.ok(!name.includes('reported')));
    }
    const actions = page.locator('#scvi-actions');
    for (const [width, height, theme] of [[1440, 1000, 'light'], [414, 896, 'dark']]) {
      await page.setViewportSize({width, height}); await page.emulateMedia({colorScheme: theme});
      await actions.evaluate(el => el.scrollIntoView({block: 'start', behavior: 'instant'}));
      await page.evaluate(async () => {await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame);});
      const text = await actions.innerText();
      check(theme + ' recovery and restrictions visible', () => {assert.match(text, /skipped3/); assert.match(text, /Failure2/); assert.match(text, /NEW_DIRECTORY/); assert.match(text, /Historical1\.0\.3\/1\.2\.0/); assert.match(text, /pickle/);});
      const box = await actions.boundingBox(), heading = await actions.locator('h2').boundingBox();
      const bottom = await page.locator('site-header').evaluate(el => Math.max(0, ...[el, ...el.querySelectorAll('*')].map(x => x.getBoundingClientRect()).filter(r => r.width > 0 && r.height > 0).map(r => r.bottom)));
      fs.writeFileSync(path.join(out, 'layout-' + theme + '.json'), JSON.stringify(await actions.evaluate(el => ({rect: el.getBoundingClientRect().toJSON(), margin: getComputedStyle(el).scrollMarginTop, scrollY, viewport: innerHeight})), null, 2));
      check(theme + ' heading clears header and card fits', () => {assert.ok(heading.y >= bottom + 4, JSON.stringify({heading, bottom})); assert.ok(box.x >= 0 && box.x + box.width <= width + 1);});
      const shot = 'scvi-actions-' + theme + '.png'; await page.screenshot({path: path.join(out, shot)}); screenshots.push(shot);
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html#scvi-actions');
    const warningText = await page.locator('#scvi-actions').innerText();
    check('Warnings page retains restrictions', () => {assert.match(warningText, /Failure2/); assert.match(warningText, /skipped3/); assert.match(warningText, /Historical1\.0\.3\/1\.2\.0/);});
    for (const href of await page.locator('#scvi-actions a').evaluateAll(a => a.map(x => x.href))) check('Source documentation link ' + href, () => assert.match(href, /^https:\/\/github\.com\/robbyjo\/OmicsMatic\/blob\/codex\/analysis-pipeline\//));
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(out, 'ui-validation.json'), JSON.stringify({status: 'passed-eleven-scvi-source-roles-and-recovery-in-real-browser', checks, screenshots, console_errors: errors}, null, 2) + '\n');
    console.log('SCVI UI PASSED', checks.length, 'checks');
  } finally { await browser.close(); }
})().catch(e => {console.error(e); process.exitCode = 1;});
