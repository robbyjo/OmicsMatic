const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4],cdp=process.argv[5];
const root=path.resolve(__dirname,'../../..');
assert(out&&base&&cdp&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty output and owned loopback endpoints');
assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base)&&/^http:\/\/127\.0\.0\.1:\d+$/.test(cdp));
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
const primary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/fusion-catalog-sources/ROLES.json'),'utf8'));
assert.equal(primary.roles.length,6);
(async()=>{
 const browser=await chromium.connectOverCDP(cdp),context=await browser.newContext({viewport:{width:1440,height:1000}}),page=await context.newPage(),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert(await page.evaluate(()=>{const link=document.querySelector('link[rel="stylesheet"][href="assets/styles.css"]');return !!link?.sheet&&link.sheet.cssRules.length>0;}));
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2722 tool observations/);
  const notice=page.locator('#fusion-catalog-notice');assert.equal(await notice.count(),1);
  for(const pattern of [/151 scoped families/,/1,679 reviewed/,/1,043 pending/,/27 native/,/124 upstream/,/258 missing/,/22 method-overlap/,/SKIP3/,/FAIL2/,/P212/,/P108/,/isoTWAS/,/pre-fix/,/SuSiE/])assert.match(await notice.innerText(),pattern);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const role of primary.roles){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===role.paper_id).doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^FUSION(?:\s|$)/})});assert.equal(await row.count(),1,role.paper_id);
   const text=await row.innerText();assert(text.includes(role.summary),role.paper_id+' use');assert(text.includes(role.scope),role.paper_id+' scope');checked.push(role.paper_id);
   if(role.paper_id==='P183'){await row.scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,'P183-training-no-circRNA.png')});}
  }
  for(const id of ['P108','P212']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);
   assert.equal(await page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^FUSION(?:\s|$)/})}).count(),0,id+' is not an original software use');
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',27],['upstream-adapter-validated',124],['validated-family-alias',3],['method-overlap',22],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('FUSION');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FUSION \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/6 papers/);assert.match(await card.innerText(),/fusion-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391,'Warnings overflow mobile viewport');
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#fusion-catalog-notice').count(),1);assert.match(await page.locator('#fusion-catalog-notice p').first().innerText(),/SKIP3: DEPENDENT ANALYSIS SKIPPED/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_FUSION_SIX_ROLES_COHORT_EXCLUSION_BASELINE_LIMITS_COUNTS_WARNINGS_DESKTOP_MOBILE',papers:250,observations:2722,reviewed:1679,pending:1043,integrations:151,native:27,upstream:124,aliases:3,method_overlap:22,missing:258,paper_roles_checked:checked,excluded_software_rows:['P108','P212'],page_errors:errors,website_published:false,scientific_models_run:false,browser_launched_or_killed_by_this_script:false},null,2)+'\n');console.log('PASS FUSION roles, exclusions, counts and desktop/mobile warnings');
 }finally{try{await context.close();}finally{if(browser.isConnected()){const session=await browser.newBrowserCDPSession();await session.send('Browser.close').catch(error=>{if(browser.isConnected())throw error;});}await browser.close();}}
})().catch(e=>{console.error(e);process.exitCode=1;});
