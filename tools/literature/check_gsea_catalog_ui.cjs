// Local admitted/preview catalog checks: Playwright module, NEW empty output, base URL.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]);
const out=process.argv[3],base=process.argv[4]||'http://127.0.0.1:8771';
const root=path.resolve(__dirname,'../..');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
assert(out&&fs.existsSync(out),'Create a NEW empty evidence output');assert.equal(fs.readdirSync(out).length,0,'Preserve previous UI evidence');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  for(const expression of [/446 named tools/,/2714 tool observations/])assert.match(await page.locator('#overview').innerText(),expression);
  const notice=page.locator('#gsea-catalog-notice');assert.equal(await notice.count(),1);
  for(const expression of [/140 scoped integrations/,/1,548 individually reviewed/,/1,166 pending/,/SKIP3/,/FAIL2/,/NO COMPLETE RESULT/,/other providers/,/2023\.1/,/timestamp/])assert.match(await notice.innerText(),expression);
  const first=await notice.locator('p').first().innerText();assert.match(first,/Warnings and required actions/);assert.match(first,/SKIP3/);assert.match(first,/FAIL2/);
  await page.screenshot({path:path.join(out,'desktop.png')});const checked=[];
  for(const pid of ['P040','P042','P051','P062','P077','P096','P098','P151','P170','P182','P220']){
   await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);assert.equal(await page.locator('#results article').count(),1);
   const article=page.locator('#'+pid),row=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^GSEA(?:reported|\b|$)/})});assert.equal(await row.count(),1,pid+' GSEA role count');const text=await row.innerText(),name=await row.locator('td').first().innerText();
   if(pid==='P040'){assert.doesNotMatch(name,/4\.3\.3/);assert.match(text,/clusterProfiler/);assert.match(text,/not.*Broad|must not.*Broad/);}
   if(pid==='P042')assert.match(text,/analysis-result|analysis-result-statement|analysis-result statement|explicit analysis-result/);
   if(pid==='P051'){assert.match(text,/tumour-core/);await row.getByText('Evidence locator',{exact:true}).click();assert.match(await row.innerText(),/paragraph 120/);}
   if(pid==='P062')assert.match(text,/GSEApy 1\.1\.8/);
   if(pid==='P077'){assert.match(name,/4\.3\.2/);assert.match(text,/supplement/);}
   if(pid==='P096'){assert.doesNotMatch(name,/2023\.1/);for(const expression of [/2023\.1/,/timestamp/,/SKIP3/,/zero adjusted P/])assert.match(text,expression);}
   if(pid==='P098'){for(const expression of [/ChEA/,/ranked.*executor|ranked-GSEA executor/])assert.match(text,expression);await row.getByText('Evidence locator',{exact:true}).click();assert.match(await row.innerText(),/paragraph 54/);}
   if(pid==='P151')for(const expression of [/MAGeCK/,/100,000/,/8,149/])assert.match(text,expression);
   if(pid==='P170'){assert.match(text,/fgsea/);await row.getByText('Evidence locator',{exact:true}).click();assert.match(await row.innerText(),/paragraph 38/);}
   if(pid==='P182')for(const expression of [/fgsea 1\.20\.0/,/10-1,500/,/clusterProfiler/])assert.match(text,expression);
   if(pid==='P220'){assert.match(name,/4\.3\.3/);for(const expression of [/10,000/,/rlog/,/null type/,/final PMC12696503/])assert.match(text,expression);}
   checked.push({paper_id:pid,gsea_rows:1});
  }
  await page.locator('#query').fill('');for(const [journal,n] of [['Nature',71],['Nat Genet',104],['Am J Hum Genet',75]]){await page.locator('#journal').selectOption(journal);assert.equal(await page.locator('#results article').count(),n);}
  await page.locator('#journal').selectOption('');await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('native-subset-validated');assert.equal(await page.locator('#results article').count(),26);await page.locator('#query').fill('GSEA');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^GSEA \(/})});assert.equal(await card.count(),1);for(const expression of [/28 papers/,/broad-gsea-tools/,/BroadGseaWorkflowCli/,/4\.3\.2/,/4\.3\.3/,/4\.4\.0/,/other providers/])assert.match(await card.innerText(),expression);
  await page.locator('#query').fill('');for(const [status,n] of [['method-overlap',35],['missing',259],['upstream-adapter-validated',114]]){await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n);}
  await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:path.join(out,'mobile.png')});const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
  await page.goto(base+'/literature-warnings.html');assert.equal(await page.locator('#gsea-catalog-notice').count(),1);assert.match(await page.locator('#gsea-catalog-notice p').first().innerText(),/supply the named exact permitted/);assert.deepEqual(errors,[]);
  fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_GSEA_CATALOG_DESKTOP_MOBILE_PROVIDER_DISTINCTIONS_COUNTS_WARNINGS',papers:250,observations:2714,reviewed:1548,pending:1166,integrations:140,native:26,upstream:114,method_overlap:35,missing:259,paper_roles_checked:checked,warning_actions_first:true,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');console.log('PASS GSEA local roles, provider distinctions, counts and desktop/mobile warnings');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
