// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Local rendered-data check only; execute no scientific code and deploy no public page.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const overview=await page.locator('#overview').innerText();for(const s of ['430 named tools','2669 tool observations'])assert(overview.includes(s),s);
  const notice=page.locator('#imagej-catalog-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  await page.locator('#query').fill('10.1038/s41586-024-08493-8');assert.equal(await page.locator('#results article').count(),1);const bleach=page.locator('#P026 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Bleach Correction/})});assert.equal(await bleach.count(),1);for(const s of ['unreported','2.1.0','Fast4DReg','intensity'])assert((await bleach.innerText()).includes(s),s);
  await page.locator('#query').fill('10.1038/s41586-024-08348-2');const literal=page.locator('#P006 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Fiji\/ImageJ/})});assert((await literal.innerText()).includes('1.8.0 (literal reported value)'));assert(!(await literal.innerText()).includes('Java1.8'));
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),430);
  for(const [title,words,file] of [['Fiji/ImageJ (51 papers)',['1.54t','353','plugins'],'imagej-card.png'],['Bleach Correction (1 paper)',['2.1.0','1.53t','1,048','native'],'bleach-card.png'],['Rphenograph (1 paper)',['missing','0.1.1','0.99.1'],'unchanged-rphenograph-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);for(const word of words)assert((await card.innerText()).includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#imagej-catalog-required-actions');const text=await warnings.innerText();for(const word of ['430','2,669','1,130','105','266','SKIP3','FAIL2','Histogram matching must not be used','standalone','semantic review remains open'])assert(text.includes(word),word);await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_IMAGEJ_AND_BLEACH_CURRENT_SCOPE_VERSIONS_COUNTS_AND_WARNINGS_UI',papers:250,named_tools:430,observations:2669,reviewed_uses:1130,new_scoped_integrations:105,missing:266,reported_literal_versions_preserved:true,missing_Rphenograph_edition_not_substituted:true,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
