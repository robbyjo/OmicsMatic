// Local fastp catalog/role/recovery checks; no public site deployment.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/fastp-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));const report={status:'passed',checks:[],screenshots:[]};
 try{
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,48);assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,13);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,259);
  const item=tools.find(t=>t.id==='fastp');assert.equal(item.source_roles_reviewed,7);assert.equal(item.project_adapter_available,true);assert.equal(item.canonical_adapter_available,false);assert.equal(item.development_adapter_cli,'fastp-tools');assert.equal(item.runtime_profiles.length,4);assert.equal(item.whole_paper_reproduced,false);assert.equal(item.paper_exact_unknown_settings_available,false);
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [['P007',[/cut_front/,/disabled by default/]],['P046',[/100 bp/,/not proof/]],['P054',[/dup_calc_accuracy 6/,/barcode/,/24 GiB/]],['P089',[/patch is unresolved/,/do not silently equate/]],['P110',[/unreported/,/Do not infer/]],['P223',[/no fastp version/,/separate steps/]],['P248',[/quality assessment/,/Do not invent/]]]){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^fastp(?:reported|\s|$)/})});assert.equal(await row.count(),1,id);const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern,id);
  }
  report.checks.push('All seven paper roles retain exact defaults,unknown editions,barcodes and downstream boundaries','61new/64includingExisting scoped integrations;259missing');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('fastp');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^fastp\s*\(/})});assert.equal(await card.count(),1);for(const pattern of [/upstream-adapter-validated/,/fastp-tools/,/0\.22\.0/,/1\.0\.1/,/58/,/415/,/300,000/,/not independently/])assert.match(await card.innerText(),pattern);
  await card.screenshot({style:'header, .skip-link { visibility:hidden !important; }',path:path.join(out,'fastp-card-desktop.png')});report.screenshots.push('fastp-card-desktop.png');
  for(const url of ['literature-review.html','literature-warnings.html']){
   if(url==='literature-warnings.html')await page.goto('http://127.0.0.1:8768/'+url);
   const action=page.locator('#fastp-required-actions');assert.equal(await action.count(),1);for(const pattern of [/SKIP3/,/FAIL2/,/Required action/,/NEW paths/,/COMPLETED\.json/,/--verify DIRECTORY/,/not necessarily duplicates/,/thread\s?0/,/non-atomic/,/24\s?GiB/,/never silently lower accuracy/,/P089/,/P110\/P223/,/not authenticity/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),pattern,url);
   const label=url==='literature-review.html'?'review':'warnings';await action.screenshot({style:'header, .skip-link { visibility:hidden !important; }',path:path.join(out,'fastp-'+label+'-actions-desktop.png')});report.screenshots.push('fastp-'+label+'-actions-desktop.png');await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await action.screenshot({style:'header, .skip-link { visibility:hidden !important; }',path:path.join(out,'fastp-'+label+'-actions-mobile-dark.png')});report.screenshots.push('fastp-'+label+'-actions-mobile-dark.png');await page.setViewportSize({width:1440,height:1000});
  }
  assert.equal(errors.length,0,errors.join('\n'));report.checks.push('Complete CLI/dependency/capacity/paired-omission recovery on both pages,desktop and390px dark');fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
