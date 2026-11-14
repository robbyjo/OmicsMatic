// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Check every admitted primary role, unchanged neighboring families and visible recovery.
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
  const notice=page.locator('#cell2location-required-actions');
  assert.equal(await notice.count(),1);
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const required={
   P005:['snRNA-seq','spinal-cord','separate sklearn NMF','continuous_covariates','does not establish'],
   P008:['negative-binomial','Visium','Edition, input layer','Space Ranger'],
   P070:['13763','jointly across slides','microenvironments','downstream membership rule'],
   P096:['0.7a0','log_normalised','np.arange(5,30)','5through29','250epochs','cannot establish'],
   P099:['about50','top10','above1','aggregation operation','unreported'],
   P100:['0.1.3','separately for each','untransformed/unnormalized','30000','scale_elbo=None']
  };
  for(const [id,words] of Object.entries(required)){
   const source=papers.find(p=>p.paper_id===id);assert(source);
   await page.locator('#query').fill(source.doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^cell2location/})});
   assert.equal(await row.count(),1);const text=await row.innerText();
   for(const word of [...words,'not a standalone JAR or paper reproduction'])assert(text.includes(word),id+': '+word);
   if(['P096','P100'].includes(id)){
    await row.scrollIntoViewIfNeeded();await row.screenshot({path:path.join(output,id+'-current-role.png')});
   }
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),432);
  for(const [title,words,file] of [
   ['cell2location (6 papers)',['upstream-adapter-validated','cell2location013/run.py','0.1.3','0.7a0','647','65,212','684','41 Linux','not a standalone JAR'],'cell2location-card.png'],
   ['rstatix (6 papers)',['upstream-adapter-validated','revision2','rstatix072_workflow_v2/run.py','41 Linux'],'unchanged-rstatix-card.png'],
   ['ggpubr (9 papers)',['upstream-adapter-validated','0.4.0','0.6.0','171,084'],'unchanged-ggpubr-card.png'],
   ['AllelicSeries / COAST-SS (1 paper)',['missing','1.1.2','0.1.1.2'],'unchanged-allelicseries-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});
   assert.equal(await card.count(),1);const text=await card.innerText();
   for(const word of words)assert(text.includes(word),title+': '+word);
   await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');
  const warnings=page.locator('#cell2location-required-actions'),text=await warnings.innerText();
  for(const word of ['432','2,671','1,162','1,509','110','263','20 native','90 original upstream','113','zero native','SKIP3','FAIL2','0.7a0','np.arange(5,30)','5 through 29','17 original delivered warnings','5 warning/error','unavailable, not zero','condition_counts_complete false','warning_review_required true','SUPERVISOR_ERROR.txt','WORKER_DIAGNOSTIC_ERROR.txt','process.stdout','Cell Ranger ARC','retry NEW paths','not a standalone JAR'])assert(text.includes(word),word);
  await warnings.scrollIntoViewIfNeeded();
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});
  await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());
  assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  assert.deepEqual(errors,[]);
  const result={status:'PASS_SIX_CELL2LOCATION_PRIMARY_ROLES_EDITION_LIMITS_AND_PROMINENT_RECOVERY_UI',papers:250,named_tools:432,observations:2671,reviewed_uses:1162,unreviewed_uses:1509,new_scoped_integrations:110,missing:263,source_rows_checked:6,added_reviews:6,new_upstream_tool_credit:1,new_native_credit:0,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');
  console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
