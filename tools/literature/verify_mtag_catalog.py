# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify one scoped MTAG catalog admission, complete primary roles and unchanged predecessor science; keep the full goal open."""
import argparse
from collections import Counter
import gzip
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from verify_mtag_workflow_evidence import inspect as workflow
from verify_mtag_dimensions import inspect as dimensions

DATA=ROOT/'docs/literature/omics-2023-2026'
STATUS='PASS_MTAG_SCOPED_WORKFLOWS_FIVE_PRIMARY_ROLES_AND_CATALOG_WITH_GOAL_OPEN'
PREFIX='build/literature-review/mtag-runtime/'


def verify(check_current=True):
    proof=json.loads((DATA/'mtag-catalog-validation.json').read_bytes());assert proof['status']==STATUS and proof['new_catalog_credit'] and proof['new_tool_id']=='mtag'
    assert not proof['whole_goal_complete'] and not proof['whole_package_implemented'] and not proof['whole_paper_reproduced']
    assert not proof['predecessor_scientific_verifiers_changed'] and not proof['scientific_source_allowlist_changed']
    for name,wanted in proof['source_pins'].items():verify_source(ROOT,DATA,name,wanted['sha256'])
    for name,wanted in proof['component_files'].items():assert pin(ROOT/name)==wanted,('Changed scientific dependency',name)
    workflow();dimensions()
    from verify_genesis_catalog import verify as previous
    previous(False)
    assert pin(DATA/'mtag-catalog-support.zip')==proof['archive'];files=unpack(DATA/'mtag-catalog-support.zip',proof)
    for name,wanted in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==wanted
    before={r['id']:r for r in json.loads(files['catalog-before/tool-inventory.json'])};after={r['id']:r for r in json.loads(files['catalog-snapshot/tool-inventory.json'])}
    assert set(before)==set(after) and len(after)==402 and before['mtag']['implementation_status']=='missing'
    assert all(after[n]==v for n,v in before.items() if n!='mtag')
    tool=after['mtag'];assert tool['implementation_status']=='upstream-adapter-validated' and tool['source_roles_reviewed']==5 and tool['root_command']=='mtag-tools'
    assert tool['papers']==['P077','P088','P090','P112','P115'] and tool['supported_source_versions']==['1.0.8'] and not tool['canonical_adapter_available']
    assert not tool['analysis_installs_software'] and after['cellranger']['implementation_status']=='excluded-by-user'
    summary=json.loads(files['catalog-snapshot/summary.json']);counts=dict(Counter(r['implementation_status'] for r in after.values()));assert counts==summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,402,2475,19,79,3,245)
    old_reviews=json.loads(gzip.decompress(files['catalog-snapshot/mtag-prior-reviews.json.gz']))['reviews'];reviews=json.loads(files['current-source/tools/literature/reviewed-uses.json'])['reviews']
    assert len(old_reviews)==1038 and len(reviews)==1043 and reviews[:1038]==old_reviews and len({(r['pmcid'],r['tool_id']) for r in reviews})==1043
    assert all(r['tool_id']=='mtag' for r in reviews[1038:]) and sum(len(r.get('supporting_evidence',[])) for r in reviews)==374
    updates=json.loads(files['catalog-snapshot/mtag-applied-reviews.json'])['updates'];assert [u['review'] for u in updates]==reviews[1038:]
    papers=json.loads(files['catalog-snapshot/papers.json']);prior=json.loads(gzip.decompress(files['catalog-snapshot/mtag-prior-papers.json.gz']));assert len(papers)==len(prior)==250
    assert sum(len(p['tools']) for p in papers)==2475
    for a,b in zip(prior,papers,strict=True):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert [t for t in a['tools'] if t['tool_id']!='mtag']==[t for t in b['tools'] if t['tool_id']!='mtag']
    coverage=json.loads(files['catalog-snapshot/mtag-source-role-coverage.json']);assert coverage['applied'] and coverage['prior_reviews_preserved']==1038 and coverage['added_reviews']==5
    equation=json.loads(files['catalog-snapshot/mtag-P088-equation-source-review.json']);assert equation['expression']=='n_snp_eff = 1 / (2 * MAF * (1 - MAF) * SE^2)' and equation['formula_is_separate_from_paragraph_text']
    def obj(name):return json.loads(files[PREFIX+name])
    audit=obj('catalog-source-audit-1/COMPLETED.json');assert (audit['papers'],audit['observations'],audit['reviewed_roles'],audit['supporting_pointers'])==(250,2475,1043,374)
    assert audit['separate_P088_author_equation_revalidated'] and len(audit['package_census'])==14
    gates=obj('catalog-shared-gates-1/COMPLETED.json');assert len(gates['checks'])==55 and not gates['scientific_verifiers_changed'] and not gates['scientific_source_allowlist_changed']
    ui=obj('catalog-ui-1/COMPLETED.json');assert (ui['general_checks'],ui['preserved_genesis_scope_checks'],ui['focused_mtag_checks'])==(299,18,11)
    assert ui['all_predecessor_scientific_scope_assertions_unchanged'] and ui['current_count_expectations_updated']
    assert obj('catalog-render-1/COMPLETED.json')['unchanged_other_tool_objects']==401
    assert obj('catalog-visual-review-1/COMPLETED.json')['status']=='PASS_TWO_MTAG_BROWSER_ARTIFACTS_VISUALLY_REVIEWED'
    assert obj('catalog-metadata-idempotency-1/COMPLETED.json')['status']=='PASS_MTAG_CURRENT_METADATA_REFRESH_IDEMPOTENT_AND_ALL_RENDERED_DATA_UNCHANGED'
    assert obj('catalog-example-contract-1/COMPLETED.json')['status']=='PASS_PUBLISHED_MTAG_REQUEST_EXAMPLE_TYPED_SCHEMA_ONLY'
    root_delta=json.loads(files['catalog-snapshot/mtag-dispatch-shared-source-compatibility.json'])['paths'][0]
    live=files['current-source/src/main/java/org/jlinalg/cli/JLinAlgCli.java'];assert len(root_delta['edits'])==2 and byte_pin(live)['sha256']==root_delta['after_sha256']
    assert reverse_delta(live,root_delta)==files[PREFIX+'dispatch-live-1/root.before.java']
    readme=files['catalog-snapshot/README.md'];assert b'98 new scoped integrations' in readme and b'101 including three existing adapters' in readme and b'245 named tools still missing' in readme
    warnings=files['catalog-snapshot/WARNINGS.md'];assert warnings.startswith(b'## MTAG: warnings and required actions')
    for token in [b'SKIP3',b'FAIL2',b'NO RESULT ADMITTED',b'n_max',b'custom',b'3268760',b'P088',b'P115',b'Cell Ranger']:assert token in warnings,token
    assert proof['canonical_executable_unchanged']['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    if check_current:
        assert all(pin(DATA/n)==v for n,v in proof['catalog_snapshot'].items())
        assert all(pin(ROOT/n)==v for n,v in proof['page_pins'].items())
        expected={p.name:pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
        rows=(DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines();actual={r.split('  ',1)[1]:r.split('  ',1)[0] for r in rows};assert len(rows)==len(actual) and actual==expected
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full source/role/science/browser/catalog preservation checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--archived-catalog-only',action='store_true');a=p.parse_args();verify(not a.archived_catalog_only)
    print(STATUS,'250 papers;98 new/101 including existing;245 missing;goal open')
