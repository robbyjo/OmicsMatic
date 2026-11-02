// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Focused checks for current scoped FUMA availability and actionable failures.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),data=path.join(root,'docs/literature/omics-2023-2026');
const out=process.env.JLINALG_FUMA_UI_OUT;if(!out||fs.existsSync(out))throw Error('Choose a new JLINALG_FUMA_UI_OUT directory');
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});const errors=[],report={status:'passed',checks:[],screenshots:[]};
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000}});page.on('pageerror',e=>errors.push(String(e)));
  const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'))),item=inventory.find(t=>t.id==='fuma'),summary=JSON.parse(fs.readFileSync(path.join(data,'summary.json')));
  assert.equal(item.implementation_status,'native-subset-validated');assert(item.project_adapter_available);assert(!item.canonical_adapter_available&&!item.full_hosted_tool_available&&!item.whole_paper_reproduced);
  const counts=summary.implementation_status_counts;assert.equal(summary.papers,250);assert.equal(summary.paper_tool_observations,2430);assert.equal(counts['native-subset-validated'],15);assert.equal(counts['upstream-adapter-validated'],48);assert.equal(counts.missing,257);
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const roles=JSON.parse(fs.readFileSync(path.join(data,'fuma-role-coverage.json')));assert.equal(roles.papers.length,14);const p243=roles.papers.find(r=>r.paper_id==='P243');assert.equal(p243.command_scope,'source-method-unspecified');assert.equal(p243.capability_ids.length,0);
  for(const role of roles.papers){const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^FUMA(?:reported|\s|$)/})});assert.equal(await row.count(),1,role.paper_id);assert((await row.innerText()).length>100);}
  report.checks.push('250 papers and2430 observations;63 new scoped integrations:15 native/48 upstream;257 missing','Fourteen source-reviewed FUMA rows retained;P243 has no invented module command');
  await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('native-subset-validated');await page.locator('#query').fill('FUMA');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FUMA \(/})});assert.equal(await card.count(),1);
  for(const phrase of ['native-subset-validated','fuma-tools','148','663','sample-level','unsupported','31cf']){
   // The hash is bound by inventory; user-facing cards describe scope and link evidence.
   if(phrase==='31cf'){assert.equal(item.development_candidate_sha256.slice(0,8),'31cf8700');continue;}
   if(phrase==='unsupported'){assert((await card.innerText()).includes('No automatic'));continue;}
   assert((await card.innerText()).includes(phrase),phrase);
  }
  const history=card.locator('details.fuma-card-history');assert.equal(await history.count(),1);assert.equal(await history.evaluate(e=>e.open),false);
  await card.screenshot({path:path.join(out,'fuma-current-card-desktop.png'),style:'header,.skip-link{visibility:hidden !important}'});report.screenshots.push('fuma-current-card-desktop.png');
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await history.evaluate(e=>e.open=true);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await history.evaluate(e=>e.open=false);
  await card.screenshot({path:path.join(out,'fuma-current-card-mobile-dark.png'),style:'header,.skip-link{visibility:hidden !important}'});report.screenshots.push('fuma-current-card-mobile-dark.png');
  await page.locator('#coverage').selectOption('missing');assert.equal(await card.count(),0);report.checks.push('FUMA available under native filter, absent under missing filter;current and historical candidates separated;390px history expansion fits');
  for(const url of ['literature-review.html','literature-warnings.html']){
   await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8768/'+url);const action=page.locator('#fuma-current-actions');assert.equal(await action.count(),1);
   const text=await action.innerText();for(const phrase of ['31cf8700','prepare-reference','prepare-expression','SKIP3','FAIL2','Required action','NEW directory','Cell Ranger remains excluded','663','201','unsupported'])assert(text.includes(phrase),phrase);
   const label=url.startsWith('literature-review')?'review':'warnings';await action.screenshot({path:path.join(out,'fuma-'+label+'-current-actions-desktop.png'),style:'header,.skip-link{visibility:hidden !important}'});report.screenshots.push('fuma-'+label+'-current-actions-desktop.png');
   await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await action.screenshot({path:path.join(out,'fuma-'+label+'-current-actions-mobile-dark.png'),style:'header,.skip-link{visibility:hidden !important}'});report.screenshots.push('fuma-'+label+'-current-actions-mobile-dark.png');
  }
  assert.equal(errors.length,0,errors.join('\n'));report.checks.push('Current reference/expression scopes and explicit SKIP3/FAIL2 recovery on both pages at desktop and390px dark;no overflow or page errors');fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }catch(error){fs.writeFileSync(path.join(out,'FAILURE.json'),JSON.stringify({status:'failed',error:String(error),stack:error.stack,errors},null,2)+'\n');throw error;}finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
