# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently verify the chromVAR catalog closure against current sources, frozen snapshots and primary roles."""
import json
from pathlib import Path
from signac_scan_evidence import unpack
from validate_mashr_native import pin
from catalog_source_integrity import verify_source,reverse_delta
from chromvar_catalog import verify
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026';BASE=ROOT/'build/literature-review/signac-runtime'
def read(p):return json.loads(p.read_bytes())
def main():
    out=BASE/'chromvar-catalog-final-integrity-1.json';assert not out.exists();record=read(DATA/'chromvar-catalog-validation.json');assert record['status']=='passed-scoped-chromVAR-catalog-primary-use-browser-and-source-closure' and pin(DATA/'chromvar-catalog-support.zip')==record['archive'];files=unpack(DATA/'chromvar-catalog-support.zip',record);assert len(files)==record['logical_files'] and not any('PRIVATE-before-cache-' in n for n in files)
    for n,v in record['source_pins'].items():assert pin(ROOT/n)==v and files['current-source/'+n]==(ROOT/n).read_bytes()
    proof=verify(ROOT,DATA);assert proof['artifact']==record['artifact'] and pin(ROOT/'docs/literature/chromvar-tools-development/VALIDATION.json')==record['public_workflow_validation'] and proof['archive']==record['public_workflow_archive']
    prefix='build/literature-review/signac-runtime/chromvar-catalog-promotion-1/';old=json.loads(files[prefix+'before/docs/literature/omics-2023-2026/tool-inventory.json']);new=read(DATA/'tool-inventory.json');a={r['id']:r for r in old};b={r['id']:r for r in new};assert set(a)==set(b) and [k for k in a if a[k]!=b[k]]==['chromvar'] and b['chromvar']['implementation_status']=='upstream-adapter-validated'
    expected=dict(papers=250,tools=387,observations=2442,reviewed_uses=832,supporting_pointers=255,new_scoped_integrations=69,including_existing=72,missing=258);assert record['catalog_counts']==expected;summary=read(DATA/'summary.json');assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(250,387,2442,16,53,258)
    reviews=read(ROOT/'tools/literature/reviewed-uses.json');old_reviews=json.loads(files[prefix+'before/tools/literature/reviewed-uses.json']);assert len(reviews['reviews'])==832 and reviews['reviews'][:827]==old_reviews['reviews'] and sum(len(r.get('supporting_evidence',[])) for r in reviews['reviews'])==255
    for name,checks in [('focused-ui-1',5),('broad-ui-1',299)]:r=json.loads(files[prefix+name+'/ui-validation.json']);assert r['status']=='passed' and len(r['checks'])==checks
    assert b'BUILD SUCCESSFUL' in files[prefix+'checkWebsite.stdout'] and json.loads(files[prefix+'checkWebsite-process.json'])['exit_code']==0 and b'Ran 11 tests' in files[prefix+'source-review-tests.stderr'] and b'OK' in files[prefix+'source-review-tests.stderr'] and json.loads(files[prefix+'browser-run-1/SERVER.json'])['owned_helper_stopped']
    shared=read(DATA/'shared-build-context-validation.json');assert [r['bound_scientific_classes'] for r in shared['reports']]==[413,94] and all(r['external_dependency_entries']==14253 and not r['scientific_hash_waiver'] for r in shared['reports']);assert pin(ROOT/'tools/literature/catalog_shared_build_context.py')==shared['source_pin']
    for name,h in shared['original_module_sha256'].items():assert pin(ROOT/'tools/literature'/(name+'.py'))['sha256']==h
    assert len(json.loads(files[prefix+'shared-context-negative-controls.json'])['checks'])==8
    context=read(DATA/'shared-build-catalog-shared-source-compatibility.json')['paths'][0];renderer=reverse_delta((ROOT/'tools/literature/render_review.py').read_bytes(),context);bridge=read(DATA/'chromvar-catalog-shared-source-compatibility.json')['paths'][0];assert reverse_delta(renderer,bridge)==files[prefix+'before/tools/literature/render_review.py']
    checksum_count=0
    for line in (DATA/'SHA256SUMS').read_text().splitlines():h,n=line.split('  ',1);assert pin(DATA/n)['sha256']==h;checksum_count+=1
    assert checksum_count==410 and record['preserved_unrelated_tool_objects']==386 and record['preserved_prior_reviews']==827 and record['new_scoped_runtime_implementation_credit'] and record['root_dispatch_available'] and record['literature_pages_withheld'] and not any(record[k] for k in ['original_prior_provider_files_changed','generic_scientific_source_waivers_added','long_primary_text_or_private_cache_published','historical_whole_packages_executed','independent_native_RNG_replay','full_Signac_workflow_available','full_ArchR_workflow_available','whole_paper_reproduced','biological_accuracy_certified','released','pushed','deployed','whole_goal_complete'])
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==record['canonical_executable_preserved'] and 'ignore_patterns("literature-review.html", "literature-warnings.html")' in (ROOT/'.github/workflows/pages.yml').read_text()
    result=dict(status='passed-independent-current-scoped-chromVAR-catalog-integrity',catalog_counts=expected,source_pins=len(record['source_pins']),catalog_checksums=checksum_count,archive=record['archive'],preserved_unrelated_tool_objects=386,focused_UI_checks=5,broad_UI_checks=299,website_check_passed=True,source_evidence_tests=11,strict_source_context_rejections=8,canonical_preserved=True,original_prior_providers_preserved=True,root_dispatch_available=True,new_scoped_runtime_credit=True,literature_pages_withheld=True,pushed=False,whole_goal_complete=False);out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');print(json.dumps(result),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable independent catalog closure verification')
    main()
