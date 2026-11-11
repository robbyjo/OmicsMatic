// Scoped ChIPseeker integration, primary roles and actionable recovery; localhost only.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT);
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1280,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  const data=path.join(root,'docs/literature/omics-2023-2026');
  const summary=JSON.parse(fs.readFileSync(path.join(data,'summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.distinct_named_tools,387);assert.equal(summary.paper_tool_observations,2446);
  assert.deepEqual(summary.implementation_status_counts,{'upstream-adapter-validated':58,'method-overlap':47,'excluded-by-user':1,
   'native-subset-validated':16,'existing-adapter':3,'existing-lookup':7,'missing':254,'existing-export':1});
  const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));
  const tool=inventory.find(t=>t.id==='chipseeker');
  const proof=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/chipseeker-annotation-development/VALIDATION.json'),'utf8'));
  assert.equal(tool.implementation_status,'upstream-adapter-validated');assert.equal(tool.paper_count,7);assert.equal(tool.source_roles_reviewed,7);
  assert.equal(tool.development_candidate_sha256,proof.artifact.sha256);assert.equal(tool.project_adapter_available,true);
  assert.equal(tool.native_methods_available,true);assert.equal(tool.canonical_adapter_available,false);assert.equal(tool.analysis_installs_software,false);
  assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const drafts=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/chipseeker-use-reviews.json'),'utf8'));
  assert.equal(drafts.length,7);
  for(const draft of drafts){
   const row=page.locator('#'+draft.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^ChIPseeker(?:reported|\s|$)/})});
   assert.equal(await row.count(),1,draft.paper_id);const text=await row.innerText();
   assert(text.includes(draft.review.use_summary));assert(text.includes(draft.review.implementation_scope_note));
  }
  const support=page.locator('#P139 tr').filter({has:page.locator('td:first-child').filter({hasText:/^ChIPseeker(?:reported|\s|$)/})});
  assert.match(await support.innerText(),/distal.*intron/i);
  await support.getByText('Evidence locator',{exact:true}).click();
  assert.match(await support.innerText(),/Supporting paragraph 33/);
  const action=page.locator('#chipseeker-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/skip the dependent analysis/,/installation is forbidden/,/FAIL2/,/no result admitted/,/Required action/,
   /NEW/,/COMPLETED/,/INCOMPLETE/,/result_admitted=true/,/49 original errors/,/numeric geneChr/,/GENCODE v30/,/202 native tests/,/424/,/16 recovery/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),pattern);
  assert.ok((await action.boundingBox()).y<1000,'ChIPseeker recovery must appear near the top');
  // Hide the sticky navigation only in captures so it cannot cover a tall locator.
  await page.addStyleTag({content:'header{position:static!important}'});
  await action.screenshot({path:path.join(out,'chipseeker-actions-desktop.png')});
  await page.locator('#tools-tab').click();await page.locator('#query').fill('ChIPseeker');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^ChIPseeker\s*\(/})});assert.equal(await card.count(),1);
  for(const pattern of [/7 papers/,/upstream-adapter-validated/,/chipseeker-tools/,/1\.22\.1\/1\.26\.2\/1\.34\.1\/1\.48\.1/,/202/,/caller-supplied TxDb/,/CHIPSEEKER_ANNOTATION_DEVELOPMENT/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'chipseeker-card-desktop.png')});
  await page.setViewportSize({width:390,height:950});
  await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  await action.scrollIntoViewIfNeeded();
  const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));
  assert.deepEqual(layout,{viewport:390,document:390});
  await action.screenshot({path:path.join(out,'chipseeker-actions-mobile-dark.png')});
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');
  const warning=page.locator('#chipseeker-required-actions');assert.equal(await warning.count(),1);
  assert.match(await warning.innerText(),/skip the dependent analysis/);assert.match(await warning.innerText(),/no result admitted/);
  assert.deepEqual(errors,[]);
  const report={status:'passed',checks:['74 new scoped integrations / 77 including existing / 254 missing',
   'Seven exact reviewed primary roles and P139 supporting paragraph 33','Four original editions and separate native nearest scope',
   'Prominent actionable runtime skips and atomic errors on both pages','1280px desktop and 390px dark mobile without overflow or JavaScript errors'],
   source_roles:7,reviewed_uses:905,supporting_pointers:302,mobile_layout:layout,
   visual_files:['chipseeker-actions-desktop.png','chipseeker-card-desktop.png','chipseeker-actions-mobile-dark.png']};
  fs.writeFileSync(path.join(out,'chipseeker-ui-verification.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify({status:report.status,checks:report.checks.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
