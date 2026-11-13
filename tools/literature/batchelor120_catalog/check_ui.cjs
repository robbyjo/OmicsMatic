// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Local-only browser check; requires permitted Playwright and Chromium/Edge.
const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const [modules, executablePath, output, base] = process.argv.slice(2);
const {chromium} = require(path.join(modules, 'playwright'));
fs.mkdirSync(output, {recursive: false});
(async () => {
  const browser = await chromium.launch({executablePath, headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    await page.goto(base + '/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    assert.equal(await page.locator('#results article').count(), 250);
    assert.match(await page.locator('#overview').innerText(), /406 named tools/);
    assert.match(await page.locator('#overview').innerText(), /2481 tool observations/);
    for (const [id,doi,required] of [['P040','10.1038/s41586-026-10954-1',['1.29','imaging mass cytometry','not raw RNA counts','does not implement this distinct FastMNN/protein role']],['P051','10.1038/s41586-025-09807-0',['1.20.0','reducedMNN','explicit interpretation']]]) {
      await page.locator('#query').fill(doi);
      assert.equal(await page.locator('#results article').count(), 1);
      const row = page.locator('#' + id + ' tr').filter({hasText: 'Batchelor / FastMNN'});
      assert.equal(await row.count(), 1);
      const text = await row.textContent();
      for (const value of required) assert(text.includes(value), id + ': ' + value);
      assert(text.includes('missing'));
    }
    await page.locator('#query').fill('');
    await page.locator('#tools-tab').click();
    const card = page.locator('#results article').filter({has: page.getByRole('heading', {name: /^Batchelor \/ FastMNN \(2 papers\)$/})});
    assert.equal(await card.count(), 1);
    const cardText = await card.textContent();
    for (const value of ['missing','Partial original upstream integration','FastMNN','P040','P051','no new scoped integration badge']) assert(cardText.includes(value), value);
    await card.scrollIntoViewIfNeeded();
    await card.screenshot({path: path.join(output, 'batchelor-card.png')});
    await page.goto(base + '/literature-warnings.html');
    const warning = page.locator('#batchelor-required-actions');
    const text = await warning.innerText();
    for (const value of ['P040','P051','SKIP3','FAIL2','NEW','protein','missing','reducedMNN','1.29']) assert(text.includes(value), value);
    assert.equal(await warning.locator('code').filter({hasText: /^docs\/literature\/BATCHELOR_INTEGRATION.md$/}).count(), 1);
    assert.equal(await warning.locator('strong').filter({hasText: /^SKIP3:/}).count(), 1);
    assert.equal(await warning.locator('strong').filter({hasText: /^FAIL2:/}).count(), 1);
    await warning.scrollIntoViewIfNeeded();
    await warning.screenshot({path: path.join(output, 'batchelor-warnings.png'), style: 'header, site-header, .skip-link { visibility:hidden !important; }'});
    await page.setViewportSize({width: 390, height: 1000});
    await page.evaluate(() => document.documentElement.dataset.theme = 'dark');
    await warning.scrollIntoViewIfNeeded();
    assert(await warning.isVisible());
    assert(await warning.evaluate(e => e.scrollWidth <= e.clientWidth + 1));
    await warning.screenshot({path: path.join(output, 'batchelor-warnings-mobile.png'), style: 'header, site-header, .skip-link { visibility:hidden !important; }'});
    assert.deepEqual(errors, []);
    const report = {status: 'PASS_CURRENT_BATCHELOR_CATALOG_AND_WARNINGS_UI', papers: 250, named_tools: 406, observations: 2481, checked_roles: ['P040','P051'], gap_visible: true, desktop: '1440x1000', mobile_dark: '390x1000', warning_overflow: false, browser_errors: errors, public_site_deployed: false};
    fs.writeFileSync(path.join(output, 'COMPLETED.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(report.status);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
