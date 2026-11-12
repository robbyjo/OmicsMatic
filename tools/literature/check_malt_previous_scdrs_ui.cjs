// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=process.env.JLINALG_REVIEW_UI_OUT;
(async()=>{
  const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[],checks=[];
  page.on('pageerror',e=>errors.push(e.message));
  try{
    const read=n=>JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026',n)));
    const inventory=read('tool-inventory.json'),papers=read('papers.json');
    assert.equal(inventory.length,405);assert.equal(papers.length,250);
    for(const [status,n] of Object.entries({'native-subset-validated':19,'upstream-adapter-validated':80,'existing-adapter':3,'missing':247}))assert.equal(inventory.filter(t=>t.implementation_status===status).length,n);
    const tool=inventory.find(t=>t.id==='scdrs');assert.equal(tool.paper_count,6);assert.equal(tool.source_roles_reviewed,6);assert.equal(tool.root_command,'scdrs-tools');assert.equal(tool.canonical_adapter_available,false);assert.deepEqual(tool.supported_source_versions,['1.0.0','1.0.1','1.0.2']);
    assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');checks.push('250 papers/405 tools;99 new/102 including existing;247 missing;six scDRS roles;Cell Ranger excluded');
    await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    for(const name of ['scdrs','mtag','genesis','snprelate'])assert.equal(await page.locator('#'+name+'-required-actions').count(),1);checks.push('scDRS and preserved predecessor required actions remain prominent');
    await page.locator('#tools-tab').click();await page.locator('#query').fill('scDRS');
    const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^scDRS \(/})});assert.equal(await card.count(),1);
    const text=await card.innerText();for(const term of ['upstream-adapter-validated','1.0.0','1.0.1','1.0.2','scdrs-tools','877998','67161','corr_mcp','whole-paper'])assert(text.includes(term),term);
    await card.screenshot({path:path.join(out,'scdrs-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('scDRS card shows exact versions,typed route,controls,native BH and scientific limits');
    const versions={P021:'1.0.1',P024:null,P055:'1.0.2',P085:'1.0.0',P123:'1.0.2',P169:'1.0.1'};
    for(const [id,version] of Object.entries(versions)){
      await page.locator('#papers-tab').click();await page.locator('#query').fill(id);assert.equal(await page.locator('#results article').count(),1);
      const article=page.locator('#results article');
      const row=article.locator('tr').filter({has:page.locator('td:first-child').filter({hasText:/^scDRS(?:reported|\s|$)/})});assert.equal(await row.count(),1);
      await row.getByText('Evidence locator',{exact:true}).click();const content=await row.innerText();
      const method=papers.find(p=>p.paper_id===id).tools.find(t=>t.tool_id==='scdrs');
      assert.equal(method.version_reported_near_name,version);assert(content.includes(method.use_summary));assert(content.includes(method.implementation_scope_note));assert(content.includes(method.evidence_paragraph_sha256.slice(0,12)));
      if(version)assert((await row.locator('td').first().innerText()).includes(version));
      else assert(!(await row.locator('td').first().innerText()).includes('reported 1.0.'));
      if(id==='P021')assert(content.includes('200 controls'));
      if(id==='P085'){assert(content.includes('Pearson'));assert(content.includes('strata'));}
      if(id==='P123'){assert(content.includes('q<0.05'));assert(content.includes('within each dataset'));}
      if(id==='P169'){assert(content.includes('scDRSs'));assert(content.includes('control count'));assert(content.includes('not stated'));assert.equal(method.supporting_evidence.length,1);}
      checks.push(id+' source-bound role/hash and reported-or-unstated settings are visible');
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#scdrs-required-actions');assert.equal(await actions.count(),1);const warning=await actions.innerText();
    for(const term of ['SKIP3','FAIL2','NO RESULT ADMITTED','forbidden','NEW paths','exit0','--allow-download','--allow-install','Pearson','corr_mcp','covariate','not OS RAM quotas','atlas benchmark','P021','200 controls','P169','Cell Ranger'])assert(warning.includes(term),term);
    const style=await actions.evaluate(e=>({border:getComputedStyle(e).borderLeftWidth,display:getComputedStyle(e).display}));assert.equal(style.border,'5px');assert.notEqual(style.display,'none');
    await actions.screenshot({path:path.join(out,'scdrs-required-actions-desktop.png')});checks.push('Warnings explain prerequisite skips,no-result failures,source limitations,statistical families,capacity and recovery');
    await page.setViewportSize({width:390,height:1000});await page.emulateMedia({colorScheme:'dark'});
    const layout=await page.evaluate(()=>({width:innerWidth,body:document.documentElement.scrollWidth}));assert(layout.body<=layout.width+1);
    await actions.screenshot({path:path.join(out,'scdrs-required-actions-mobile-dark.png')});checks.push('390px dark required actions wrap without horizontal overflow');
    assert.deepEqual(errors,[]);checks.push('No browser JavaScript errors');assert.equal(checks.length,12);
    fs.writeFileSync(path.join(out,'scdrs-ui-verification.json'),JSON.stringify({status:'passed',checks,screenshots:['scdrs-card-desktop.png','scdrs-required-actions-desktop.png','scdrs-required-actions-mobile-dark.png'],layout},null,2)+'\n');
    console.log(JSON.stringify({status:'passed',checks:checks.length}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
