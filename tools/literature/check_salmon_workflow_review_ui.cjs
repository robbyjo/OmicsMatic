// Current complete Salmon workflow catalog/role/recovery checks; no deployment.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/salmon-workflow-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const report={status:'passed',checks:[],screenshots:[]};
 try {
  const tools=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(tools.filter(t=>t.implementation_status==='upstream-adapter-validated').length,50);
  assert.equal(tools.filter(t=>t.implementation_status==='native-subset-validated').length,15);assert.equal(tools.filter(t=>t.implementation_status==='missing').length,255);
  const item=tools.find(t=>t.id==='salmon');assert.equal(item.implementation_status,'upstream-adapter-validated');assert.equal(item.source_roles_reviewed,8);
  for(const key of ['project_adapter_available','public_quantification_CLI_available','complete_quantification_output_audit_available','runtime_setup_export_available','download_install_available','controlled_original_fits_admitted'])assert.equal(item[key],true,key);
  for(const key of ['canonical_adapter_available','scientific_runtime_admitted','whole_paper_reproduced','biological_accuracy_certified','paper_exact_unknown_settings_available'])assert.equal(item[key],false,key);
  assert.equal(item.runtime_profiles.length,5);assert.equal(item.development_adapter_cli,'salmon-tools');
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
  ]){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^Salmon(?:reported|\s|$)/})});assert.equal(await row.count(),1,id);
   for(const pattern of patterns)assert.match(await row.innerText(),pattern,id);
  }
  report.checks.push('Eight source roles preserve exact editions, unknown library/optimizer settings, author code and separate downstream methods');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Salmon');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Salmon \(/})});assert.equal(await card.count(),1);
  for(const pattern of [/salmon-tools/,/449/,/36 complete controlled/,/1275/,/32720/,/five exact editions/,/source-runtime/,/false declared-retained coverage/,/Older canonical/])assert.match(await card.innerText(),pattern);
  await card.screenshot({path:path.join(out,'salmon-workflow-card-desktop.png')});report.screenshots.push('salmon-workflow-card-desktop.png');
  await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#salmon-required-actions');assert(await actions.isVisible());
  const checkContrast=async()=>{
   const ratios=await actions.evaluate(panel=>{
    const rgba=s=>{const v=s.match(/[\d.]+/g).map(Number);return [...v.slice(0,3),v[3]??1];};
    const over=(fg,bg)=>fg.slice(0,3).map((v,i)=>v*fg[3]+bg[i]*(1-fg[3]));
    const luminance=c=>c.slice(0,3).map(v=>{v/=255;return v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4;}).reduce((s,v,i)=>s+v*[0.2126,0.7152,0.0722][i],0);
    return [...panel.querySelectorAll('h2,p,strong,code')].map(el=>{
     const chain=[];for(let node=el;node;node=node.parentElement)chain.unshift(node);
     let bg=[255,255,255];for(const node of chain)bg=over(rgba(getComputedStyle(node).backgroundColor),bg);
     const text=over(rgba(getComputedStyle(el).color),bg),a=luminance(text),b=luminance(bg);
     return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
    });
   });
   assert(ratios.length>0&&Math.min(...ratios)>=4.5,'Warning text contrast must be at least4.5:1, observed '+Math.min(...ratios));
   return Math.min(...ratios);
  };
  report.warning_contrast={desktop:await checkContrast()};
  for(const pattern of [/1\.0\.0/,/1\.1\.0/,/1\.5\.2/,/1\.8\.0/,/1\.9\.0/,/SKIP3/,/skip dependent analysis/,/FAIL2/,/no result/,/Required action|Preserve every attempt/,/WARNINGS.md/,/FAILURE.json/,/COMPLETED.json/,/--verify/,/--allow-install/,/--allow-download/,/coverage=false/,/impute zeros/,/positional-bias crashes/,/SHA512/,/raw integer reads/,/portable/i,/Cell Ranger remains excluded/])assert.match(await actions.innerText(),pattern);
  await actions.screenshot({path:path.join(out,'salmon-workflow-actions-desktop.png'),style:'header { visibility:hidden !important; }'});report.screenshots.push('salmon-workflow-actions-desktop.png');
  await page.setViewportSize({width:390,height:844});await page.emulateMedia({colorScheme:'dark'});await actions.scrollIntoViewIfNeeded();const bounds=await actions.boundingBox();assert(bounds.x>=0&&bounds.x+bounds.width<=391);
  report.warning_contrast.mobile_dark=await checkContrast();
  await actions.screenshot({path:path.join(out,'salmon-workflow-actions-mobile-dark.png'),style:'header,.skip-link { visibility:hidden !important; }'});report.screenshots.push('salmon-workflow-actions-mobile-dark.png');
  assert.equal(errors.length,0,errors.join('\n'));
  report.checks.push('65 new scoped integrations,68 including existing adapters,255 missing; source command availability stays distinct from older release',
   'Desktop/mobile dark warnings retain exact runtime setup, strict/source populations, missing dependencies, original model limits and complete recovery');
  fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,screenshots:report.screenshots.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
