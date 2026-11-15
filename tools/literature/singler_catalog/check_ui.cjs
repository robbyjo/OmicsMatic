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
  const notice=page.locator('#singler-required-actions');
  assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const history=page.locator('details').filter({has:page.locator('summary').filter({hasText:'Previous HICAT checkpoint (historical)'})});
  assert.equal(await history.count(),1);assert.equal(await history.getAttribute('open'),null);
  for(const id of ['P051','P082','P192','P233']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:'SingleR'})});
   assert.equal(await row.count(),1);
   const text=await row.innerText();
   for(const word of ['upstream-adapter-validated','SKIP3','FAIL2'])assert(text.includes(word),id+':'+word);
   if(id==='P233')for(const word of ['2.41','unresolved','unpruned'])assert(text.includes(word),word);
   if(id==='P051')for(const word of ['2.8.0','2.6.0','celldex'])assert(text.includes(word),word);
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),441);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^SingleR \(4 papers/})});
  assert.equal(await card.count(),1);
  for(const word of ['upstream-adapter-validated','250','232','18 original','2.41','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
  await card.screenshot({path:path.join(output,'singler-card.png')});
  const data=JSON.parse(await page.locator('#review-data').textContent());
  assert.equal(data.summary.implementation_status_counts.missing,264);
  assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],98);
  assert.equal(data.summary.implementation_status_counts['native-subset-validated'],20);
  for(const id of ['celldex','spotlight','bd-rhapsody']){
   assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing');
   assert.deepEqual(data.tools.find(t=>t.id===id).papers,['P051']);
  }
  const literal=data.papers.find(p=>p.paper_id==='P233').tools.find(t=>t.tool_id==='singler');
  assert.equal(literal.version_reported_near_name,'2.41');
  assert.equal(data.papers.flatMap(p=>p.tools).length,2682);
  await page.goto(base+'/literature-warnings.html');const warning=page.locator('#singler-required-actions');
  const text=await warning.innerText();
  for(const word of ['441 named','2,682','1,187','1,495','118 scoped','264 named','2.41','232 complete','18 original','SKIP3','FAIL2','COMPLETED.json','NEW paths','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warning.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await warning.isVisible());assert(await warning.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await page.locator('#singler-runtime-guide summary').click();
  assert.equal(await page.locator('#singler-runtime-guide pre:visible').count(),2);
  assert((await page.locator('#singler-runtime-guide').innerText()).includes('--allow-install --allow-download'));
  assert(await warning.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warning.screenshot({path:path.join(output,'warnings-mobile.png')});
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_SINGLER_CATALOG_DISTINCT_RELEASES_UNRESOLVED_LITERAL_MISSING_FAMILIES_COUNTS_AND_WARNINGS_UI',papers:250,named_tools:441,observations:2682,reviewed_uses:1187,new_scoped_integrations:118,missing:264,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_SINGLER_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
