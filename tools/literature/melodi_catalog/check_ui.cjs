// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
try{
 await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
 const notice=page.locator('#melodi-required-actions');assert.equal(await notice.count(),1);assert(await notice.isVisible());assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
 await page.locator('#query').fill('10.1038/s41588-024-01900-w');assert.equal(await page.locator('#results article').count(),1);
 const rows=page.locator('#P169 tbody tr'),melodi=rows.filter({has:page.locator('td:first-child').filter({hasText:/^MELODI Presto/})});assert.equal(await melodi.count(),1);
 for(const word of ['literature','colorectal cancer','upstream-adapter-validated','SKIP3','FAIL2','live-unversioned'])assert((await melodi.innerText()).includes(word),word);
 for(const name of ['OncoScore','oncoEnrichR','IntOGen','HaploReg'])assert.equal(await rows.filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+name,'i')})}).count(),1,name);
 await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),445);
 const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MELODI Presto \(1 paper/})});assert.equal(await card.count(),1);await card.screenshot({path:path.join(output,'melodi-card.png')});
 const data=JSON.parse(await page.locator('#review-data').textContent());assert.equal(data.summary.implementation_status_counts.missing,265);assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],101);assert.equal(data.summary.implementation_status_counts['native-subset-validated'],20);assert.equal(data.papers.flatMap(p=>p.tools).length,2689);
 assert.equal(data.tools.filter(t=>t.id==='presto').length,0);assert.equal(data.tools.find(t=>t.id==='melodi-presto').native_analytical_credit_added,0);
 for(const id of ['oncoscore','oncoenrichr','intogen','haploreg'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing',id);
 for(const id of ['P038','P157'])assert.equal(data.papers.find(p=>p.paper_id===id).tools.filter(t=>['presto','melodi-presto'].includes(t.tool_id)).length,0);
 await page.goto(base+'/literature-warnings.html');const warning=page.locator('#melodi-required-actions'),text=await warning.innerText();
 for(const word of ['445 named','2,689','1,195','1,494','121 scoped','265 named','not the presto','3April2024','live-unversioned','COADREAD','SKIP3','FAIL2','COMPLETED.json','response.bin','Cell Ranger/ARC'])assert(text.includes(word),word);
 await warning.screenshot({path:path.join(output,'warnings-desktop.png')});await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await page.locator('#melodi-runtime-guide summary').click();assert.equal(await page.locator('#melodi-runtime-guide pre:visible').count(),2);assert(await warning.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
 await warning.screenshot({path:path.join(output,'warnings-mobile.png')});assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_MELODI_IDENTITY_COMPANION_ROLES_COUNTS_LIVE_SNAPSHOT_FAILURE_RECOVERY_AND_MOBILE_COMMANDS_UI',papers:250,named_families:445,observations:2689,reviewed_uses:1195,new_scoped_integrations:121,missing:265,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
}finally{await browser.close();}})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_MELODI_CATALOG_UI_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
