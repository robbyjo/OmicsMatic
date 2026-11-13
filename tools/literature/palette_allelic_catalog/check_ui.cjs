// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Check local rendered records and prominent recovery text; deploy no public page.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const overview=await page.locator('#overview').innerText();for(const word of ['432 named tools','2671 tool observations'])assert(overview.includes(word),word);
  const notice=page.locator('#palette-allelic-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  for(const [doi,id,name,words] of [
   ['10.1038/s41586-025-09794-2','P054','wesanderson',['0.3.7','MFOD1','viridisLite','no palette']],
   ['10.1016/j.ajhg.2025.09.012','P204','AllelicSeries / COAST-SS',['1.1.2','0.1.1.2','full in-sample LD','quantitative','unresolved']]]){
   await page.locator('#query').fill(doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+name.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'))})});assert.equal(await row.count(),1);
   const text=await row.innerText();for(const word of words)assert(text.toLowerCase().includes(word.toLowerCase()),id+': '+word);
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),432);
  for(const [title,words,file] of [
   ['wesanderson (1 paper)',['native-subset-validated','0.3.7','6,108','viridisLite','R graphics'],'wesanderson-card.png'],
   ['AllelicSeries / COAST-SS (1 paper)',['missing','1.1.2','0.1.1.2','not a standalone JAR','1,026','SKAT-O'],'allelicseries-candidate-card.png'],
   ['Rphenograph (1 paper)',['missing','0.1.1','0.99.1'],'unchanged-rphenograph-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#palette-allelic-required-actions');const text=await warnings.innerText();for(const word of ['432','2,671','1,132','106','267','SKIP3','FAIL2','zero credit','Cell Ranger ARC','retry NEW paths','quantification'])assert(text.includes(word),word);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_PALETTE_AND_ALLELIC_COUNTS_SOURCE_ROLES_VERSIONS_AND_PROMINENT_WARNINGS_UI',papers:250,named_tools:432,observations:2671,reviewed_uses:1132,new_scoped_integrations:106,missing:267,allelic_candidate_credited:false,reported_versions_preserved:true,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
