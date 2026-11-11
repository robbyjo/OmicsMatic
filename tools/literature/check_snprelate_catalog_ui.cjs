// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=process.env.JLINALG_REVIEW_UI_OUT;
(async()=>{
  const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
  const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[],checks=[];page.on('pageerror',e=>errors.push(e.message));
  try{
    const data=n=>JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026',n)));
    const inventory=data('tool-inventory.json'),papers=data('papers.json');assert.equal(inventory.length,402);assert.equal(papers.length,250);
    for(const [status,count] of Object.entries({'native-subset-validated':19,'upstream-adapter-validated':77,'existing-adapter':3,'missing':247}))assert.equal(inventory.filter(t=>t.implementation_status===status).length,count);
    const tool=inventory.find(t=>t.id==='snprelate');assert.equal(tool.source_roles_reviewed,4);assert.equal(tool.paper_count,4);assert.equal(tool.canonical_adapter_available,false);assert.equal(tool.root_command,'snprelate-tools');
    assert.equal(inventory.find(t=>t.id==='genesis').implementation_status,'missing');assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
    checks.push('250 papers/402 tools/2475 observations;96 new scoped integrations/99 including existing;247 missing;GENESIS missing and Cell Ranger excluded');
    await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    assert.equal(await page.locator('#snprelate-required-actions').count(),1);
    await page.locator('#tools-tab').click();await page.locator('#query').fill('SNPRelate');
    const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^SNPRelate \(/})});assert.equal(await card.count(),1);
    assert.deepEqual(tool.supported_source_versions,['1.46.0']);
    const shown=await card.innerText();for(const term of ['upstream-adapter-validated','1.46','KING','LD','PCA','snprelate-tools','320','DSPEVX','independently refit'])assert(shown.includes(term),term);
    await card.screenshot({path:path.join(out,'snprelate-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('SNPRelate card shows four workflows,source version,complete-case controls and independent-refit limits');
    await page.locator('#papers-tab').click();
    for(const [id,tid] of [['P128','genesis'],['P130','snprelate'],['P130','genesis'],['P142','genesis'],['P170','snprelate'],['P191','snprelate'],['P191','genesis'],['P216','snprelate'],['P219','genesis']]){
      const paper=papers.find(p=>p.paper_id===id),method=paper.tools.find(t=>t.tool_id===tid);await page.locator('#query').fill(paper.doi);
      const name=tid==='snprelate'?'SNPRelate':'GENESIS';
      const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+name+'(?:reported |$)')})});assert.equal(await row.count(),1);
      await row.getByText('Evidence locator',{exact:true}).click();const text=await row.innerText();assert(text.includes(method.use_summary));assert(text.includes(method.evidence_paragraph_sha256.slice(0,12)));
      if(id==='P170')assert.equal(method.supporting_evidence.length,1);
      checks.push(id+' '+name+' exact reviewed role and original primary paragraph hash are visible');
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#snprelate-required-actions');assert.equal(await actions.count(),1);
    const warnings=await actions.innerText();for(const term of ['SKIP3','FAIL2','forbidden','skip the dependent','Required action','Analysis never installs','Windows R4.6.1','downstream_eligible=false','P170','GENESIS remains missing','Cell Ranger'])assert(warnings.includes(term),term);
    await actions.screenshot({path:path.join(out,'snprelate-required-actions-desktop.png')});checks.push('Prominent warnings explain dependency skips,failure recovery,unchanged source NaNs and paper/version limits');
    await page.setViewportSize({width:390,height:844});await page.emulateMedia({colorScheme:'dark'});await actions.scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));
    await page.screenshot({path:path.join(out,'snprelate-required-actions-mobile-dark.png')});checks.push('390px dark required actions fit without horizontal overflow');assert.deepEqual(errors,[]);checks.push('No browser page errors');
    fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_SNPRELATE_NINE_ROLES_BROWSER_AND_REQUIRED_ACTIONS',checks,screenshots:['snprelate-card-desktop.png','snprelate-required-actions-desktop.png','snprelate-required-actions-mobile-dark.png'],whole_goal_complete:false},null,2)+'\n');
    console.log('Passed '+checks.length+' SNPRelate/GENESIS browser checks');
  }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
