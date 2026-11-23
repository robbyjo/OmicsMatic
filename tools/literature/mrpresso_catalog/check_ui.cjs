const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4],cdp=process.argv[5];
const root=path.resolve(__dirname,'../../..');
assert(out&&base&&cdp&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW output and owned loopback endpoints');
assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base)&&/^http:\/\/127\.0\.0\.1:\d+$/.test(cdp));
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
const primary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/mrpresso-catalog-sources/ROLES.json'),'utf8'));
assert.equal(primary.roles.length,6);
(async()=>{
 const browser=await chromium.connectOverCDP(cdp),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[];
 page.on('pageerror',error=>errors.push(error.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert(await page.evaluate(()=>{const link=document.querySelector('link[rel="stylesheet"][href="assets/styles.css"]');return !!link?.sheet&&link.sheet.cssRules.length>0;}));
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2722 tool observations/);
  const notice=page.locator('#mrpresso-catalog-notice');assert.equal(await notice.count(),1);
  for(const pattern of [/153 scoped families/,/1,691 reviewed/,/1,031 pending/,/28 native/,/125 upstream/,/SKIP3/,/FAIL2/,/above one/])assert.match(await notice.innerText(),pattern);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const details=notice.locator('#mrpresso-source-details');await details.evaluate(node=>node.open=true);
  for(const pattern of [/258 missing/,/20 method-overlap/,/P135/,/P150/,/P189/,/P224/,/P177/])assert.match(await notice.innerText(),pattern);
  await details.evaluate(node=>node.open=false);
  const checked=[];
  for(const role of primary.roles){
   await page.locator('#query').fill(papers.find(x=>x.paper_id===role.paper_id).doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child .mrpresso-version')});assert.equal(await row.count(),1,role.paper_id);
   assert.equal(await row.locator('td').first().evaluate(cell=>cell.firstChild.textContent),'MR-PRESSO');
   const text=await row.innerText();assert(text.includes(role.summary),role.paper_id+' use');assert(text.includes(role.scope_limit),role.paper_id+' scope');
   assert.equal(await row.locator('.mrpresso-version').innerText(),'edition, seed and simulation count not reported');checked.push(role.paper_id);
   if(role.paper_id==='P135')assert.match(text,/one fracture outcome/);
   if(role.paper_id==='P150')assert.match(text,/global test is nonsignificant/);
   if(role.paper_id==='P189')assert.match(text,/nonsignificant/);
   if(role.paper_id==='P224')assert.match(text,/primary MR-RAPS/);
   if(['P135','P150','P189','P224'].includes(role.paper_id)){await row.scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,role.paper_id+'-role.png')});}
  }
  await page.locator('#query').fill(papers.find(x=>x.paper_id==='P177').doi);assert.equal(await page.locator('#P177 .mrpresso-version').count(),0,'Discussion-only method must not become execution');
  await page.locator('#query').fill(papers.find(x=>x.paper_id==='P009').doi);
  const oldEdition=page.locator('#P009 .squidpy-version');assert.equal(await oldEdition.count(),1);assert.match(await oldEdition.innerText(),/broader software paragraph; near-name edition unreported/);
  for(const id of ['P108','P212']){
   await page.locator('#query').fill(papers.find(x=>x.paper_id===id).doi);
   assert.equal(await page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^FUSION(?:\s|$)/})}).count(),0,id+' original exclusion/baseline');
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',28],['upstream-adapter-validated',125],['validated-family-alias',3],['method-overlap',20],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('native-subset-validated');await page.locator('#query').fill('MR-PRESSO');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MR-PRESSO \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/6 papers/);assert.match(await card.innerText(),/mrpresso-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391,'Warnings overflow mobile viewport');
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#mrpresso-catalog-notice').count(),1);assert.match(await page.locator('#mrpresso-catalog-notice p').first().innerText(),/SKIP3: dependent analysis skipped/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_MRPRESSO_SIX_ROLES_UNKNOWN_SETTINGS_NATIVE_COUNTS_WARNINGS_DESKTOP_MOBILE',papers:250,observations:2722,reviewed:1691,pending:1031,integrations:153,native:28,upstream:125,aliases:3,method_overlap:20,missing:258,paper_roles_checked:checked,discussion_only_not_added:['P177'],prior_squidpy_renderer_preserved:true,prior_excluded_software_rows:['P108','P212'],page_errors:errors,website_published:false,scientific_models_run:false,browser_launched_or_killed_by_this_script:false},null,2)+'\n');console.log('PASS MR-PRESSO six roles, unknown settings, native counts and desktop/mobile warnings');
 }finally{try{await context.close();}finally{if(browser.isConnected()){const session=await browser.newBrowserCDPSession();await session.send('Browser.close').catch(error=>{if(browser.isConnected())throw error;});}await browser.close();}}
})().catch(error=>{console.error(error);process.exitCode=1;});
