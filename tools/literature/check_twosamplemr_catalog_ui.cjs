// Check only local planned/admitted reports; publish no website and run no model.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4];
const root=path.resolve(__dirname,'../..');
assert(out&&base&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty UI output and local report URL');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2722 tool observations/);
  const notice=page.locator('#twosamplemr-catalog-notice');assert.equal(await notice.count(),1);
  for(const exp of [/144 scoped integrations/,/1,630 individually reviewed/,/1,092 pending/,/zero.credit validated aliases/,/no second family credit/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/absence is not an installation prohibition/,/dependency eras/,/P177 IVW/])assert.match(await notice.innerText(),exp);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const [pid,patterns] of Object.entries({P077:[/0\.4\.9/,/IVW/],P088:[/0\.5\.6/,/weighted median/i],P105:[/precise estimator/i,/MR-PRESSO/],P112:[/three remain/],P125:[/SIMEX/,/0\.6/],P150:[/MAF/,/0\.5\.6/],P157:[/0\.5\.7/,/harmonise_data/,/both/,/archive/i],P162:[/without additional LD/],P177:[/MendelianRandomization::mr_input\/mr_ivw/,/inferred/,/Egger/,/TEMR/],P182:[/more than three/],P189:[/PCOS/,/odds ratio/],P211:[/0\.5\.7/,/-1\.96/,/R4\.2\.1/],P224:[/0\.6\.8/,/mv_harmonise_data/,/MVMR/],P239:[/0\.05/,/gene identifiers/]})){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^TwoSampleMR(?:reported|\b|$)/i})});
   assert.equal(await rows.count(),1);const text=await rows.innerText();for(const exp of patterns)assert.match(text,exp,pid);
   if(pid==='P177')assert.doesNotMatch(text,/Reported TwoSampleMR edition:/);checked.push({paper_id:pid,twosamplemr_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',118],['validated-family-alias',2],['method-overlap',29],['missing',259]]){
   await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);
  }
  await page.locator('#coverage').selectOption('validated-family-alias');const aliases=await page.locator('#results').innerText();assert.match(aliases,/GCTA-COJO/);assert.match(aliases,/voom/i);
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('TwoSampleMR');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^TwoSampleMR \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/14 papers/);assert.match(await card.innerText(),/twosamplemr-tools/);assert.match(await card.innerText(),/no second family credit/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#twosamplemr-catalog-notice').count(),1);assert.match(await page.locator('#twosamplemr-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_TWOSAMPLEMR14_ROLES_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2722,reviewed:1630,pending:1092,integrations:144,native:26,upstream:118,validated_family_aliases:2,method_overlap:29,missing:259,paper_roles_checked:checked,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS local TwoSampleMR roles, counts, provider boundaries and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
