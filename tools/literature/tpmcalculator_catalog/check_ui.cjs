// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert/strict');
const [modules,executablePath,output,siteArg]=process.argv.slice(2),site=path.resolve(siteArg);
const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const server=http.createServer((request,response)=>{try{
  const file=path.resolve(site,'.'+decodeURIComponent(new URL(request.url,'http://localhost').pathname));
  if(!file.startsWith(site+path.sep)){response.writeHead(403);response.end();return;}
  response.setHeader('Content-Type',file.endsWith('.html')?'text/html; charset=utf-8':file.endsWith('.css')?'text/css':file.endsWith('.js')?'text/javascript':'application/octet-stream');
  response.end(fs.readFileSync(file));
 }catch(error){response.writeHead(404);response.end();}});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 let browser;
 try{
  browser=await chromium.launch({executablePath,headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));const base='http://127.0.0.1:'+server.address().port;
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);
  const notice=page.locator('#tpmcalculator-required-actions');assert.equal(await notice.count(),1);assert(await notice.isVisible());
  assert(await notice.evaluate(element=>element.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  await page.locator('#query').fill('10.1038/s41586-023-06821-y');assert.equal(await page.locator('#results article').count(),1);
  const rows=page.locator('#P050 tbody tr');
  const tpm=rows.filter({has:page.locator('td:first-child').filter({hasText:/^TPMCalculator/})});assert.equal(await tpm.count(),1);
  for(const word of ['transcripts','native-subset-validated','unknown','SKIP3','FAIL2'])assert((await tpm.innerText()).includes(word),word);
  const idep=rows.filter({has:page.locator('td:first-child').filter({hasText:/^iDEP/})});assert.equal(await idep.count(),1);
  for(const word of ['iDEP.96','DESeq2','missing','default settings'])assert((await idep.innerText()).includes(word),word);
  await tpm.screenshot({path:path.join(output,'P050-tpmcalculator-row.png')});
  await idep.screenshot({path:path.join(output,'P050-idep-row.png')});
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),446);
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^TPMCalculator \(1 paper/})});assert.equal(await card.count(),1);
  await card.screenshot({path:path.join(output,'tpmcalculator-card.png')});
  const data=JSON.parse(await page.locator('#review-data').textContent());
  assert.equal(data.summary.implementation_status_counts.missing,265);
  assert.equal(data.summary.implementation_status_counts['native-subset-validated'],21);
  assert.equal(data.summary.implementation_status_counts['upstream-adapter-validated'],101);
  assert.equal(data.papers.flatMap(p=>p.tools).length,2690);
  assert.equal(data.tools.find(t=>t.id==='idep').new_scoped_integration_credit,0);
  assert.equal(data.tools.find(t=>t.id==='idep').implementation_status,'missing');
  assert.equal(data.tools.find(t=>t.id==='tpmcalculator').native_analytical_credit_added,1);
  assert.equal(data.tools.find(t=>t.id==='tpmcalculator').root_dispatch_available,false);
  await page.goto(base+'/literature-warnings.html');const warning=page.locator('#tpmcalculator-required-actions'),text=await warning.innerText();
  for(const word of ['446 named','2,690','1,197','1,493','122 scoped','21 native','265 named','iDEP.96','SKIP3','FAIL2','COMPLETED.json','worker/stdout.bin','Cell Ranger/ARC'])assert(text.includes(word),word);
  await warning.screenshot({path:path.join(output,'warnings-desktop.png')});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  await page.locator('#tpmcalculator-runtime-guide summary').click();assert.equal(await page.locator('#tpmcalculator-runtime-guide pre:visible').count(),2);
  assert(await warning.evaluate(element=>element.scrollWidth<=element.clientWidth+1));
  await warning.screenshot({path:path.join(output,'warnings-mobile.png')});assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify({status:'PASS_NATIVE_TPMCALCULATOR_IDEP_COUNTS_SOURCE_ROLES_WARNINGS_RECOVERY_AND_MOBILE_COMMANDS_UI',papers:250,named_families:446,observations:2690,reviewed_uses:1197,new_scoped_integrations:122,native_integrations:21,missing:265,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false},null,2)+'\n');
 }finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{fs.writeFileSync(path.join(output,'FAILURE.json'),JSON.stringify({status:'FAIL2_TPMCALCULATOR_CATALOG_UI_UNADMITTED',error:String(error),stack:error.stack},null,2)+'\n');console.error(error);process.exitCode=2;});
