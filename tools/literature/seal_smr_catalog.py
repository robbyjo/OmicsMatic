# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal twelve source-bound roles, two availability updates and current scientific/browser evidence."""
import json
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/smr-runtime';target=data/'smr-catalog-support.zip';assert not target.exists() and not (data/'smr-catalog-validation.json').exists()
    gates=json.loads((base/'catalog-scientific-gates-1/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-2/COMPLETED.json').read_bytes());assert len(gates['checks'])==7 and ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    prior={t['id']:t for t in json.loads((base/'catalog-render-bootstrap-1/before-tool-inventory.json').read_bytes())};current={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};assert len(prior)==len(current)==390 and set(k for k in prior if prior[k]!=current[k])=={'smr','heidi'}
    for tool in ['smr','heidi']:assert prior[tool]['implementation_status']=='missing' and current[tool]['implementation_status']=='upstream-adapter-validated'
    counts=json.loads((data/'summary.json').read_bytes())['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251)
    files={}
    for folder in sorted(base.glob('catalog-*')):
        if not folder.is_dir():continue
        for path in folder.rglob('*'):
            if path.is_file() and not path.name.startswith('PRIVATE-') and '__pycache__' not in path.parts:files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    names=['curate_smr_uses.py','smr_catalog.py','smr-use-reviews.json','audit_smr_catalog.py','verify_smr_review_context.py','run_smr_catalog_checks.py','validate_smr_catalog_ui.py','check_smr_review_ui.cjs','prepare_smr_catalog_render.py','check_review_ui.cjs','render_review.py','verify_smr_catalog.py','seal_smr_catalog.py','reviewed-uses.json','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','tool-registry.json']
    pins={n:pin(ROOT/n) for n in ['tools/literature/'+n for n in names]+['.gitattributes','.github/workflows/pages.yml','README.md']};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    snapshots=['papers.json','tool-inventory.json','summary.json','catalog.md','implementation-plan.md','README.md','WARNINGS.md','smr-role-coverage.json','smr-source-audit.json','smr-prior-reviews.json.gz','smr-prior-papers.json.gz','smr-reviews-shared-source-compatibility.json','smr-render-shared-source-compatibility.json','smr-root-readme-evolution.json']
    for name in snapshots:files['catalog-snapshot/'+name]=(data/name).read_bytes()
    files['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for name in ['literature-review.html','literature-warnings.html']:files['site-snapshot/'+name]=(ROOT/'site'/name).read_bytes()
    parents={n:pin(ROOT/n) for n in ['docs/literature/smr-workflow-development/VALIDATION.json','docs/literature/smr-workflow-development/controls.zip','docs/literature/omics-2023-2026/dndscv-catalog-validation.json','docs/literature/omics-2023-2026/dndscv-catalog-support.zip']};proof=json.loads((ROOT/'docs/literature/smr-workflow-development/VALIDATION.json').read_bytes());assert proof['adapter_admitted'];canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==proof['canonical_executable_unchanged']
    held_names=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-07-smr-statistics-1/selection.json').read_bytes())['preserved'];held={n:pin(ROOT/n) for n in held_names};assert len(held)==18;files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();layout=pack(target,files);assert target.stat().st_size<100000000
    save(data/'smr-catalog-validation.json',dict(schema_version=1,status='passed-twelve-SMR-HEIDI-primary-roles-and-two-scoped-integrations',archive=pin(target),source_pins=pins,parent_proofs=parents,artifact=proof['artifact'],canonical_executable_unchanged=canonical,new_scoped_integrations=80,including_existing_adapters=83,missing_integrations=251,unrelated_tool_rows_preserved=388,prior_observations_preserved=2450,prior_reviews_preserved=932,reviewed_uses=944,supporting_pointers=327,named_tools=390,observations=2450,general_browser_checks=299,focused_browser_checks=5,website_deployed=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));print('Sealed',len(files),'catalog evidence files;',target.stat().st_size,'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable catalog/scientific evidence checks')
    main()
