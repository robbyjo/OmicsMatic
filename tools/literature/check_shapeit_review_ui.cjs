const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {chromium} = require(path.join(process.argv[2], 'playwright'));
const root = path.resolve(__dirname, '../..'), out = process.argv[4];
fs.mkdirSync(out, {recursive: true});
const reviews = JSON.parse(fs.readFileSync(path.join(root, 'tools/literature/shapeit-use-reviews.json')));
const papers = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/papers.json')));
const inventory = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/tool-inventory.json')));
const checks = [], plain = value => value.replace(/\s+/g, ' ').trim();
function check(name, fn) { fn(); checks.push(name); }
(async () => {
  const browser = await chromium.launch({executablePath: process.argv[3], headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}}), errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    check('No phasing integration credited for a native audit foundation', () => {
      assert.equal(inventory.find(tool => tool.id === 'shapeit').implementation_status, 'missing');
      assert.equal(inventory.filter(tool => tool.implementation_status === 'upstream-adapter-validated').length, 44);
      assert.equal(inventory.filter(tool => tool.implementation_status === 'native-subset-validated').length, 12);
      assert.equal(inventory.filter(tool => tool.implementation_status === 'missing').length, 263);
    });
    for (const item of reviews) {
      const paper = papers.find(p => p.paper_id === item.paper_id);
      await page.locator('#query').fill(paper.doi);
      assert.equal(await page.locator('#results article').count(), 1);
      const row = page.locator('#' + item.paper_id + ' tbody tr').filter({has: page.locator('td:first-child').filter({hasText: /^SHAPEIT(?:reported\b|\s|$)/})});
      assert.equal(await row.count(), 1);
      const cells = await row.locator('td').allInnerTexts();
      check(item.paper_id + ' full reviewed use', () => assert.ok(plain(cells[1]).includes(plain(item.review.use_summary))));
      check(item.paper_id + ' full scope and version uncertainty', () => assert.ok(plain(cells[2]).includes(plain(item.review.implementation_scope_note))));
      check(item.paper_id + ' explicit unavailable phasing status', () => assert.ok(plain(cells[2]).startsWith('missing')));
      check(item.paper_id + ' exact reported version', () => item.review.version_reported_near_name
        ? assert.ok(plain(cells[0]).includes('reported ' + item.review.version_reported_near_name))
        : assert.ok(!cells[0].includes('reported')));
      await row.locator('summary').first().click();
      const evidence = plain(await row.locator('details').innerText());
      check(item.paper_id + ' primary paragraph identity', () => assert.ok(evidence.includes(item.review.evidence_paragraph_sha256)));
      for (const pointer of item.review.supporting_evidence) {
        check(item.paper_id + ' supporting paragraph ' + pointer.paragraph_index, () => assert.ok(evidence.includes(pointer.paragraph_sha256)));
      }
    }
    for (const [width, height, theme] of [[1440, 1000, 'light'], [414, 896, 'dark']]) {
      await page.setViewportSize({width, height});
      await page.evaluate(value => document.documentElement.dataset.theme = value, theme);
      await page.evaluate(async () => {await document.fonts.ready; await new Promise(requestAnimationFrame);});
      const withinViewport = await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1);
      check(theme + ' page remains within viewport', () => assert.ok(withinViewport));
      await page.screenshot({path: path.join(out, 'shapeit-' + theme + '.png')});
    }
    assert.deepEqual(errors, []);
    fs.writeFileSync(path.join(out, 'ui-validation.json'), JSON.stringify({status: 'passed-ten-SHAPEIT-source-role-and-scope-UI', checks, console_errors: errors, new_integrations_credited: 0}, null, 2) + '\n');
    console.log('SHAPEIT UI PASSED', checks.length, 'checks');
  } finally {await browser.close();}
})().catch(error => {console.error(error); process.exitCode = 1;});
