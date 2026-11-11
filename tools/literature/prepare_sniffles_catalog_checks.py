# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive additive catalog gates/UI checks; retain the prior scientific verifiers byte-exact."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def generated():
    folder=ROOT/'tools/literature';result={}
    text=(folder/'run_rsubread_catalog_checks.py').read_text(encoding='utf8')
    for old,new in [('from rsubread_catalog import','from sniffles_catalog import'),
        ('rsubread-runtime','sniffles-runtime'),("'rsubread_execution_evidence'];","'rsubread_execution_evidence','sniffles_execution_evidence'];"),
        ('rsubread_catalog.py','sniffles_catalog.py'),('current_reviews=1003','current_reviews=1009'),
        ('passed-thirty-frozen-and-additive-Rsubread-gates','passed-thirty-one-frozen-and-additive-Sniffles-gates'),
        ('Passed30 unchanged/additive gates','Passed31 unchanged/additive gates')]:
        assert old in text,old;text=text.replace(old,new)
    text=text.replace('"""Run unchanged scientific gates and the exact archived980 metadata gate, alongside current988-role checks."""',
        '"""Run30 unchanged scientific gates plus Sniffles, with exact historical980 metadata inputs."""')
    result['run_sniffles_catalog_checks.py']=text
    text=(folder/'check_rsubread_catalog_review_ui.cjs').read_text(encoding='utf8')
    assert text.count("['upstream-adapter-validated',73],['missing',251]")==1
    text=text.replace("['upstream-adapter-validated',73],['missing',251]","['upstream-adapter-validated',74],['missing',250]")
    text=text.replace('400 cataloged tools,90 new integrations,251 missing','400 cataloged tools,91 new integrations,250 missing')
    assert text.count('assert.equal(gu.length,1003)')==1
    assert text.count('assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),349)')==1
    text=text.replace('assert.equal(gu.length,1003)','assert.equal(gu.length,1009)')
    text=text.replace('assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),349)','assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),353)')
    text=text.replace('1003 roles and349 support pointers retain six GSEApy uses','1009 roles and353 support pointers retain six GSEApy uses')
    marker="fs.writeFileSync(path.join(out,'rsubread-ui-verification.json')";assert text.count(marker)==1
    extra=r'''await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8768/literature-review.html');await page.evaluate(()=>document.documentElement.dataset.theme='light');await page.locator('#tools-tab').click();await page.locator('#query').fill('Sniffles');
 const sniffles=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^Sniffles \(/i})});assert.equal(await sniffles.count(),1);const st=await sniffles.innerText();fs.writeFileSync(path.join(out,'sniffles-card-visible-text.txt'),st);for(const value of ['SnifflesWorkflowCli','1.0.7','2.0.2','2.0.7','2.3.2','2.4','legacy','alignment','upstream','whole-batch','HP/PS'])assert(st.toLowerCase().includes(value.toLowerCase()),value);await sniffles.screenshot({path:path.join(out,'sniffles-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('Sniffles card shows exact source editions, scoped original workflows, distinct legacy/modern settings and complete admission');
 const si=inventory.find(t=>t.id==='sniffles'),sp=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/sniffles-workflows/VALIDATION.json')));assert.equal(si.implementation_status,'upstream-adapter-validated');assert.equal(si.development_candidate,sp.artifact.path);assert.equal(si.development_candidate_sha256,sp.artifact.sha256);assert.equal(si.development_cli_class,'org.jlinalg.cli.SnifflesWorkflowCli');assert.equal(si.runtime_binding_cli_class,'org.jlinalg.cli.SnifflesRuntimeCli');assert.equal(si.frozen_verification_cli_class,'org.jlinalg.cli.SnifflesVerifyCli');assert.equal(si.canonical_adapter_available,false);assert.equal(si.analysis_installs_software,false);checks.push('Sniffles metadata binds the exact final standalone artifact and call/bind/verify entry points');
 await page.locator('#papers-tab').click();const sd=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/sniffles-use-reviews.json'))).reviews;assert.equal(sd.length,6);for(const item of sd){const p=papers.find(p=>p.paper_id===item.paper_id);await page.locator('#query').fill(p.doi);assert.equal(await page.locator('#results article').count(),1);const t=clean(await page.locator('#'+p.paper_id).textContent());assert(t.includes(clean(item.review.use_summary)),p.paper_id);assert(t.includes(clean(item.review.implementation_scope_note)),p.paper_id);}checks.push('all six Sniffles papers retain full reviewed usage and source-specific unresolved settings');
 const sr=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/sniffles-role-coverage.json')));assert.equal(sr.prior_reviews,1003);assert.equal(sr.unchanged_prior_reviews,1003);assert.equal(sr.new_reviews,6);assert.equal(sr.total_reviews,1009);assert.equal(sr.supporting_pointers,353);assert.equal(sr.checked_method_detail_links,1);checks.push('1009 reviews and353 pointers preserve all1003 prior reviews and the explicit P102 method-detail link');
 await page.goto('http://127.0.0.1:8768/literature-warnings.html');const sw=page.locator('#sniffles-required-actions');assert(await sw.isVisible());const swt=await sw.innerText();for(const value of ['SKIP3','FAIL2','NEW paths','CANCEL_REQUESTED','analysis_admitted=true','exit0','zero-byte','--allow-download','--allow-install','legacy_minimum_segment_size','minimum_alignment_length','DUP','INV','RE/SUPPORT','HP/PS','Cell Ranger','not an OS security sandbox','Frozen verification'])assert(swt.includes(value),value);await sw.screenshot({path:path.join(out,'sniffles-actions-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('prominent Sniffles warnings explain skips, fatal exit0, source discrepancies, edition semantics and permitted installation actions');
 await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await sw.screenshot({path:path.join(out,'sniffles-actions-mobile-dark.png'),style:'header{visibility:hidden!important}'});assert.equal(errors.length,0,errors.join('\n'));checks.push('Sniffles dark warnings fit390px with no horizontal overflow or browser errors');
 assert.equal(inventory.filter(t=>t.implementation_status==='native-subset-validated').length,17);assert.equal(inventory.filter(t=>t.implementation_status==='upstream-adapter-validated').length,74);assert.equal(inventory.filter(t=>t.implementation_status==='missing').length,250);assert.equal(si.whole_paper_reproduced,false);assert.equal(si.whole_goal_complete,false);checks.push('91 new scoped integrations and250 missing tools retain scientific and whole-goal limits');
 '''
    text=text.replace(marker,"{\n"+extra+"}\n"+"fs.writeFileSync(path.join(out,'sniffles-ui-verification.json')")
    text=text.replace('Passed36 focused current CellPhoneDB, Palantir, scFates, GSEApy, hifiasm and Rsubread browser checks',
        'Passed43 focused current and additive Sniffles browser checks')
    result['check_sniffles_catalog_review_ui.cjs']=text
    text=(folder/'validate_rsubread_catalog_ui.py').read_text(encoding='utf8').replace('rsubread','sniffles')
    text=text.replace('Run general and cellphonedb browser checks','Run general and additive Sniffles browser checks')
    text=text.replace('Passed general and cellphonedb browser checks','Passed299 general and43 focused Sniffles browser checks')
    result['validate_sniffles_catalog_ui.py']=text
    return result


def verify():
    for name,text in generated().items():assert (ROOT/'tools/literature'/name).read_bytes()==text.encode(),name


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    payload=generated();assert all(not (ROOT/'tools/literature'/name).exists() for name in payload)
    for name,text in payload.items():
        if name.endswith('.py'):compile(text,name,'exec')
        (ROOT/'tools/literature'/name).write_text(text,encoding='utf8',newline='\n')
    verify();save(out/'COMPLETED.json',dict(status='passed-additive-Sniffles-catalog-check-source-preparation',
        source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),*[ROOT/'tools/literature'/name for name in payload]]},
        predecessor_scientific_verifiers_changed=False,source_allowlist_changed=False,whole_goal_complete=False))
    print('Prepared31 scientific gates,299 unchanged general and43 additive focused browser checks')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact derivative checks')
    main()
