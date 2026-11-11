// Local Signac catalog and recovery-message validation; no publication or remote mutations.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/signac-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const report={status:'passed',checks:[],screenshots:[]};
 try{
  const summary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.paper_tool_observations,2445);
  assert.equal(summary.distinct_named_tools,387);
  assert.deepEqual(summary.implementation_status_counts,{'upstream-adapter-validated':55,'method-overlap':47,
   'excluded-by-user':1,'native-subset-validated':16,'existing-adapter':3,'existing-lookup':7,'missing':257,'existing-export':1});
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  const item=tools.find(t=>t.id==='signac');assert.equal(item.paper_count,10);
  for(const key of ['project_adapter_available','native_methods_available','original_runtime_adapters_available'])assert.equal(item[key],true);
  for(const key of ['canonical_adapter_available','analysis_installs_software','duplicate_chromVAR_credit','whole_paper_reproduced','historical_whole_packages_reproduced','biological_accuracy_certified'])assert.equal(item[key],false);
  assert.equal(item.implementation_status,'upstream-adapter-validated');assert.equal(item.source_roles_reviewed,10);
  assert.equal(tools.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const expected={P134:[/logistic regression/i,/figure caption/,/edition/],P158:[/ambiguous/,/NucleosomeSignal/],
   P187:[/denominator/,/not stated/],P225:[/FindMarkers/,/FindMotifs/],P099:[/Monocle2/,/not Signac/],
   P039:[/GeneActivity/,/raw accessibility proxy/,/median-library/],P051:[/count cutoff 5/,/strict greater-than/]};
  for(const [id,patterns] of Object.entries(expected)){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^Signac/})});
   assert.equal(await row.count(),1,id);const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern,id);
  }
  assert.equal(await page.locator('#P007 tr').filter({has:page.locator('td:first-child').filter({hasText:/^Signac/})}).count(),0);
  report.checks.push('Ten reviewed software papers, seven explicit role boundaries and excluded institutional SignAC collision');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Signac');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Signac\s*\(/})});
  assert.equal(await card.count(),1);const text=await card.innerText();
  for(const pattern of [/10 papers/,/upstream-adapter-validated/,/signac-tools/,/1679/,/21 real public analyses/,/51 portable/,/Inf/,/PNG/])assert.match(text,pattern);
  await card.screenshot({path:path.join(out,'signac-card-desktop.png')});report.screenshots.push('signac-card-desktop.png');
  const action=page.locator('#signac-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/skip dependent analysis/,/installation is forbidden/,/FAIL2/,/no result admitted/,/Required action/,/NEW paths/,/Inf/,/Empty feature selection/,/Cell Ranger remains excluded/,/PNG pixels do not/,/chromVAR gets no duplicate/])assert.match(await action.innerText(),pattern);
  await action.screenshot({path:path.join(out,'signac-actions-desktop.png')});report.screenshots.push('signac-actions-desktop.png');
  await page.setViewportSize({width:390,height:900});
  await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  await action.scrollIntoViewIfNeeded();
  const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));
  assert.equal(layout.viewport,390);assert.ok(layout.document<=390);report.mobile_layout=layout;
  await action.screenshot({path:path.join(out,'signac-actions-mobile-dark.png')});report.screenshots.push('signac-actions-mobile-dark.png');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');
  const warning=page.locator('#signac-required-actions');assert.equal(await warning.count(),1);
  assert.match(await warning.innerText(),/skip dependent analysis/);assert.match(await warning.innerText(),/no result admitted/);
  assert.deepEqual(errors,[]);
  report.checks.push('71 new scoped integrations, 74 including existing adapters, 257 missing',
   'Prominent runtime skip/failure actions and source edge behavior on both pages',
   'Desktop and390px dark layout without overflow or JavaScript errors');
  fs.writeFileSync(path.join(out,'signac-ui-verification.json'),JSON.stringify(report,null,2)+'\n');
  console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
