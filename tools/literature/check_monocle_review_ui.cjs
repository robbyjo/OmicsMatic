// Local-only source-role, missing-coverage and actionable-warning browser checks.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT);
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1280,height:1100}}),errors=[],checks=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  const data=path.join(root,'docs/literature/omics-2023-2026');
  const summary=JSON.parse(fs.readFileSync(path.join(data,'summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.distinct_named_tools,389);assert.equal(summary.paper_tool_observations,2449);
  assert.equal(summary.implementation_status_counts['missing'],256);assert.equal(summary.implementation_status_counts['upstream-adapter-validated'],58);assert.equal(summary.implementation_status_counts['native-subset-validated'],16);
  const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));
  for(const [id,n] of [['monocle',9],['cicero',2],['py-monocle',1]]){const t=inventory.find(t=>t.id===id);assert.equal(t.paper_count,n);assert.equal(t.implementation_status,'missing');}
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const drafts=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/monocle-catalog-use-reviews.json'),'utf8'));assert.equal(drafts.length,12);
  const names={monocle:'Monocle',cicero:'Cicero','py-monocle':'py-monocle'};
  for(const d of drafts){
   const row=page.locator('#'+d.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+names[d.review.tool_id]+'(?:reported|\\s|$)')})});
   assert.equal(await row.count(),1,d.paper_id+'/'+d.review.tool_id);const text=await row.innerText();assert(text.includes(d.review.use_summary));assert(text.includes(d.review.implementation_scope_note));
   if(d.review.supporting_evidence.length){await row.getByText('Evidence locator',{exact:true}).click();for(const p of d.review.supporting_evidence)assert((await row.innerText()).includes('Supporting paragraph '+p.paragraph_index));}
  }
  checks.push('All12 source-bound summaries/scopes and seven supporting paragraph locators rendered');
  const action=page.locator('#monocle-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/skip dependent analysis/,/installation is forbidden/,/Linux Java/,/HSMMSingleCell1.18.0/,/FAIL2/,/atomic batch/,/p\/q=1/,/NEW paths/,/Cicero/,/noncommercial/,/No runtime was installed/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),pattern);
  assert.ok((await action.boundingBox()).y<1000);checks.push('Prominent distinct-tool, runtime-skip, original-failure and license actions rendered');
  await page.addStyleTag({content:'header{position:static!important}.skip-link{visibility:hidden!important}'});await action.screenshot({path:path.join(out,'monocle-actions-desktop.png')});
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Monocle');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Monocle\s*\(/})});assert.equal(await card.count(),1);
  for(const p of [/9 papers/,/missing/,/Monocle2/,/1.4.27/,/1.3.3/,/1.3.7/,/canonical JAR is unchanged/i])assert.match(await card.innerText(),p);
  await card.screenshot({path:path.join(out,'monocle-card-desktop.png')});checks.push('Monocle development workflows remain visibly separate from full missing coverage');
  await page.setViewportSize({width:390,height:950});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});await action.scrollIntoViewIfNeeded();await page.evaluate(()=>document.activeElement?.blur());await action.screenshot({path:path.join(out,'monocle-actions-mobile.png')});
  const dimensions=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.deepEqual(dimensions,{viewport:390,document:390});checks.push('Mobile warning layout fits390px without overflow');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');assert.equal(await page.locator('#monocle-required-actions').count(),1);assert.match(await page.locator('#monocle-required-actions').innerText(),/noncommercial/);checks.push('Separate warnings page retains Monocle/Cicero/py-monocle recovery and license state');
  assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'monocle-ui-verification.json'),JSON.stringify({status:'passed',checks,mobile_layout:dimensions,page_errors:errors},null,2)+'\n');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
