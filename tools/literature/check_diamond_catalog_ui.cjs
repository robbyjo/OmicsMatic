// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {chromium} = require(path.join(process.argv[2], 'playwright'));
const root = path.resolve(__dirname, '../..'), out = process.env.JLINALG_REVIEW_UI_OUT;
(async () => {
  const browser = await chromium.launch({executablePath: process.argv[3], headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}}), errors = [], checks = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    const data = n => JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026', n)));
    const inventory = data('tool-inventory.json');
    assert.equal(inventory.length, 400); assert.equal(data('summary.json').papers, 250);
    for (const [status, count] of Object.entries({'native-subset-validated': 19, 'upstream-adapter-validated': 76, 'existing-adapter': 3, 'missing': 246}))
      assert.equal(inventory.filter(t => t.implementation_status === status).length, count);
    const tool = inventory.find(t => t.id === 'diamond');
    assert.equal(tool.source_roles_reviewed, 5); assert.equal(tool.paper_count, 5);
    assert.equal(tool.root_command, 'diamond-tools'); assert.equal(tool.canonical_adapter_available, false);
    assert.equal(tool.analysis_installs_software, false); assert.equal(tool.whole_package_implemented, false);
    assert.equal(tool.whole_paper_reproduced, false); assert.equal(tool.whole_goal_complete, false);
    checks.push('250 papers/400 tools;95 new scoped integrations/98 including existing;246 missing; exact DIAMOND scope');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    assert.equal(await page.locator('#diamond-required-actions').count(), 1);
    await page.locator('#tools-tab').click(); await page.locator('#query').fill('DIAMOND');
    const card = page.locator('#results article').filter({has: page.getByRole('heading', {name: /^DIAMOND \(/i})});
    assert.equal(await card.count(), 1);
    const text = (await card.innerText()).toLowerCase();
    for (const term of ['upstream-adapter-validated', 'diamond-tools', 'DiamondToolsCli', '2.0.0', '2.0.15', '2.1.8', '72', 'six', '216', 'P017', 'P054', 'rounded', 'paired', 'separate'])
      assert(text.includes(term.toLowerCase()), term);
    await card.screenshot({path: path.join(out, 'diamond-card-desktop.png'), style: 'header{visibility:hidden!important}'});
    checks.push('DIAMOND card exposes original versions, complete source/root controls, retained failures and study-cutoff limits');
    const papers = data('papers.json');
    const drafts = JSON.parse(fs.readFileSync(path.join(root, 'tools/literature/diamond-use-reviews.json'))).reviews;
    await page.locator('#papers-tab').click();
    for (const d of drafts) {
      const paper = papers.find(p => p.paper_id === d.paper_id);
      const method = paper.tools.find(t => t.tool_id === 'diamond');
      assert.equal(method.use_summary, d.review.use_summary);
      await page.locator('#query').fill(paper.doi);
      const row = page.locator('#'+d.paper_id+' tr').filter({has: page.locator('td:first-child').filter({hasText: /^DIAMOND(?:reported |$)/})});
      assert.equal(await row.count(), 1);
      await row.getByText('Evidence locator', {exact: true}).click();
      const evidence = await row.innerText();
      assert(evidence.includes(d.review.use_summary));
      assert(evidence.includes(d.review.evidence_paragraph_sha256.slice(0, 12)));
      if (d.paper_id === 'P037') assert.equal(method.version_reported_near_name, '2.0.0');
      checks.push(d.paper_id+' primary-bound DIAMOND role, summary and original anchor remain visible');
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    const actions = page.locator('#diamond-required-actions');
    assert.equal(await actions.count(), 1);
    const warnings = await actions.innerText();
    for (const term of ['SKIP3', 'FAIL2', 'skip dependent', 'forbidden', 'Analysis installs nothing', 'paired FASTQ', 'min-score', 'Duplicate IDs', 'P017', 'P054', 'raw versus bit score', 'whole papers', 'Cell Ranger'])
      assert(warnings.includes(term), term);
    await actions.screenshot({path: path.join(out, 'diamond-required-actions-desktop.png')});
    checks.push('Dedicated warnings page gives visible skip/failure recovery and original scientific limits');
    await page.setViewportSize({width: 390, height: 844}); await page.emulateMedia({colorScheme: 'dark'});
    await actions.scrollIntoViewIfNeeded();
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth+1));
    await page.screenshot({path: path.join(out, 'diamond-required-actions-mobile-dark.png')});
    checks.push('390px dark warnings fit without horizontal overflow');
    assert.deepEqual(errors, []); checks.push('No browser page errors');
    assert.equal(inventory.find(t => t.id === 'cellranger').implementation_status, 'excluded-by-user');
    assert.equal(inventory.find(t => t.id === 'spyrrow').implementation_status, 'missing');
    checks.push('Cell Ranger exclusion and separate missing Spyrrow scope preserved');
    fs.writeFileSync(path.join(out, 'COMPLETED.json'), JSON.stringify({status: 'PASS_DIAMOND_BROWSER_CATALOG_AND_REQUIRED_ACTIONS', checks, screenshots: ['diamond-card-desktop.png', 'diamond-required-actions-desktop.png', 'diamond-required-actions-mobile-dark.png'], whole_goal_complete: false}, null, 2)+'\n');
    console.log('Passed '+checks.length+' DIAMOND browser checks');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
