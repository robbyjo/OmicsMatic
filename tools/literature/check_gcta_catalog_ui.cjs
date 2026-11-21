// Local preview/admitted report checks; no website publication or scientific execution.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4]||'http://127.0.0.1:8773';
const root=path.resolve(__dirname,'../..');
assert(out&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Choose a NEW empty UI evidence directory');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2715 tool observations/);
  const notice=page.locator('#gcta-catalog-notice');assert.equal(await notice.count(),1);
  for(const expression of [/141 scoped integrations/,/1,575 individually reviewed/,/1,140 pending/,/one validated family alias/,/zero extra credit/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/no liability conversion/])assert.match(await notice.innerText(),expression);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const pid of ['P090','P112','P127','P169','P171','P238','P248']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^GCTA(?:-COJO)?(?:reported|\b|$)/})});
   if(pid==='P127'){assert.equal(await rows.count(),0);checked.push({paper_id:pid,comparison_only_observation_removed:true});continue;}
   assert.equal(await rows.count(),1);const text=await rows.innerText();
   if(pid==='P090')for(const exp of [/1\.92\.4/,/chromosome-wide/,/no liability conversion|no.*liability/])assert.match(text,exp);
   if(pid==='P112')for(const exp of [/smoking initiation/,/cigarettes/,/edition/])assert.match(text,exp);
   if(pid==='P169')for(const exp of [/colorectal/,/6,684/,/4,284/,/respectively/,/rather than swapping/])assert.match(text,exp);
   if(pid==='P171')for(const exp of [/tetraploid/,/100,000/,/not an established PCA/])assert.match(text,exp);
   if(pid==='P238')assert.match(text,/outside 0\.\.1/);
   if(pid==='P248')for(const exp of [/1\.94/,/equality/,/fraction/])assert.match(text,exp);
   checked.push({paper_id:pid,gcta_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',115],['validated-family-alias',1],['method-overlap',33],['missing',259]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('validated-family-alias');assert.match(await page.locator('#results').innerText(),/GCTA-COJO/);assert.match(await page.locator('#results').innerText(),/zero additional integration credit|zero additional|zero extra credit/);
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('GCTA');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^GCTA \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/19 papers/);assert.match(await card.innerText(),/gcta-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#gcta-catalog-notice').count(),1);assert.match(await page.locator('#gcta-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_GCTA_CATALOG_ROLES_ALIAS_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2715,reviewed:1575,pending:1140,integrations:141,native:26,upstream:115,validated_family_aliases:1,method_overlap:33,missing:259,paper_roles_checked:checked,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS GCTA local source roles,one family/alias,counts and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
