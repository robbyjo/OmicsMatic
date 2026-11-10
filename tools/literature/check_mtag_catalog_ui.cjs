// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=process.env.JLINALG_REVIEW_UI_OUT;
(async()=>{
  const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[],checks=[];
  page.on('pageerror',e=>errors.push(e.message));
  try{
    const data=n=>JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026',n)));
    const inventory=data('tool-inventory.json'),papers=data('papers.json');assert.equal(inventory.length,402);assert.equal(papers.length,250);
    for(const [status,n] of Object.entries({'native-subset-validated':19,'upstream-adapter-validated':79,'existing-adapter':3,'missing':245}))assert.equal(inventory.filter(t=>t.implementation_status===status).length,n);
    const tool=inventory.find(t=>t.id==='mtag');assert.equal(tool.paper_count,5);assert.equal(tool.source_roles_reviewed,5);assert.equal(tool.root_command,'mtag-tools');assert.equal(tool.canonical_adapter_available,false);assert.deepEqual(tool.supported_source_versions,['1.0.8']);
    assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');checks.push('250 papers/402 tools;98 new scoped integrations/101 including existing;245 missing;five MTAG roles;Cell Ranger excluded');
    await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    for(const name of ['mtag','genesis','snprelate'])assert.equal(await page.locator('#'+name+'-required-actions').count(),1);checks.push('MTAG and preserved GENESIS/SNPRelate action sections remain prominent');
    await page.locator('#tools-tab').click();await page.locator('#query').fill('MTAG');
    const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MTAG \(/})});assert.equal(await card.count(),1);
    const text=await card.innerText();for(const term of ['upstream-adapter-validated','1.0.8','mtag-tools','two-to-eight','SciPy','172625','386088','35853','whole-paper'])assert(text.includes(term),term);
    await card.screenshot({path:path.join(out,'mtag-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('MTAG card shows original version,typed route,native/source boundaries,validation population and limitations');
    await page.locator('#papers-tab').click();
    for(const id of ['P077','P088','P090','P112','P115']){
      const paper=papers.find(p=>p.paper_id===id),method=paper.tools.find(t=>t.tool_id==='mtag');await page.locator('#query').fill(paper.doi);
      const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^MTAG(?:reported|\s|$)/})});assert.equal(await row.count(),1);
      await row.getByText('Evidence locator',{exact:true}).click();const content=await row.innerText();assert(content.includes(method.use_summary));assert(content.includes(method.implementation_scope_note));assert(content.includes(method.evidence_paragraph_sha256.slice(0,12)));
      if(['P077','P088','P090'].includes(id)){assert.equal(method.version_reported_near_name,'1.0.8');assert((await row.locator('td').first().innerText()).includes('1.0.8'));}
      else{assert.equal(method.version_reported_near_name,null);assert(!(await row.locator('td').first().innerText()).includes('reported 1.0.8'));}
      if(id==='P088'){assert(content.includes('positive rg>0.7'));assert(content.includes('separate JATS equation'));assert(content.includes('1/(2*MAF*(1-MAF)*SE^2)'));}
      if(id==='P090')assert(content.includes('|rg|>0.7'));
      if(id==='P115'){assert(content.includes('downstream in LDSC'));assert(content.includes('heritability inflation'));}
      checks.push(id+' MTAG primary role/hash,reported-or-unstated version,and distinct scientific scope are visible');
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#mtag-required-actions');assert.equal(await actions.count(),1);const warnings=await actions.innerText();
    for(const term of ['SKIP3','FAIL2','NO RESULT ADMITTED','forbidden','NEW paths','exit0','Python2','no_allele_flipping','n_max','custom','3268760','P088','P115','P=0','Cell Ranger','not OS memory quotas'])assert(warnings.includes(term),term);
    const style=await actions.evaluate(e=>({border:getComputedStyle(e).borderLeftWidth,display:getComputedStyle(e).display}));assert.equal(style.border,'5px');assert.notEqual(style.display,'none');
    await actions.screenshot({path:path.join(out,'mtag-required-actions-desktop.png')});checks.push('Prominent warnings explain prerequisites,original zero-exit errors,source limitations,N definitions,default grid capacity and recovery');
    await page.setViewportSize({width:390,height:1000});await page.emulateMedia({colorScheme:'dark'});
    const layout=await page.evaluate(()=>({width:innerWidth,body:document.documentElement.scrollWidth}));assert(layout.body<=layout.width+1);await actions.screenshot({path:path.join(out,'mtag-required-actions-mobile-dark.png')});checks.push('390px dark required actions wrap without horizontal overflow');
    assert.deepEqual(errors,[]);checks.push('No browser JavaScript errors');
    fs.writeFileSync(path.join(out,'mtag-ui-verification.json'),JSON.stringify({status:'passed',checks,screenshots:['mtag-card-desktop.png','mtag-required-actions-desktop.png','mtag-required-actions-mobile-dark.png'],layout},null,2)+'\n');console.log(JSON.stringify({status:'passed',checks:checks.length}));
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
