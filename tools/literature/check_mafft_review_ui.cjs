// Local MAFFT catalog verification; requires Playwright and a Chromium/Edge executable.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/mafft-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const report={status:'passed',checks:[],screenshots:[]};
 try {
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,47);
  assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,13);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,260);
  const item=tools.find(t=>t.id==='mafft');assert.equal(item.project_adapter_available,true);assert.equal(item.canonical_adapter_available,false);
  assert.equal(item.source_roles_reviewed,8);assert.equal(item.whole_paper_reproduced,false);assert.equal(item.biological_accuracy_certified,false);assert.equal(item.paper_exact_unknown_settings_available,false);
  assert.equal(item.runtime_profiles.length,6);assert.equal(item.development_adapter_cli,'mafft-tools');assert.equal(item.workflow_validation,'mafft-workflow-validation.json');
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [['P052',[/exact edition remains unresolved/,/must be skipped/,/no tested edition/]],['P101',[/7\.475/,/input alphabet/,/do not infer/,/memory-bug warning/]],['P110',[/L-INS-i/,/PAL2NAL/,/separate/]],['P171',[/-AUTO flag belongs to IQ-TREE2/,/not inferred/]],['P235',[/maximum_iterations=100/,/does not assert/,/manual/]]]){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^MAFFT/})});assert.equal(await row.count(),1);
   assert.equal(await row.locator('td').first().evaluate(node=>node.firstChild.textContent),'MAFFT');
   const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern);
  }
  report.checks.push('Five explicit unknown-setting/manual/downstream paper-role boundaries','60new and63includingExisting scoped tools;260missing');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('MAFFT');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MAFFT\s*\(/})});assert.equal(await card.count(),1);
  const text=await card.innerText();for(const pattern of [/upstream-adapter-validated/,/7\.313/,/7\.526/,/mafft-tools/,/complete/,/intentional|deliberate/,/57/,/35/,/13/,/9/,/biological/])assert.match(text,pattern);
  await card.screenshot({path:path.join(out,'mafft-card-desktop.png')});report.screenshots.push('mafft-card-desktop.png');
  const action=page.locator('#mafft-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/FAIL2/,/Required action/,/NEW paths/,/COMPLETED\.json/,/residue-map\.tsv/,/7\.475/,/memory-bug/,/P052/,/P101/,/not authenticity/,/37 deliberate deletions/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),pattern);
  await action.screenshot({path:path.join(out,'mafft-actions-desktop.png')});report.screenshots.push('mafft-actions-desktop.png');
  await page.setViewportSize({width:390,height:900});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  await action.scrollIntoViewIfNeeded();const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.equal(layout.viewport,390);assert.ok(layout.document<=390);
  await action.screenshot({path:path.join(out,'mafft-actions-mobile-dark.png')});report.screenshots.push('mafft-actions-mobile-dark.png');report.mobile_layout=layout;
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');const warning=page.locator('#mafft-required-actions');assert.equal(await warning.count(),1);assert.match(await warning.innerText(),/skip dependent analysis/);assert.match(await warning.innerText(),/Keep-length deletes insertions/);
  assert.deepEqual(errors,[]);report.checks.push('MAFFT scoped card,version/complete-population contracts and portable native audit','Prominent skip/failure recovery,source bug and insertion deletion warnings on desktop and390pxdark','Action section also appears on the dedicated warning page;no JavaScript errors');
  fs.writeFileSync(path.join(out,'mafft-ui-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
