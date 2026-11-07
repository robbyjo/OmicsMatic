# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal one appended primary role and two scoped availability updates without altering prior evidence."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack


def main():
    data=ROOT/'docs/literature/omics-2023-2026';base=ROOT/'build/literature-review/fitdistrplus-runtime';target=data/'fitdistrplus-catalog-support.zip';assert not target.exists() and not (data/'fitdistrplus-catalog-validation.json').exists()
    gates=json.loads((base/'catalog-scientific-gates-1/COMPLETED.json').read_bytes());ui=json.loads((base/'catalog-ui-2/COMPLETED.json').read_bytes());assert len(gates['checks'])==4 and ui['page']==pin(ROOT/'site/literature-review.html') and ui['warning_page']==pin(ROOT/'site/literature-warnings.html')
    inventory=json.loads((data/'tool-inventory.json').read_bytes());prior=json.loads((base/'catalog-render-bootstrap-1/before-tool-inventory.json').read_bytes());a={t['id']:t for t in prior};b={t['id']:t for t in inventory};assert len(a)==389 and len(b)==390 and [k for k in a if a[k]!=b[k]]==['cicero'] and set(b)-set(a)=={'fitdistrplus'}
    summary=json.loads((data/'summary.json').read_bytes());assert summary['implementation_status_counts']['native-subset-validated']==17 and summary['implementation_status_counts']['upstream-adapter-validated']==59 and summary['implementation_status_counts']['missing']==255
    files={}
    for folder in ['catalog-curation-1','catalog-render-bootstrap-1','catalog-ui-1','catalog-ui-2','catalog-scientific-gates-1','catalog-seal-attempt-1']:
        for path in (base/folder).rglob('*'):
            if path.is_file() and not path.name.startswith('PRIVATE-'):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    source_names=['tools/literature/'+n for n in ['curate_fitdistrplus_use.py','fitdistrplus_catalog.py','fitdistrplus-use-review.json','fitdistrplus-named-software-registry.json','audit_fitdistrplus_current_catalog.py','verify_fitdistrplus_review_context.py','run_fitdistrplus_catalog_checks.py','validate_fitdistrplus_catalog_ui.py','check_fitdistrplus_review_ui.cjs','check_review_ui.cjs','render_review.py','verify_fitdistrplus_catalog.py','seal_fitdistrplus_catalog.py','collect_omics_review.py','collect_htseq_omics.py','tool-registry.json','monocle-named-software-registry.json','monocle_catalog_context.py']]
    source_names.extend(['docs/literature/FITDISTRPLUS_SOURCE_REVIEW.md','.gitattributes','.github/workflows/pages.yml'])
    source_names.append('tools/literature/reviewed-uses.json')
    source_names.extend('docs/literature/omics-2023-2026/'+n for n in ['fitdistrplus-render-shared-source-compatibility.json','fitdistrplus-reviews-shared-source-compatibility.json','fitdistrplus-browser-shared-source-compatibility.json'])
    pins={n:pin(ROOT/n) for n in source_names};files.update({'current-source/'+n:(ROOT/n).read_bytes() for n in pins})
    snapshots=['papers.json','tool-inventory.json','summary.json','catalog.md','implementation-plan.md','README.md','WARNINGS.md','catalog-enrichment.json','fitdistrplus-role-coverage.json','fitdistrplus-source-audit.json','fitdistrplus-prior-reviews.json.gz','fitdistrplus-prior-papers.json.gz','fitdistrplus-render-shared-source-compatibility.json','fitdistrplus-reviews-shared-source-compatibility.json','fitdistrplus-browser-shared-source-compatibility.json']
    for name in snapshots:files['catalog-snapshot/'+name]=(data/name).read_bytes()
    files['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    for name in ['literature-review.html','literature-warnings.html']:files['site-snapshot/'+name]=(ROOT/'site'/name).read_bytes()
    parent_paths=['docs/literature/'+n+'/VALIDATION.json' for n in ['normal-fit-development','cicero-coaccessibility-development','cicero-prediction-development']]+['docs/literature/omics-2023-2026/monocle-catalog-validation.json'];parents={n:pin(ROOT/n) for n in parent_paths}
    components={}
    for key,name,admitted in [('native_normal','normal-fit-development','component_admitted'),('original_Cicero','cicero-coaccessibility-development','adapter_admitted'),('native_prediction','cicero-prediction-development','workflow_admitted')]:
        proof=json.loads((ROOT/'docs/literature'/name/'VALIDATION.json').read_bytes());assert proof[admitted];components[key]=dict(admitted=True,artifact=proof['artifact'],canonical_executable_unchanged=proof['canonical_executable_unchanged'])
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert all(c['canonical_executable_unchanged']==canonical for c in components.values());held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-07-cicero-prediction-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode('utf8')
    layout=pack(target,files);assert target.stat().st_size<100000000
    save(data/'fitdistrplus-catalog-validation.json',dict(schema_version=1,status='passed-fitdistrplus-primary-role-and-two-scoped-integrations',archive=pin(target),source_pins=pins,parent_proofs=parents,components=components,canonical_executable_unchanged=canonical,new_scoped_integrations=76,including_existing_adapters=79,missing_integrations=255,unrelated_tool_rows_preserved=388,prior_observations_preserved=2449,prior_reviews_preserved=917,reviewed_uses=918,named_tools=390,observations=2450,general_browser_checks=299,focused_browser_checks=6,website_deployed=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));print('Sealed',len(files),'catalog evidence files;',target.stat().st_size,'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/catalog/evidence bindings')
    main()
