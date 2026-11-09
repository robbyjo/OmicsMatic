# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive additive cisTarget scientific and browser checks without changing predecessor verifiers."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def generated():
    folder=ROOT/'tools/literature';result={};text=(folder/'run_mrmega_catalog_checks.py').read_text(encoding='utf8')
    for old,new in [('from mrmega_catalog import','from cistarget_catalog import'),('mrmega-runtime','cistarget-runtime'),("'mrmega_workflow_evidence'];","'mrmega_workflow_evidence','cistarget_development_evidence'];"),('mrmega_catalog.py','cistarget_catalog.py'),('current_reviews=1012','current_reviews=1018'),('passed-thirty-two-frozen-and-additive-MRMEGA-gates','passed-thirty-three-frozen-and-additive-cisTarget-gates'),('Passed32 unchanged/additive gates','Passed33 unchanged/additive gates')]:
        assert old in text,old;text=text.replace(old,new)
    result['run_cistarget_catalog_checks.py']=text
    text=(folder/'check_review_ui.cjs').read_text(encoding='utf8');old='2465 tool observations';assert text.count(old)==1
    result['check_cistarget_general_review_ui.cjs']=text.replace(old,'2466 tool observations')
    text=(folder/'check_mrmega_catalog_review_ui.cjs').read_text(encoding='utf8')
    edits=[("['native-subset-validated',18]","['native-subset-validated',19]"),("['missing',249]","['missing',248]"),("'native-subset-validated').length,18","'native-subset-validated').length,19"),("'missing').length,249","'missing').length,248"),
        ('assert.equal(gu.length,1012)','assert.equal(gu.length,1018)'),('assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),355)','assert.equal(gu.reduce((s,r)=>s+(r.supporting_evidence||[]).length,0),356)'),
        ('1012 roles and355 support pointers','1018 roles and356 support pointers'),('400 cataloged tools,92 new integrations,249 missing','400 cataloged tools,93 new integrations,248 missing'),
        ('92 new scoped integrations and249 missing tools','93 new scoped integrations and248 missing tools'),('92 new integrations/95 including existing/249 missing','93 new integrations/96 including existing/248 missing')]
    for old,new in edits:assert old in text,old;text=text.replace(old,new)
    marker="fs.writeFileSync(path.join(out,'mrmega-ui-verification.json')";assert text.count(marker)==1
    extra=r'''{
await page.setViewportSize({width:1440,height:1000});await page.goto('http://127.0.0.1:8768/literature-review.html');await page.evaluate(()=>document.documentElement.dataset.theme='light');await page.locator('#tools-tab').click();await page.locator('#query').fill('cisTarget');
const card=page.locator('#results article').filter({has:page.getByRole('heading',{name:/^cisTarget \(/i})});assert.equal(await card.count(),1);const visible=await card.innerText();fs.writeFileSync(path.join(out,'cistarget-card-visible-text.txt'),visible);for(const term of ['native-subset-validated','cistarget-tools','672','108','NumPy1.26.4','RcisTarget1.29.0','leading','SCENIC','biological/paper reproduction'])assert(visible.toLowerCase().includes(term.toLowerCase()),term);await card.screenshot({path:path.join(out,'cistarget-card-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('cisTarget card exposes complete ranked-feature native scope, explicit numerical profiles, tested controls and separate SCENIC operations');
const item=inventory.find(t=>t.id==='cistarget'),kernel=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/cistarget-development/VALIDATION.json'))),built=JSON.parse(fs.readFileSync(path.join(root,'build/literature-review/cistarget-runtime/catalog-root-build21-1/COMPLETED.json')));assert.equal(item.implementation_status,'native-subset-validated');assert.equal(item.root_command,'cistarget-tools');assert.equal(item.paper_count,7);assert.equal(item.source_roles_reviewed,7);assert.equal(kernel.complete_kernel_comparisons,672);checks.push('cisTarget native status binds seven source roles and672 original numerical comparisons');
assert.equal(item.development_candidate,'docs/literature/cistarget-catalog-development/cistarget-root-javac21.jar');assert.equal(item.development_candidate_sha256,built.artifact.sha256);assert.equal(item.development_cli_class,'org.jlinalg.cli.CisTargetWorkflowCli');assert.equal(item.canonical_adapter_available,false);assert.equal(item.analysis_installs_software,false);assert.equal(item.original_runtime_required,false);checks.push('cisTarget metadata binds exact source-preserving root supplement and installation-free TSV entry point');
const drafted=JSON.parse(fs.readFileSync(path.join(root,'tools/literature/cistarget-use-reviews.json'))).reviews;assert.equal(drafted.length,7);await page.locator('#papers-tab').click();for(const role of drafted){const paper=papers.find(p=>p.paper_id===role.paper_id);await page.locator('#query').fill(paper.doi);assert.equal(await page.locator('#results article').count(),1);const body=clean(await page.locator('#'+paper.paper_id).textContent());assert(body.includes(clean(role.review.use_summary)),paper.paper_id);assert(body.includes(clean(role.review.implementation_scope_note)),paper.paper_id);}checks.push('all seven cisTarget papers display complete reviewed uses and source-specific computational boundaries');
const roles=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/cistarget-role-coverage.json'))),supersession=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/cistarget-review-supersession.json'))),observation=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/cistarget-new-observation.json')));assert.equal(roles.prior_reviews,1012);assert.equal(roles.unchanged_prior_reviews,1011);assert.equal(roles.superseded_prior_reviews,1);assert.equal(roles.new_reviews,6);assert.equal(roles.total_reviews,1018);assert.equal(roles.supporting_pointers,356);assert.equal(roles.observations,2466);assert.equal(supersession.supersessions.length,1);assert.equal(supersession.supersessions[0].paper_id,'P061');assert.equal(observation.observations[0].paper_id,'P145');assert.equal(observation.observations[0].primary_paragraph,53);checks.push('explicit P061 review supersession and P145 spelling observation preserve1011 other reviews and the full250-paper cohort');
assert.equal(drafted.find(r=>r.paper_id==='P065').role_kind,'motif-collection-resource-only');assert.equal(drafted.find(r=>r.paper_id==='P094').role_kind,'SCENIC-plus-database-resource');assert.equal(drafted.find(r=>r.paper_id==='P145').review.version_reported_near_name,null);assert.equal(drafted.find(r=>r.paper_id==='P145').review.supporting_evidence[0].paragraph_index,64);checks.push('resource-only Tomtom/SCENIC roles and unreported standalone editions remain distinct from native algorithm execution');
await page.goto('http://127.0.0.1:8768/literature-warnings.html');const actions=page.locator('#cistarget-required-actions');assert(await actions.isVisible());const text=await actions.innerText();for(const term of ['FAIL2','SKIP3','NEW output paths','partial/','NaN NES','SOURCE ROUNDING WARNING','PyArrow17.0.0','skip the conversion','installs nothing','full250-paper goal','Cell Ranger','Canonical executable unchanged'])assert(text.includes(term),term);await actions.screenshot({path:path.join(out,'cistarget-actions-desktop.png'),style:'header{visibility:hidden!important}'});checks.push('prominent cisTarget warnings explain source rounding, undefined NES, no-result admission, optional runtime skips and permitted recovery');
await page.setViewportSize({width:390,height:1000});await page.evaluate(()=>document.documentElement.dataset.theme='dark');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));await actions.screenshot({path:path.join(out,'cistarget-actions-mobile-dark.png'),style:'header{visibility:hidden!important}'});assert.equal(errors.length,0,errors.join('\n'));checks.push('cisTarget warnings remain readable at390px in dark mode without horizontal overflow or page errors');
assert.equal(inventory.filter(t=>t.implementation_status==='native-subset-validated').length,19);assert.equal(inventory.filter(t=>t.implementation_status==='upstream-adapter-validated').length,74);assert.equal(inventory.filter(t=>t.implementation_status==='existing-adapter').length,3);assert.equal(inventory.filter(t=>t.implementation_status==='missing').length,248);assert.equal(item.whole_paper_reproduced,false);assert.equal(item.whole_goal_complete,false);checks.push('93 new integrations/96 including existing/248 missing retain the full implementation goal and bounded scientific claims');
}
'''
    text=text.replace(marker,extra+"fs.writeFileSync(path.join(out,'cistarget-ui-verification.json')")
    text=text.replace('Passed51 focused current and additive MR-MEGA browser checks','Passed60 focused current and additive cisTarget browser checks');result['check_cistarget_catalog_review_ui.cjs']=text
    text=(folder/'validate_mrmega_catalog_ui.py').read_text(encoding='utf8');text=text.replace('check_review_ui.cjs','check_cistarget_general_review_ui.cjs').replace('mrmega','cistarget').replace('299 general and51 focused MR-MEGA','299 general and60 focused cisTarget')
    result['validate_cistarget_catalog_ui.py']=text;return result


def verify():
    for name,text in generated().items():assert (ROOT/'tools/literature'/name).read_bytes()==text.encode(),name


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True);payload=generated()
    assert all(not(ROOT/'tools/literature'/name).exists() for name in payload)
    for name,text in payload.items():
        if name.endswith('.py'):compile(text,name,'exec')
        (ROOT/'tools/literature'/name).write_text(text,encoding='utf8',newline='\n')
    verify();save(out/'COMPLETED.json',dict(status='PASS_EXACT_ADDITIVE_CISTARGET_SCIENCE_AND_BROWSER_CHECK_DERIVATIONS',source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),*[ROOT/'tools/literature'/n for n in payload]]},predecessor_verifiers_changed=False,source_allowlist_changed=False,whole_goal_complete=False));print('Prepared33 strict science gates,299 general and60 focused additive browser checks')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact derivative checks')
    main()
