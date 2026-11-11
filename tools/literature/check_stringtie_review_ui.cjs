// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Actual local browser checks for StringTie scope, source ambiguity and recovery.
const fs=require('fs');const path=require('path');const assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));const root=path.resolve(__dirname,'../..');const out=path.resolve(process.argv[4]);
const data=path.join(root,'docs/literature/omics-2023-2026');const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));
const papers=JSON.parse(fs.readFileSync(path.join(data,'papers.json'),'utf8'));const proof=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/stringtie-main-workflow/VALIDATION.json'),'utf8'));
(async()=>{
 fs.mkdirSync(out,{recursive:true});const browser=await chromium.launch({executablePath:process.argv[3],headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];const checks=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  const tool=inventory.find(t=>t.id==='stringtie');assert.equal(tool.implementation_status,'upstream-adapter-validated');assert.equal(tool.project_adapter_available,true);assert.equal(tool.canonical_adapter_available,false);assert.equal(tool.source_roles_reviewed,8);assert.equal(tool.whole_paper_reproduced,false);assert.equal(tool.development_candidate_sha256,proof.artifact.sha256);checks.push('StringTie validated development status is bound to the exact root artifact; canonical/release and whole-paper claims remain false');
  assert.equal(inventory.filter(t=>t.implementation_status==='upstream-adapter-validated').length,52);assert.equal(inventory.filter(t=>t.implementation_status==='missing').length,259);checks.push('Current inventory has52 upstream adapters and259 missing tools');
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('StringTie');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^StringTie \(/})});assert.equal(await card.count(),1);const text=await card.innerText();
  for(const value of ['stringtie-tools','BGZF','1.3.0','3.0.3','omission','STRINGTIE_TOOLS.md','1,361','-rf','RNA-SeQC'])assert(text.includes(value),value);checks.push('Validated StringTie card exposes the project command, complete input/output scope, supported editions and source limitations');
  await card.screenshot({path:path.join(out,'stringtie-card-desktop.png'),style:'header { visibility:hidden !important; }'});
  const themeControl=page.getByRole('combobox',{name:'Color scheme'});await themeControl.selectOption('light');const lightBackground=await page.evaluate(()=>getComputedStyle(document.body).backgroundColor);await themeControl.selectOption('dark');
  const darkBackground=await page.evaluate(()=>getComputedStyle(document.body).backgroundColor);assert.notEqual(darkBackground,lightBackground);assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'dark');checks.push('Actual color-scheme control changes computed background and persists the dark selection');
  await page.locator('#coverage').selectOption('missing');assert.equal(await page.locator('#results article').count(),0);await page.locator('#coverage').selectOption('');checks.push('StringTie is absent from the missing filter');
  await page.locator('#papers-tab').click();
  for(const [id,required] of [['P102',['1.3.6','SAMtools','-e']],['P109',['1.3.0','unclear']],['P110',['2.1.2','TPM','Kallisto']],['P171',['3.0.0','-rf','--rf']],['P183',['hg19','GENCODEv38','RNA-SeQC']],['P223',['Salmon','edgeR']],['P248',['2.2.1','strand']]]){
   const paper=papers.find(p=>p.paper_id===id);await page.locator('#query').fill(paper.doi);assert.equal(await page.locator('#results article').count(),1);
   await page.locator('#results details').evaluateAll(nodes=>nodes.forEach(node=>node.open=true));const articleText=await page.locator('#results').innerText();for(const value of required)assert(articleText.includes(value),id+':'+value);
   const use=paper.tools.find(t=>t.tool_id==='stringtie');assert.equal(use.review_status,'usage-summary-reviewed; paper-wide-completeness-review-open');checks.push(id+' preserves its reviewed source version, separate tools and unresolved settings');
  }
  await page.setViewportSize({width:390,height:1000});assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));checks.push('Reviewed paper source display fits390px dark viewport');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#stringtie-required-actions');assert(await actions.isVisible());
  assert.equal(await page.evaluate(()=>document.documentElement.dataset.theme),'dark');assert.equal(await page.evaluate(()=>getComputedStyle(document.body).backgroundColor),darkBackground);
  const actionText=await actions.innerText();for(const value of ['Linux x86_64 Java','--export-runtime-setup','RUN-SETUP.txt','SKIP3','FAIL2','NEW output paths','COMPLETED.json','INCOMPLETE.json','--verify','RETAIN_OMISSIONS','-rf','--rf','GENCODEv38','Cell Ranger'])assert(actionText.includes(value),value);checks.push('Prominent actions name exact setup, execution platform, SKIP3/FAIL2, complete results, source ambiguity and user exclusion');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await actions.screenshot({path:path.join(out,'stringtie-actions-mobile-dark.png'),style:'header, .skip-link { visibility:hidden !important; }'});
  await page.setViewportSize({width:1440,height:1000});await actions.screenshot({path:path.join(out,'stringtie-actions-desktop.png'),style:'header, .skip-link { visibility:hidden !important; }'});
  assert.equal(errors.length,0,errors.join('\n'));checks.push('No browser JavaScript errors or mobile overflow');
  const report={status:'passed-StringTie-browser-catalog-source-and-recovery-checks',checks,check_count:checks.length,errors,actual_theme_control_tested:true,dark_background:darkBackground,dark_warning_theme_persisted_after_navigation:true,screenshots:['stringtie-card-desktop.png','stringtie-actions-mobile-dark.png','stringtie-actions-desktop.png']};fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:checks.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
