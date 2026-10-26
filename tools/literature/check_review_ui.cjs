// Local browser verification; requires Playwright and a Chromium/Edge executable.
const fs = require('fs');
const path = require('path');
const assert = require('assert/strict');
const modules = process.argv[2];
const browserPath = process.argv[3];
const {chromium} = modules ? require(path.join(modules, 'playwright')) : require('playwright');
const root = path.resolve(__dirname, '../..');
const papers = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/papers.json'), 'utf8'));
const out = path.join(root, 'build/literature-review/ui');
fs.mkdirSync(out, {recursive: true});

(async () => {
  const browser = await chromium.launch({executablePath: browserPath, headless: true});
  const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  try {
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    assert.equal(await page.locator('#results article').count(), 250);
    assert.match(await page.locator('#overview').innerText(), /376 named tools/);
    assert.match(await page.locator('#overview').innerText(), /2422 tool observations/);
    assert.match(await page.locator('#P001 a[data-link]').first().getAttribute('href'), /^https:\/\/doi.org\/10\./);
    await page.screenshot({path: path.join(out, 'catalog-desktop.png')});
    await page.locator('#query').fill('10.1038/s41586-025-10058-2');
    assert.equal(await page.locator('#results article').count(), 1);
    await page.locator('#query').fill('10.1038/s41586-025-09890-3');
    assert.equal(await page.locator('#results article').count(), 1);
    const reviewedTsne = page.locator('tr').filter({hasText: 't-SNE'});
    assert.match(await reviewedTsne.innerText(), /perplexity 80/);
    assert.match(await reviewedTsne.innerText(), /coordinate parity/);
    await page.locator('#query').fill('');
    await page.locator('#journal').selectOption('Nature');
    assert.equal(await page.locator('#results article').count(), 71);
    await page.locator('#journal').selectOption('');
    await page.locator('#tools-tab').click();
    await page.locator('#coverage').selectOption('native-subset-validated');
    assert.equal(await page.locator('#results article').count(), 12);
    assert.match(await page.locator('#results').innerText(), /t-SNE/);
    await page.locator('#query').fill('tximport');
    assert.equal(await page.locator('#results article').count(), 1);
    assert.match(await page.locator('#results').innerText(), /204/);
    assert.match(await page.locator('#results').innerText(), /15615/);
    assert.match(await page.locator('#results').innerText(), /fractional estimates/i);
    assert.match(await page.locator('#results').innerText(), /length offset/);
    assert.match(await page.locator('#results').innerText(), /TXIMPORT_WORKFLOWS/);
    await page.locator('#query').fill('UCSC liftOver');
    const lift=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^UCSC liftOver \(/})});
    assert.equal(await lift.count(),1);assert.match(await lift.innerText(),/HTSJDK/);assert.match(await lift.innerText(),/dbSNP/);assert.match(await lift.innerText(),/no preferred alternative hit/);
    await page.locator('#query').fill('seqtk');
    assert.equal(await page.locator('#results article').count(), 1);
    assert.match(await page.locator('#results').innerText(), /MT19937/);
    assert.match(await page.locator('#results').innerText(), /620/);
    await page.locator('#query').fill('');
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    assert.equal(await page.locator('#results article').count(), 31);
    for (const [name, pattern] of [['clusterProfiler',/enrichit0\.2\.6/],['fgsea',/1\.38\.0/],['DOSE',/negative first-hit/]]) {
      await page.locator('#query').fill(name);
      const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:new RegExp('^'+name+' \\(')})});
      assert.equal(await card.count(),1);assert.match(await card.innerText(),pattern);assert.match(await card.innerText(),/ENRICHMENT_WORKFLOWS/);assert.match(await card.innerText(),/Native score alone computes no NES\/P/);
    }
    await page.locator('#query').fill('kallisto');
    const kallisto=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^kallisto \(/})});
    assert.equal(await kallisto.count(),1);
    for (const pattern of [/0\.48\.0/,/20\.5\+20\.5/,/27/,/HDF5/,/run-info-audited/,/KALLISTO_WORKFLOWS/]) assert.match(await kallisto.innerText(),pattern);
    await page.locator('#query').fill('Trimmomatic');
    const trim=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Trimmomatic \(/})});
    assert.equal(await trim.count(),1);assert.match(await trim.innerText(),/4236/);assert.match(await trim.innerText(),/TRIMMOMATIC_WORKFLOWS/);assert.match(await trim.innerText(),/0\.32\/0\.36/);
    await page.locator('#query').fill('MAGMA');
    const magma=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MAGMA \(/})});
    assert.equal(await magma.count(),1);assert.match(await magma.innerText(),/raw0\/1/);assert.match(await magma.innerText(),/804/);assert.match(await magma.innerText(),/MAGMA_WORKFLOWS/);assert.match(await magma.innerText(),/FUMA\/R wrappers/);
    await page.locator('#query').fill('');
    for (const [name, pattern] of [['Scrublet', /invalid.sqrt/], ['sctransform', /post-fit regression/]]) {
      await page.locator('#query').fill(name);
      const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:new RegExp('^'+name+' \\(')})});
      assert.equal(await card.count(),1);assert.match(await card.innerText(),pattern);assert.match(await card.innerText(),/SINGLE_CELL_QC_WORKFLOWS/);
    }
    await page.locator('#query').fill('');
    for (const [name, pattern] of [['featureCounts', /read\/fragment units/], ['minimap2', /MD overrides cs/], ['HISAT2', /213 compiler warnings/]]) {
      await page.locator('#query').fill(name);
      const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:new RegExp('^'+name+' \\(')})});
      assert.equal(await card.count(),1);assert.match(await card.innerText(),pattern);assert.match(await card.innerText(),/full.paper|paper\/version/);
    }
    await page.locator('#query').fill('');
    for (const tool of ['Cutadapt', 'FastQC', 'MultiQC', 'Picard', 'SAMtools', 'BCFtools', 'BWA', 'Bowtie', 'GATK', 'STAR', 'REGENIE']) assert.match(await page.locator('#results').innerText(), new RegExp(tool));
    await page.locator('#query').fill('REGENIE');
    const regenie=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^REGENIE \(/})});
    assert.equal(await regenie.count(),1);assert.match(await regenie.innerText(),/4.1.3/);assert.match(await regenie.innerText(),/Firth\/SPA/);assert.match(await regenie.innerText(),/sum\/comphet/);assert.match(await regenie.innerText(),/HTP\/REMETA/);
    await page.locator('#query').fill('');
    await page.locator('#coverage').selectOption('native-subset-validated');
    await page.locator('#query').fill('MACS');
    const macs=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MACS \(/})});
    assert.equal(await macs.count(),1);
    assert.match(await macs.innerText(),/0xc0000005/);
    assert.match(await macs.innerText(),/Native Java genomic-signal/);
    assert.match(await macs.innerText(),/129/);
    assert.match(await macs.innerText(),/track-peaks/);
    await page.locator('#query').fill('');assert.equal(await page.locator('#results article').count(),12);
    await page.locator('#query').fill('FLASH');
    const flash=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FLASH \(/})});
    assert.equal(await flash.count(),1);
    for(const pattern of [/Native Java FLASH 1\.2\.11/,/53 packaged calls/,/5,185/,/score denominator|scoring denominator/,/barcode/,/No external runtime/,/FLASH_WORKFLOWS/]) assert.match(await flash.innerText(),pattern);
    await page.locator('#coverage').selectOption('missing');
    for (const name of ['VIPER','aREA']) {
      await page.locator('#query').fill(name);
      const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:new RegExp('^'+name+' \\(')})});
      assert.equal(await card.count(),1);assert.match(await card.innerText(),/eligibility remains pending/);assert.match(await card.innerText(),/No restricted VIPER runtime/);
    }
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    await page.locator('#query').fill('FIMO');
    const fimo=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FIMO \(/})});
    assert.equal(await fimo.count(),1);
    for(const pattern of [/FIMO 5\.5\.9/,/native Java/,/no MEME runtime/,/q-estimator/,/truncation/,/FIMO_WORKFLOWS/])assert.match(await fimo.innerText(),pattern);
    await page.locator('#coverage').selectOption('missing');
    await page.locator('#query').fill('HMMRATAC');
    const hmm=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^HMMRATAC \(/})});
    assert.equal(await hmm.count(),1);assert.match(await hmm.innerText(),/missing/);
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    await page.locator('#query').fill('GATK');
    const gatk=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^GATK \(/})});
    assert.equal(await gatk.count(),1);assert.match(await gatk.innerText(),/GenotypeGVCFs/);assert.match(await gatk.innerText(),/4.7.0.0/);
    await page.locator('#query').fill('STAR');
    const star=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^STAR \(/})});
    assert.equal(await star.count(),1);assert.match(await star.innerText(),/GeneCounts/);assert.match(await star.innerText(),/outQuant/);assert.match(await star.innerText(),/BySJout/);
    await page.locator('#query').fill('Bowtie');
    assert.equal(await page.locator('#results article').count(), 1);
    assert.match(await page.locator('#results').innerText(), /dovetail/);
    await page.locator('#query').fill('BWA');
    const bwa = page.locator('#results article').filter({has: page.getByRole('heading', {name: /^BWA \(/})});
    assert.equal(await bwa.count(), 1);
    assert.match(await bwa.innerText(), /0.7.19/);
    await page.locator('#query').fill('HTSlib');
    assert.equal(await page.locator('#results article').count(), 2);
    assert.match(await page.locator('#results').innerText(), /uniform ploidy/);
    await page.locator('#query').fill('Picard');
    assert.match(await page.locator('#results').innerText(), /3.5.0/);
    assert.match(await page.locator('#results').innerText(), /CLI 4/);
    await page.locator('#query').fill('harmonypy');
    assert.equal(await page.locator('#results article').count(), 1);
    assert.match(await page.locator('#results').innerText(), /Harmony/);
    assert.match(await page.locator('#results').innerText(), /R harmony 2.0.5/);
    await page.locator('#query').fill('');
    await page.locator('#coverage').selectOption('');
    await page.locator('#query').fill('BWA-MEM2');
    const mem2 = page.locator('#results article').filter({has: page.locator('h2', {hasText: 'BWA-MEM2'})});
    assert.equal(await mem2.count(), 1);
    assert.match(await mem2.innerText(), /missing/);
    await page.locator('#coverage').selectOption('missing');await page.locator('#query').fill('STARsolo');
    const solo=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^STARsolo \(/})});
    assert.equal(await solo.count(),1);assert.match(await solo.innerText(),/barcode\/UMI/);assert.match(await solo.innerText(),/missing/);
    await page.locator('#coverage').selectOption('');
    await page.locator('#query').fill('');
    await page.locator('#coverage').selectOption('method-overlap');
    await page.locator('#coverage').selectOption('native-subset-validated');
    await page.locator('#query').fill('qnorm');
    const quantile=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^qnorm \(/})});
    assert.equal(await quantile.count(),1);assert.match(await quantile.innerText(),/61403/);assert.match(await quantile.innerText(),/float32/);assert.match(await quantile.innerText(),/QNORM_WORKFLOWS/);
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    await page.locator('#query').fill('deepTools');
    const deep=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^deepTools \(/})});
    assert.equal(await deep.count(),1);assert.match(await deep.innerText(),/4064/);assert.match(await deep.innerText(),/canonical validated executable/);assert.match(await deep.innerText(),/DEEPTOOLS_WORKFLOWS/);assert.match(await deep.innerText(),/ALL optional tags/);assert.match(await deep.innerText(),/58 actual upstream workflow calls/);assert.match(await deep.innerText(),/eight original 3.5.6 workflows/);
    await page.locator('#coverage').selectOption('');
    await page.locator('#query').fill('');
    await page.locator('#papers-tab').click();
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P110').doi);assert.match(await page.locator('#P110').innerText(),/methylation around genes/);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P212').doi);assert.match(await page.locator('#P212').innerText(),/length-scaled TPM/);assert.match(await page.locator('#P212').innerText(),/count-scale output/);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P134').doi);assert.match(await page.locator('#P134').innerText(),/biomaRt/);assert.match(await page.locator('#P134').innerText(),/ShortRead/);assert.match(await page.locator('#P134').innerText(),/ARACNe/);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P170').doi);
    assert.equal(await page.locator('#P170 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^qnorm(?:\s|$)/})}).count(),0,'R inverse-normal function is not the Python qnorm package');
    for(const [id,pattern] of [['P091',/forward reads with HEADCROP10/],['P171',/MINLEN36, SLIDINGWINDOW4:20/],['P226',/TPM\/count wording/]]){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);
      assert.match(await page.locator('#'+id).innerText(),pattern);
    }
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P029').doi);
    assert.equal(await page.locator('#P029 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^FLASH(?:\s|$)/})}).count(),0,'Varioskan FLASH plate reader is not the paired-read merger');
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P163').doi);
    assert.match(await page.locator('#results').innerText(), /bdgpeakcall/);
    assert.match(await page.locator('#results').innerText(), /raw signal cutoff 2/);
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P204').doi);
    assert.equal(await page.locator('#P204 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^REGENIE(?:\s|$)/})}).count(),0);
    for(const id of ['P019','P094','P120','P121','P152','P201','P242','P249']){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);
      assert.equal(await page.locator('#'+id+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^UCSC liftOver(?:\s|$)/})}).count(),0,id+' must not acquire a direct UCSC execution claim');
    }
    for(const [id,name] of [['P019','MungeSumstats'],['P094','rtracklayer'],['P201','liftOver R workflow'],['P242','CrossMap']]){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);assert.match(await page.locator('#'+id).innerText(),new RegExp(name));
    }
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P242').doi);assert.match(await page.locator('#P242').innerText(),/100 random vectors/);assert.match(await page.locator('#P242').innerText(),/25 focal SNPs/);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P123').doi);assert.match(await page.locator('#P123').innerText(),/executable-use-not-specified|not specify direct command execution/);
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P147').doi);
    assert.match(await page.locator('#results').innerText(), /omitting binary, Firth and SPA flags/);
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P207').doi);
    const regenieRow=page.locator('#P207 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^REGENIE(?:\s|$)/})});
    await regenieRow.locator('summary').click();
    assert.match(await regenieRow.innerText(),/named tool anchor:/);
    assert.match(await regenieRow.innerText(),/paragraph 22/);
    assert.match(await regenieRow.innerText(),/Shared method: SBAT/);
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P023').doi);
    assert.match(await page.locator('#results').innerText(), /hmmratac/);
    await page.locator('#query').fill(papers.find(p => p.paper_id === 'P053').doi);
    assert.match(await page.locator('#results').innerText(), /BAMPE/);
    assert.match(await page.locator('#results').innerText(), /shift/);
    await page.locator('#query').fill('10.1038/s41586-026-11053-x');
    assert.match(await page.locator('#results').innerText(), /Removed PCR duplicates/);
    assert.match(await page.locator('#results').innerText(), /reported 3.0.0/);
    assert.match(await page.locator('#results').innerText(), /supplied-junction second-pass/);
    await page.locator('#query').fill('10.1038/s41586-025-09853-8');
    assert.match(await page.locator('#results').innerText(), /BySJout/);assert.match(await page.locator('#results').innerText(), /CLAPAnalysis/);
    await page.locator('#query').fill('10.1038/s41588-024-02019-8');
    assert.match(await page.locator('#results').innerText(), /STARsolo/);assert.match(await page.locator('#results').innerText(), /WASP/);
    await page.locator('#query').fill('10.1038/s41586-023-06872-1');
    assert.match(await page.locator('#results').innerText(), /mapping quality at least 20/);
    await page.locator('#query').fill('10.1016/j.ajhg.2026.06.005');
    assert.match(await page.locator('#results').innerText(), /50% and 25%/);
    await page.locator('#query').fill('10.1038/s41588-025-02367-z');
    await page.locator('#P164 tbody tr').filter({hasText: 'seqtk'}).locator('summary').click();
    assert.match(await page.locator('#results').innerText(), /Supporting paragraph 35/);
    assert.match(await page.locator('#results').innerText(), /Supporting paragraph 37/);
    await page.locator('#query').fill('10.1038/s41586-023-06831-w');
    assert.match(await page.locator('#results').innerText(),/haploid ploidy/);
    await page.locator('#query').fill('10.1038/s41586-024-08420-x');
    assert.match(await page.locator('#results').innerText(),/Legacy indel realignment/);
    await page.locator('#query').fill('');
    await page.setViewportSize({width: 390, height: 900});
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1);
    assert.equal(overflow, false, 'Mobile catalog must not overflow horizontally');
    const mobilePaperLabelWidth=(await page.locator('#P001 th').first().boundingBox()).width;
    assert(mobilePaperLabelWidth>=50,'Mobile paper label column must stay readable: '+mobilePaperLabelWidth);
    await page.screenshot({path: path.join(out, 'catalog-mobile.png')});
    await page.evaluate(() => document.documentElement.dataset.theme = 'dark');
    const darkContrast=await page.evaluate(()=>{
      const luminance=css=>{const c=css.match(/[0-9.]+/g).slice(0,3).map(Number).map(v=>v/255).map(v=>v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4);return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2];};
      const ratio=(a,b)=>{const x=luminance(a),y=luminance(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);};
      const button=getComputedStyle(document.querySelector('#papers-tab')),input=getComputedStyle(document.querySelector('#query')),placeholder=getComputedStyle(document.querySelector('#query'),'::placeholder');
      return {tabs:ratio(button.color,button.backgroundColor),query_placeholder:ratio(placeholder.color,input.backgroundColor)};
    });
    assert(darkContrast.tabs>=4.5&&darkContrast.query_placeholder>=4.5,JSON.stringify(darkContrast));
    await page.screenshot({path: path.join(out, 'catalog-mobile-dark.png')});
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    assert.match(await page.locator('main').innerText(), /ENRICHMENT DEPENDENCIES REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /ENRICHMENT INFERENCE AND FAMILY REQUIRE REVIEW/);
    assert.match(await page.locator('main').innerText(), /VIPER\/aREA ORIGINAL-ENGINE VALIDATION IS HELD/);
    assert.match(await page.locator('main').innerText(), /install_enrichment_dependencies.R/);
    assert.match(await page.locator('main').innerText(), /skipped-dependency-unavailable/);
    assert.match(await page.locator('main').innerText(), /MSVC/);
    assert.match(await page.locator('main').innerText(), /PICARD VALIDATION ERRORS REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /uniform ploidy/);
    assert.match(await page.locator('main').innerText(), /allow-source-build/);
    assert.match(await page.locator('main').innerText(), /BWA-MEM2/);
    assert.match(await page.locator('main').innerText(), /FALLBACK CAN RETAIN POOR-QUALITY/);
    assert.match(await page.locator('main').innerText(), /NO TERMINAL REPEAT HIT/);
    assert.match(await page.locator('main').innerText(), /GVCF IS AN INTERMEDIATE/);
    assert.match(await page.locator('main').innerText(), /CHOOSE MISSING-ANNOTATION/);
    assert.match(await page.locator('main').innerText(), /MULTIQC PRESERVES SOURCE QC/);
    assert.match(await page.locator('main').innerText(), /RECORDED STAR SOURCE FIX/);
    assert.match(await page.locator('main').innerText(), /STAR GENE COUNTS REQUIRE LIBRARY-STRAND CHOICE/);
    assert.match(await page.locator('main').innerText(), /STAR FILTER THRESHOLDS BOTH APPLY/);
    assert.match(await page.locator('main').innerText(), /MACS CALLPEAK FAILED/);
    assert.match(await page.locator('main').innerText(), /0xc0000005/);
    assert.match(await page.locator('main').innerText(), /NATIVE GENOMIC SIGNAL REQUIRES EXPLICIT/);
    assert.match(await page.locator('main').innerText(), /NATIVE TRACK PEAKS ARE A SUPPLIED-SCORE SUBSET/);
    assert.match(await page.locator('main').innerText(), /MACS_RUNTIME_STATUS.md/);
    assert.match(await page.locator('main').innerText(), /REGENIE MODEL AND DEPENDENCY CHOICES REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /install_regenie.py --allow-download --allow-source-build/);
    assert.match(await page.locator('main').innerText(), /COORDINATE CONVERSION REQUIRES EXPLICIT REFERENCES AND UNMAPPED HANDLING/);
    assert.match(await page.locator('main').innerText(), /aligned SOURCE bases, not sequence identity/);
    assert.match(await page.locator('main').innerText(), /RNA\/FEATURE COUNTING REQUIRES EXPLICIT LIBRARY CHOICES/);
    assert.match(await page.locator('main').innerText(), /2\/213 compiler warnings/);
    assert.match(await page.locator('main').innerText(), /RNA_ALIGNMENT_COUNTING_WORKFLOWS.md/);
    assert.match(await page.locator('main').innerText(), /SINGLE-CELL MODEL WARNINGS REQUIRE REVIEW/);
    assert.match(await page.locator('main').innerText(), /SCTRANSFORM LAYERS AND RUNTIMES/);
    assert.match(await page.locator('main').innerText(), /SINGLE_CELL_QC_WORKFLOWS.md/);
    assert.match(await page.locator('main').innerText(), /MAGMA PRIVATE RUNTIME AND PHENOTYPE CODING REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /TRIMMOMATIC ORDER, MATE AND ENCODING REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /TRIMMOMATIC\/JAVA UNAVAILABLE: SKIP AFFECTED UPSTREAM ANALYSIS/);
    assert.match(await page.locator('main').innerText(), /DEEPTOOLS DEPENDENCY AND DATA-LOSS ACTIONS/);
    assert.match(await page.locator('main').innerText(), /TRANSCRIPT IMPORT UNITS AND LENGTH CORRECTION REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /KALLISTO RUNTIME AND OUTPUT REQUIRE ACTION/);
    assert.match(await page.locator('main').innerText(), /KALLISTO MODEL, ESTIMATES AND METADATA REQUIRE REVIEW/);
    assert.match(await page.locator('main').innerText(), /NO HDF5 output/);
    assert.match(await page.locator('main').innerText(), /install_kallisto.py --allow-download/);
    assert.match(await page.locator('main').innerText(), /SKIP affected analysis/);
    assert.match(await page.locator('main').innerText(), /Reconstructed counts must not also receive a length offset/);
    assert.match(await page.locator('main').innerText(), /MAGMA GENE UNIVERSE AND LD\/N CHOICES/);
    assert.match(await page.locator('main').innerText(), /DROPS SEQ,QUAL AND ALL OPTIONAL SAM TAGS/);
    assert.match(await page.locator('main').innerText(), /administrator-prepared profile/);
    assert.match(await page.locator('main').innerText(), /DEEPTOOLS FIGURES REQUIRE PRESENTATION REVIEW/);
    assert.match(await page.locator('main').innerText(), /constant correlation columns/);
    assert.match(await page.locator('main').innerText(), /SVG retains DejaVuSans/);
    const repository = JSON.parse(fs.readFileSync(path.join(root, 'docs/literature/omics-2023-2026/repository-validation.json'), 'utf8'));
    if (repository.status === 'shared-checkout-changed-during-passing-gate') {
      assert.match(await page.locator('main').innerText(), /SHARED BUILD PASSED, BUT SOURCES CHANGED/);
      assert.match(await page.locator('main').innerText(), /does not certify the latest shared checkout/);
    }
    assert.equal(errors.length, 0, errors.join('\n'));
    const report = {status: 'passed', checks: ['250 paper cards', 'resolved primary DOI links', 'DOI search', 'reviewed t-SNE settings and scope', 'journal filter', 'seven native subsets including reference-validated seqtk methods', 'fourteen upstream adapters including typed STAR and GATK workflows', 'STARsolo barcode/UMI and WASP/Hornet remain distinct missing integrations', 'reviewed STAR BySJout/manual junction settings and CLAPAnalysis-derived pipeline scope', 'prominent STAR source fix, strand-column and combined-mate threshold warnings', 'reviewed sequence simulations and multi-paragraph curation pointers', 'native sequence fallback/no-hit recovery', 'reviewed GATK haploid and legacy model boundaries', 'gVCF/missing-annotation/Windows diagnostics and QC warning promotion', 'BWA and Bowtie version/paired scope', 'BWA-MEM2 remains a distinct missing integration', 'SAMtools/BCFtools HTSlib and calling-model scope', 'reviewed SAMtools duplicate/selection settings', 'reviewed Picard PCR duplicate use and published version', 'Picard validation error recovery', 'Harmony R success and Python skip scope', 'mobile overflow', 'light/dark rendering', 'prominent actionable warning page including source builds', 'no page JavaScript errors'], screenshots: ['catalog-desktop.png', 'catalog-mobile.png', 'catalog-mobile-dark.png']};
    report.checks = report.checks.map(c => c.replace('seven native subsets', 'eight native subsets including threshold track calling'));
    report.checks = report.checks.map(c => c.replace('eight native subsets including threshold track calling', 'ten native subsets including qnorm columns, chain mapping and threshold track calling'));
    report.checks = report.checks.map(c => c.replace('fourteen upstream adapters', 'fifteen upstream adapters including REGENIE fit/variant/gene-mask subset'));
    report.checks.push('available-tool lists excluded as execution evidence; explicit REGENIE quantitative flags retained', 'reviewed method details show a hash-checked named-tool anchor and shared specific method');
    report.checks.push('native chain/coordinate subset with independent dbSNP truth and explicit reference/unmapped/minMatch scope', 'UCSC execution separated from R wrappers, CrossMap chain URLs, lifted resources, prior eligibility and custom mapping', 'reviewed SME/FAME random-vector settings and harmonizer procedure-versus-executable distinction');
    report.checks.push('MACS native supplied-track subset distinct from failed callpeak runtime; HMMRATAC missing', '129 actual bdgpeakcall references and reviewed raw cutoff/paired model scope', 'native genomic signal and peak reference/missing/precision warnings', 'passing shared snapshot distinguished from concurrent source changes');
    report.checks = report.checks.map(c => c.replace('fifteen upstream adapters', 'eighteen upstream adapters including featureCounts/minimap2/HISAT2'));
    report.checks.push('featureCounts read/fragment/NH/fraction/primary and Rsubread API boundaries', 'minimap2 MD/cs and split-index conflicts are prominent', 'HISAT2 sealed index and compiler warning scope', 'RNA runtime setup, native helper guide and downstream pipeline limits');
    report.checks = report.checks.map(c => c.replace('eighteen upstream adapters', 'twenty upstream adapters'));
    report.checks.push('original Scrublet and distinct vst/Seurat models with scope and source warning', 'single-cell runtime, estimate/layer and threshold recovery is prominent');
    report.checks = report.checks.map(c => c.replace('twenty upstream adapters', 'twenty-one upstream adapters'));
    report.checks.push('MAGMA original private runtime/native mapping and804reference rows are scoped', 'MAGMA raw zero coding, LD/N/background choices and runtime recovery are prominent');
    report.checks = report.checks.map(c => c.replace('twenty-one upstream adapters', 'twenty-two upstream adapters'));
    report.checks.push('Trimmomatic4236actual references, native13steps, original0.39/0.41 and historical version scope', 'literal step/mate/Phred order, MAXINFO and Java/runtime recovery are prominent');
    report.checks = report.checks.map(c => c.replace('twenty-two upstream adapters', 'twenty-three upstream adapters'));
    report.checks.push('qnorm finite column subset retains dtype/axis/model boundaries', 'deepTools native helper and eight original typed methods, sealed models and presentation limitations are scoped among23adapters', 'DeepTools2 methylation use and R qnorm function/package exclusion', 'deepTools retained native Windows failure, working prepared Cygwin profile,ATAC all-tag loss and dependency/skip recovery are prominent');
    report.checks=report.checks.map(c=>c.replace('ten native subsets','eleven native subsets including transcript import'));
    report.checks.push('tximport dense native point estimates,204original R cases/15615values and explicit length-offset/uncertainty/reference/omission scope');
    report.checks=report.checks.map(c=>c.replace('twenty-three upstream adapters','twenty-four upstream adapters').replace('among23adapters','among24adapters'));
    report.checks.push('original kallisto0.48 bulk/plaintext,actual fractional EM,sealed index/model and metadata repair scope', 'HDF5 negative capability,0.52 failed build and missing-runtime recovery remain prominent');
    report.checks=report.checks.map(c=>c.replace('twenty-four upstream adapters','twenty-eight upstream adapters').replace('among24adapters','among28adapters'));
    report.checks.push('clusterProfiler/current enrichit,original fgsea and explicit legacy DOSE scopes and native score-only helper are visible', 'enrichment q-versus-adjustedP,complete-family,signed-zero-core and exact private runtime recovery are prominent', 'VIPER/aREA remain missing with pending eligibility and no restricted-runtime execution claim');
    const p108=papers.find(p=>p.paper_id==='P108');assert(!p108.tools.some(t=>t.tool_id==='kallisto'),'compatible examples must not imply kallisto execution');
    assert(p108.tools.some(t=>t.tool_id==='salmon'),'actual Salmon execution must remain');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.waitForFunction(() => document.querySelector('#count')?.textContent === '250 of 250 papers');
    for (const [pid, patterns] of [
      ['P044', [/quality 20/, /Do not transfer/, /ATAC/]],
      ['P046', [/WGBS/, /do not infer RRBS/, /15-base/]],
      ['P099', [/--poly_a/, /default-step wording/]],
      ['P122', [/50 bases/, /ambiguous/, /minimum-length filter/, /hardtrim5/]],
      ['P163', [/35\/75-base/, /sequencing inputs/, /0\.3\.7/]]
    ]) {
      await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
      const row=page.locator('tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Trim Galore(?:reported|\s|$)/})});
      assert.equal(await row.count(),1);const text=await row.innerText();
      for (const pattern of patterns) assert.match(text,pattern);
      if (pid==='P122') {
        const cutadaptRow=page.locator('tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Cutadapt(?:reported|\s|$)/})});
        assert.equal(await cutadaptRow.count(),1);assert.match(await cutadaptRow.innerText(),/wrapper\/Cutadapt attribution ambiguous/);
        await page.setViewportSize({width:390,height:6000});
        await page.evaluate(() => window.scrollTo(0,0));
        await page.locator('#P122').screenshot({path:path.join(out,'trim-galore-source-context.png')});
        await page.setViewportSize({width:390,height:900});
      }
    }
    await page.locator('#tools-tab').click();
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    await page.locator('#query').fill('Trim Galore');
    const originalTrim=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Trim Galore \(/})});
    assert.equal(await originalTrim.count(),1);
    for (const pattern of [/unmodified Perl 0\.6\.7\/0\.6\.10/,/Cutadapt 5\.2/,/25 packaged/,/ASCII/,/UTF8 options-file/,/skip with exit 3/,/primer caveat/,/No RRBS/,/TRIM_GALORE_WORKFLOWS/]) assert.match(await originalTrim.innerText(),pattern);
    await page.setViewportSize({width:390,height:900});
    await page.evaluate(() => { document.activeElement?.blur(); window.scrollTo(0,0); });
    const originalTrimLayout=await page.evaluate(() => ({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert(originalTrimLayout.content<=originalTrimLayout.viewport,JSON.stringify(originalTrimLayout));
    // A tall viewport keeps this complete card visible without the sticky
    // navigation being composited over an element taller than the viewport.
    await page.setViewportSize({width:390,height:3000});
    await page.evaluate(() => window.scrollTo(0,0));
    await originalTrim.screenshot({path:path.join(out,'trim-galore-original-workflow.png')});
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    for (const pattern of [/ORIGINAL TRIM GALORE REQUIRES ACTION/,/ANALYSIS|SKIP exit3/,/ASCII recipe path/,/Analysis never installs/,/TRIM GALORE ORIGINAL EDGE CASES AND QC/,/rescue precedes maximum length/]) assert.match(await page.locator('main').innerText(),pattern);
    assert.equal(errors.length,0);
    report.mobile_layout={paper_label_width:mobilePaperLabelWidth,tool_card_viewport:originalTrimLayout.viewport,tool_card_document_width:originalTrimLayout.content};
    report.tool_card_screenshot='element-only;width390px;tall3000px capture viewport;layout checked at390x900';
    report.screenshots.push('trim-galore-source-context.png','trim-galore-original-workflow.png');
    report.checks.push('Trim Galore RNA/ATAC options,WGBS/RRBS,default/poly-A,crop/filter,input-length and version-attribution distinctions remain visible;bounded original workflow card and prominent skip/error/Unicode/ASCII/rescue actions are verified');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('native-subset-validated');await page.locator('#query').fill('FLASH');
    const nativeFlash=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FLASH \(/})});
    await page.setViewportSize({width:390,height:900});
    const flashLayout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));assert(flashLayout.content<=flashLayout.viewport);
    await page.setViewportSize({width:390,height:3000});await page.evaluate(()=>window.scrollTo(0,0));
    await nativeFlash.screenshot({path:path.join(out,'flash-native-workflow.png')});
    await page.locator('#papers-tab').click();await page.locator('#coverage').selectOption('');
    for(const [id,patterns] of [['P091',[/forward read only/,/score denominator/,/version and quality encoding are unstated/i]],['P007',[/empirically/,/does not compute q-values/,/missing allele match with zero/]],['P068',[/100,000/,/q-value, not a P-value/]],['P215',[/Do not transfer HOMER/,/E-value cutoff/]],['P217',[/P < 0\.001/,/5\.5\.4/]]]){
      await page.locator('#query').fill(papers.find(p=>p.paper_id===id).doi);for(const pattern of patterns)assert.match(await page.locator('#'+id).innerText(),pattern);
    }
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    for(const pattern of [/NATIVE FLASH HEADER\/INPUT REVIEW/,/FIMO SOURCE THRESHOLDS ARE DISTINCT/,/no external runtime/,/quarantined partials/,/FIMO RUNTIME AND INFERENCE REQUIRE ACTION/,/stored-hit/i])assert.match(await page.locator('main').innerText(),pattern);
    assert.equal(errors.length,0);report.flash_mobile_layout=flashLayout;report.screenshots.push('flash-native-workflow.png');
    report.checks.push('Native FLASH workflow card,53packagedcalls,5185originalpairs,barcode/header/scoring/no-runtime/failure actions and390pxlayout;FIMO custom empirical versus original P/q/HOMER thresholds remain distinct');
    report.checks=report.checks.map(c=>c.replace('eleven native subsets','twelve native subsets including FLASH read merging'));
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.locator('#tools-tab').click();
    await page.locator('#coverage').selectOption('upstream-adapter-validated');
    await page.locator('#query').fill('FIMO');
    const currentFimo=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^FIMO \(/})});
    const fimoText=await currentFimo.innerText();
    for(const pattern of [/motif-scan/,/fimo-tools/,/72/,/6805|6,805/,/96/,/31643|31,643/,/48/,/32/,/16/,/256/,/No-Q/,/PSP/,/FIMO_VALIDATION_STATUS/])assert.match(fimoText,pattern);
    assert.doesNotMatch(fimoText,/No motif science|implementation pending/);
    await page.setViewportSize({width:390,height:900});
    const fimoLayout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert(fimoLayout.content<=fimoLayout.viewport,JSON.stringify(fimoLayout));
    await page.setViewportSize({width:390,height:6000});
    await page.evaluate(()=>window.scrollTo(0,0));
    await currentFimo.screenshot({path:path.join(out,'fimo-workflow.png')});
    await page.evaluate(()=>document.documentElement.dataset.theme='dark');
    await currentFimo.screenshot({path:path.join(out,'fimo-workflow-dark.png')});
    await page.evaluate(()=>document.documentElement.dataset.theme='light');
    await page.locator('#papers-tab').click();await page.locator('#coverage').selectOption('');
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P007').doi);
    const fimoSource=page.locator('#P007 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^FIMO(?:reported|\s|$)/})});
    assert.equal(await fimoSource.count(),1);await fimoSource.locator('summary').click();
    for(const pattern of [/paragraph 55/,/Supporting paragraph 53/,/Supporting paragraph 56/,/Supporting paragraph 71/,/empirically/,/does not compute q-values/,/missing allele match with zero/])assert.match(await fimoSource.innerText(),pattern);
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    const warningText=await page.locator('main').innerText();
    for(const pattern of [/FIMO RUNTIME AND INFERENCE REQUIRE ACTION/,/FIMO SOURCE THRESHOLDS ARE DISTINCT/,/install_fimo\.py/,/--reference-home/,/--allow-install/,/--allow-download/,/exit ?3/,/quarantine/,/stored-hit/i,/no-Q|no-qvalue/i,/placeholder/,/pi0|small-cohort/,/FIMO_WORKFLOWS/])assert.match(warningText,pattern);
    assert.doesNotMatch(warningText,/FIMO ORIGINAL ANALYSIS UNAVAILABLE HERE/);
    await page.setViewportSize({width:1440,height:1100});
    await page.setViewportSize({width:390,height:900});
    const warningLayout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert(warningLayout.content<=warningLayout.viewport,JSON.stringify(warningLayout));
    await page.setViewportSize({width:1440,height:1100});
    const fimoWarning=page.locator('#fimo-required-actions');
    assert.equal(await fimoWarning.count(),1);assert.equal(await fimoWarning.isVisible(),true);
    for(const pattern of [/FIMO: required actions/,/skip the original analysis \(exit 3\)/,/fail with exit 2/,/Analysis never installs software/,/q-values are blank/,/NEW_PRIVATE_HOME/])assert.match(await fimoWarning.innerText(),pattern);
    report.fimo_warning_mobile_layout=warningLayout;
    await fimoWarning.screenshot({path:path.join(out,'fimo-warning-actions.png')});
    assert.equal(errors.length,0,errors.join('\n'));
    report.fimo_mobile_layout=fimoLayout;
    report.screenshots.push('fimo-workflow.png','fimo-workflow-dark.png','fimo-warning-actions.png');
    report.checks=report.checks.map(c=>c.replace('twenty-eight upstream adapters','twenty-nine upstream adapters').replace('among28adapters','among29adapters'));
    report.checks.push('FIMO validated original adapter and independent native Java PSSM/P helper expose distinct model,reference counts,formats and q-value limits',
      'FIMO source expansion retains four hash-checked paragraphs and custom empirical calibration versus original P/q/HOMER boundaries',
      'FIMO missing-profile and explicit install/download recovery,stored-hit quarantine,no-Q placeholders and small-population estimator actions are prominent',
      'FIMO light/dark complete-card screenshots and 390px overflow check pass');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    for (const [pid,absent] of [['P024','PLINK'],['P106','PLINK'],['P136','PLINK'],['P152','PLINK 2'],['P213','PLINK']]) {
      await page.locator('#query').fill(papers.find(p=>p.paper_id===pid).doi);
      const row=page.locator('#'+pid+' tbody tr').filter({has:page.locator('td:first-child').filter({hasText:new RegExp('^'+absent+'(?:reported|\\s|$)')})});
      // PLINK2 has its own label; the generic PLINK expression must be exact.
      const names=await page.locator('#'+pid+' tbody tr td:first-child').allTextContents();
      assert(!names.some(n=>n.replace(/reported[\s\S]*/,'').trim()===absent),pid+' format/release identity '+absent);
    }
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P112').doi);
    const releaseOne=page.locator('#P112 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^PLINKreported 1\.9$/})});
    const releaseTwo=page.locator('#P112 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^PLINK 2reported 2\.0$/})});
    assert.equal(await releaseOne.count(),1);assert.equal(await releaseTwo.count(),1);
    assert.match(await releaseOne.innerText(),/Yale-Penn/);assert.match(await releaseTwo.innerText(),/MVP/);
    await page.setViewportSize({width:1440,height:2400});
    await releaseOne.screenshot({path:path.join(out,'plink-release1-source.png')});
    await releaseTwo.screenshot({path:path.join(out,'plink-release2-source.png')});
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P076').doi);
    const p76=page.locator('#P076 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^PLINK 2/})});
    assert.match(await p76.innerText(),/quantitative-trait/);await p76.locator('summary').click();
    for(const pattern of [/paragraph 54/,/Supporting paragraph 51/,/Supporting paragraph 53/,/Supporting paragraph 55/,/Supporting paragraph 56/])assert.match(await p76.innerText(),pattern);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P224').doi);
    const p224=page.locator('#P224 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^PLINK 2/})});
    assert.match(await p224.innerText(),/2\.00a3LM/);assert.doesNotMatch(await p224.locator('td').first().innerText(),/LM56/);
    assert.match(await p224.innerText(),/Appendix GWASs/);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P192').doi);
    for(const pattern of [/CADD-SV/,/25\.7/,/standalone SIFT/,/lookup differs from model execution/])assert.match(await page.locator('#P192').innerText(),pattern);
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P176').doi);
    assert.match(await page.locator('#P176').innerText(),/GEMMA fits association/);assert.match(await page.locator('#P176').innerText(),/1\.9\.0/);assert.match(await page.locator('#P176').innerText(),/2\.0\.0/);
    await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('method-overlap');await page.locator('#query').fill('PLINK 2');
    const plinkCard=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^PLINK 2 \(/})});
    assert.equal(await plinkCard.count(),1);
    for(const pattern of [/adapter remains in progress/,/PLINK_SOURCE_REVIEW/,/PlinkBedLdClumper/])assert.match(await plinkCard.innerText(),pattern);
    for(const pattern of [/genotype-score/,/GENOTYPE_SCORE_WORKFLOWS/,/supplied effect-allele frequencies/,/all-missing unreportability/])assert.match(await plinkCard.innerText(),pattern);
    const scoredOverlap=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'),'utf8')).filter(t=>['plink','plink2'].includes(t.id));
    assert.equal(scoredOverlap.length,2);for(const tool of scoredOverlap){assert.equal(tool.implementation_status,'method-overlap');assert.equal(tool.project_adapter_available,true);assert.equal(tool.canonical_adapter_available,false);assert.equal(tool.development_adapter_cli,'plink-tools');assert.equal(tool.native_helper_cli,'genotype-score');}
    await page.setViewportSize({width:390,height:900});
    const plinkLayout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));assert(plinkLayout.content<=plinkLayout.viewport);
    await page.setViewportSize({width:390,height:3000});await page.evaluate(()=>window.scrollTo(0,0));
    await plinkCard.screenshot({path:path.join(out,'plink-overlap-card.png')});
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    const plinkActions=page.locator('#plink-source-actions');assert.equal(await plinkActions.isVisible(),true);
    const scoreActions=page.locator('#genotype-score-actions');assert.equal(await scoreActions.isVisible(),true);for(const pattern of [/needs only Java/,/blank unreportable scores/,/conditional reference expectations/,/quarantine/,/GENOTYPE_SCORE_WORKFLOWS/])assert.match(await scoreActions.innerText(),pattern);
    for(const pattern of [/development adapter/,/640 MiB/,/no-mean-imputation/,/firth-fallback/,/P200/,/P019/,/P112/,/P242/,/skip analysis/,/PLINK_SOURCE_REVIEW/])assert.match(await plinkActions.innerText(),pattern);
    await page.setViewportSize({width:1440,height:1100});
    await plinkActions.screenshot({path:path.join(out,'plink-required-actions.png')});
    assert.equal(errors.length,0,errors.join('\n'));
    report.plink_mobile_layout=plinkLayout;
    report.screenshots.push('plink-release1-source.png','plink-release2-source.png','plink-overlap-card.png','plink-required-actions.png');
    report.checks.push('PLINK explicit release2 replaces generic-only spelling and format-only/URL-only observations are absent',
      'PLINK1.9 Yale-Penn and PLINK2 MVP source roles coexist,with exact release labels and separate screenshots',
      'PLINK2 cohort/model/supporting paragraphs,alpha-build citation boundary and GEMMA-versus-QC scope remain visible',
      'SIFT-through-VEP and source CADD-SV score remain annotations with explicit execution limits',
      'PLINK overlap card retains pending adapter status and a readable top warning names version-specific option/resource and replay-unit/model choices');
    report.checks.push('Native supplied-weight genotype-score is visible on both PLINK overlap cards without an upstream adapter badge','Top native score actions explain explicit frequencies,denominators,unreportability and actionable quarantine recovery');
    report.toolbar_dark_contrast=darkContrast;
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('Beagle');
    const beagle=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Beagle \(/})});
    assert.equal(await beagle.count(),1);
    for(const pattern of [/5\.4-29Oct24/,/4\.1-21Jan17/,/176 focused tests/,/forty-two actual packaged/,/950,400/,/allow-rephase/,/Never reconstruct GT from GP/,/canonical a104 is unchanged/,/BEAGLE_WORKFLOWS/])assert.match(await beagle.innerText(),pattern);
    await page.setViewportSize({width:1440,height:1800});await beagle.screenshot({path:path.join(out,'beagle-workflow.png')});
    await page.evaluate(()=>document.documentElement.dataset.theme='dark');await beagle.screenshot({path:path.join(out,'beagle-workflow-dark.png')});
    await page.evaluate(()=>document.documentElement.dataset.theme='light');await page.setViewportSize({width:390,height:900});
    report.beagle_mobile_layout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert(report.beagle_mobile_layout.content<=report.beagle_mobile_layout.viewport);
    await page.setViewportSize({width:1440,height:1200});await page.locator('#papers-tab').click();await page.locator('#coverage').selectOption('');
    await page.locator('#query').fill(papers.find(p=>p.paper_id==='P209').doi);
    const beagleSource=page.locator('#P209 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^Beagle(?:reported|\s|$)/})});
    assert.equal(await beagleSource.count(),1);await beagleSource.locator('summary').click();
    for(const pattern of [/reported 5\.4/,/paragraph 36/,/Supporting paragraph 31/,/Supporting paragraph 37/,/reused earlier Beagle 5\.2/])assert.match(await beagleSource.innerText(),pattern);
    await beagleSource.screenshot({path:path.join(out,'beagle-P209-source.png')});
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');
    const beagleWarning=page.locator('#beagle-required-actions');assert.equal(await beagleWarning.isVisible(),true);
    for(const pattern of [/Beagle: required actions/,/5ed54e44/,/skip analysis \(exit 3\)/,/Analysis never installs software/,/--allow-install/,/--allow-download/,/preserve-provided/,/allow-rephase/,/GT separate from rounded GP/,/exit 2/,/new directory/])assert.match(await beagleWarning.innerText(),pattern);
    await beagleWarning.screenshot({path:path.join(out,'beagle-warning-actions.png')});
    await page.setViewportSize({width:390,height:900});report.beagle_warning_mobile_layout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));
    assert(report.beagle_warning_mobile_layout.content<=report.beagle_warning_mobile_layout.viewport);
    assert.equal(errors.length,0,errors.join('\n'));
    report.screenshots.push('beagle-workflow.png','beagle-workflow-dark.png','beagle-P209-source.png','beagle-warning-actions.png');
    report.checks=report.checks.map(c=>c.replace('twenty-nine upstream adapters','thirty upstream adapters').replace('among29adapters','among30adapters'));
    report.checks.push('Beagle four-profile validated development command is distinct from older canonical full gate and a public release',
      'Beagle phase/exclusion, exact runtime install/download permissions, skip/failure and GT-versus-rounded-GP actions are prominent',
      'Beagle P209 actual new5.4 and reused5.2 source pointers remain distinct',
      'Beagle light/dark screenshots and 390px catalog/warning overflow checks pass',
      'PLINK typed development subsets are available while broader family remains method-overlap');
    await page.goto('http://127.0.0.1:8768/literature-review.html');
    await page.setViewportSize({width:1440,height:1600});await page.locator('#tools-tab').click();await page.locator('#coverage').selectOption('upstream-adapter-validated');await page.locator('#query').fill('PRS-CS');
    const prscs=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^PRS-CS \(/})});assert.equal(await prscs.count(),1);
    for(const pattern of [/prs-cs-tools/,/source-fbc9ec0/,/1\.1\.0/,/218 focused tests/,/30 native/,/208 output files/,/Twelve actual packaged/,/520posterior/,/offline-reproduction/,/unknown.numerical|unknown\/numerical|unknown or numerical/i,/canonical a104 is unchanged/,/PRSCS_WORKFLOWS/])assert.match(await prscs.innerText(),pattern);
    await prscs.screenshot({path:path.join(out,'prscs-workflow.png')});await page.evaluate(()=>document.documentElement.dataset.theme='dark');await prscs.screenshot({path:path.join(out,'prscs-workflow-dark.png')});
    await page.setViewportSize({width:390,height:900});report.prscs_mobile_layout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));assert(report.prscs_mobile_layout.content<=report.prscs_mobile_layout.viewport);
    await page.evaluate(()=>document.documentElement.dataset.theme='light');await page.setViewportSize({width:1440,height:1200});await page.locator('#papers-tab').click();await page.locator('#coverage').selectOption('');await page.locator('#query').fill(papers.find(p=>p.paper_id==='P074').doi);
    const prscsSource=page.locator('#P074 tbody tr').filter({has:page.locator('td:first-child').filter({hasText:/^PRS-CS(?:reported|\s|$)/})});assert.equal(await prscsSource.count(),1);await prscsSource.locator('summary').click();
    for(const pattern of [/2021-Jun-4/,/paragraph 53/,/Supporting paragraph 54/,/Supporting paragraph 68/,/MGBB/,/1e-6/,/LDpred2/])assert.match(await prscsSource.innerText(),pattern);await prscsSource.screenshot({path:path.join(out,'prscs-P074-source.png')});
    await page.goto('http://127.0.0.1:8768/literature-warnings.html');const prscsWarning=page.locator('#prscs-required-actions');assert.equal(await prscsWarning.isVisible(),true);
    for(const pattern of [/PRS-CS: required actions/,/33b26e76/,/skip analysis \(exit 3\)/,/Analysis never installs software/,/--allow-install/,/--allow-download/,/NEW_PRIVATE_HOME/,/offline-reproduction/,/out of support/,/unknown or numerical warnings fail/i,/exit 2/,/new directory/,/seven significant digits/])assert.match(await prscsWarning.innerText(),pattern);
    await prscsWarning.screenshot({path:path.join(out,'prscs-warning-actions.png')});await page.setViewportSize({width:390,height:900});report.prscs_warning_mobile_layout=await page.evaluate(()=>({viewport:innerWidth,content:document.documentElement.scrollWidth}));assert(report.prscs_warning_mobile_layout.content<=report.prscs_warning_mobile_layout.viewport);assert.equal(errors.length,0,errors.join('\n'));
    report.screenshots.push('prscs-workflow.png','prscs-workflow-dark.png','prscs-P074-source.png','prscs-warning-actions.png');report.checks=report.checks.map(c=>c.replace('thirty upstream adapters','31 upstream adapters').replace('among30adapters','among31adapters'));
    report.checks.push('PRS-CS three-profile development command discloses218focused tests,actual original/package comparisons and separate canonical history',
      'PRS-CS install/download permissions,skip/failure,unsupported historical Python and retained known versus rejected unknown/numerical warnings are prominent',
      'PRS-CS exact format,N/units,model/sampling/panel/allele/exclusion and downstream prediction-assessment choices remain explicit',
      'PRS-CS P074 primary and grid/version supporting paragraphs retain date-label and LDpred2 tuning distinctions',
      'PRS-CS light/dark cards and390px catalog/warning overflow checks pass');
    fs.writeFileSync(path.join(out, 'ui-validation.json'), JSON.stringify(report, null, 2) + '\n');
    console.log(JSON.stringify(report));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
