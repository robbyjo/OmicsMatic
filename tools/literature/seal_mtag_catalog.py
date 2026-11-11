# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal one scoped MTAG admission, all250-paper primary checks and unchanged predecessor scientific gates."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE
from signac_scan_evidence import pack
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
from verify_mtag_catalog import DATA,STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    required={
        'catalog-render-1':'PASS_MTAG_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED',
        'catalog-source-audit-1':'PASS250_PRIMARY_SOURCES_2475_OBSERVATIONS_1043_REVIEWS_AND_FIVE_MTAG_ROLES',
        'catalog-shared-gates-1':'PASS55_MTAG_AND_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',
        'catalog-ui-1':'PASS299_GENERAL18_PRESERVED_GENESIS_AND11_MTAG_BROWSER_CHECKS',
        'catalog-visual-review-1':'PASS_TWO_MTAG_BROWSER_ARTIFACTS_VISUALLY_REVIEWED',
        'catalog-metadata-idempotency-1':'PASS_MTAG_CURRENT_METADATA_REFRESH_IDEMPOTENT_AND_ALL_RENDERED_DATA_UNCHANGED',
        'catalog-example-contract-1':'PASS_PUBLISHED_MTAG_REQUEST_EXAMPLE_TYPED_SCHEMA_ONLY'}
    assert all(json.loads((BASE/n/'COMPLETED.json').read_bytes())['status']==v for n,v in required.items())
    assert not any((DATA/n).exists() for n in ['mtag-catalog-validation.json','mtag-catalog-support.zip'])
    names=['curate_mtag_uses.py','mtag_catalog.py','wire_mtag_dispatch.py','wire_mtag_catalog.py','refresh_mtag_catalog_metadata.py','render_mtag_catalog.py',
           'audit_mtag_catalog.py','run_mtag_catalog_checks.py','validate_mtag_catalog_ui.py','check_mtag_catalog_ui.cjs','check_mtag_genesis_review_ui.cjs','verify_mtag_catalog.py','seal_mtag_catalog.py']
    metadata_sources=['tools/literature/'+n for n in names]+['docs/literature/MTAG_INTEGRATION.md','docs/literature/mtag-request-example.json']
    attrs=ROOT/'.gitattributes';before=attrs.read_bytes();after=before+('\n# Preserve exact MTAG catalog,primary-review,guide and browser metadata bytes.\n'+''.join(n+' -text whitespace=cr-at-eol\n' for n in metadata_sources)).encode('utf8')
    delta=inverse(before,after);delta['path']='.gitattributes';assert reverse_delta(after,delta)==before
    descriptor=DATA/'mtag-catalog-sourcebytes-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mtag',scope='Append-only exact-byte attributes for new catalog/review/guide/browser metadata. No scientific-source or allowlist changes.',paths=[delta]))
    (out/'gitattributes.before').write_bytes(before);attrs.write_bytes(after);verify_source(ROOT,DATA,'.gitattributes',delta['before_sha256'])
    sources=[ROOT/n for n in metadata_sources]+[ROOT/n for n in ['.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py','tools/literature/reviewed-uses.json',
        'tools/literature/check_cistarget_general_review_ui.cjs','tools/literature/check_snprelate_general_review_ui.cjs','tools/literature/check_genesis_catalog_ui.cjs']]
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};files={}
    def add(name,path):
        assert name not in files and path.is_file() and not path.is_symlink();files[name]=path.read_bytes()
    for n in source_pins:add('current-source/'+n,ROOT/n)
    folders=[*required,'paper-role-review-2','paper-role-review-3','paper-role-apply-1','dispatch-live-1','catalog-wire-1']
    for n in folders:
        for path in sorted((BASE/n).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                assert path.suffix.lower() not in ['.exe','.dll','.so','.tar','.xz','.pdf'],path
                assert path.suffix.lower()!='.gz' or path.name.endswith('.json.gz'),path
                add(path.relative_to(ROOT).as_posix(),path)
    for n in ['shared-gates-derivation.json','prepare_shared_gates.py','prepare_catalog_ui.py','finish_catalog_checks.py']:add((BASE/n).relative_to(ROOT).as_posix(),BASE/n)
    snapshot_names=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md']
    snapshot_names+=[p.name for p in sorted(DATA.glob('mtag-*.json'))]+['mtag-prior-reviews.json.gz','mtag-prior-papers.json.gz'];assert len(snapshot_names)==len(set(snapshot_names))
    snapshot={n:pin(DATA/n) for n in snapshot_names}
    for n in snapshot_names:add('catalog-snapshot/'+n,DATA/n)
    for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:add('catalog-before/'+n,BASE/'catalog-render-1'/(n+'.before'))
    pages={n:pin(ROOT/n) for n in ['site/literature-review.html','site/literature-warnings.html']}
    for n in pages:add('pages/'+n,ROOT/n)
    component_files={}
    for folder in ['mtag-workflow-development','mtag-dimensions-development']:
        component_files.update({p.relative_to(ROOT).as_posix():pin(p) for p in (ROOT/'docs/literature'/folder).iterdir() if p.is_file()})
    for n in ['genesis-catalog-validation.json','genesis-catalog-support.zip']:component_files[(DATA/n).relative_to(ROOT).as_posix()]=pin(DATA/n)
    record=pack(out/'mtag-catalog-support.zip',files);assert pin(out/'mtag-catalog-support.zip')['bytes']<95_000_000
    (DATA/'mtag-catalog-support.zip').write_bytes((out/'mtag-catalog-support.zip').read_bytes())
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-10-mtag-workflow-1/selection.json').read_bytes());assert len(previous['preserved'])==18
    assert all(pin(ROOT/n)==v for n,v in previous['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=True,new_tool_id='mtag',new_tool_integration_count=1,
        whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,full_250_paper_semantic_review_complete=False,
        source_pins=source_pins,component_files=component_files,catalog_snapshot=snapshot,page_pins=pages,archive=pin(DATA/'mtag-catalog-support.zip'),
        papers=250,definitions=440,observed_tools=402,observations=2475,reviewed_roles=1043,supporting_pointers=374,added_MTAG_reviews=5,unchanged_prior_reviews=1038,
        new_scoped_integrations=98,native_java_integrations=19,new_upstream_adapters=79,including_existing_adapters=101,missing=245,unchanged_other_tool_objects=401,
        scientific_gates=55,general_browser_checks=299,preserved_GENESIS_browser_scope_checks=18,focused_MTAG_browser_checks=11,
        option_controls=25,admitted_option_workflows=20,expected_option_scientific_refusals=4,expected_option_skips=1,option_target_row_checks=172625,
        additional_original_dimensions=[4,5,6,7,8],additional_dimension_target_row_checks=30000,additional_dimension_saved_replays=15,
        complete_unpruned_default_four_trait_grid_points=3268760,root_admitted_analyses=3,root_source_error=1,root_skips=3,root_saved_replays=9,option_saved_replays=60,
        portable_safety_protocols=24,source_tail_finite_comparisons=386088,original_tail_zeros_preserved=35853,
        predecessor_scientific_verifiers_changed=False,scientific_source_allowlist_changed=False,new_dimension_verifier_added=True,
        canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,analysis_installs_software=False,
        participant_data_distributed=False,primary_article_prose_distributed=False,
        scope='One scoped original MTAG1.0.8 common-SNP adapter with exact private runtime preparation,explicit scientific inputs and native complete effect/tail/identity/diagnostic admission. Current original estimators/full QC/maxFDR execute upstream. Five primary roles and a separate author equation are reviewed; historical commits,unreported settings,full paper pipelines and biological validity remain unresolved. All predecessor science is unchanged and the full250-paper goal remains open.',**record)
    save(DATA/'mtag-catalog-validation.json',proof)
    old=(DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines();order=[r.split('  ',1)[1] for r in old];manifest={p.name:pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    order=[n for n in order if n in manifest]+[n for n in sorted(manifest) if n not in order];assert len(order)==len(set(order));(DATA/'SHA256SUMS').write_text(''.join(manifest[n]+'  '+n+'\n' for n in order),encoding='utf8',newline='\n')
    verify();save(out/'COMPLETED.json',dict(status=STATUS,validation=pin(DATA/'mtag-catalog-validation.json'),archive=proof['archive'],whole_goal_complete=False));print(STATUS,proof['archive']['bytes'],flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full source/role/science/browser/history or unrelated-work preservation checks')
    main()
