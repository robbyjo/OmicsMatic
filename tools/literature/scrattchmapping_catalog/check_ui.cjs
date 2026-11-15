// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
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
  const notice=page.locator('#scrattchmapping-required-actions');
  assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  for(const [pid,words] of [
   ['P058',['Hierarchical Approximate Nearest Neighbour','unknown','v0.2','upstream-adapter-validated','SKIP3','FAIL2']],
   ['P059',['nearest cluster centroid','0.2','unreported','caller-prepared','upstream-adapter-validated','SKIP3','FAIL2']]]){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+pid+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:'scrattch.mapping'})});
   assert.equal(await row.count(),1,pid);const text=await row.innerText();
   for(const word of words)assert(text.includes(word),pid+': '+word);
   await row.screenshot({path:path.join(output,pid+'-mapping.png')});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),438);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:'scrattch.mapping (2 papers)',exact:true})});
  assert.equal(await card.count(),1);
  for(const word of ['upstream-adapter-validated','0.2','11 fresh','seven complete','four original','318','58,131','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
  await card.screenshot({path:path.join(output,'mapping-card.png')});
  const embedded=await page.locator('#review-data').textContent();const data=JSON.parse(embedded);
  assert.equal(data.summary.implementation_status_counts.missing,265);
  assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],94);
  for(const id of ['scrattch-hicat','scrattch-bigcat','arrow-r','pyarrow'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing');
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#scrattchmapping-required-actions');
  const text=await warnings.innerText();
  for(const word of ['438 named','2,679','1,180','1,499','114 scoped','265 named','P058','unknown','P059','v0.2','180 original','four package-load','pointer','RDS','SKIP3','FAIL2','FINALIZATION_ERROR.json','NEW paths','redistribution','3,706','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});
  await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warnings.screenshot({path:path.join(output,'warnings-mobile.png')});
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_MAPPING_CATALOG_ROLES_UNCERTAINTY_COUNTS_AND_WARNINGS_UI',papers:250,named_tools:438,observations:2679,reviewed_uses:1180,new_scoped_integrations:114,missing:265,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_MAPPING_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
