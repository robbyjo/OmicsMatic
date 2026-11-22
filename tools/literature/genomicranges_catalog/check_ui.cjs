// Connect to an externally owned browser and request its graceful CDP shutdown.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4],cdp=process.argv[5];
const root=path.resolve(__dirname,'../../..');
assert(out&&base&&cdp&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty output,local report URL and owned CDP URL');
assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(base)&&/^http:\/\/127\.0\.0\.1:\d+$/.test(cdp),'Use loopback endpoints only');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
const roles=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/genomicranges-catalog-sources/CENSUS.json'),'utf8')).roles;
assert.equal(roles.length,7);
(async()=>{
 const browser=await chromium.connectOverCDP(cdp),context=await browser.newContext({viewport:{width:1440,height:1000}});
 const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert(await page.evaluate(()=>{const link=document.querySelector('link[rel="stylesheet"][href="assets/styles.css"]');return !!link?.sheet&&link.sheet.cssRules.length>0;}),'Shared site stylesheet must be loaded for a styled preview');
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2723 tool observations/);
  const notice=page.locator('#genomicranges-catalog-notice');assert.equal(await notice.count(),1);
  for(const pattern of [/149 scoped families/,/1666 reviewed/,/1057 pending/,/27 native/,/122 upstream/,/258 missing/,/24 method-overlap/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/P039/,/R4\.4\.2/,/pre-fix/])assert.match(await notice.innerText(),pattern);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const role of roles){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===role.paper_id).doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^GenomicRanges(?:\s*reported\s+.+)?$/i})});assert.equal(await row.count(),1,role.paper_id);
   const sourceUse=papers.find(p=>p.paper_id===role.paper_id).tools.find(t=>t.tool_id==='genomicranges');if(sourceUse.version_reported_near_name)assert((await row.locator('td').first().innerText()).includes('reported '+sourceUse.version_reported_near_name));
   const text=await row.innerText();assert(text.includes(role.usage_summary),role.paper_id+' summary');assert(text.includes(role.implementation_scope_note),role.paper_id+' scope');checked.push(role.paper_id);
   if(role.paper_id==='P094'){await row.scrollIntoViewIfNeeded();await page.screenshot({path:path.join(out,'P094-role.png')});}
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',27],['upstream-adapter-validated',122],['validated-family-alias',3],['method-overlap',24],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('GenomicRanges');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^GenomicRanges \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/7 papers/);assert.match(await card.innerText(),/genomicranges-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391,'Warnings overflow mobile viewport');
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#genomicranges-catalog-notice').count(),1);assert.match(await page.locator('#genomicranges-catalog-notice p').first().innerText(),/Supply the exact named permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_GENOMICRANGES_ROLES_COUNTS_WARNINGS_DESKTOP_MOBILE',papers:250,observations:2723,reviewed:1666,pending:1057,integrations:149,native:27,upstream:122,aliases:3,method_overlap:24,missing:258,paper_roles_checked:checked,new_review_records:5,existing_scope_notes_updated:2,page_errors:errors,website_published:false,scientific_models_run:false,browser_launched_or_killed_by_this_script:false},null,2)+'\n');console.log('PASS GenomicRanges roles,scope,counts and desktop/mobile warnings');
 }finally{try{await context.close();}finally{if(browser.isConnected()){const session=await browser.newBrowserCDPSession();await session.send('Browser.close').catch(error=>{if(browser.isConnected())throw error;});}await browser.close();}}
})().catch(e=>{console.error(e);process.exitCode=1;});
