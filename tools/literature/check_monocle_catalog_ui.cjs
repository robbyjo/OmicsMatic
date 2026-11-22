// Check local proposed/admitted reports; deploy no website and run no model.
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
  const notice=page.locator('#monocle-catalog-notice');assert.equal(await notice.count(),1);
  for(const exp of [/145 scoped integrations/,/1,630 reviewed/,/1,092 pending/,/258 missing/,/zero duplicate review credit/,/no second family credit/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/absence alone is not a prohibition/,/py-monocle/,/P085/])assert.match(await notice.innerText(),exp);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  const expected={P021:[/UMAT/,/residual-Moran/,/per-gene FAIL/,/shortest/,/not run or redistributed/],P024:[/py-monocle/,/research\/nonprofit/,/No py-monocle runtime/,/200/],P043:[/Cicero/,/not a Monocle trajectory/,/Hypendymal_NN/],P051:[/1\.3\.7/,/1\.20/,/no longer source-only/,/Wilcoxon/],P085:[/GLM/,/1\.3\.1/,/historical/,/grouped-Holm/,/Wald/],P099:[/2\.26\.0/,/selected qualified edition/,/p\/q=1/,/TPM/],P117:[/1\.3\.3/,/130\/140\/150/,/15\/20/,/assumed/],P134:[/protein-activity/,/1,?856/,/not raw RNA counts/],P233:[/FD\/WT/,/do not pool/,/unreported paper edition/]};
  for(const [pid,patterns] of Object.entries(expected)){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^Monocle(?:reported|\b|$)/i})});assert.equal(await rows.count(),1);const text=await rows.innerText();for(const exp of patterns)assert.match(text,exp,pid);
   if(pid==='P024'){
    const py=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^py-monocle(?:reported|\b|$)/i})});assert.equal(await py.count(),1);assert.match(await py.innerText(),/recorded intended use category/);assert.doesNotMatch(await py.innerText(),/implementation awaits the user license preference/);
   }
   checked.push({paper_id:pid,monocle_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',119],['validated-family-alias',2],['method-overlap',29],['missing',258]]){
   await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);
  }
  await page.locator('#coverage').selectOption('validated-family-alias');const aliases=await page.locator('#results').innerText();assert.match(aliases,/GCTA-COJO/);assert.match(aliases,/voom/i);
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('Monocle');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Monocle \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/9 papers/);assert.match(await card.innerText(),/monocle-tools/);assert.match(await card.innerText(),/no second family credit/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#monocle-catalog-notice').count(),1);assert.match(await page.locator('#monocle-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_MONOCLE9_SCOPES_RELATED_PY_POLICY_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2722,reviewed:1630,pending:1092,integrations:145,native:26,upstream:119,validated_family_aliases:2,method_overlap:29,missing:258,paper_roles_checked:checked,related_py_policy_updated:true,new_review_records:0,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS local Monocle roles, related py policy, counts, provider boundaries and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
