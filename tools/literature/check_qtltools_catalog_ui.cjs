// Exercise local proposed/admitted catalog views; publish no site and run no model.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4];
const root=path.resolve(__dirname,'../..');
assert(out&&base&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty output and a local report URL');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2722 tool observations/);
  const notice=page.locator('#qtltools-catalog-notice');assert.equal(await notice.count(),1);
  for(const exp of [/146 scoped families/,/1639 reviewed/,/1083 pending/,/258 missing/,/28 method-overlap/,/native helper adds no duplicate family/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/separate checksummed transfer/])assert.match(await notice.innerText(),exp);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const expected={P073:[/bare --grp/,/does not restate an edition/,/50000/],P074:[/mode/,/COLOC/,/unstated/],P104:[/1\.3\.1/,/QTLs having any annotation overlap/,/radius/],P108:[/nominal/,/HCP/,/175/],P190:[/S1\.7/,/--grp-best/,/adaptive/,/no QTLtools edition/],P201:[/10000 adaptive/,/standard-error/,/unstated/],P203:[/literal match/,/No QTLtools edition/,/20 PEER/],P219:[/quan edition unknown/,/second residual/,/minor allele/],P227:[/citation-pruned/,/not1\.2\.27/,/five sample swaps/i]};const checked=[];
  for(const [pid,patterns] of Object.entries(expected)){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+pid+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^QTLtools(?:reported|\b|$)/i})});assert.equal(await row.count(),1);const text=await row.innerText();for(const exp of patterns)assert.match(text,exp,pid);checked.push({paper_id:pid,qtltools_rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',26],['upstream-adapter-validated',120],['validated-family-alias',2],['method-overlap',28],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('QTLtools');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^QTLtools \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/9 papers/);assert.match(await card.innerText(),/qtltools-tools/);assert.match(await card.innerText(),/no duplicate family/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#qtltools-catalog-notice').count(),1);assert.match(await page.locator('#qtltools-catalog-notice p').first().innerText(),/Supply the exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_QTLTOOLS9_ROLES_COUNTS_DESKTOP_MOBILE_WARNINGS',papers:250,observations:2722,reviewed:1639,pending:1083,integrations:146,native:26,upstream:120,method_overlap:28,missing:258,paper_roles_checked:checked,new_review_records:9,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS local QTLtools roles,counts,provider boundaries and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
