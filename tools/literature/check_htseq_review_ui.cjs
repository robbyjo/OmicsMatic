// Verify scoped HTSeq catalog admission, primary roles and prominent recovery; localhost only.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT);
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1100}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  const summary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.distinct_named_tools,387);assert.equal(summary.paper_tool_observations,2446);
  assert.deepEqual(summary.implementation_status_counts,{'upstream-adapter-validated':56,'method-overlap':47,'excluded-by-user':1,
   'native-subset-validated':16,'existing-adapter':3,'existing-lookup':7,'missing':256,'existing-export':1});
  const inventory=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  const tool=inventory.find(t=>t.id==='htseq'),proof=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/htseq-tools-development/VALIDATION.json'),'utf8'));
  assert.equal(tool.implementation_status,'upstream-adapter-validated');assert.equal(tool.paper_count,8);assert.equal(tool.source_roles_reviewed,8);
  assert.equal(tool.project_adapter_available,true);assert.equal(tool.canonical_adapter_available,false);assert.equal(tool.native_methods_available,true);assert.equal(tool.original_runtime_adapters_available,true);
  assert.equal(tool.development_candidate_sha256,proof.artifact.sha256);assert.equal(tool.analysis_installs_software,false);
  assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const expected={P005:[/0\.6\.0/,/HT-seq/],P020:[/0\.5\.3/,/stranded library do not establish/],P044:[/0\.13\.5/,/vM25/],
   P099:[/at least one read/,/custom binary read-presence/,/not evidence/],P113:[/0\.11\.1/,/30, 90 and 240 kb/,/does not separately state/],
   P121:[/0\.13\.5/,/strict MAPQ>20/,/counting unit/],P148:[/hg19/,/unstated/],P195:[/major\/minor/,/not evidence of the 0\.13\.5/]};
  for(const [id,patterns] of Object.entries(expected)){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^HTSeq/})});
   assert.equal(await row.count(),1,id);for(const pattern of patterns)assert.match(await row.innerText(),pattern,id);
  }
  const action=page.locator('#htseq-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/skip dependent analysis/,/installation is forbidden/,/FAIL2/,/no result admitted/,/Required action/,/NEW paths/,
   /COMPLETED/,/INCOMPLETE/,/Minimum 21/,/FRACTION/,/P099/,/P195/,/Cell Ranger remains excluded/,/260/,/7 final count analyses/,/24 positive portable/,/33 consistency/,/not heap guarantees/])assert.match(await action.innerText(),pattern);
  assert.ok((await action.boundingBox()).y<1500,'HTSeq recovery must appear prominently near the top');
  await action.screenshot({path:path.join(out,'htseq-actions-desktop.png')});
  await page.locator('#tools-tab').click();await page.locator('#query').fill('HTSeq');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^HTSeq\s*\(/})});
  assert.equal(await card.count(),1);
  for(const pattern of [/8 papers/,/upstream-adapter-validated/,/htseq-tools/,/260/,/7 real count analyses/,/24 positive portable VERIFY/,/binary/,/HTSEQ_TOOLS/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'htseq-card-desktop.png')});
  await page.setViewportSize({width:390,height:950});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  await action.scrollIntoViewIfNeeded();const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.deepEqual(layout,{viewport:390,document:390});
  await action.screenshot({path:path.join(out,'htseq-actions-mobile-dark.png')});
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');
  const warning=page.locator('#htseq-required-actions');assert.equal(await warning.count(),1);assert.match(await warning.innerText(),/skip dependent analysis/);assert.match(await warning.innerText(),/no result admitted/);
  assert.deepEqual(errors,[]);
  const report={status:'passed',checks:['72 new scoped integrations/75 including existing/256 missing','Eight primary HTSeq roles and unresolved settings',
   'Five original editions plus native prepared/binary scope','Prominent actionable runtime skips and no-partial-admission errors on both pages','Desktop and390px dark mobile layout without overflow or JavaScript errors'],
   source_roles:8,reviewed_uses:898,supporting_pointers:301,mobile_layout:layout,visual_files:['htseq-actions-desktop.png','htseq-card-desktop.png','htseq-actions-mobile-dark.png']};
  fs.writeFileSync(path.join(out,'htseq-ui-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
