# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Archive the validated trace foundation without crediting a Nextflow adapter."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/nextflow-runtime';matrix=args.matrix.resolve();assert matrix.is_relative_to(base.resolve())
    proof=json.loads((matrix/'COMPLETED.json').read_bytes());assert proof['status']=='passed-three-JVM-native-Nextflow-trace-foundation' and proof['tests_per_JVM']==30 and len(proof['runs'])==3
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value,name
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    controls=json.loads((base/'original-controls-2/COMPLETED.json').read_bytes());assert controls['cases']==15 and all(row['control_expectation_passed'] for row in controls['runs'])
    reviews=json.loads((base/'role-verification-1/verification.json').read_bytes());assert not reviews['applied'] and reviews['reviewed_roles']==10
    data=ROOT/'docs/literature/omics-2023-2026';inventory=json.loads((data/'tool-inventory.json').read_bytes());summary=json.loads((data/'summary.json').read_bytes());current_reviews=json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews']
    assert next(t for t in inventory if t['id']=='nextflow')['implementation_status']=='missing' and len(current_reviews)==775 and not any(r['tool_id']=='nextflow' for r in current_reviews)
    assert (summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(15,48,257)
    target=ROOT/'docs/literature/nextflow-trace-foundation';assert not target.exists();target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    entries={}
    def add(path,name):
        assert path.is_file() and name not in entries;entries[name]=path
    for name in proof['source_pins']:add(ROOT/name,'source/'+name)
    extra=['tools/literature/audit_nextflow_roles.py','tools/literature/curate_nextflow_uses.py','tools/literature/nextflow-use-reviews.json','tools/literature/nextflow-runtime-lock.json','tools/literature/freeze_nextflow_trace.py']
    for name in extra:add(ROOT/name,'source/'+name)
    for path in sorted(matrix.rglob('*')):
        if path.is_file():add(path,'matrix/'+path.relative_to(matrix).as_posix())
    for path in sorted((base/'preflight-java11-1').glob('*/result.json')):add(path,'preflight-java11/'+path.parent.name+'/result.json')
    for path in sorted((base/'preflight-java21-1').glob('*/*.log')):add(path,'retained-java21/'+path.parent.name+'/'+path.name)
    for name in ['preflight-java21-1.log','preflight-java11-1.log','original-controls-1.log','original-controls-2.log','freeze-controls-1.log','freeze-controls-2.log','native-trace-build-1.log','native-trace-matrix-1.log']:
        path=base/name;assert path.is_file();add(path,'retained-attempts/'+name)
    for name in ['control.nf','control.config','21.04.1/success/stdout.log','21.04.1/success/stderr.log','21.04.1/success/nextflow.log','21.04.1/success/trace.tsv']:add(base/'original-controls-1'/name,'retained-original-control-1/'+name)
    for name in ['download-cache-1/downloads.json','java11-download-1/download.json','role-verification-1/verification.json','original-controls-2/COMPLETED.json']:add(base/name,'evidence/'+name)
    for name in ['papers.json','tool-inventory.json','summary.json']:add(data/name,'catalog-snapshot/'+name)
    archive=target/'controls.zip';archived={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as output:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;output.writestr(info,raw);archived[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as source:
        assert len(source.infolist())==len(archived)==len(set(source.namelist())) and source.testzip() is None
        assert all(len(source.read(name))==value['bytes'] and hashlib.sha256(source.read(name)).hexdigest()==value['sha256'] for name,value in archived.items())
    result=dict(schema_version=1,status='passed-native-Nextflow-trace-foundation-public-adapter-open',recorded_utc=datetime.now(timezone.utc).isoformat(),
        tests_per_JVM=30,platforms=['windows-java21','windows-java25','linux-java21'],heap_mib=128,skipped_or_aborted_tests=0,original_engine_controls=15,original_engine_editions=controls['engines'],
        scientific_validation=proof,source_pins={name:pin(ROOT/name) for name in [*proof['source_pins'],*extra]},
        native_api='org.jlinalg.workflow.NextflowTraceAudit.read(Path,long,int,Limits,Runnable)',
        method_scope=proof['scope'],source_roles=dict(reviewed_drafts=10,new_supporting_pointers=5,verified_against_all_250_sources=True,applied=False,current_review_count=775,proposed_review_count=785),
        catalog_snapshot=dict(new_scoped_integrations=63,native_scopes=15,upstream_adapters=48,including_preexisting_adapters=66,missing=257,Nextflow_status='missing'),
        archive=dict(path=archive.name,**pin(archive),entries=len(archived)),archive_files=archived,
        claims=dict(native_trace_api_available=True,public_runtime_installer_available=False,public_Nextflow_analysis_adapter_available=False,catalog_promoted=False,whole_paper_reproduced=False,whole_goal_complete=False,released_or_deployed=False),
        required_action='Nextflow remains missing until a typed public adapter, exact-runtime setup/admission, complete output/bundle provenance and packaged recovery checks are validated. Skip analyses depending on that unavailable adapter. The trace API requires an independently declared complete row count and reports only the observed single-attempt trace; retries with repeated IDs are rejected. An ignored failed task can coexist with engine exit0; empty traces establish no biological result. Never treat a trace summary as output, runtime or whole-paper certification.')
    output=target/'VALIDATION.json';output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    (target/'SHA256SUMS').write_text(''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in [archive,output]),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive_bytes=pin(archive)['bytes'],archive_entries=len(archived),tests_per_JVM=30,original_engine_controls=15,catalog_promoted=False)))


if __name__=='__main__':main()
