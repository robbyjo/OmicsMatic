// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
const root=path.resolve(__dirname,'../../..');const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json')));
(async()=>{const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
page.on('pageerror',e=>errors.push(e.message));try{
 await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
 assert.equal(await page.locator('#results article').count(),250);const notice=page.locator('#celldex114-required-actions');assert.equal(await notice.count(),1);assert(await notice.isVisible());
 assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
 const history=page.locator('details').filter({has:page.locator('summary').filter({hasText:'Previous SingleR checkpoint (historical)'})});assert.equal(await history.count(),1);assert.equal(await history.getAttribute('open'),null);
 await page.locator('#query').fill(papers.find(p=>p.paper_id==='P051').doi);assert.equal(await page.locator('#results article').count(),1);
 const row=page.locator('#P051 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^celldex/})});assert.equal(await row.count(),1);
 for(const word of ['1.14.0','upstream-adapter-validated','MouseRNAseq','LOCAL','SKIP3','FAIL2'])assert((await row.innerText()).includes(word),word);
 await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),441);
 const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^celldex \(1 paper/})});assert.equal(await card.count(),1);
 for(const word of ['1.14.0','upstream-adapter-validated','12complete','4original','4469','private','SKIP3','FAIL2'])assert((await card.innerText()).includes(word),word);
 await card.screenshot({path:path.join(output,'celldex-card.png')});
 const data=JSON.parse(await page.locator('#review-data').textContent());assert.equal(data.summary.implementation_status_counts.missing,263);assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],99);assert.equal(data.summary.implementation_status_counts['native-subset-validated'],20);
 assert.equal(data.papers.flatMap(p=>p.tools).length,2682);for(const id of ['spotlight','bd-rhapsody'])assert.equal(data.tools.find(t=>t.id===id).implementation_status,'missing');
 const primary=data.papers.find(p=>p.paper_id==='P051').tools.find(t=>t.tool_id==='celldex');assert.equal(primary.version_reported_near_name,'1.14.0');
 assert.equal(data.tools.find(t=>t.id==='celldex').author_reference_snapshots_established,false);assert.equal(data.tools.find(t=>t.id==='celldex').reference_matrices_redistributed,false);
 await page.goto(base+'/literature-warnings.html');const warning=page.locator('#celldex114-required-actions'),text=await warning.innerText();
 for(const word of ['441 named','2,682','1,187','1,495','119 scoped','263 named','LOCAL','12 complete','four original','seven returns','nine original','4,469','SKIP3','FAIL2','COMPLETED.json','Cell Ranger/ARC'])assert(text.includes(word),word);
 await warning.screenshot({path:path.join(output,'warnings-desktop.png')});await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
 await page.locator('#celldex114-runtime-guide summary').click();assert.equal(await page.locator('#celldex114-runtime-guide pre:visible').count(),2);assert(await warning.evaluate(e=>e.scrollWidth<=e.clientWidth+1));
 await warning.screenshot({path:path.join(output,'warnings-mobile.png')});assert.deepEqual(errors,[]);
 fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_CELLDEX114_PRIMARY_ROLE_COUNTS_LOCAL_OPTIONS_PRIVATE_RECORD_SCOPE_AND_MOBILE_COMMANDS_UI',papers:250,named_tools:441,observations:2682,reviewed_uses:1187,new_scoped_integrations:119,missing:263,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
}finally{await browser.close();}})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_CELLDEX114_CATALOG_UI_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
