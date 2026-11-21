// Local planned/admitted report checks; no public site or model execution.
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
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2720 tool observations/);
  const notice=page.locator('#peer-catalog-notice');assert.equal(await notice.count(),1);
  for(const exp of [/143 scoped integrations/,/1,616 individually reviewed/,/1,104 pending/,/two validated family aliases/,/zero extra credit/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/absence is not an installation prohibition/,/legacy native Java Peer API/,/no stock-output qualification/])assert.match(await notice.innerText(),exp);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const [pid,patterns] of Object.entries({P074:[/60|Sixty|sixty/],P107:[/45/,/15/,/strat/i],P108:[/supplied|provided|GTEx/i],P169:[/residual/i],P193:[/sensitivity/i],P201:[/29|twenty-nine/i],P203:[/BIC/],P212:[/1\.3/,/40/,/20/],P213:[/four/i,/six/i],P219:[/BMI/,/QTLtools/],P221:[/JHS/,/MESA/],P234:[/GTEx/],P249:[/logistic/i]})){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^PEER(?:reported|\b|$)/})});
   assert.equal(await rows.count(),1);const text=await rows.innerText();for(const exp of patterns)assert.match(text,exp,pid);
   if(pid==='P219')assert.doesNotMatch(text,/PEER edition: 1\.2/);checked.push({paper_id:pid,peer_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',117],['validated-family-alias',2],['method-overlap',30],['missing',259]]){
   await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);
  }
  await page.locator('#coverage').selectOption('validated-family-alias');const aliases=await page.locator('#results').innerText();assert.match(aliases,/GCTA-COJO/);assert.match(aliases,/voom/i);
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('PEER');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^PEER \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/18 papers/);assert.match(await card.innerText(),/peer-tools/);assert.match(await card.innerText(),/no stock-output qualification/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});
  const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#peer-catalog-notice').count(),1);assert.match(await page.locator('#peer-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_PEER18_ROLES_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2720,reviewed:1616,pending:1104,integrations:143,native:26,upstream:117,validated_family_aliases:2,method_overlap:30,missing:259,paper_roles_checked:checked,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS local PEER roles,counts,legacy-native boundary and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
