# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify preserved cohort/reviews, distinct recovered software, original controls and rendered evidence."""
import csv
import gzip
import io
import json
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from monocle_catalog_context import verify_current_review_extension,load_catalog_registry


def verify():
    data=ROOT/'docs/literature/omics-2023-2026';proof=json.loads((data/'monocle-catalog-validation.json').read_bytes());assert proof['status']=='passed-Monocle-related-catalog-source-and-original-Cicero-readiness'
    assert pin(data/'monocle-catalog-support.zip')==proof['archive'];files=unpack(data/'monocle-catalog-support.zip',proof)
    for n,v in proof['source_pins'].items():verify_source(ROOT,data,n,v['sha256']);assert byte_pin(files['current-source/'+n])==v
    for n,v in proof['parent_proofs'].items():assert pin(ROOT/n)==v
    papers=json.loads(files['catalog-snapshot/papers.json']);verify_fixed_cohort(ROOT,data,papers);assert json.loads((data/'papers.json').read_bytes())==papers
    reviews=json.loads(files['catalog-snapshot/reviewed-uses.json']);assert (ROOT/'tools/literature/reviewed-uses.json').read_bytes()==files['catalog-snapshot/reviewed-uses.json'];verify_current_review_extension(ROOT,data,reviews['reviews']);assert len(load_catalog_registry(ROOT))==427
    before=json.loads(gzip.decompress(files['catalog-snapshot/monocle-prior-reviews.json.gz']));assert len(before['reviews'])==905 and reviews['reviews'][:905]==before['reviews'] and len(reviews['reviews'])==917
    base='build/literature-review/monocle-runtime/';get=lambda n:json.loads(files[base+n]);oldpapers=json.loads(gzip.decompress(files[base+'catalog-curation-1/before-papers.json.gz']))
    assert len(papers)==len(oldpapers)==250 and sum(len(p['tools']) for p in papers)==2449
    for old,new in zip(oldpapers,papers):
        assert {k:v for k,v in old.items() if k!='tools'}=={k:v for k,v in new.items() if k!='tools'}
        assert [t for t in old['tools'] if t['tool_id']!='monocle']==[t for t in new['tools'] if t['tool_id'] not in ['monocle','cicero','py-monocle']]
    inventory=json.loads(files['catalog-snapshot/tool-inventory.json']);assert inventory==json.loads((data/'tool-inventory.json').read_bytes());a={t['id']:t for t in json.loads(files[base+'catalog-source-intake-1/docs/literature/omics-2023-2026/tool-inventory.json'])};b={t['id']:t for t in inventory}
    assert set(b)-set(a)=={'cicero','py-monocle'} and len(b)==389 and [n for n in a if a[n]!=b[n]]==['monocle']
    assert all(b[n]['implementation_status']=='missing' for n in ['monocle','cicero','py-monocle']) and (b['monocle']['paper_count'],b['cicero']['paper_count'],b['py-monocle']['paper_count'])==(9,2,1)
    assert b['monocle']['partial_development_workflows_available'] and not b['monocle']['canonical_adapter_available']
    assert next(t for t in papers[50]['tools'] if t['tool_id']=='monocle')['version_reported_near_name']=='1.3.7'
    summary=json.loads(files['catalog-snapshot/summary.json']);assert summary==json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(250,389,2449,16,58,256)
    audit=json.loads(files['catalog-snapshot/monocle-source-audit.json']);assert audit['papers_pin']==byte_pin(files['catalog-snapshot/papers.json']) and audit['reviews_pin']==byte_pin(files['catalog-snapshot/reviewed-uses.json'])
    assert (audit['source_xml_hashes_verified'],audit['tool_observation_pointers_verified'],audit['individually_reviewed_usage_summaries_verified'],audit['supporting_paragraph_pointers_verified'])==(250,2449,917,309)
    for n,v in audit['source_pins'].items():assert pin(ROOT/n)==v
    history=get('version-history-intake-2/COMPLETED.json');assert len(history['records'])==182 and {r['version']:r['commit'] for r in history['candidates']}=={'1.3.3':'4b5cd9bf0dc525144723b0d41eb445cada7cd7c7','1.3.7':'98402ed0c10cac020524bebbb9300614a799f6d1'}
    related='related-source-intake-1/';source=get(related+'COMPLETED.json');assert len(source['profiles'])==4 and not source['paper_commits_inferred']
    for id,count in [('cicero-m3',174),('monocle3-1.3.3',207),('monocle3-1.3.7',207)]:
        receipt=get(related+id+'/SOURCE.json');assert len(receipt['source_members'])==count
        for n,v in receipt['source_members'].items():assert byte_pin(files[base+related+id+'/source/'+n])==v
    py=get(related+'py-monocle/SOURCE.json');assert len(py['source_members'])==35
    assert {n.removeprefix(base+related+'py-monocle/source/') for n in files if n.startswith(base+related+'py-monocle/source/')}=={'LICENSE'}
    assert base+related+'py-monocle/source.tar.gz' not in files and not proof['py_monocle_runtime_executed'] and not proof['restricted_python_source_distributed']
    setup=get('cicero-reference-setup-1/RESULT.json');assert setup['status']=='INSTALLED_ORIGINAL_CICERO_REFERENCE_ONLY' and setup['preexisting_libraries_unchanged'] and setup['preexisting_file_count']==32321
    assert get('cicero-reference-setup-1/BASES-BEFORE.json')==get('cicero-reference-setup-1/BASES-AFTER.json')
    assert len(get('cicero-reference-setup-1/health.json')['selected_original_functions_identical'])==7
    sci=get('cicero-readiness-probe-2/RESULT.json');assert sci['status']=='COMPLETED_ORIGINAL_CICERO_COACCESSIBILITY_CONTROL' and sci['staged_and_direct_run_identical'] and sci['save_reload_identical']
    assert (sci['genes'],sci['cells'],sci['aggregates'],sci['distance_windows'],sci['successful_models'],sci['connection_rows'],sci['unresolved_NA'])==(80,240,68,6,8,2172,4)
    assert get('cicero-readiness-probe-2/LIBRARIES-BEFORE.json')==get('cicero-readiness-probe-2/LIBRARIES-AFTER.json') and sci['library_files_checked']==33277
    for n in ['input.rds','connections.rds','run-cicero-return.rds','models.rds','distance-parameters.rds']:assert files[base+'cicero-readiness-probe-1/'+n]==files[base+'cicero-readiness-probe-2/'+n]
    table=list(csv.DictReader(io.StringIO(files[base+'cicero-readiness-probe-2/connections.tsv'].decode('utf8')),delimiter='\t'));assert len(table)==2172 and sum(r['coaccess']=='NA' for r in table)==4
    prefix=proof['ui_prefix'];ui=json.loads(files[prefix+'COMPLETED.json']);visual=json.loads(files[prefix+'VISUAL-REVIEW.json']);general=json.loads(files[prefix+'general/ui-validation.json']);scoped=json.loads(files[prefix+'monocle/monocle-ui-verification.json'])
    assert ui['page']==visual['page']==byte_pin(files['site-snapshot/literature-review.html']) and ui['warning_page']==byte_pin(files['site-snapshot/literature-warnings.html'])
    assert general['status']==scoped['status']=='passed' and len(general['checks'])==299 and len(scoped['checks'])==5 and scoped['mobile_layout']==dict(viewport=390,document=390) and visual['desktop_and_mobile_reviewed']
    for n,v in visual['visual_files'].items():assert byte_pin(files[prefix+'monocle/'+n])==v
    for name in ['monocle-render-shared-source-compatibility.json','monocle-reviews-shared-source-compatibility.json']:
        delta=json.loads(files['catalog-snapshot/'+name])
        for row in delta['paths']:reverse_delta(files['current-source/'+row['path']],row)
    names=set()
    for line in (data/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        digest,n=line.split('  ',1);assert n not in names and pin(data/n)['sha256']==digest;names.add(n)
    assert names=={p.name for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'}
    assert not proof['implementation_promoted'] and not proof['whole_goal_complete'];print('Verified250 source records,2449 observations,917 reviewed uses,309 support pointers, recovered source candidates and2172 original Cicero connections with four retained NA values')
    return proof


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete catalog/source/evidence checks')
    verify()
