# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the frozen SNPRelate catalog checkpoint and strict live scientific dependencies."""
import argparse
import collections
import gzip
import json
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source
from verify_snprelate_tools_evidence import verify as component_verify
DATA=ROOT/'docs/literature/omics-2023-2026'
STATUS='PASS_SNPRELATE_FOUR_WORKFLOWS_NINE_PACKAGE_ROLES_AND_CATALOG_WITH_GOAL_OPEN'
BASE='build/literature-review/snprelate-runtime/'


def verify(check_current=True):
    proof=json.loads((DATA/'snprelate-catalog-validation.json').read_bytes())
    assert proof['status']==STATUS and proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['whole_package_implemented']
    for name,value in proof['source_pins'].items():verify_source(ROOT,DATA,name,value['sha256'])
    for name,value in proof['component_files'].items():assert pin(ROOT/name)==value,('Changed frozen component',name)
    component_verify()
    assert pin(DATA/'snprelate-catalog-support.zip')==proof['archive'];files=unpack(DATA/'snprelate-catalog-support.zip',proof)
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    def obj(name):return json.loads(files[BASE+name])
    before={r['id']:r for r in json.loads(files['catalog-before/tool-inventory.json'])};after={r['id']:r for r in json.loads(files['catalog-snapshot/tool-inventory.json'])}
    assert len(before)==400 and len(after)==402 and set(after)-set(before)=={'snprelate','genesis'} and all(after[k]==v for k,v in before.items())
    assert after['snprelate']['implementation_status']=='upstream-adapter-validated' and after['snprelate']['papers']==['P130','P170','P191','P216']
    assert after['snprelate']['source_roles_reviewed']==4 and after['snprelate']['supported_source_versions']==['1.46.0']
    assert after['snprelate']['root_command']=='snprelate-tools' and not after['snprelate']['canonical_adapter_available'] and not after['snprelate']['analysis_installs_software']
    assert after['genesis']['implementation_status']=='missing' and after['genesis']['papers']==['P128','P130','P142','P191','P219']
    assert after['cellranger']['implementation_status']=='excluded-by-user'
    counts=dict(collections.Counter(r['implementation_status'] for r in after.values()));summary=json.loads(files['catalog-snapshot/summary.json'])
    assert counts==summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,402,2475,19,77,3,247)
    reviews=json.loads(files['current-source/tools/literature/reviewed-uses.json'])['reviews'];old=json.loads(gzip.decompress(files['catalog-snapshot/snprelate-prior-reviews.json.gz']))['reviews']
    assert len(old)==1029 and reviews[:1029]==old and len(reviews)==len({(r['pmcid'],r['tool_id']) for r in reviews})==1038
    assert len([r for r in reviews[1029:] if r['tool_id']=='snprelate'])==4 and len([r for r in reviews[1029:] if r['tool_id']=='genesis'])==5
    assert sum(len(r.get('supporting_evidence',[])) for r in reviews)==369
    papers=json.loads(files['catalog-snapshot/papers.json']);oldpapers=json.loads(gzip.decompress(files['catalog-snapshot/snprelate-prior-papers.json.gz']))
    assert len(papers)==len(oldpapers)==250
    for a,b in zip(oldpapers,papers,strict=True):
        assert {k:v for k,v in a.items() if k!='tools'}=={k:v for k,v in b.items() if k!='tools'}
        assert a['tools']==[r for r in b['tools'] if r['tool_id'] not in ['snprelate','genesis']]
    assert sum(len(p['tools']) for p in papers)==2475
    p170=next(p for p in papers if p['paper_id']=='P170');assert len(next(t for t in p170['tools'] if t['tool_id']=='snprelate')['supporting_evidence'])==1
    assert files['catalog-snapshot/WARNINGS.md'].startswith(b'## SNPRelate: warnings and required actions')
    readme=files['catalog-snapshot/README.md'];assert b'96 new scoped integrations' in readme and b'99 including three existing adapters' in readme and b'247 named tools still missing' in readme
    assert obj('catalog-source-audit-2/COMPLETED.json')['reviewed_roles']==1038 and len(obj('catalog-shared-gates-2/COMPLETED.json')['checks'])==46
    browser=obj('catalog-ui-2/COMPLETED.json');assert browser['general_checks']==299 and browser['focused_checks']==14 and browser['count_expectations_explicitly_updated']==2
    original=files['current-source/tools/literature/check_cistarget_general_review_ui.cjs'];derived=files[BASE+'catalog-ui-2/general/general-derived.cjs']
    assert original.count(b'/400 named tools/')==original.count(b'/2466 tool observations/')==1
    assert original.replace(b'/400 named tools/',b'/402 named tools/').replace(b'/2466 tool observations/',b'/2475 tool observations/')==derived
    assert obj('catalog-render-3/COMPLETED.json')['all402_tool_objects_unchanged']
    for name in ['tools-offline-windows21-1','tools-offline-linux21-1']:
        record=obj(name+'/COMPLETED.json');assert record['status']=='PASS320_OFFLINE_SNPRELATE_TOOLS_SAVED_BUNDLE_REPLAYS_WITHOUT_R' and len(record['records'])==320 and not record['original_R_analysis_executed']
    assert json.loads(files['genesis-preparation/private-original-setup-1.json'])['analysis_performed'] is False
    assert proof['canonical_executable_unchanged']['sha256']=='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
    if check_current:
        for name,value in proof['catalog_snapshot'].items():assert pin(DATA/name)==value
        for name,value in proof['page_pins'].items():assert pin(ROOT/name)==value
        expected={p.name:pin(p)['sha256'] for p in DATA.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
        rows=(DATA/'SHA256SUMS').read_text(encoding='utf8').splitlines();actual={r.split('  ',1)[1]:r.split('  ',1)[0] for r in rows}
        assert len(rows)==len(actual) and actual==expected
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact current/frozen scientific or catalog checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--archived-catalog-only',action='store_true');a=p.parse_args()
    verify(not a.archived_catalog_only);print(STATUS,'250 papers;99 including existing;247 missing;goal open')
