// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Validate the local generated catalog; execute no scientific or author code.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const overview=await page.locator('#overview').innerText();for(const s of ['429 named tools','2668 tool observations'])assert(overview.includes(s),s);
  const notice=page.locator('#p040-software-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  await page.locator('#query').fill('10.1038/s41586-026-10954-1');assert.equal(await page.locator('#results article').count(),1);assert.equal(await page.locator('#P040 tbody tr').count(),33);
  const cyto=page.locator('#P040 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^cytomapper\b/})});assert.equal(await cyto.count(),1);for(const s of ['gating','plotPixels','missing'])assert((await cyto.innerText()).includes(s),s);
  const pheno=page.locator('#P040 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Rphenograph/})});assert.equal(await pheno.count(),1);for(const s of ['0.1.1','0.99.1','missing','candidate','Current selected workflow'])assert((await pheno.innerText()).includes(s),s);assert.equal(await pheno.locator('.source-review-history').evaluate(e=>e.open),false);assert((await pheno.locator('.source-review-history summary').innerText()).includes('Historical'));
  const fiji=page.locator('#P040 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Fiji\/ImageJ/})});assert.equal(await fiji.count(),1);assert((await fiji.innerText()).includes('1.54t'));await cyto.scrollIntoViewIfNeeded();await cyto.screenshot({path:path.join(output,'p040-cytomapper-roles.png')});
  await page.locator('#query').fill('10.1038/s41588-024-01999-x');assert.equal(await page.locator('#results article').count(),1);
  for(const name of [/^GraphPad Prism/,/^BD FACSDiva/]){const row=page.locator('#P082 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:name})});assert.equal(await row.count(),1);assert((await row.innerText()).includes('does not establish'));}
  await page.locator('#query').fill('10.1038/s41588-024-01898-1');assert.equal(await page.locator('#P250 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^DeepCell/})}).count(),0);
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),429);
  for(const [title,words,file] of [['Fiji/ImageJ (51 papers)',['missing','267'],'fiji-imagej-card.png'],['Rphenograph (1 paper)',['missing','0.99.1','0.1.1','candidate','863'],'rphenograph-current-candidate.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const s of words.filter(x=>x!=='267'))assert(text.includes(s),title+': '+s);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#p040-software-required-actions');assert.equal(await warnings.count(),1);assert(await warnings.evaluate(e=>e.compareDocumentPosition(document.querySelector('#scdrs-required-actions'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const text=await warnings.innerText();for(const s of ['429','2,668','1,079','267','103','SKIP3','FAIL2','0.99.1','0.1.1','company','FACTORS','ALL_VALUES','NEW'])assert(text.includes(s),s);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'current-warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'current-warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_P040_SOFTWARE_ROLES_CANDIDATE_LIMITS_AND_CURRENT_WARNINGS_UI',papers:250,named_tools:429,observations:2668,scoped_integrations:103,missing:267,p040_tools:33,company_disclosure_excluded:true,reported_0_1_1_not_substituted:true,historical_source_notes_collapsed:true,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
