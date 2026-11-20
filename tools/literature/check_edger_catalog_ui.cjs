// Local admitted/preview catalog checks: Playwright module, NEW empty output, base URL.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(process.argv[2]);
const out=process.argv[3],base=process.argv[4]||'http://127.0.0.1:8771';
const root=path.resolve(__dirname,'../..');
const papers=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/papers.json'),'utf8'));
assert(out&&fs.existsSync(out),'Create a NEW empty evidence output');
assert.equal(fs.readdirSync(out).length,0,'Preserve previous UI evidence');
(async()=>{
  const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  try {
    await page.goto(base+'/literature-review.html');
    await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    assert.equal(await page.locator('#results article').count(),250);
    assert.match(await page.locator('#overview').innerText(),/446 named tools/);
    assert.match(await page.locator('#overview').innerText(),/2705 tool observations/);
    const notice=page.locator('#edger-catalog-notice');
    assert.equal(await notice.count(),1);
    for(const expression of [/139 scoped integrations/,/1,520 individually reviewed/,/1,185 pending/,
        /SKIP3/,/FAIL2/,/power dispersion trend/,/technical-replicate/,/Windows R4\.6\.1/])
      assert.match(await notice.innerText(),expression);
    const firstParagraph=await notice.locator('p').first().innerText();
    assert.match(firstParagraph,/Warnings and required actions/);
    assert.match(firstParagraph,/SKIP3/);assert.match(firstParagraph,/FAIL2/);
    await page.screenshot({path:path.join(out,'desktop.png')});
    const checked=[];
    for(const pid of ['P005','P053','P087','P088','P108','P237']){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
      assert.equal(await page.locator('#results article').count(),1);
      const article=page.locator('#'+pid);
      const row=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^(?:edgeR|EdgeR)(?:reported|\b|$)/})});
      assert.equal(await row.count(),1,pid+' edgeR observation count');
      const text=await row.innerText();
      if(pid==='P005')assert.match(text,/Lipid MRM/);
      if(pid==='P053'){
        for(const expression of [/3\.36\.0/,/fold change >=2/,/fold change >=4/])assert.match(text,expression);
        await row.getByText('Evidence locator',{exact:true}).click();
        assert.match(await row.innerText(),/paragraph 73/);
      }
      if(pid==='P087')for(const expression of [/calcNormFactors/,/CPM/,/3\.42\.4/])assert.match(text,expression);
      if(pid==='P088')for(const expression of [/3\.22\.3/,/not an edgeR operation|not established|unresolved/,/Code availability/])assert.match(text,expression);
      if(pid==='P108'){
        assert.doesNotMatch(await row.locator('td').first().innerText(),/3\.40\.2/);
        assert.match(text,/paragraph 47/);assert.match(text,/GTEx paragraph 44/);
      }
      if(pid==='P237')for(const expression of [/sumTechReps/,/not exposed/,/voomLmFit/,/prior\.count=2/])assert.match(text,expression);
      checked.push({paper_id:pid,edger_rows:1});
    }
    await page.locator('#query').fill('');
    for(const [journal,n] of [['Nature',71],['Nat Genet',104],['Am J Hum Genet',75]]){
      await page.locator('#journal').selectOption(journal);
      assert.equal(await page.locator('#results article').count(),n);
    }
    await page.locator('#journal').selectOption('');await page.locator('#tools-tab').click();
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    assert.equal(await page.locator('#results article').count(),114);
    await page.locator('#query').fill('edgeR');
    const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^edgeR \(/})});
    assert.equal(await card.count(),1);
    for(const expression of [/17 papers/,/edger-tools/,/edger-normalize/,/EdgeRWorkflowCli/,
        /3\.40\.2/,/3\.42\.4/,/4\.10\.7/,/power dispersion trend/,/unsupported/])
      assert.match(await card.innerText(),expression);
    await page.locator('#query').fill('');
    for(const [status,n] of [['method-overlap',36],['missing',259],['native-subset-validated',25]]){
      await page.locator('#coverage').selectOption(status);assert.equal(await page.locator('#results article').count(),n);
    }
    await page.setViewportSize({width:390,height:844});await page.evaluate(()=>scrollTo(0,0));
    await page.screenshot({path:path.join(out,'mobile.png')});
    const box=await notice.boundingBox();assert(box&&box.x>=0&&box.x+box.width<=391);
    await page.goto(base+'/literature-warnings.html');
    assert.equal(await page.locator('#edger-catalog-notice').count(),1);
    assert.match(await page.locator('#edger-catalog-notice p').first().innerText(),/supply the named exact permitted/);
    assert.deepEqual(errors,[]);
    fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_LOCAL_EDGER_CATALOG_DESKTOP_MOBILE_SOURCE_ROLES_COUNTS_AND_WARNINGS',
      papers:250,named_tools:446,observations:2705,reviewed:1520,pending:1185,integrations:139,upstream:114,native:25,
      method_overlap:36,missing:259,paper_roles_checked:checked,warning_actions_first:true,page_errors:errors,
      website_published:false,scientific_models_run:false},null,2)+'\n');
    console.log('PASS edgeR local source roles, counts, desktop/mobile and prominent warning actions');
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1);});
