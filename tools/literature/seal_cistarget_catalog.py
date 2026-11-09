# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the complete cisTarget catalog/root/source/browser checkpoint and final integrity index."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from verify_cistarget_catalog import DATA,HOME,STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    prerequisites={'catalog-root-validation-2':'PASS108_COMPLETE_CISTARGET_ROOT_FLOWS_12_PREDECESSOR_FLOWS_AND72_DISPATCH_CONTRACTS','catalog-render-3':'PASS_CISTARGET_RENDER_WITH_399_UNRELATED_TOOL_OBJECTS_IDENTICAL',
        'catalog-source-audit-2':'PASS250_PRIMARY_SOURCES_2466_OBSERVATIONS_1018_REVIEWS_AND_SEVEN_CISTARGET_ROLES','catalog-gate-execution-1':'PASS33_CISTARGET_SCIENCE_GATES_WITH_UNCHANGED_EXECUTED_SOURCES_AND_METADATA',
        'catalog-ui-2':'passed-local-general-and-cistarget-catalog-browser-checks','catalog-readme-1':'PASS_CURRENT_CISTARGET_README_HEADER_WITH_EXACT_HISTORICAL_TEXT_RETAINED'}
    assert all(json.loads((base/n/'COMPLETED.json').read_bytes())['status']==value for n,value in prerequisites.items())
    destinations=[DATA/'cistarget-catalog-validation.json',DATA/'cistarget-catalog-support.zip',HOME/'VALIDATION.json',HOME/'controls.zip'];assert not any(p.exists() for p in destinations)
    source_paths=[*sorted((ROOT/'tools/literature').glob('*cistarget*.*')),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/reviewed-uses.json',ROOT/'docs/literature/CISTARGET_INTEGRATION.md']
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths};files={};objects={}
    def add(name,path):
        raw=path.read_bytes();files[name]=objects.setdefault(byte_pin(raw)['sha256'],raw)
    for name in source_pins:add('current-source/'+name,ROOT/name)
    def tree(root):
        for folder,dirs,names in os.walk(root,followlinks=False):
            dirs[:]=[d for d in dirs if d!='__pycache__']
            for name in names:
                path=Path(folder)/name;assert stat.S_ISREG(path.stat(follow_symlinks=False).st_mode);add(path.relative_to(ROOT).as_posix(),path)
    folders=['catalog-dispatch-bridge-1','catalog-root-build21-1','catalog-root-build25-1','catalog-root-validation-1','catalog-root-validation-2','catalog-role-candidate-1','catalog-preparation-1','catalog-roles-applied-1','catalog-source-audit-1','catalog-source-audit-2',
        'catalog-summary-transition-1','catalog-check-preparation-1','catalog-check-preparation-2','catalog-render-1','catalog-render-2','catalog-render-3','catalog-manifest-before-gates-1','catalog-gates-1','catalog-gate-execution-1','catalog-ui-1','catalog-general-retained-1','catalog-ui-2','catalog-visual-review-1','catalog-readme-1']
    for name in folders:tree(base/name)
    snapshot_names=['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','cistarget-source-audit.json','cistarget-role-coverage.json','cistarget-prior-reviews.json.gz','cistarget-prior-papers.json.gz','cistarget-review-supersession.json','cistarget-new-observation.json',
        'cistarget-dispatch-shared-source-compatibility.json','cistarget-renderer-shared-source-compatibility.json','cistarget-reviews-shared-source-compatibility.json']
    catalog_snapshot={name:pin(DATA/name) for name in snapshot_names}
    for name in snapshot_names:add('catalog-snapshot/'+name,DATA/name)
    for name in ['tool-inventory.json','papers.json','summary.json','README.md','WARNINGS.md','reviewed-uses.json']:add('catalog-before/'+name,base/'catalog-preparation-1'/name)
    page_pins={name:pin(ROOT/name) for name in ['site/literature-review.html','site/literature-warnings.html']}
    for name in page_pins:add('catalog-snapshot/'+name,ROOT/name)
    published={p.relative_to(ROOT).as_posix():pin(p) for p in HOME.glob('*.jar')};assert len(published)==2
    development=ROOT/'docs/literature/cistarget-development';frozen={p.relative_to(ROOT).as_posix():pin(p) for p in development.iterdir() if p.is_file()}
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-cistarget-development-1/selection.json').read_bytes())
    assert len(previous['preserved'])==18 and all(pin(ROOT/n)==v for n,v in previous['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']
    packing=pack(out/'cistarget-catalog-support.zip',files);assert pin(out/'cistarget-catalog-support.zip')['bytes']<95000000
    proof=dict(schema_version=1,status=STATUS,new_catalog_credit=True,source_pins=source_pins,catalog_snapshot=catalog_snapshot,page_pins=page_pins,
        archive=pin(out/'cistarget-catalog-support.zip'),published_root_artifacts=published,frozen_development_files=frozen,papers=250,tools=400,definitions=438,observations=2466,reviewed_roles=1018,supporting_pointers=356,
        new_scoped_integrations=93,including_existing_adapters=96,missing=248,unchanged_unrelated_tool_objects=399,unchanged_prior_reviews=1011,explicit_prior_review_supersessions=1,new_reviews=6,new_observations=1,
        numerical_component_comparisons=672,complete_root_workflow_comparisons=108,predecessor_workflows=12,dispatcher_contracts=72,scientific_gates=33,general_browser_checks=299,focused_browser_checks=60,
        source_roles_reviewed=7,standalone_native_tsv_workflow_available=True,root_command_available=True,root_command='cistarget-tools',canonical_adapter_available=False,
        canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,scientific_verifiers_changed=False,source_allowlist_changed=False,
        full_R_package_installed=False,restricted_pycistarget_wrapper_installed=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        post_gate_documentation_only_change='README current-count introduction added after the gates; exact prior text retained and all scientific/JSON/page inputs unchanged. Final manifest records this documentation change and the two new closure files.',
        verification_scope='Stored source/archive integrity, exact prior-review/observation preservation, unchanged scientific class bytes and complete captured routed-output relationships. No Java/Python/R execution, independent current-data refit, execution authentication, biological certification or whole-paper/full-goal claim.',**packing)
    save(out/'cistarget-catalog-validation.json',proof);verify(False,out,False)
    for source,destination in [(out/'cistarget-catalog-validation.json',DATA/'cistarget-catalog-validation.json'),(out/'cistarget-catalog-support.zip',DATA/'cistarget-catalog-support.zip'),(out/'cistarget-catalog-validation.json',HOME/'VALIDATION.json'),(out/'cistarget-catalog-support.zip',HOME/'controls.zip')]:destination.write_bytes(source.read_bytes())
    # The actual pre-gate index stays archived. Only the README document and
    # new closure files change after that verified scientific execution epoch.
    index={p.name:pin(p) for p in sorted(DATA.iterdir()) if p.is_file() and p.name!='SHA256SUMS'}
    (DATA/'SHA256SUMS').write_text(''.join(row['sha256']+'  '+name+'\n' for name,row in index.items()),encoding='utf8',newline='\n')
    assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and all(pin(DATA/n)==v for n,v in catalog_snapshot.items()) and all(pin(ROOT/n)==v for n,v in previous['preserved'].items())
    assert all(pin(DATA/name)==pin(out/name) for name in ['cistarget-catalog-validation.json','cistarget-catalog-support.zip'])
    save(out/'COMPLETED.json',dict(status='PASS_FROZEN_CISTARGET_CATALOG_AND_IDENTICAL_PUBLISHED_CHECKPOINT',source_pins=source_pins,
        published_files={p.relative_to(ROOT).as_posix():pin(p) for p in [*destinations,DATA/'SHA256SUMS']},final_manifest_files=index,
        new_scoped_integrations=93,including_existing_adapters=96,missing=248,whole_goal_complete=False));print('Sealed93 new integrations/96 including existing;248 missing;33 science gates and359 browser checks; goal active')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/catalog preservation checks')
    main()
