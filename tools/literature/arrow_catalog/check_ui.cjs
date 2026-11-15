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
  const notice=page.locator('#arrow-required-actions');
  assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const historical=page.locator('details').filter({has:page.locator('summary').filter({hasText:'Previous scrattch.mapping checkpoint (historical)'})});
  assert.equal(await historical.count(),1);assert.equal(await historical.getAttribute('open'),null);
  await page.locator('#query').fill(papers.find(p=>p.paper_id==='P059').doi);
  assert.equal(await page.locator('#results article').count(),1);
  const row=page.locator('#P059 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:'arrow (R)'})});
  assert.equal(await row.count(),1);
  for(const word of ['12.0.1','50,000','500','upstream-adapter-validated','unreported','SKIP3','FAIL2'])assert((await row.innerText()).includes(word),word);
  await row.screenshot({path:path.join(output,'P059-arrow.png')});
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  assert.equal(await page.locator('#results article').count(),438);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^arrow \(R\) \(1 paper/})});
  assert.equal(await card.count(),1);
  for(const word of ['upstream-adapter-validated','12.0.1','h32ed672_2_cpu','13 direct','10 complete','3 original','trace','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
  await card.screenshot({path:path.join(output,'arrow-card.png')});
  const data=JSON.parse(await page.locator('#review-data').textContent());
  assert.equal(data.summary.implementation_status_counts.missing,264);
  assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],95);
  assert.equal(data.summary.implementation_status_counts['native-subset-validated'],20);
  for(const id of ['scrattch-hicat','scrattch-bigcat','pyarrow'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing');
  assert.equal(data.tools.find(t=>t.id==='scrattch-mapping').implementation_status,'upstream-adapter-validated');
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#arrow-required-actions');
  const text=await warnings.innerText();
  for(const word of ['438 named','2,679','1,180','1,499','115 scoped','264 named','P059','12.0.1','50,000','500','h32ed672_2_cpu','h0de2578_50_cpu','INVALIDATION.json','trace','integer64','SKIP3','FAIL2','FINALIZATION_ERROR.json','NEW writable paths','LZO','3,706','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
  await warnings.screenshot({path:path.join(output,'warnings-mobile.png')});
  assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_ARROW_CATALOG_PRIMARY_ROLES_SOURCE_BUILD_COUNTS_AND_WARNINGS_UI',papers:250,named_tools:438,observations:2679,reviewed_uses:1180,new_scoped_integrations:115,missing:264,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_ARROW_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
