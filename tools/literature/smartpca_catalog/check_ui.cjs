const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4],cdp=process.argv[5];
const root=path.resolve(__dirname,'../../..');
assert(out&&base&&cdp&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW output and owned loopback endpoints');
assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base)&&/^http:\/\/127\.0\.0\.1:\d+$/.test(cdp));
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
const primary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/smartpca-catalog-sources/ROLES.json'),'utf8'));
assert.equal(primary.roles.length,6);
(async()=>{
 const browser=await chromium.connectOverCDP(cdp),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert(await page.evaluate(()=>{const link=document.querySelector('link[rel="stylesheet"][href="assets/styles.css"]');return !!link?.sheet&&link.sheet.cssRules.length>0;}));
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2722 tool observations/);
  const notice=page.locator('#smartpca-catalog-notice');assert.equal(await notice.count(),1);
  for(const pattern of [/154 scoped families/,/1,697 reviewed/,/1,025 pending/,/29 native/,/125 upstream/,/SKIP3/,/FAIL2/,/Negative FST/])assert.match(await notice.innerText(),pattern);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const details=notice.locator('#smartpca-source-details');await details.evaluate(node=>node.open=true);
  for(const pattern of [/258 missing/,/19 method-overlap/,/P019/,/P041/,/P045/,/P071/,/P180/,/P229/])assert.match(await notice.innerText(),pattern);
  await details.evaluate(node=>node.open=false);
  const checked=[];
  for(const role of primary.roles){
   await page.locator('#query').fill(papers.find(x=>x.paper_id===role.paper_id).doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child .smartpca-version')});assert.equal(await row.count(),1,role.paper_id);
   const text=await row.innerText();assert(text.includes(role.summary),role.paper_id+' use');assert(text.includes(role.scope_limit),role.paper_id+' scope');
   const contexts=await row.locator('.smartpca-context-version').allInnerTexts();
   assert.deepEqual(contexts,role.roles.map(r=>r.context+': '+(r.reported_package_edition===null?'package edition not reported':'reported package edition '+r.reported_package_edition)+(r.reported_program_version===null?'':'; reported program identifier '+r.reported_program_version)),role.paper_id+' contexts');
   if(role.paper_id==='P019'){assert.match(contexts[0],/MVP.*edition 6$/);assert.match(contexts[1],/ROSMAP.*edition not reported$/);}
   if(role.paper_id==='P180'){assert.match(contexts[0],/PCA.*edition 7.2.0$/);assert.match(contexts[1],/FST.*edition not reported$/);assert.match(text,/its application to FST is not established/);}
   if(role.paper_id==='P229'){assert.match(contexts[0],/package edition not reported; reported program identifier 13050$/);assert(!contexts[0].includes('edition 13050'));}
   if(['P045','P071'].includes(role.paper_id))assert(contexts.every(c=>c.endsWith('edition not reported')));
   checked.push(role.paper_id);
   if(['P019','P180','P229'].includes(role.paper_id)){await row.scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,role.paper_id+'-role.png')});}
  }
  await page.locator('#query').fill(papers.find(x=>x.paper_id==='P105').doi);
  assert.equal(await page.locator('#P105 .mrpresso-version').innerText(),'edition, seed and simulation count not reported');
  await page.locator('#query').fill(papers.find(x=>x.paper_id==='P009').doi);
  assert.match(await page.locator('#P009 .squidpy-version').innerText(),/broader software paragraph; near-name edition unreported/);
  for(const id of ['P108','P212']){
   await page.locator('#query').fill(papers.find(x=>x.paper_id===id).doi);
   assert.equal(await page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^FUSION(?:\s|$)/})}).count(),0,id+' original exclusion/baseline');
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',29],['upstream-adapter-validated',125],['validated-family-alias',3],['method-overlap',19],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('native-subset-validated');await page.locator('#query').fill('smartpca');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^smartpca \(/i})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/6 papers/);assert.match(await card.innerText(),/smartpca-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391,'Warnings overflow mobile viewport');
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#smartpca-catalog-notice').count(),1);assert.match(await page.locator('#smartpca-catalog-notice p').first().innerText(),/SKIP3: dependent analysis skipped/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_SMARTPCA_SIX_CONTEXT_ROLES_NATIVE_COUNTS_WARNINGS_DESKTOP_MOBILE',papers:250,observations:2722,reviewed:1697,pending:1025,integrations:154,native:29,upstream:125,aliases:3,method_overlap:19,missing:258,paper_roles_checked:checked,prior_mrpresso_squidpy_renderers_preserved:true,prior_excluded_software_rows:['P108','P212'],page_errors:errors,website_published:false,scientific_models_run:false,browser_launched_or_killed_by_this_script:false},null,2)+'\n');console.log('PASS smartpca six source contexts, native counts and desktop/mobile warnings');
 }finally{try{await context.close();}finally{if(browser.isConnected()){const session=await browser.newBrowserCDPSession();await session.send('Browser.close').catch(error=>{if(browser.isConnected())throw error;});}await browser.close();}}
})().catch(error=>{console.error(error);process.exitCode=1;});
