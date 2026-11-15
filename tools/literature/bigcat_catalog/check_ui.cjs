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
  const notice=page.locator('#bigcat-required-actions');
  assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const historical=page.locator('details').filter({has:page.locator('summary').filter({hasText:'Previous R Arrow checkpoint (historical)'})});
  assert.equal(await historical.count(),1);assert.equal(await historical.getAttribute('open'),null);
  for(const paperId of ['P058','P059']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===paperId).doi);
   assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+paperId+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:'scrattch.bigcat'})});
   assert.equal(await row.count(),1);
   const text=await row.innerText();
   for(const word of ['upstream-adapter-validated','SKIP3','FAIL2','Original'])assert(text.includes(word),paperId+' '+word);
   for(const word of paperId==='P058'?['1,871','15','follows integration','unknown']:['0.0.5','50000','500','8460','534','all training cells per centroid'])assert(text.includes(word),paperId+' '+word);
   await row.screenshot({path:path.join(output,paperId+'-bigcat.png')});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),438);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^scrattch\.bigcat \(2 papers/})});
  assert.equal(await card.count(),1);
  for(const word of ['upstream-adapter-validated','0.0.5','19 complete','six original','3198','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
  await card.screenshot({path:path.join(output,'bigcat-card.png')});
  const data=JSON.parse(await page.locator('#review-data').textContent());
  assert.equal(data.summary.implementation_status_counts.missing,263);
  assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],96);
  assert.equal(data.summary.implementation_status_counts['native-subset-validated'],20);
  for(const id of ['scrattch-hicat','pyarrow'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing');
  for(const id of ['scrattch-mapping','arrow-r'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'upstream-adapter-validated');
  for(const [id,version] of [['P058',null],['P059','0.0.5']])assert.equal(data.papers.find(p=>p.paper_id===id).tools.find(t=>t.tool_id==='scrattch-bigcat').version_reported_near_name,version);
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#bigcat-required-actions');
  const text=await warnings.innerText();
  for(const word of ['438 named','2,679','1,180','1,499','116 scoped','263 named','P058','P059','0.0.5','19 complete','six original','3,198','follows integration','all training cells','live R-object','SKIP3','FAIL2','COMPLETED.json','NEW writable','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warnings.screenshot({path:path.join(output,'warnings-mobile.png')});
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_BIGCAT_CATALOG_TWO_PRIMARY_ROLES_COUNTS_AND_WARNINGS_UI',papers:250,named_tools:438,observations:2679,reviewed_uses:1180,new_scoped_integrations:116,missing:263,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_BIGCAT_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
