// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Verify current routing and all six unchanged roles with prominent recovery.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
const root=path.resolve(__dirname,'../../..'),papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json')));
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const notice=page.locator('#rstatix-revision2-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const required={P032:['test','unknown','GSVA'],P040:['two-sided','pairing','unchanged'],P054:['wilcoxon_test','wilcox_test','SKIP3','one-sided','4.2.3'],P087:['wilcox_test','BH','OLR1'],P098:['t_test','Holm','VST'],P241:['unreported','not another execution','caller choice']};
  for(const [id,words] of Object.entries(required)){
   const source=papers.find(p=>p.paper_id===id);assert(source);await page.locator('#query').fill(source.doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^rstatix/})});assert.equal(await row.count(),1);const text=await row.innerText();
   for(const word of [...words,'supervisor revision2','rstatix072_workflow_v2/run.py','unavailable, not zero','not a standalone JAR','retry NEW paths'])assert(text.includes(word),id+': '+word);
   if(['P054','P241'].includes(id)){await row.scrollIntoViewIfNeeded();await row.screenshot({path:path.join(output,id+'-current-role.png')});}
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),432);
  for(const [title,words,file] of [
   ['rstatix (6 papers)',['upstream-adapter-validated','revision2','rstatix072_workflow_v2/run.py','41 Linux','5,185','not a standalone JAR','native Java Levene'],'rstatix-card.png'],
   ['ggpubr (9 papers)',['upstream-adapter-validated','0.4.0','0.6.0','not a standalone JAR','171,084'],'unchanged-ggpubr-card.png'],
   ['QuPath (12 papers)',['upstream-adapter-validated','0.5.1','H-DAB','StarDist'],'unchanged-qupath-card.png'],
   ['AllelicSeries / COAST-SS (1 paper)',['missing','1.1.2','0.1.1.2'],'unchanged-allelicseries-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#rstatix-revision2-required-actions');const text=await warnings.innerText();
  for(const word of ['432','2,671','1,156','1,515','109','264','20 native','89 upstream','112','zero','SKIP3','FAIL2','unavailable, not zero','condition_counts_complete false','warning_review_required true','SUPERVISOR_ERROR.txt','WORKER_DIAGNOSTIC_ERROR.txt','console','Computation failed in','Cell Ranger ARC','retry NEW paths','no new original analysis'])assert(text.toLowerCase().includes(word.toLowerCase()),word);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_SIX_UNCHANGED_RSTATIX_ROLES_CURRENT_REVISION2_ROUTING_AND_PROMINENT_RECOVERY_UI',papers:250,named_tools:432,observations:2671,reviewed_uses:1156,unreviewed_uses:1515,new_scoped_integrations:109,missing:264,source_rows_checked:6,added_reviews:0,new_tool_credit:0,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
