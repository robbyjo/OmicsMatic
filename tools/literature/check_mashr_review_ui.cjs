// Local mashr catalog/role/recovery checks; no public deployment.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/mashr-ui'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));const report={status:'passed',checks:[],screenshots:[]};
 try{
  const data=path.join(root,'docs/literature/omics-2023-2026');const tools=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));const papers=JSON.parse(fs.readFileSync(path.join(data,'papers.json'),'utf8'));
  assert.equal(tools.length,380);assert.equal(papers.length,250);assert.equal(papers.reduce((s,p)=>s+p.tools.length,0),2430);
  for(const [status,count] of [['upstream-adapter-validated',48],['native-subset-validated',14],['missing',258]])assert.equal(tools.filter(t=>t.implementation_status===status).length,count);
  const item=tools.find(t=>t.id==='mashr');assert.equal(item.source_roles_reviewed,8);assert.equal(item.project_adapter_available,true);assert.equal(item.canonical_adapter_available,false);assert.equal(item.external_analysis_runtime_required,false);assert.equal(item.original_covariance_preparation_external_runtime_required,true);assert.equal(item.summary_command_java_main,'org.jlinalg.cli.MashSummaryCli');assert.equal(item.whole_paper_reproduced,false);
  const summary=JSON.parse(fs.readFileSync(path.join(data,'summary.json'),'utf8'));assert.equal(summary.goal_status,'implementation-in-progress');
  await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  for(const [id,patterns] of [['P019',[/Z proxies/,/SE1e10/,/LFSR<=0\.05/]],['P021',[/nominal P<0\.05/,/at least two/,/scores>0\.9/]],['P024',[/scores>=0\.05/,/scores>=0\.01/,/eight disease/]],['P073',[/20,000/,/never invent signs/,/unsigned P/]],['P092',[/Supplementary Note/,/Pi-0\.5/,/1e-6/]],['P104',[/600,000/,/independent/,/AT_LEAST_ONE|ANY=/,/0\.8/]],['P201',[/random40%/,/factor0\.5/,/untested/]],['P213',[/Z scores/,/log2FC/,/Z\/SE/]]]){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^mashr(?:reported|\s|$)/})});assert.equal(await row.count(),1,id);const text=await row.innerText();for(const pattern of patterns)assert.match(text,pattern,id);
  }
  report.checks.push('Eight reviewed paper roles, exact reported scales/missingness/thresholds and unresolved conversions','62 new/65 including existing scoped integrations;14 native/48 upstream/258 missing;250 papers/2430 observations');
  await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('native-subset-validated');await page.locator('#query').fill('mashr');const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^mashr\s*\(/})});assert.equal(await card.count(),1);
  for(const p of [/native-subset-validated/,/mashr-tools/,/477/,/360/,/SQP/,/MASHR_PROXY_COMMAND/,/not inferred/])assert.match(await card.innerText(),p);
  await card.screenshot({style:'header, .skip-link {visibility:hidden !important;}',path:path.join(out,'mashr-card-desktop.png')});report.screenshots.push('mashr-card-desktop.png');
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await card.screenshot({style:'header, .skip-link {visibility:hidden !important;}',path:path.join(out,'mashr-card-mobile-dark.png')});report.screenshots.push('mashr-card-mobile-dark.png');
  for(const url of ['literature-review.html','literature-warnings.html']){
   await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8768/'+url);const action=page.locator('#mashr-required-actions');assert.equal(await action.count(),1);
   for(const p of [/SKIP3/,/FAIL2/,/Require exit 0/,/COMPLETED\.json/,/--verify/,/Java alone/,/Windows R/,/not integrate the joint posterior/,/new output paths/,/P019/,/P092/,/P104/,/1e-10|1e10/,/1e-6/,/0\.01/,/not authenticity/,/Cell Ranger remains excluded/])assert.match(await action.innerText(),p,url);
   const label=url==='literature-review.html'?'review':'warnings';await action.screenshot({style:'header, .skip-link {visibility:hidden !important;}',path:path.join(out,'mashr-'+label+'-actions-desktop.png')});report.screenshots.push('mashr-'+label+'-actions-desktop.png');
   await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await action.screenshot({style:'header, .skip-link {visibility:hidden !important;}',path:path.join(out,'mashr-'+label+'-actions-mobile-dark.png')});report.screenshots.push('mashr-'+label+'-actions-mobile-dark.png');
  }
  assert.equal(errors.length,0,errors.join('\n'));report.checks.push('Prominent dependency/proxy/FAIL2 recovery on both pages at desktop and390px dark;no horizontal overflow or page errors');fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }catch(e){fs.writeFileSync(path.join(out,'FAILURE.json'),JSON.stringify({status:'failed',error:String(e),stack:e.stack,errors},null,2)+'\n');throw e;}finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
