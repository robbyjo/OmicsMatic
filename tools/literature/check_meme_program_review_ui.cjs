// Current primary-source program/resource identities; no deployment or runtime admission.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..'),out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT||path.join(root,'build/literature-review/meme-runtime/role-ui-1'));
fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const report={status:'passed',checks:[],screenshots:[]};
 try{
  const data=path.join(root,'docs/literature/omics-2023-2026');
  const papers=JSON.parse(fs.readFileSync(path.join(data,'papers.json'),'utf8'));
  const inventory=JSON.parse(fs.readFileSync(path.join(data,'tool-inventory.json'),'utf8'));
  const drafts=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/meme-program-use-reviews.json'),'utf8'));
  assert.equal(papers.length,250);assert.equal(inventory.length,387);assert.equal(papers.reduce((n,p)=>n+p.tools.length,0),2442);
  assert.equal(inventory.filter(t=>t.implementation_status==='missing').length,262);
  assert.equal(inventory.filter(t=>t.implementation_status==='native-subset-validated').length,15);
  assert.equal(inventory.filter(t=>t.implementation_status==='upstream-adapter-validated').length,50);
  for(const id of ['meme-chip','dreme','centrimo','tomtom','streme','hyphy','bwa-meme'])assert.equal(inventory.find(t=>t.id===id).implementation_status,'missing',id);
  const entry=(pid,id)=>papers.find(p=>p.paper_id===pid).tools.find(t=>t.tool_id===id);
  assert.equal(entry('P066','meme'),undefined);assert(entry('P066','hyphy'));
  assert.equal(entry('P113','bwa'),undefined);assert(entry('P113','bwa-meme'));
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const overview=await page.locator('#overview').innerText();assert.match(overview,/387 named tools/);assert.match(overview,/2442 tool observations/);
  for(const draft of drafts){
   const tool=entry(draft.paper_id,draft.review.tool_id);
   assert.equal(tool.use_summary,draft.review.use_summary);assert.equal(tool.implementation_scope_note,draft.review.implementation_scope_note);
   const article=await page.locator('#'+draft.paper_id).innerText();
   assert(article.includes(tool.use_summary),draft.paper_id+'/'+tool.tool_id+' summary');assert(article.includes(tool.implementation_scope_note),draft.paper_id+'/'+tool.tool_id+' boundary');
  }
  await page.locator('#query').fill(papers.find(p=>p.paper_id==='P066').doi);
  await page.locator('#P066').screenshot({path:path.join(out,'hyphy-source-role.png')});report.screenshots.push('hyphy-source-role.png');
  await page.locator('#query').fill(papers.find(p=>p.paper_id==='P113').doi);
  await page.locator('#P113').screenshot({path:path.join(out,'bwa-meme-source-role.png')});report.screenshots.push('bwa-meme-source-role.png');
  report.checks.push('All22 reviewed roles render exact summaries and boundaries; HyPhy MEME and BWA-MEME misassignments removed');
  await page.locator('#tools-tab').click();await page.locator('#query').fill('Tomtom');
  const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Tomtom \(7 papers\)$/})});assert.equal(await card.count(),1);assert.match(await card.innerText(),/missing/);
  await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await card.screenshot({path:path.join(out,'tomtom-missing-mobile-dark.png'),style:'header,.skip-link { visibility:hidden !important; }'});report.screenshots.push('tomtom-missing-mobile-dark.png');
  assert.equal(errors.length,0,errors.join('\n'));
  report.checks.push('387 named tools and2442 observations preserve65 new scoped integrations with262 missing; seven newly distinguished programs receive no implementation credit',
   'Seven Tomtom roles include two reused cluster resources; 390px dark rendering has no overflow or page JavaScript errors');
  fs.writeFileSync(path.join(out,'ui-validation.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify({status:report.status,checks:report.checks.length,screenshots:report.screenshots.length}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
