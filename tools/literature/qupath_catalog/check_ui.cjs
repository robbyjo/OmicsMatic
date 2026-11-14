// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Inspect all12 source-role rows, retained edition gaps and prominent recovery instructions locally.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const overview=await page.locator('#overview').innerText();for(const word of ['432 named tools','2671 tool observations'])assert(overview.includes(word),word);
  const notice=page.locator('#qupath-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const sourceRows=[
   ['P009','10.1038/s41586-024-08334-8',['0.5.1','DAPI','H-DAB','50-micrometer','20-micrometer']],
   ['P020','10.1038/s41586-025-09869-0',['0.6.0','0.5.1','classifiers','not recovered']],
   ['P021','10.1038/s41586-026-10271-7',['Edition unreported','H-DAB','78','not another QuPath execution','Fiji']],
   ['P024','10.1038/s41586-025-09573-z',['Edition unreported','H-DAB','CLR','unknown']],
   ['P040','10.1038/s41586-026-10954-1',['0.5.1','Numeric stain vectors','area-unit','0.08/0.16/0.24','unchanged']],
   ['P049','10.1038/s41586-026-10981-y',['0.4.3','0.5.1','H-DAB','LIF']],
   ['P056','10.1038/s41586-025-09762-w',['Edition unreported','without specifying an algorithm','no missing settings']],
   ['P058','10.1038/s41586-023-06817-8',['0.4.1','0.5.1','73','not another execution','Wilcoxon']],
   ['P098','10.1038/s41588-026-02745-1',['Edition unreported','blinded','no author pipeline']],
   ['P158','10.1038/s41588-025-02368-y',['v.0.2.3 to v.0.4.3','0.5.1','StarDist','skip the dependent analysis']],
   ['P161','10.1038/s41588-026-02665-0',['Edition unreported','registration','unknown','CytoSignal']],
   ['P226','10.1016/j.ajhg.2025.08.004',['Edition unreported','2-micrometer','Nikon','H-DAB']]];
  for(const [id,doi,words] of sourceRows){
   await page.locator('#query').fill(doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^QuPath/})});assert.equal(await row.count(),1);
   const text=await row.innerText();for(const word of words)assert(text.includes(word),id+': '+word);
   // Visible selected-workflow text must not hide current limitations in collapsed history.
   const visible=await row.evaluate(e=>e.innerText);for(const word of words)assert(visible.includes(word),id+' visible: '+word);
   if(['P009','P040','P158'].includes(id)){await row.scrollIntoViewIfNeeded();await row.screenshot({path:path.join(output,id+'-current-role.png')});}
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),432);
  for(const [title,words,file] of [
   ['QuPath (12 papers)',['upstream-adapter-validated','0.5.1','private','trusted','H-DAB','1,381','72 detections','StarDist'],'qupath-card.png'],
   ['AllelicSeries / COAST-SS (1 paper)',['missing','1.1.2','0.1.1.2','not a standalone JAR'],'unchanged-allelicseries-card.png'],
   ['Rphenograph (1 paper)',['missing','0.1.1','0.99.1'],'unchanged-rphenograph-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#qupath-required-actions');const text=await warnings.innerText();
  for(const word of ['432','2,671','1,143','1,528','107','266','SKIP3','FAIL2','Cell Ranger ARC','retry NEW paths','StarDist','0.6.0','trusted','no new original analysis'])assert(text.includes(word),word);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_QUPATH_TWELVE_VISIBLE_SOURCE_ROLES_EDITIONS_AND_PROMINENT_WARNINGS_UI',papers:250,named_tools:432,observations:2671,reviewed_uses:1143,unreviewed_uses:1528,new_scoped_integrations:107,missing:266,QuPath_source_rows_checked:12,reported_versions_preserved:true,unrelated_candidate_credit_unchanged:true,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
