const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {chromium} = require(path.join(process.argv[2], 'playwright'));
const root = path.resolve(__dirname, '../..'), out = process.argv[4]; fs.mkdirSync(out, {recursive: true});
const rows = JSON.parse(fs.readFileSync(path.join(root, 'tools/literature/quantification-use-reviews.json'), 'utf8'));
const papers = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/papers.json'), 'utf8'));
const inventory = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/tool-inventory.json'), 'utf8'));
const checks = [], screenshots = [], plain = s => s.replace(/\s+/g, ' ').trim();
function check(name, fn) {fn(); checks.push(name);}
(async () => {
  const browser = await chromium.launch({executablePath: process.argv[3], headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}}), errors = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    check('RSEM and bcl2fastq remain missing', () => {for (const id of ['rsem', 'bcl2fastq']) assert.equal(inventory.find(t => t.id === id).implementation_status, 'missing');});
    check('Integration counts unchanged', () => {assert.equal(inventory.filter(t => t.implementation_status === 'upstream-adapter-validated').length, 43); assert.equal(inventory.filter(t => t.implementation_status === 'native-subset-validated').length, 12); assert.equal(inventory.filter(t => t.implementation_status === 'missing').length, 264);});
    for (const row of rows) {
      const paper = papers.find(p => p.paper_id === row.paper_id), name = row.review.tool_id === 'rsem' ? 'RSEM' : 'bcl2fastq';
      await page.locator('#query').fill(paper.doi); assert.equal(await page.locator('#results article').count(), 1);
      const tr = page.locator('#' + row.paper_id + ' td:first-child').filter({hasText: new RegExp('^' + name + '(?:reported\\b|\\s|$)')}).locator('..');
      assert.equal(await tr.count(), 1); const text = plain(await tr.innerText());
      check(row.paper_id + '/' + name + ' reviewed use and scope', () => {assert.ok(text.includes(plain(row.review.use_summary))); assert.ok(text.includes(plain(row.review.implementation_scope_note)));});
      const status = plain(await tr.locator('td').nth(2).innerText());
      check(row.paper_id + '/' + name + ' missing status', () => assert.ok(status.startsWith('missing')));
      const shownName = plain(await tr.locator('td').first().innerText());
      check(row.paper_id + '/' + name + ' exact reported version', () => row.review.version_reported_near_name ? assert.ok(shownName.includes('reported ' + row.review.version_reported_near_name)) : assert.ok(!shownName.includes('reported')));
    }
    // A user follows the recovery anchor after leaving the search field. Blur
    // also prevents pending browser focus scrolling from overriding that anchor.
    await page.locator('#query').evaluate(el => el.blur());
    const actions = page.locator('#quantification-actions');
    for (const [width, height, theme] of [[1440, 1000, 'light'], [414, 896, 'dark']]) {
      await page.setViewportSize({width, height}); await page.evaluate(t => document.documentElement.dataset.theme = t, theme);
      await page.evaluate(async () => {await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame);});
      // Site-wide smooth focus scrolling may still be running after repeated
      // searches. Wait for that actual motion to finish before following a new anchor.
      await page.evaluate(() => new Promise((resolve, reject) => {
        let previous = scrollY, stable = 0; const deadline = performance.now() + 3000;
        function frame() {
          stable = Math.abs(scrollY - previous) < 0.01 ? stable + 1 : 0; previous = scrollY;
          if (stable >= 4) resolve();
          else if (performance.now() > deadline) reject(new Error('Prior search focus scrolling did not settle'));
          else requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
      }));
      await actions.evaluate(el => el.scrollIntoView({block: 'start', behavior: 'instant'}));
      await page.evaluate(async () => {await document.fonts.ready; await new Promise(requestAnimationFrame); await new Promise(requestAnimationFrame);});
      const text = await actions.innerText();
      check(theme + ' skip and unfinished status visible', () => {assert.match(text, /runtime analysis is skipped/); assert.match(text, /permitted installation/); assert.match(text, /instrument\/data eligibility/); assert.match(text, /adapter is unfinished/); assert.match(text, /two-decimal/); assert.match(text, /Both tools remain missing/);});
      const box = await actions.boundingBox(), heading = await actions.locator('h2').boundingBox();
      const bottom = await page.locator('site-header').evaluate(el => Math.max(0, ...[el, ...el.querySelectorAll('*')].map(x => x.getBoundingClientRect()).filter(r => r.width > 0 && r.height > 0).map(r => r.bottom)));
      fs.writeFileSync(path.join(out, 'layout-' + theme + '.json'), JSON.stringify({box, heading, bottom}, null, 2));
      check(theme + ' recovery heading and width', () => {assert.ok(heading.y >= bottom + 4, JSON.stringify({theme, heading, bottom})); assert.ok(box.x >= 0 && box.x + box.width <= width + 1);});
      const shot = 'quantification-actions-' + theme + '.png'; await page.screenshot({path: path.join(out, shot)}); screenshots.push(shot);
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html#quantification-actions');
    const warnings = await page.locator('#quantification-actions').innerText();
    check('Warnings page retains required actions', () => {assert.match(warnings, /runtime analysis is skipped/); assert.match(warnings, /run folder and sample sheet/); assert.match(warnings, /adapter is unfinished/); assert.match(warnings, /Cell Ranger remains excluded/);});
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(out, 'ui-validation.json'), JSON.stringify({status: 'passed-twenty-quantification-source-roles-and-prominent-required-actions', checks, screenshots, console_errors: errors, integrations_added: 0}, null, 2) + '\n');
    console.log('QUANTIFICATION UI PASSED', checks.length, 'checks');
  } finally {await browser.close();}
})().catch(e => {console.error(e); process.exitCode = 1;});
