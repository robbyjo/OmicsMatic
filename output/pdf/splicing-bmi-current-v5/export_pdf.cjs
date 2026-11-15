const fs=require('node:fs');const path=require('node:path');const {pathToFileURL}=require('node:url');const {chromium}=require('playwright');
(async()=>{
 const b=path.resolve(process.argv[2]);const remote=[],errors=[];
 const browser=await chromium.launch({headless:true,executablePath:process.env.MANUSCRIPT_BROWSER});
 try{
  const page=await browser.newPage({viewport:{width:1200,height:1100}});
  await page.route(/^https?:/,r=>{remote.push(r.request().url());return r.abort()});page.on('pageerror',e=>errors.push(e.message));
  await page.goto(pathToFileURL(path.join(b,'artifacts/manuscript.html')).href);await page.evaluate(()=>document.fonts.ready);
  const check=await page.evaluate(()=>({mathStatus:document.documentElement.dataset.manuscriptMath,equations:document.querySelectorAll('.manuscript-math').length,
   errors:[...document.querySelectorAll('.manuscript-math[data-math-status="error"]')].map(e=>({tex:e.dataset.tex,error:e.title})),
   wide:[...document.querySelectorAll('.manuscript-math-display,table')].filter(e=>e.scrollWidth>e.clientWidth+2).map(e=>e.textContent.slice(0,100)),
   missingImages:[...document.images].filter(e=>!e.complete||!e.naturalWidth).map(e=>e.src),bodyText:document.body.innerText}));
  const report={...check,browser:browser.version(),node:process.version,playwright:require('playwright/package.json').version,remoteRequests:remote,pageErrors:errors};
  fs.writeFileSync(path.join(b,'logs/browser-validation.json'),JSON.stringify(report,null,2)+'\n');
  if(check.mathStatus!=='complete'||check.errors.length||check.wide.length||check.missingImages.length||remote.length||errors.length)throw new Error('Browser validation failed; inspect logs/browser-validation.json');
  const footer='<div style="font:8px Arial;width:100%;margin:0 48px;color:#60717b;display:flex;justify-content:space-between"><span>Splicing-BMI · Author-review manuscript</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>';
  await page.pdf({path:path.join(b,'previews/manuscript-unsealed.pdf'),format:'Letter',preferCSSPageSize:true,printBackground:true,displayHeaderFooter:true,headerTemplate:'<span></span>',footerTemplate:footer});
  fs.writeFileSync(path.join(b,'metadata/rendered-reading-copy.html'),await page.content());
  console.log(JSON.stringify({equations:check.equations,browser:report.browser,remoteRequests:remote.length,errors:errors.length}));
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exitCode=1});
