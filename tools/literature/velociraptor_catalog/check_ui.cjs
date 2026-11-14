// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Verify current availability, unknown source settings and prominent failure recovery.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));
fs.mkdirSync(output,{recursive:false});
const root=path.resolve(__dirname,'../../..');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json')));
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const notice=page.locator('#velociraptor-required-actions');assert.equal(await notice.count(),1);assert(await notice.isVisible());assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  await page.locator('#query').fill(papers.find(p=>p.paper_id==='P228').doi);assert.equal(await page.locator('#results article').count(),1);
  const wrapper=page.locator('#P228 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^velociraptor/})});assert.equal(await wrapper.count(),1);
  for(const word of ['0.3.3','unknown','SKIP3','FAIL2','unresolved'])assert((await wrapper.innerText()).includes(word),word);await wrapper.screenshot({path:path.join(output,'P228-wrapper-role.png')});
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),433);
  for(const [title,words,file] of [
   ['velociraptor (1 paper)',['upstream-adapter-validated','1.20.0','0.3.3','2,457,351','36 controls','unknown','unresolved'],'velociraptor-card.png'],
   ['scVelo (5 papers)',['upstream-adapter-validated','0.2.4','0.2.5','0.24','irrecoverable'],'unchanged-scvelo-card.png'],
   ['cell2location (6 papers)',['upstream-adapter-validated','0.1.3','0.7a0'],'unchanged-cell2location-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#velociraptor-required-actions'),text=await warnings.innerText();
  for(const word of ['112 scoped','92 original upstream','262 named','1,168','1,504','SKIP3','FAIL2','unresolved','unavailable, never zero','FINALIZATION_ERROR.json','NEW writable','Cell Ranger/ARC','first frozen dossier remains in place'])assert(text.includes(word),word);
  await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_WRAPPER_CATALOG_ROLE_AVAILABILITY_AND_PROMINENT_RECOVERY_UI',papers:250,named_tools:433,reviewed_uses:1168,new_scoped_integrations:112,missing:262,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{await browser.close();}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_WRAPPER_CATALOG_BROWSER_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
