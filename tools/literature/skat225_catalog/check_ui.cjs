// Actual local browser checks;no website publication or association execution.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]),out=process.argv[3],base=process.argv[4];
const root=path.resolve(__dirname,'../../..');
assert(out&&base&&fs.existsSync(out)&&fs.readdirSync(out).length===0,'Use NEW empty output and local report URL');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
const census=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/acat-skat-catalog-sources/CENSUS.json'),'utf8'));
const roles=census.roles.filter(r=>['skat','skato'].includes(r.tool_id));assert.equal(roles.length,15);
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  assert.equal(await page.locator('#results article').count(),250);assert.match(await page.locator('#overview').innerText(),/2723 tool observations/);
  const notice=page.locator('#skat225-catalog-notice');assert.equal(await notice.count(),1);
  for(const expected of [/148 scoped families/,/1661 reviewed/,/1062 pending/,/27 native/,/121 upstream/,/258 missing/,/25 method-overlap/,/three aliases/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/P246/,/does not reproduce those workflows/])assert.match(await notice.innerText(),expected);
  assert.match(await notice.locator('p').first().innerText(),/Warnings and required actions/);await page.screenshot({path:path.join(out,'desktop.png')});
  const checked=[];
  for(const role of roles){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===role.paper_id).doi);assert.equal(await page.locator('#results article').count(),1);
   const pattern=role.tool_id==='skat'?/^SKAT(?![- ]?O)/i:/^SKAT[- ]?O/i;
   const row=page.locator('#'+role.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:pattern})});assert.equal(await row.count(),1,role.paper_id+'/'+role.tool_id);
   const text=await row.innerText();assert(text.includes(role.usage_summary));assert(text.includes(role.implementation_scope_note));checked.push({paper_id:role.paper_id,tool_id:role.tool_id,rows:1});
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();
  for(const [status,n] of [['native-subset-validated',27],['upstream-adapter-validated',121],['validated-family-alias',3],['method-overlap',25],['missing',258]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n,status);}
  await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('SKAT');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^SKAT \(/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/6 papers/);assert.match(await card.innerText(),/skat-tools/);
  await page.locator('#coverage').selectOption('validated-family-alias');await page.locator('#query').fill('SKAT-O');const alias=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^SKAT-O \(/})});assert.equal(await alias.count(),1);assert.match(await alias.innerText(),/9 papers/);assert.match(await alias.innerText(),/not another family/);
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#skat225-catalog-notice').count(),1);assert.match(await page.locator('#skat225-catalog-notice p').first().innerText(),/Supply the exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_SKAT225_ROLES_COUNTS_WARNINGS_DESKTOP_MOBILE',papers:250,observations:2723,reviewed:1661,pending:1062,integrations:148,native:27,upstream:121,aliases:3,method_overlap:25,missing:258,paper_roles_checked:checked,new_review_records:0,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS local original SKAT225 roles,scope,counts and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
