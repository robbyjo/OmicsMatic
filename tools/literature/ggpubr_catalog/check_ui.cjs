// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
// Inspect every current ggpubr source role and the complete required-action notice.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const [modules,executablePath,output,base]=process.argv.slice(2);const {chromium}=require(path.join(modules,'playwright'));fs.mkdirSync(output,{recursive:false});
(async()=>{
 const browser=await chromium.launch({executablePath,headless:true});const page=await browser.newPage({viewport:{width:1440,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto(base+'/literature-review.html');await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');assert.equal(await page.locator('#results article').count(),250);
  const overview=await page.locator('#overview').innerText();for(const word of ['432 named tools','2671 tool observations'])assert(overview.includes(word),word);
  const notice=page.locator('#ggpubr-required-actions');assert.equal(await notice.count(),1);assert(await notice.evaluate(e=>e.compareDocumentPosition(document.querySelector('#overview'))&Node.DOCUMENT_POSITION_FOLLOWING));
  const sources=[
   ['P040','10.1038/s41586-026-10954-1',['0.6.0','signed-rank','unpaired','Pearson','unchanged']],
   ['P054','10.1038/s41586-025-09794-2',['0.6.0','No specific ggpubr function','per-figure','Illustrator']],
   ['P058','10.1038/s41586-023-06817-8',['unreported','QuPath','RNA-ISH','CellPose','not three proven command runs']],
   ['P087','10.1038/s41588-026-02774-w',['unreported','stat_compare_means','unpaired two-sample','Variance assumptions']],
   ['P098','10.1038/s41588-026-02745-1',['0.4.0','violin','does not identify','no ggpubr test or R version']],
   ['P101','10.1038/s41588-023-01571-z',['0.4.0','base R','agricolae','not reassigned']],
   ['P195','10.1016/j.ajhg.2024.10.003',['0.4.0','Mann-Whitney','does not name','NMF']],
   ['P231','10.1016/j.ajhg.2026.02.020',['unreported','No individual plot','R 4.4.0']],
   ['P241','10.1016/j.ajhg.2024.05.023',['unreported','ggplot2 or ggpubr','111','not another execution']]];
  for(const [id,doi,words] of sources){
   await page.locator('#query').fill(doi);assert.equal(await page.locator('#results article').count(),1);
   const row=page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^ggpubr/})});assert.equal(await row.count(),1);const text=await row.innerText();
   for(const word of [...words,'private Linux/WSL R 4.3.3','not a standalone JAR','eligibility'])assert(text.includes(word),id+': '+word);
   if(['P058','P098','P101'].includes(id)){await row.scrollIntoViewIfNeeded();await row.screenshot({path:path.join(output,id+'-current-role.png')});}
  }
  await page.locator('#query').fill('');await page.locator('#tools-tab').click();assert.equal(await page.locator('#results article').count(),432);
  for(const [title,words,file] of [
   ['ggpubr (9 papers)',['upstream-adapter-validated','0.4.0','0.6.0','not a standalone JAR','79','171,084','11,513'],'ggpubr-card.png'],
   ['rstatix (6 papers)',['upstream-adapter-validated','0.7.2','native Java Levene','wilcoxon_test'],'unchanged-rstatix-card.png'],
   ['QuPath (12 papers)',['upstream-adapter-validated','0.5.1','H-DAB','StarDist'],'unchanged-qupath-card.png'],
   ['AllelicSeries / COAST-SS (1 paper)',['missing','1.1.2','0.1.1.2','not a standalone JAR'],'unchanged-allelicseries-card.png']]){
   const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:title,exact:true})});assert.equal(await card.count(),1);const text=await card.innerText();for(const word of words)assert(text.includes(word),title+': '+word);await card.scrollIntoViewIfNeeded();await card.screenshot({path:path.join(output,file)});
  }
  await page.goto(base+'/literature-warnings.html');const warnings=page.locator('#ggpubr-required-actions');const text=await warnings.innerText();
  for(const word of ['432','2,671','1,156','1,515','109','264','20 native','89 upstream','112','SKIP3','FAIL2','Computation failed in','unavailable, not zero','silent','warn=2','Cell Ranger ARC','retry NEW paths','no new original analysis'])assert(text.toLowerCase().includes(word.toLowerCase()),word);
  await warnings.scrollIntoViewIfNeeded();await warnings.screenshot({path:path.join(output,'warnings-desktop.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await warnings.scrollIntoViewIfNeeded();assert(await warnings.isVisible());assert(await warnings.evaluate(e=>e.scrollWidth<=e.clientWidth+1));await warnings.screenshot({path:path.join(output,'warnings-mobile.png'),style:'header,site-header,.skip-link{visibility:hidden!important}'});assert.deepEqual(errors,[]);
  const result={status:'PASS_NINE_GGPUBR_VISIBLE_SOURCE_ROLES_LITERAL_VERSIONS_ELIGIBILITY_AND_PROMINENT_WARNINGS_UI',papers:250,named_tools:432,observations:2671,reviewed_uses:1156,unreviewed_uses:1515,new_scoped_integrations:109,missing:264,ggpubr_source_rows_checked:9,native_family_credit_added:0,unrelated_family_credit_unchanged:true,desktop:'1440x1000',mobile_dark:'390x1000',warning_overflow:false,browser_errors:errors,public_site_deployed:false};fs.writeFileSync(path.join(output,'COMPLETED.json'),JSON.stringify(result,null,2)+'\n');console.log(result.status);
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
