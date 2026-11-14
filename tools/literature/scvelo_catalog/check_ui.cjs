// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Verify exact roles, separately missing R interface, neighboring families and recovery visibility.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));
fs.mkdirSync(output,{recursive:false});
const root=path.resolve(__dirname,'../../..');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json')));
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);
  const notice=page.locator('#scvelo-required-actions');assert.equal(await notice.count(),1);
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const required={P051:['stochastic','2,000','50 neighbors','min_shared_counts=30','caller choice'],P070:['0.24','HTTP404','SKIP3'],P117:['0.2.4','PAGA','use_time_prior=False'],P161:['0.2.5','latent time','CellRank','seven macrostates'],P228:['R velociraptor','latent-time','unknown','SKIP3']};
  for(const [id,words] of Object.entries(required)){
   const source=papers.find(p=>p.paper_id===id);await page.locator('#query').fill(source.doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^scVelo/})});assert.equal(await row.count(),1);const text=await row.innerText();
   for(const word of words)assert(text.includes(word),id+': '+word);
   if(['P070','P228'].includes(id)){await row.scrollIntoViewIfNeeded();await row.screenshot({path:path.join(output,id+'-scvelo-role.png')});}
   if(id==='P228'){
    const wrapper=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^velociraptor/})});assert.equal(await wrapper.count(),1);const description=await wrapper.innerText();
    for(const word of ['0.3.3','unqualified','unknown','SKIP3'])assert(description.includes(word),'wrapper row: '+word);
   }
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),433);
  for(const [title,words,file] of [
   ['scVelo (5 papers)',['upstream-adapter-validated','0.2.4','0.2.5','2,124','25,165,036','0.24','velociraptor','irrecoverable','not a standalone JAR'],'scvelo-card.png'],
   ['velociraptor (1 paper)',['missing','0.3.3','SingleCellExperiment','unqualified','unknown'],'missing-wrapper-card.png'],
   ['cell2location (6 papers)',['upstream-adapter-validated','0.1.3','0.7a0','647','684'],'unchanged-cell2location-card.png'],
   ['rstatix (6 papers)',['upstream-adapter-validated','revision2','41 Linux'],'unchanged-rstatix-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#scvelo-required-actions'),text=await warnings.innerText();
  for(const word of ['433','2,672','1,168','1,504','111','91 original upstream','263','0.24','0.3.3','168','268,435,479','cannot be recovered','UserWarning','NaN','SKIP3','FAIL2','unavailable, never zero','condition_counts_complete false','warning_review_required true','SUPERVISOR_ERROR.txt','WORKER_DIAGNOSTIC_ERROR.txt','NATIVE_TEMPORARY_ERROR.txt','Cell Ranger ARC','retry NEW paths','not a standalone JAR'])assert(text.includes(word),word);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_FIVE_SCVELO_ROLES_SEPARATE_MISSING_WRAPPER_AND_PROMINENT_RECOVERY_UI',papers:250,named_tools:433,observations:2672,reviewed_uses:1168,unreviewed_uses:1504,new_scoped_integrations:111,missing:263,new_upstream_credit:1,new_R_wrapper_credit:0,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_SCVELO_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
