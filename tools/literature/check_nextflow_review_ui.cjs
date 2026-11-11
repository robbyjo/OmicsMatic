// Local Nextflow catalog/role/recovery checks; no public site deployment.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/nextflow-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));const report={status:'passed',checks:[],screenshots:[]};
 try {
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,49);
  assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,15);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,256);
  const item=tools.find(t=>t.id==='nextflow');assert.equal(item.implementation_status,'upstream-adapter-validated');
  assert.equal(item.source_roles_reviewed,10);assert.equal(item.project_adapter_available,true);assert.equal(item.canonical_adapter_available,false);
  assert.equal(item.development_adapter_cli,'nextflow-tools');assert.equal(item.runtime_profiles.length,3);
  assert.equal(item.whole_paper_reproduced,false);assert.equal(item.biological_accuracy_certified,false);assert.equal(item.paper_exact_unknown_settings_available,false);
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [
   ['P002',[/21\.04\.1/,/2\.4\.4/,/citation/]],['P008',[/scRNA/,/scATAC/,/ISS/,/unstated/]],
   ['P013',[/distiller/i]],['P016',[/Java\s*8/i,/Docker/]],['P027',[/24\.04\.4/,/1\.0\.4/]],
   ['P036',[/DuplexPipe/,/fgbio/i]],['P143',[/MHC\s*Hammer/,/HLA/]],['P156',[/22\.10\.6/,/SLURM/]],
   ['P169',[/1\.2\.1/,/engine/]],['P227',[/4778/,/4732/,/5591/]]
  ]) {
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^Nextflow(?:reported|\s|$)/})});
   assert.equal(await row.count(),1,id);const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern,id);
  }
  report.checks.push('All ten primary-source Nextflow roles retain exact editions, unknown settings and component boundaries');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Nextflow');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Nextflow(?:\s|\()/})});
  assert.equal(await card.count(),1);const text=await card.innerText();
  for(const p of [/nextflow-tools/,/21\.04\.1/,/22\.10\.6/,/24\.04\.4/,/166/,/36/,/10 papers/,/Windows/,/immutable/i,/whole/i])assert.match(text,p);
  await card.screenshot({path:path.join(out,'nextflow-card-desktop.png')});report.screenshots.push('nextflow-card-desktop.png');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');
  const actions=page.locator('#nextflow-required-actions');assert(await actions.isVisible());
  for(const p of [/SKIP3/,/skip dependent analysis/,/FAIL2/,/no result is admitted/,/Required action/,/WARNINGS.md/,/FAILURE.json/,/COMPLETED.json/,/--verify/,/--allow-install/,/--allow-download/,/Analysis never installs/,/130 filesystem permission/,/immutable output copies/i,/failed latest attempt/,/hash-bound proven state/,/remote scheduler/,/not authenticity/,/Cell Ranger remains excluded/])assert.match(await actions.innerText(),p);
  await actions.screenshot({path:path.join(out,'nextflow-actions-desktop.png'),style:'header { visibility:hidden !important; }'});report.screenshots.push('nextflow-actions-desktop.png');
  await page.setViewportSize({width:390,height:844});await page.emulateMedia({colorScheme:'dark'});
  await actions.scrollIntoViewIfNeeded();assert(await actions.isVisible());
  const bounds=await actions.boundingBox();assert(bounds.x>=0&&bounds.x+bounds.width<=391);
  await actions.screenshot({path:path.join(out,'nextflow-actions-mobile-dark.png'),style:'header { visibility:hidden !important; }'});report.screenshots.push('nextflow-actions-mobile-dark.png');
  assert.equal(errors.length,0,errors.join('\n'));
  report.checks.push('64 new scoped integrations,67 including three preexisting adapters,256 missing; older release remains distinct',
   'Desktop and390px dark warnings name dependencies, setup permissions, SKIP3/FAIL2, immutable outputs and recovery without hiding paper limitations');
  fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,screenshots:report.screenshots.length}));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
