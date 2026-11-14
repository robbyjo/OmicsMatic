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
  const notice=page.locator('#doubletfinder-required-actions');
  assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  for(const [pid,tool,words] of [
   ['P059','scrattch.hicat',['1.0.9','strictly greater than 0.3','missing']],
   ['P058','scrattch.mapping',['Hierarchical Approximate Nearest Neighbour','missing']],
   ['P059','scrattch.mapping',['0.2','nearest cluster centroid','missing']],
   ['P059','arrow (R)',['12.0.1','50,000','missing']],
   ['P250','PyArrow',['14.0.2','development','does not establish','missing']],
   ['P096','Scrublet',['Scrublet 0.2,','INTERSECTION']],
   ['P096','DoubletFinder',['3.0','unresolved','_v3']]]){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+pid+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:tool})});
   assert.equal(await row.count(),1,pid+'/'+tool);const text=await row.innerText();
   for(const word of words)assert(text.includes(word),pid+'/'+tool+': '+word);
   await row.screenshot({path:path.join(output,pid+'-'+tool.replace(/[^a-zA-Z0-9]/g,'_')+'.png')});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),438);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:'DoubletFinder (5 papers)',exact:true})});
  assert.equal(await card.count(),1);
  for(const word of ['upstream-adapter-validated','2.0.6','388','22 complete','3,706','Convergence is not qualified','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
  await card.screenshot({path:path.join(output,'DoubletFinder-card.png')});
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#doubletfinder-required-actions');
  const text=await warnings.innerText();
  for(const word of ['438 named','2,679','1,180','1,499','113 scoped','266 named','SCT convergence is not qualified','3,706','12 iteration-limit','SKIP3','FAIL2','FINALIZATION_ERROR.json','NEW writable','Script_5.py','line 21','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});
  await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warnings.screenshot({path:path.join(output,'warnings-mobile.png')});
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_DOUBLET_FINDER_CATALOG_SOURCE_ROLES_COUNTS_AND_RECOVERY_UI',papers:250,named_tools:438,observations:2679,reviewed_uses:1180,new_scoped_integrations:113,missing:266,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_DOUBLET_FINDER_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
