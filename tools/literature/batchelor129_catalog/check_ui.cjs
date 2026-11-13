// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Local report validation; no author-code or external-service execution.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);const overview=await page.locator('#overview').innerText();assert(overview.includes('407 named tools'));assert(overview.includes('2482 tool observations'));
  const notice=page.locator('#batchelor129-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  for(const [id,doi] of [['P040','10.1038/s41586-026-10954-1'],['P051','10.1038/s41586-025-09807-0']]){
   await page.locator('#query').fill(doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Batchelor \/ FastMNN/})});assert.equal(await row.count(),1);
   const visible=await row.innerText();for(const word of ['upstream-adapter-validated','Current selected workflow','selected'])assert(visible.toLowerCase().includes(word.toLowerCase()),id+': '+word);
   assert.equal(await row.locator('.source-review-history').count(),1);assert.equal(await row.locator('.source-review-history').evaluate(e=>e.open),false);assert((await row.locator('.source-review-history summary').innerText()).includes('Historical'));
   if(id==='P040'){
    for(const word of ['1.29','prepared','protein','Ki-67','unknown'])assert(visible.includes(word),word);
    const pheno=page.locator('#P040 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Rphenograph/})});assert.equal(await pheno.count(),1);assert.equal((await pheno.locator('td').first().innerText()).split('reported')[0].trim(),'Rphenograph');const text=await pheno.innerText();for(const word of ['missing','0.1.1','0.99.1','k = 40'])assert(text.includes(word),word);
   }else for(const word of ['1.20.0','reducedMNN','explicit interpretation'])assert(visible.toLowerCase().includes(word.toLowerCase()),word);
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  const batch=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Batchelor \/ FastMNN \(2 papers\)$/})});assert.equal(await batch.count(),1);
  for(const word of ['upstream-adapter-validated','P040','P051','1.29.0','reducedMNN','single-matrix'])assert((await batch.innerText()).includes(word),word);
  await batch.scrollIntoViewIfNeeded();await batch.screenshot({path:path.join(output,'batchelor-current-card.png')});
  const pheno=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Rphenograph \(1 paper\)$/})});assert.equal(await pheno.count(),1);for(const word of ['missing','0.1.1','0.99.1','Artistic-2.0','k = 40'])assert((await pheno.innerText()).includes(word),word);await pheno.scrollIntoViewIfNeeded();await pheno.screenshot({path:path.join(output,'rphenograph-missing-card.png')});
  await page.goto(base+'/literature-warnings.html');const warning=page.locator('#batchelor129-required-actions');assert.equal(await warning.count(),1);assert(await warning.evaluate(e=>e.compareDocumentPosition(document.querySelector('#scdrs-required-actions'))&Node.DOCUMENT_POSITION_FOLLOWING));const text=await warning.innerText();
  for(const word of ['P040','P051','SKIP3','FAIL2','NEW','FACTORS','ALL_VALUES','0.1.1','0.99.1','245','103','407','batch_weights'])assert(text.includes(word),word);
  assert.equal(await warning.locator('strong').filter({hasText:/^SKIP3:/}).count(),1);assert.equal(await warning.locator('strong').filter({hasText:/^FAIL2:/}).count(),1);
  await warning.scrollIntoViewIfNeeded();await warning.screenshot({path:path.join(output,'current-warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warning.scrollIntoViewIfNeeded();assert(await warning.isVisible());assert(await warning.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warning.screenshot({path:path.join(output,'current-warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  assert.deepEqual(errors,[]);const result={status:'PASS_CURRENT_BATCHELOR_ADMISSION_RPHENOGRAPH_GAP_AND_PROMINENT_WARNINGS_UI',papers:250,named_tools:407,observations:2482,scoped_integrations:103,missing:245,historical_notes_labeled:true,paper_roles:['P040','P051'],new_missing_tool:'rphenograph',reported_version_literal_preserved:'0.1.1',desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
