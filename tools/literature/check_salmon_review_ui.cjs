// Local Salmon roles/native-foundation/missing-status checks; no deployment.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/salmon-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const report={status:'passed',checks:[],screenshots:[]};
 try {
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,49);
  assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,15);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,256);
  const item=tools.find(t=>t.id==='salmon');assert.equal(item.implementation_status,'missing');assert.equal(item.source_roles_reviewed,8);
  for(const key of ['project_adapter_available','canonical_adapter_available','public_quantification_CLI_available','scientific_runtime_admitted','whole_paper_reproduced','biological_accuracy_certified'])assert.equal(item[key],false,key);
  assert.equal(item.native_inferential_decoder_API_available,true);
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [
   ['P108',[/1\.5\.2/,/1\.8\.0/,/50/,/GTEx/,/EM algorithm/,/unstated/]],
   ['P151',[/hg38/,/mm10/,/unstated/,/reused downloaded gene counts/]],
   ['P190',[/within each gene/,/percentage/,/MAJIQTL/,/unstated/]],
   ['P197',[/1\.0\.0/,/SAF Pattern/,/do not invent/]],
   ['P201',[/1\.9\.0/,/SUPPA2/,/citation 40/,/TPM/]],
   ['P223',[/StringTie/,/does not establish whether/,/Rsubread/]],
   ['P226',[/1\.8\.0/,/TPM/,/not raw integer counts/,/unresolved/]],
   ['P227',[/1\.1\.0/,/ISR/,/--useVBOpt/,/100 bootstraps/,/ambiguous|unambiguously/,/4778/,/4732/]]
  ]) {
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^Salmon(?:reported|\s|$)/})});assert.equal(await row.count(),1,id);
   const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern,id);
  }
  report.checks.push('Eight reviewed Salmon roles retain paper editions, linked author-code choices, ambiguous units and separate downstream methods');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Salmon');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Salmon \(/})});assert.equal(await card.count(),1);
  for(const pattern of [/missing/,/SalmonInferentialReplicates/,/31,600/,/27 actual-JAR/,/unfinished/,/root cause is unproven/,/not native certification/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'salmon-card-desktop.png')});report.screenshots.push('salmon-card-desktop.png');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#salmon-required-actions');assert(await actions.isVisible());
  for(const pattern of [/SKIP DEPENDENT PROJECT QUANTIFICATION/,/Stage callback output/,/complete receipt/,/position/,/Exact root cause is unproven/,/Required action/,/SKIP3/,/FAIL2/,/admits no result/,/GCC is absent/,/not a quantifier\/decoder dependency/,/TPM is not raw integer counts/,/100 bootstraps/,/integrity, not authenticity/,/Cell Ranger remains excluded/])assert.match(await actions.innerText(),pattern);
  await actions.screenshot({path:path.join(out,'salmon-actions-desktop.png'),style:'header { visibility:hidden !important; }'});report.screenshots.push('salmon-actions-desktop.png');
  await page.setViewportSize({width:390,height:844});await page.emulateMedia({colorScheme:'dark'});await actions.scrollIntoViewIfNeeded();const bounds=await actions.boundingBox();assert(bounds.x>=0&&bounds.x+bounds.width<=391);
  await actions.screenshot({path:path.join(out,'salmon-actions-mobile-dark.png'),style:'header { visibility:hidden !important; }'});report.screenshots.push('salmon-actions-mobile-dark.png');assert.equal(errors.length,0,errors.join('\n'));
  report.checks.push('Native foundation keeps Salmon missing and all implementation counts unchanged',
   'Desktop/mobile dark warnings distinguish complete native receipt, original crash, optional GCC diagnostic and unfinished public quantification');
  fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,screenshots:report.screenshots.length}));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
