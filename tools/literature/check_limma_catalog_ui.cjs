// Local preview/admitted report checks. No public website or model execution.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4];
const root=path.resolve(__dirname,'../..');
assert(out&&base&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty UI output and a local report URL');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2717 tool observations/);
  const notice=page.locator('#limma-catalog-notice');assert.equal(await notice.count(),1);
  for(const exp of [/142 scoped integrations/,/1,598 individually reviewed/,/1,119 pending/,/two validated family aliases/,/zero extra credit/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/absence is not an installation prohibition/])assert.match(await notice.innerText(),exp);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const pid of ['P059','P086','P088','P182','P183','P195','P203','P228','P237']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^limma(?:reported|\b|$)/i})});
   if(['P059','P237'].includes(pid)){assert.equal(await rows.count(),0);checked.push({paper_id:pid,limma_observation_excluded:true});continue;}
   assert.equal(await rows.count(),1);const text=await rows.innerText();
   if(pid==='P086')for(const exp of [/3\.58\.1/,/3\.50\.3/])assert.match(text,exp);
   if(pid==='P088')assert.match(text,/heritability/i);
   if(pid==='P182')for(const exp of [/1\.2/,/TREAT/,/WGCNA/])assert.match(text,exp);
   if(pid==='P183')assert.match(text,/residual/i);
   if(pid==='P195'){assert.match(text,/3\.44/);assert.doesNotMatch(text,/3\.44\.0/);}
   if(pid==='P203')assert.match(text,/preprocessCore/);
   if(pid==='P228')for(const exp of [/voomLmFit/,/lmfit/,/unresolved/])assert.match(text,exp);
   checked.push({paper_id:pid,limma_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',116],['validated-family-alias',2],['method-overlap',31],['missing',259]]){
   await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);
  }
  await page.locator('#coverage').selectOption('validated-family-alias');
  const aliases=await page.locator('#results').innerText();assert.match(aliases,/GCTA-COJO/);assert.match(aliases,/voom/i);assert.match(aliases,/zero additional integration credit/);
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('limma');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^limma \(/i})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/18 papers/);assert.match(await card.innerText(),/limma-tools/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#limma-catalog-notice').count(),1);assert.match(await page.locator('#limma-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_LIMMA_ROLES_TWO_ALIASES_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2717,reviewed:1598,pending:1119,integrations:142,native:26,upstream:116,validated_family_aliases:2,method_overlap:31,missing:259,paper_roles_checked:checked,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS limma local source roles,zero-credit voom alias,counts and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
