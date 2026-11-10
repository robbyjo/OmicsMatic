// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs = require('fs'), path = require('path'), assert = require('assert/strict');
const {chromium} = require(path.join(process.argv[2], 'playwright'));
const root = path.resolve(__dirname, '../..'), out = process.env.JLINALG_REVIEW_UI_OUT;
const clean = s => s.replace(/\s+/g, ' ').trim();
(async () => {
  const browser = await chromium.launch({executablePath: process.argv[3], headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}}), errors = [], checks = [];
  page.on('pageerror', e => errors.push(e.message));
  try {
    const data = n => JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026', n)));
    const inventory = data('tool-inventory.json'), summary = data('summary.json');
    assert.equal(inventory.length, 400); assert.equal(summary.papers, 250);
    for (const [status, count] of Object.entries({'native-subset-validated': 19, 'upstream-adapter-validated': 75, 'existing-adapter': 3, 'missing': 247}))
      assert.equal(inventory.filter(t => t.implementation_status === status).length, count);
    const tool = inventory.find(t => t.id === 'cellchat');
    assert.equal(tool.source_roles_reviewed, 6); assert.equal(tool.paper_count, 6);
    assert.equal(tool.root_command, 'cellchat-tools'); assert.equal(tool.canonical_adapter_available, false);
    assert.equal(tool.analysis_installs_software, false); assert.equal(tool.whole_package_implemented, false);
    assert.equal(tool.whole_paper_reproduced, false); assert.equal(tool.whole_goal_complete, false);
    checks.push('250 papers/400 tools;94 new scoped integrations/97 including existing;247 missing; CellChat scope explicit');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    await page.locator('#tools-tab').click(); await page.locator('#query').fill('CellChat');
    const card = page.locator('#results article').filter({has: page.getByRole('heading', {name: /^CellChat \(/i})});
    assert.equal(await card.count(), 1);
    const text = (await card.innerText()).toLowerCase();
    for (const term of ['upstream-adapter-validated', 'cellchat-tools', 'CellChatToolsCli', '1.0.0', '1.1.3', '2.1.2', '2.2.0', 'embedding', 'strength', 'P060', 'unresolved', 'skip', 'Spyrrow', '48', '30'])
      assert(text.includes(term.toLowerCase()), term);
    await card.screenshot({path: path.join(out, 'cellchat-card-desktop.png'), style: 'header{visibility:hidden!important}'});
    checks.push('CellChat card shows exact workflow families, full routing validation, unresolved custom score and separate Spyrrow scope');
    const papers = data('papers.json');
    const drafts = JSON.parse(fs.readFileSync(path.join(root, 'tools/literature/cellchat-use-reviews.json'))).reviews;
    await page.locator('#papers-tab').click();
    for (const d of drafts) {
      const paper = papers.find(p => p.paper_id === d.paper_id);
      await page.locator('#query').fill(paper.doi); assert.equal(await page.locator('#results article').count(), 1);
      const row = page.locator('#'+d.paper_id+' tbody tr').filter({has: page.locator('td:first-child').filter({hasText: /^CellChat(?:reported|\s|$)/i})});
      assert.equal(await row.count(), 1);
      const all = clean(await row.textContent());
      assert(all.includes(clean(d.review.use_summary)), d.paper_id+' complete use summary');
      assert(all.includes(clean(d.review.implementation_scope_note)), d.paper_id+' complete scope note');
      await row.locator('summary').first().click();
      assert((await row.innerText()).toLowerCase().includes('paragraph'), d.paper_id+' source disclosure');
      checks.push(d.paper_id+' complete primary-bound use/scope and source disclosure');
    }
    assert.equal(inventory.find(t => t.id === 'spyrrow').implementation_status, 'missing');
    assert.equal(inventory.find(t => t.id === 'cellranger').implementation_status, 'excluded-by-user');
    checks.push('Spyrrow remains missing and Cell Ranger remains excluded');
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    const warning = page.locator('#cellchat-required-actions'); assert(await warning.isVisible());
    assert.equal(await page.locator('section[id$="-required-actions"]').first().getAttribute('id'), 'cellchat-required-actions');
    const w = (await warning.innerText()).toLowerCase();
    for (const term of ['SKIP3', 'FAIL2', 'installs nothing', 'skip', 'forbidden', 'NEW output', 'negative', 'fallback zeros', 'missing', 'RNG', 'random_state', 'future.seed', 'UMAP', 'P060', 'unresolved', 'Spyrrow', 'P145', 'sign convention', 'full250-paper'])
      assert(w.includes(term.toLowerCase()), term);
    await warning.screenshot({path: path.join(out, 'cellchat-actions-desktop.png'), style: 'header{visibility:hidden!important}'});
    checks.push('Warnings are first, visible and contain concrete dependency/failure/numerical/RNG/custom-score actions');
    await page.setViewportSize({width: 390, height: 1000});
    await page.evaluate(() => document.documentElement.dataset.theme = 'dark');
    assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
    await warning.screenshot({path: path.join(out, 'cellchat-actions-mobile-dark.png'), style: 'header{visibility:hidden!important}'});
    checks.push('390px dark warnings remain readable without horizontal page overflow');
    assert.equal(errors.length, 0, errors.join('\n'));
    checks.push('No browser JavaScript errors');
    fs.writeFileSync(path.join(out, 'COMPLETED.json'), JSON.stringify({status: 'PASS_CELLCHAT_BROWSER_CATALOG_AND_REQUIRED_ACTIONS', checks, errors, remote_mutations: false}, null, 2)+'\n');
    console.log('PASS', checks.length, 'CellChat catalog/browser checks');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
