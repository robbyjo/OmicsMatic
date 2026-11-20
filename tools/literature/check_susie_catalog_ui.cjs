// Focused local catalog checks. argv: Playwright module path, NEW output, [base URL].
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]);
const out=process.argv[3],base=process.argv[4]||'http://127.0.0.1:8769';
const root=path.resolve(__dirname,'../..');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
assert(out && fs.existsSync(out),'Create a NEW empty evidence output before this command');
assert.equal(fs.readdirSync(out).length,0,'Preserve existing UI evidence');
(async()=>{
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  try {
    await page.goto(base+'/literature-review.html');
    await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    assert.equal(await page.locator('#results article').count(),250);
    assert.match(await page.locator('#overview').innerText(),/446 named tools/);
    assert.match(await page.locator('#overview').innerText(),/2704 tool observations/);
    const notice=page.locator('#susie-catalog-notice');assert.equal(await notice.count(),1);
    for(const expression of [/138 new scoped integrations/,/1,503 individually reviewed/,/1,201 awaiting/,/SKIP3/,/FAIL2/,/0\.11\.43/,/Linux R/])
      assert.match(await notice.innerText(),expression);
    await page.screenshot({path:path.join(out,'desktop.png')});
    const checked=[];
    for(const [pid,count] of [['P073',1],['P203',1],['P235',0],['P095',1],['P157',1]]){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
      assert.equal(await page.locator('#results article').count(),1);
      const article=page.locator('#'+pid);
      const rows=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^SuSiE(?:reported|\b|$)/})});
      assert.equal(await rows.count(),count,pid+' cells: '+JSON.stringify(await article.locator('td:first-child').allTextContents()));
      if(pid==='P095')assert.match(await rows.innerText(),/Reused GTEx/);
      if(pid==='P157')for(const expression of [/0\.11\.43/,/FINEMAP/,/not established/])assert.match(await rows.innerText(),expression);
      checked.push({paper_id:pid,susie_rows:count});
    }
    await page.locator('#query').fill('');
    for(const [journal,n] of [['Nature',71],['Nat Genet',104],['Am J Hum Genet',75]]){
      await page.locator('#journal').selectOption(journal);assert.equal(await page.locator('#results article').count(),n);
    }
    await page.locator('#journal').selectOption('');await page.locator('#tools-tab').click();
    await page.locator('#coverage').selectOption('upstream-adapter-validated');assert.equal(await page.locator('#results article').count(),113);
    await page.locator('#query').fill('SuSiE');
    const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^SuSiE \(/})});
    assert.equal(await card.count(),1);assert.match(await card.innerText(),/20 papers/);
    for(const expression of [/SusieWorkflowCli/,/0\.11\.92/,/0\.12\.35/,/0\.14\.2/,/global susie-tools is not yet added/])assert.match(await card.innerText(),expression);
    await page.locator('#query').fill('');
    for(const [status,n] of [['method-overlap',37],['missing',259],['native-subset-validated',25]]){
      await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n);
    }
    await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));
    await page.screenshot({path:path.join(out,'mobile.png')});
    const box=await notice.boundingBox();assert(box && box.x>=0 && box.x+box.width<=391);
    await page.goto(base+'/literature-warnings.html');
    assert.equal(await page.locator('#susie-catalog-notice').count(),1);
    assert.match(await page.locator('#susie-catalog-notice').innerText(),/name what must be supplied/);
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_SUSIE_CATALOG_DESKTOP_MOBILE_CORRECTIONS_FILTERS_AND_WARNINGS',
      papers:250,named_tools:446,observations:2704,reviewed:1503,pending:1201,upstream:113,native:25,
      method_overlap:37,missing:259,paper_corrections_checked:checked,page_errors:errors,website_published:false,scientific_models_run:false},null,2)+'\n');
    console.log('PASS local catalog corrections, source roles, desktop/mobile, filters and warnings');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
