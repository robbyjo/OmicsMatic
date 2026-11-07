// Scoped classic MEME discovery catalog, exact reviewed mentions and required recovery; localhost only.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT);
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  const data=path.join(root,'docs/literature/omics-2023-2026');
  const summary=JSON.parse(fs.readFileSync(path.join(data,'summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.distinct_named_tools,387);assert.equal(summary.paper_tool_observations,2446);
  assert.deepEqual(summary.implementation_status_counts,{'upstream-adapter-validated':57,'method-overlap':47,'excluded-by-user':1,
   'native-subset-validated':16,'existing-adapter':3,'existing-lookup':7,'missing':255,'existing-export':1});
  const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));
  const tool=inventory.find(t=>t.id==='meme'),proof=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/meme-discovery-workflow-development/VALIDATION.json'),'utf8'));
  assert.equal(tool.implementation_status,'upstream-adapter-validated');assert.equal(tool.paper_count,8);assert.equal(tool.source_roles_reviewed,8);
  assert.equal(tool.development_candidate_sha256,proof.artifact.sha256);assert.equal(tool.project_adapter_available,true);assert.equal(tool.canonical_adapter_available,false);
  assert.equal(tool.analysis_installs_software,false);assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
  for(const id of ['meme-chip','dreme','streme','centrimo'])assert.equal(inventory.find(t=>t.id===id).implementation_status,'missing',id);
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const drafts=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/meme-program-use-reviews.json'),'utf8')).filter(d=>d.review.tool_id==='meme');assert.equal(drafts.length,8);
  for(const draft of drafts){
   const row=page.locator('#'+draft.paper_id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^MEME(?:reported|\s|$)/})});
   assert.equal(await row.count(),1,draft.paper_id);const text=await row.innerText();
   assert(text.includes(draft.review.use_summary));assert(text.includes(draft.review.implementation_scope_note));
  }
  const action=page.locator('#meme-discovery-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/skip the affected analysis/,/installation is forbidden/,/FAIL2/,/no result admitted/,/Required action/,/NEW paths/,
   /COMPLETED/,/INCOMPLETE/,/1,000/,/zero-motif/,/biological absence/,/P007/,/P013/,/P155/,/Cell Ranger remains excluded/,/91 tests/,/27 workflow/])assert.match(await action.innerText(),pattern);
  assert.ok((await action.boundingBox()).y<1000,'MEME recovery must appear prominently near the top');
  await action.screenshot({path:path.join(out,'meme-actions-desktop.png')});
  await page.locator('#tools-tab').click();await page.locator('#query').fill('MEME');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MEME\s*\(/})});assert.equal(await card.count(),1);
  for(const pattern of [/8 papers/,/upstream-adapter-validated/,/meme-tools/,/91/,/1,000|1000|zero-motif/,/whole-paper/,/MEME_DISCOVERY_WORKFLOW/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'meme-card-desktop.png')});
  await page.setViewportSize({width:390,height:950});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  await action.scrollIntoViewIfNeeded();const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.deepEqual(layout,{viewport:390,document:390});
  await action.screenshot({path:path.join(out,'meme-actions-mobile-dark.png')});
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');
  const warning=page.locator('#meme-discovery-required-actions');assert.equal(await warning.count(),1);assert.match(await warning.innerText(),/skip the affected analysis/);assert.match(await warning.innerText(),/no result admitted/);
  assert.deepEqual(errors,[]);
  const report={status:'passed',checks:['73 new scoped integrations/76 including existing/255 missing','Eight unchanged reviewed discovery/resource/provider mentions',
   'Four exact original classic profiles, native audit and zero-motif scope','Prominent actionable runtime skips and no-partial-result errors on both pages',
   'Desktop and 390px dark mobile layout without overflow or JavaScript errors'],source_roles:8,reviewed_uses:898,supporting_pointers:301,mobile_layout:layout,
   visual_files:['meme-actions-desktop.png','meme-card-desktop.png','meme-actions-mobile-dark.png']};
  fs.writeFileSync(path.join(out,'meme-discovery-ui-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
