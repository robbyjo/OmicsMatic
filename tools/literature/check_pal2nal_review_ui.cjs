// Local browser assertions for native PAL2NAL scope and required recovery.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/pal2nal-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true}),page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));const report={status:'passed',checks:[],screenshots:[]};
 try{
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,13);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,47);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,260);
  const item=tools.find(t=>t.id==='pal2nal');assert.equal(item.project_adapter_available,true);assert.equal(item.canonical_adapter_available,false);assert.equal(item.source_roles_reviewed,2);assert.equal(item.whole_paper_reproduced,false);assert.equal(item.biological_accuracy_certified,false);assert.equal(item.external_analysis_runtime_required,false);
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [['P110',[/v14/,/genetic code/,/do not infer/,/EasyCodeML/,/separate/]],['P248',[/v14\.1/,/not silently replaced/,/unreported/,/does not calculate Ka\/Ks/,/separate/]]]){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^PAL2NAL/})});assert.equal(await row.count(),1);assert.equal(await row.locator('td').first().evaluate(n=>n.firstChild.textContent),'PAL2NAL');for(const pattern of patterns)assert.match(await row.innerText(),pattern);
  }
  report.checks.push('380tools,60new/63includingExisting scoped integrations and260missing','Both primary-source PAL2NAL roles retain code/option uncertainty and distinct upstream/downstream boundaries');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('PAL2NAL');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^PAL2NAL\s*\(/})});assert.equal(await card.count(),1);
  for(const pattern of [/native-subset-validated/,/170/,/156/,/14/,/206/,/pal2nal-tools/,/v14\/v14\.1/,/PAML/,/persistent/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'pal2nal-card-desktop.png')});report.screenshots.push('pal2nal-card-desktop.png');
  const action=page.locator('#pal2nal-required-actions');assert.equal(await action.count(),1);
  for(const pattern of [/SKIP3/,/FAIL2/,/Required action/,/NEW paths/,/COMPLETED\.json/,/cells\.tsv/,/triplets\.tsv/,/Zero verified residues/,/TGA=stop/,/19-file/,/temporary Linux raw files were unavailable/,/not authenticity/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),pattern);
  await action.screenshot({path:path.join(out,'pal2nal-actions-desktop.png')});report.screenshots.push('pal2nal-actions-desktop.png');
  await page.setViewportSize({width:390,height:900});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});await action.scrollIntoViewIfNeeded();report.mobile_layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));assert.equal(report.mobile_layout.viewport,390);assert.ok(report.mobile_layout.document<=390);
  await action.screenshot({path:path.join(out,'pal2nal-actions-mobile-dark.png')});report.screenshots.push('pal2nal-actions-mobile-dark.png');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');assert.equal(await page.locator('#pal2nal-required-actions').count(),1);assert.match(await page.locator('#pal2nal-required-actions').innerText(),/Zero verified residues/);assert.deepEqual(errors,[]);
  report.checks.push('Native scoped card and source/profile/control counts','Prominent skip/failure,source-code,weak-match and retention recovery on desktop and390pxdark','Dedicated warning page includes the same required actions;no JavaScript errors');
  fs.writeFileSync(path.join(out,'pal2nal-ui-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
