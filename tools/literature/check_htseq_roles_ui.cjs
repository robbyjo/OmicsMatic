// Local primary-source role review; no remote publication or runtime analysis.
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const {chromium}=require(path.join(process.argv[2],'playwright'));
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.env.JLINALG_REVIEW_UI_OUT);fs.mkdirSync(out,{recursive:true});
(async()=>{
 const browser=await chromium.launch({executablePath:process.argv[3],headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 try{
  const summary=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/summary.json'),'utf8'));
  assert.equal(summary.papers,250);assert.equal(summary.paper_tool_observations,2446);
  assert.equal(summary.implementation_status_counts.missing,257);
  await page.goto('http://127.0.0.1:8768/literature-review.html');
  await page.waitForFunction(()=>document.querySelector('#count')?.textContent==='250 of 250 papers');
  const expected={P005:[/0\.6\.0/,/HT-seq/,/mm10/],P020:[/0\.5\.3/,/stranded library do not establish/],
   P044:[/0\.13\.5/,/vM25/,/log\(FPKM/],P099:[/at least one read/,/custom binary read-presence/,/not evidence/],
   P113:[/0\.11\.1/,/30, 90 and 240 kb/,/does not separately state/],P121:[/0\.13\.5/,/strict MAPQ>20/,/counting unit/],
   P148:[/hg19/,/unstated/,/Wilcoxon/],P195:[/major\/minor/,/not evidence of the 0\.13\.5/,/RF strandness/]};
  for(const [id,patterns] of Object.entries(expected)){
   const row=page.locator('#'+id+' tr').filter({has:page.locator('td:first-child').filter({hasText:/^HTSeq/})});
   assert.equal(await row.count(),1,id);const text=await row.innerText();
   for(const pattern of patterns)assert.match(text,pattern,id);
  }
  const card=page.locator('#P099');await card.scrollIntoViewIfNeeded();
  await card.screenshot({path:path.join(out,'htseq-P099-desktop.png')});
  await page.setViewportSize({width:390,height:900});
  await page.evaluate(()=>{document.documentElement.dataset.theme='dark';localStorage.setItem('theme','dark');});
  const layout=await page.evaluate(()=>({viewport:innerWidth,document:document.documentElement.scrollWidth}));
  assert.deepEqual(layout,{viewport:390,document:390});
  await card.screenshot({path:path.join(out,'htseq-P099-mobile-dark.png')});
  const horizontal=await card.locator('table').evaluate(table=>{
   let area=table.parentElement;
   while(area&&area.scrollWidth<=area.clientWidth)area=area.parentElement;
   if(!area)throw new Error('Missing horizontal table scroll area');
   area.scrollLeft=area.scrollWidth;
   return {width:area.clientWidth,content:area.scrollWidth,scrolled:area.scrollLeft};
  });
  assert.ok(horizontal.scrolled>0&&horizontal.content>horizontal.width);
  await card.screenshot({path:path.join(out,'htseq-P099-mobile-dark-scrolled.png')});
  await page.locator('#tools-tab').click();await page.locator('#query').fill('HTSeq');
  const tool=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^HTSeq\s*\(/})});
  assert.equal(await tool.count(),1);assert.match(await tool.innerText(),/8 papers/);assert.match(await tool.innerText(),/missing/);
  assert.deepEqual(errors,[]);
  const report={status:'passed',primary_roles:8,papers:250,observations:2446,reviewed_uses:898,prior_reviews_preserved:890,
   mobile_layout:layout,horizontal_table_scroll:horizontal,implementation_promoted:false,checks:['Eight visible primary HTSeq roles','Recovered P005 alias and exact reported editions',
   'Custom binary peaks, strict MAPQ and unspecified settings remain explicit','Desktop/mobile dark layout without overflow or JavaScript errors']};
  fs.writeFileSync(path.join(out,'htseq-ui-verification.json'),JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
