# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze source/role/browser evidence and refresh the complete catalog integrity index."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from verify_snprelate_catalog import DATA,STATUS,verify
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    prerequisites={'catalog-render-3':'PASS_CURRENT_SNPRELATE_REVIEW_CONTEXT_RENDER_BYTES_IDENTICAL',
        'catalog-source-audit-2':'PASS250_PRIMARY_SOURCES_2475_OBSERVATIONS_1038_REVIEWS_AND_NINE_NEW_PACKAGE_ROLES',
        'catalog-shared-gates-2':'PASS46_SNPRELATE_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',
        'catalog-ui-2':'PASS299_PREDECESSOR_AND_SNPRELATE_BROWSER_CHECKS',
        'catalog-visual-review-1':'PASS_THREE_SNPRELATE_ARTIFACTS_VISUALLY_REVIEWED',
        'catalog-readme-1':'PASS_CURRENT_SNPRELATE_README_HEADER_EXACT_REFERENCE_RETAINED'}
    assert all(json.loads((BASE/n/'COMPLETED.json').read_bytes())['status']==value for n,value in prerequisites.items())
    assert not any((DATA/n).exists() for n in ['snprelate-catalog-validation.json','snprelate-catalog-support.zip'])
    sources=[p for p in sorted((ROOT/'tools/literature').glob('*snprelate*.*')) if p.suffix in ['.py','.json','.cjs']]
    sources += [ROOT/'tools/literature'/n for n in ['intake_genesis_functions.py','intake_genesis_runtime.py','setup_genesis_reference.py','check_cistarget_general_review_ui.cjs','render_review.py','reviewed-uses.json']]
    sources += [ROOT/n for n in ['src/main/java/org/jlinalg/cli/JLinAlgCli.java','docs/literature/SNPRELATE_INTEGRATION.md','docs/literature/GENESIS_DEVELOPMENT.md','docs/literature/snprelate-native-development/CENSUS-CORRECTION.md']]
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};files={};objects={}
    def add(name,path):
        assert name not in files and not path.is_symlink();raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for name in source_pins:add('current-source/'+name,ROOT/name)
    for folder in sorted(BASE.glob('catalog-*')):
        if folder.is_dir() and not folder.name.startswith('catalog-seal-') and not folder.name.startswith('catalog-candidate-'):
            for path in sorted(folder.rglob('*')):
                if path.is_file() and '__pycache__' not in path.parts:add(path.relative_to(ROOT).as_posix(),path)
    for name in ['tools-offline-windows21-1','tools-offline-linux21-1']:
        add((BASE/name/'COMPLETED.json').relative_to(ROOT).as_posix(),BASE/name/'COMPLETED.json')
    for name in ['official-intake-1','private-original-setup-1','function-intake-1']:
        add('genesis-preparation/'+name+'.json',ROOT/'build/literature-review/genesis-runtime'/name/'COMPLETED.json')
    names=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md']
    names += [p.name for p in sorted(DATA.glob('snprelate-*.json'))]
    names += ['snprelate-prior-reviews.json.gz','snprelate-prior-papers.json.gz']
    snapshot={name:pin(DATA/name) for name in names}
    for name in names:add('catalog-snapshot/'+name,DATA/name)
    for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md']:add('catalog-before/'+name,BASE/'catalog-apply-1'/name)
    pages={n:pin(ROOT/n) for n in ['site/literature-review.html','site/literature-warnings.html']}
    for n in pages:add('pages/'+n,ROOT/n)
    component=ROOT/'docs/literature/snprelate-tools-development';component_files={p.relative_to(ROOT).as_posix():pin(p) for p in component.iterdir() if p.is_file()}
    packing=pack(out/'snprelate-catalog-support.zip',files);assert pin(out/'snprelate-catalog-support.zip')['bytes']<95000000
    (DATA/'snprelate-catalog-support.zip').write_bytes((out/'snprelate-catalog-support.zip').read_bytes())
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-10-snprelate-native-1/selection.json').read_bytes())
    assert all(pin(ROOT/n)==v for n,v in previous['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=True,new_tool_integration_count=1,new_tool_id='snprelate',whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        source_pins=source_pins,catalog_snapshot=snapshot,page_pins=pages,component_files=component_files,archive=pin(DATA/'snprelate-catalog-support.zip'),
        papers=250,definitions=440,observed_tools=402,observations=2475,reviewed_roles=1038,supporting_pointers=369,new_scoped_integrations=96,native_java_integrations=19,
        new_upstream_adapters=77,including_existing_adapters=99,missing=247,unchanged_previous_tools=400,unchanged_previous_reviews=1029,unchanged_previous_observations=2466,
        new_definitions=2,new_package_observations=9,scientific_gates=46,general_browser_checks=299,focused_browser_checks=14,explicit_browser_count_updates=2,
        original_cases=320,successful_original_analyses=266,empty_QC_preflight_refusals=54,root_original_analyses=4,complete_saved_replays_without_R=640,
        focused_JUnit_executions=60,resealed_scientific_mutations_rejected=40,helper_skip_preservation_checks=4,root_commands=33,
        genesis_runtime_only_not_implemented=True,canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,
        scientific_verifiers_changed=False,scientific_source_allowlist_changed=False,analysis_installs_software=False,primary_article_prose_distributed=False,
        retained_failures='Earlier raw-name census missed citation-adjacent P170; corrected full citation-normalized census is applied. Initial renderer used a stale global observation total; current exact rerender agrees. Historical review980 was initially overwritten by the1029 archive in metadata validation; only that explicit metadata-view routing was corrected. First focused browser assertion expected1.46.0 in a card displaying1.46; the corrected assertion also checks the exact supported1.46.0 JSON profile. Original failed attempts and complete passing general browser capture remain retained.',
        scope='One scoped original SNPRelate1.46.0 adapter with native genotype/QC/identity/KING audits, full original PCA fields and explicit mathematical invariants. Independent saved verification does not refit LD/GRM/PCA, authenticate producers, reconstruct historical studies or certify biological/production-scale validity. Two omitted package definitions and nine source roles are admitted; GENESIS runtime preparation grants no implementation credit.',**packing)
    save(DATA/'snprelate-catalog-validation.json',proof)
    old=(DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines();order=[r.split('  ',1)[1] for r in old]
    manifest={p.name:pin(p)['sha256'] for p in sorted(DATA.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in order if n in manifest]+[n for n in manifest if n not in order];assert len(order)==len(set(order))
    (DATA/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf8',newline='\n')
    verify();save(out/'COMPLETED.json',dict(status=STATUS,proof=pin(DATA/'snprelate-catalog-validation.json'),archive=proof['archive'],whole_goal_complete=False))
    print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full source, archive, current manifest or unrelated-work preservation checks')
    main()
