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
    for(const [status,count] of Object.entries({'native-subset-validated':19,'upstream-adapter-validated':78,'existing-adapter':3,'missing':246}))assert.equal(inventory.filter(t=>t.implementation_status===status).length,count);
    const gene=inventory.find(t=>t.id==='genesis'),snp=inventory.find(t=>t.id==='snprelate');
    assert.equal(gene.source_roles_reviewed,5);assert.equal(gene.paper_count,5);assert.equal(gene.implementation_status,'upstream-adapter-validated');assert.equal(gene.root_command,'genesis-tools');assert.equal(gene.canonical_adapter_available,false);assert.deepEqual(gene.supported_source_versions,['2.42.0']);
    assert.equal(snp.source_roles_reviewed,4);assert.equal(snp.root_command,'snprelate-tools');assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');
    checks.push('250 papers/402 tools/2475 observations;97 new scoped integrations/100 including existing;246 missing;Cell Ranger remains excluded');
    await page.goto('http://127.0.0.1:8768/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
    assert.equal(await page.locator('#genesis-required-actions').count(),1);assert.equal(await page.locator('#snprelate-required-actions').count(),1);checks.push('Both required-action sections are prominent on the catalog');
    await page.locator('#tools-tab').click();
    for(const [name,terms] of [['GENESIS',['upstream-adapter-validated','2.42','PC-AiR','PC-Relate','GxE','BinomiRare','genesis-tools','61','87','historical']],['SNPRelate',['upstream-adapter-validated','1.46','KING','LD','PCA','snprelate-tools','320','DSPEVX']]]){
      await page.locator('#query').fill(name);const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:new RegExp('^'+name+' \\(')})});assert.equal(await card.count(),1);
      const text=await card.innerText();for(const term of terms)assert(text.includes(term),name+':'+term);
      await card.screenshot({path:path.join(out,name.toLowerCase()+'-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push(name+' card retains exact scoped methods,source version and scientific limitations');
    }
    await page.locator('#papers-tab').click();
    for(const [id,tid] of [['P128','genesis'],['P130','genesis'],['P142','genesis'],['P191','genesis'],['P219','genesis'],['P130','snprelate'],['P170','snprelate'],['P191','snprelate'],['P216','snprelate']]){
      const paper=papers.find(p=>p.paper_id===id),method=paper.tools.find(t=>t.tool_id===tid);await page.locator('#query').fill(paper.doi);
      const name=tid==='genesis'?'GENESIS':'SNPRelate';const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+name+'(?:reported |$)')})});assert.equal(await row.count(),1);
      await row.getByText('Evidence locator',{exact:true}).click();const text=await row.innerText();assert(text.includes(method.use_summary));assert(text.includes(method.evidence_paragraph_sha256.slice(0,12)));
      if(tid==='genesis')assert(text.includes(method.implementation_scope_note));
      if(id==='P128'){assert(text.includes('Supplementary Note7'));assert(text.includes('0.044'));assert(text.includes('not attributed to the JATS paragraph'));}
      if(id==='P170')assert.equal(method.supporting_evidence.length,1);
      checks.push(id+' '+name+' primary role/hash and current scope are visible without changing the source summary');
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#genesis-required-actions');assert.equal(await actions.count(),1);const text=await actions.innerText();
    for(const term of ['SKIP3','FAIL2','NO RESULT ADMITTED','forbidden','SKIP the dependent','Required action','Analysis never installs','Windows','SPAtest3.1.2','poibin1.6','COMPoissonReg0.8.2','n.carrier','without clipping','NEW paths','effective','first-stage','not independently refit','P128','0.044','P219','Cell Ranger'])assert(text.includes(term),term);
    await actions.screenshot({path:path.join(out,'genesis-required-actions-desktop.png')});checks.push('GENESIS warnings name exact missing prerequisites,skips,known source failures,effective methods and required recovery');
    const old=await page.locator('#snprelate-required-actions').innerText();assert(old.includes('GENESIS now has a separate scoped'));assert(!old.includes('GENESIS remains missing'));assert(old.includes('downstream_eligible=false'));checks.push('Older SNPRelate warning is contextualized while its scientific caveats remain');
    const extra=await page.locator('#genesis-additional-findings').innerText();for(const term of ['R_NA','48','168','SE.G:PRS','NO RESULT ADMITTED','NEW analysis','Do not fill','unadmitted-worker'])assert(extra.includes(term),term);checks.push('Additional missing-interaction warnings retain full populations and explain explicit recovery without filling or dropping rows');
    await page.setViewportSize({width:390,height:844});await page.emulateMedia({colorScheme:'dark'});await actions.scrollIntoViewIfNeeded();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+1));
    await page.screenshot({path:path.join(out,'genesis-required-actions-mobile-dark.png')});checks.push('390px dark warnings fit without horizontal overflow');assert.deepEqual(errors,[]);checks.push('No browser page errors');
    fs.writeFileSync(path.join(out,'COMPLETED.json'),JSON.stringify({status:'PASS_GENESIS_FIVE_ROLES_AND_SNPRELATE_BROWSER_REQUIRED_ACTIONS',checks,screenshots:['genesis-card-desktop.png','snprelate-card-desktop.png','genesis-required-actions-desktop.png','genesis-required-actions-mobile-dark.png'],whole_goal_complete:false},null,2)+'\n');console.log('Passed '+checks.length+' GENESIS/SNPRelate browser checks');
  }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
