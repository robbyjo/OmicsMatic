# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive additive MR-MEGA checks while leaving predecessor scientific/UI files unchanged."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def generated():
    folder=ROOT/'tools/literature';result={};text=(folder/'run_sniffles_catalog_checks.py').read_text()
    for old,new in [('from sniffles_catalog import','from mrmega_catalog import'),('sniffles-runtime','mrmega-runtime'),
        ("'sniffles_execution_evidence'];","'sniffles_execution_evidence','mrmega_workflow_evidence'];"),('sniffles_catalog.py','mrmega_catalog.py'),
        ('current_reviews=1009','current_reviews=1012'),('passed-thirty-one-frozen-and-additive-Sniffles-gates','passed-thirty-two-frozen-and-additive-MRMEGA-gates'),
        ('Passed31 unchanged/additive gates','Passed32 unchanged/additive gates')]:
        assert old in text,old;text=text.replace(old,new)
    text=text.replace('Run30 unchanged scientific gates plus Sniffles, with exact historical980 metadata inputs.','Run31 unchanged scientific gates plus native MR-MEGA, with exact historical980 metadata inputs.')
    result['run_mrmega_catalog_checks.py']=text
    text=(folder/'check_sniffles_catalog_review_ui.cjs').read_text()
    for old,new in [("['native-subset-validated',17]","['native-subset-validated',18]"),("['missing',250]","['missing',249]"),
        ("'native-subset-validated').length,17","'native-subset-validated').length,18"),("'missing').length,250","'missing').length,249"),
        ('400 cataloged tools,91 new integrations,250 missing','400 cataloged tools,92 new integrations,249 missing'),
        ('assert.equal(gu.length,1009)','assert.equal(gu.length,1012)'),
        ('assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),353)','assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),355)'),
        ('1009 roles and353 support pointers retain six GSEApy uses','1012 roles and355 support pointers retain six GSEApy uses'),
        ('91 new scoped integrations and250 missing tools','92 new scoped integrations and249 missing tools')]:
        assert old in text,old;text=text.replace(old,new)
    marker="fs.writeFileSync(path.join(out,'sniffles-ui-verification.json')";assert text.count(marker)==1
    extra=r'''await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8768/literature-review.html');await page.evaluate(()=>document.documentElement.dataset.theme='light');await page.locator('#tools-tab').click();await page.locator('#query').fill('MR-MEGA');
 const mr=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^MR-MEGA \(/i})});assert.equal(await mr.count(),1);const mt=await mr.innerText();fs.writeFileSync(path.join(out,'mrmega-card-visible-text.txt'),mt);for(const term of ['native-subset-validated','mrmega-tools','132','4134','12402','144','float32','lnBF','no_alleles','odd','stable','whole-paper'])assert(mt.toLowerCase().includes(term.toLowerCase()),term);await mr.screenshot({path:path.join(out,'mrmega-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('MR-MEGA card shows tested native file/ancestry/GC/lnBF scope and explicit source quirks');
 const mi=inventory.find(t=>t.id==='mrmega'),mp=JSON.parse(fs.readFileSync(path.join(root,'build/literature-review/mrmega-runtime/catalog-root-build21/COMPLETED.json'))),mw=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/mrmega-workflow-development/VALIDATION.json')));assert.equal(mi.implementation_status,'native-subset-validated');assert.equal(mi.development_candidate,mp.published_artifact.path);assert.equal(mi.development_candidate_sha256,mp.published_artifact.sha256);assert.equal(mi.root_command,'mrmega-tools');assert.equal(mi.development_cli_class,'org.jlinalg.cli.MrMegaWorkflowCli');assert.equal(mi.original_runtime_required,false);assert.equal(mi.analysis_installs_software,false);assert.equal(mw.complete_workflow_comparisons,132);checks.push('MR-MEGA metadata binds native workflow and exact source-preserving root supplement');
 const md=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/mrmega-use-reviews.json'))).reviews;assert.equal(md.length,3);await page.locator('#papers-tab').click();for(const item of md){const p=papers.find(p=>p.paper_id===item.paper_id);await page.locator('#query').fill(p.doi);assert.equal(await page.locator('#results article').count(),1);const t=clean(await page.locator('#'+p.paper_id).textContent());assert(t.includes(clean(item.review.use_summary)),p.paper_id);assert(t.includes(clean(item.review.implementation_scope_note)),p.paper_id);}checks.push('all three MR-MEGA papers display complete reviewed roles and paper-specific uncertainty');
 const mrRoles=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/mrmega-role-coverage.json'))),corrections=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/mrmega-draft-corrections.json')));assert.equal(mrRoles.prior_reviews,1009);assert.equal(mrRoles.unchanged_prior_reviews,1009);assert.equal(mrRoles.total_reviews,1012);assert.equal(mrRoles.supporting_pointers,355);assert.equal(corrections.corrections.length,2);assert(corrections.prior_drafts_preserved&&corrections.incorrect_drafts_never_applied);assert(md.find(r=>r.paper_id==='P072').review.implementation_scope_note.includes('six of seven'));assert(md.find(r=>r.paper_id==='P162').review.use_summary.includes('six index variants'));checks.push('corrected six-of-seven and six-index-variant source facts preserve every prior review and the unpublished draft history');
 assert.equal(md.find(r=>r.paper_id==='P072').review.supporting_evidence.length,2);assert.equal(md.find(r=>r.paper_id==='P072').review.version_reported_near_name,'0.2');assert.equal(md.find(r=>r.paper_id==='P105').review.version_reported_near_name,'0.2');assert.equal(md.find(r=>r.paper_id==='P162').review.version_reported_near_name,null);checks.push('MR-MEGA support pointers, stated editions and unreported P162 edition stay distinct');
 await page.goto('http://127.0.0.1:8768/literature-warnings.html');const ma=page.locator('#mrmega-required-actions');assert(await ma.isVisible());const mat=await ma.innerText();for(const term of ['FAIL2','SKIP3','partial/','NEW output paths','SmallCohortCount','no_alleles=true','float32','floor(n/2)-1','lower OR','upper bound','stable probability','Cell Ranger','needs no original MR-MEGA','Canonical executable unchanged'])assert(mat.includes(term),term);await ma.screenshot({path:path.join(out,'mrmega-actions-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('prominent MR-MEGA warnings explain native requirements, failed output isolation, source discrepancies and permitted reference-only recovery');
 await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await ma.screenshot({path:path.join(out,'mrmega-actions-mobile-dark.png'),style:'header{visibility:hidden!important}'});assert.equal(errors.length,0,errors.join('\n'));checks.push('MR-MEGA dark warnings fit390px without overflow or browser errors');
 assert.equal(inventory.filter(t=>t.implementation_status==='native-subset-validated').length,18);assert.equal(inventory.filter(t=>t.implementation_status==='upstream-adapter-validated').length,74);assert.equal(inventory.filter(t=>t.implementation_status==='missing').length,249);assert.equal(mi.whole_paper_reproduced,false);assert.equal(mi.whole_goal_complete,false);assert.equal(inventory.find(t=>t.id==='cellranger').implementation_status,'excluded-by-user');checks.push('92 new integrations/95 including existing/249 missing retain Cell Ranger exclusion and whole-goal limits');
 '''
    text=text.replace(marker,'{\n'+extra+'}\n'+"fs.writeFileSync(path.join(out,'mrmega-ui-verification.json')")
    text=text.replace('Passed43 focused current and additive Sniffles browser checks','Passed51 focused current and additive MR-MEGA browser checks');result['check_mrmega_catalog_review_ui.cjs']=text
    text=(folder/'validate_sniffles_catalog_ui.py').read_text().replace('sniffles','mrmega')
    text=text.replace('Passed299 general and43 focused Sniffles browser checks','Passed299 general and51 focused MR-MEGA browser checks')
    result['validate_mrmega_catalog_ui.py']=text
    return result


def verify():
    for n,text in generated().items():assert (ROOT/'tools/literature'/n).read_bytes()==text.encode(),n


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    payload=generated();assert all(not (ROOT/'tools/literature'/n).exists() for n in payload)
    for n,text in payload.items():
        if n.endswith('.py'):compile(text,n,'exec')
        (ROOT/'tools/literature'/n).write_text(text,encoding='utf8',newline='\n')
    verify();save(out/'COMPLETED.json',dict(status='passed-additive-MRMEGA-catalog-check-preparation',
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*[ROOT/'tools/literature'/n for n in payload]]},
        predecessor_verifiers_changed=False,source_allowlist_changed=False,whole_goal_complete=False));print('Prepared32 science gates,299 unchanged general and51 additive focused browser checks')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact derivative checks')
    main()
