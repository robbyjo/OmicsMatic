# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve independent runtime/parameter and completion evidence, with no tool credit."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_nextflow_setup import check_report


def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable checkpoint assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/nextflow-runtime';target=a.out.resolve();assert os.uname().sysname=='Linux' and target.is_relative_to((ROOT/'docs/literature').resolve()) and not target.exists()
    admission_dir=base/'admission-development-1/matrix-1';completion_dir=base/'completion-development-1/matrix-1';admission=read(admission_dir/'COMPLETED.json');completion=read(completion_dir/'COMPLETED.json')
    assert admission['status']=='passed-three-JVM-Nextflow-exact-runtime-admission-and-lossless-parameters' and admission['tests_per_JVM']==85 and len(admission['runs'])==3
    assert completion['status']=='passed-three-JVM-Nextflow-completion-and-recovered-retry-audit' and completion['tests_per_JVM']==29 and len(completion['runs'])==3
    sources={**admission['source_pins'],**completion['source_pins']}
    for name,value in sources.items():assert pin(ROOT/name)==value,name
    for proof in [admission,completion]:assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==admission['canonical_executable_unchanged']==completion['canonical_executable_unchanged']
    with zipfile.ZipFile(ROOT/admission['artifact']['path']) as previous,zipfile.ZipFile(ROOT/completion['artifact']['path']) as current:
        old=set(previous.namelist());new=set(current.namelist());assert old<=new and all(previous.read(name)==current.read(name) for name in old)
        added=new-old;assert len(added)==8 and all(name.startswith('org/jlinalg/workflow/NextflowCompletionAudit') for name in added)
    setup=read(base/'managed-setup-development-1/setup-validation-1/COMPLETED.json');lock=read(ROOT/'tools/literature/nextflow-managed-runtime-lock.json');post=[]
    for row in setup['rechecks']:
        verified=check_report(Path(row['report']),Path(row['home']),row['profile'],lock,pin(ROOT/'tools/literature/install_nextflow.py'));assert verified['marker']==row['marker'];post.append(verified)
    print('Complete managed runtimes and setup evidence remain unchanged after native admission and guarded workflows.',flush=True)
    data=ROOT/'docs/literature/omics-2023-2026';summary=read(data/'summary.json');inventory=read(data/'tool-inventory.json');assert next(tool for tool in inventory if tool['id']=='nextflow')['implementation_status']=='missing'
    assert (summary['papers'],summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(250,15,48,257)
    entries={}
    def add(path,name):assert path.is_file() and name not in entries;entries[name]=path
    for name in sources:add(ROOT/name,'source/'+name)
    self_name=Path(__file__).relative_to(ROOT).as_posix();sources[self_name]=pin(Path(__file__));add(Path(__file__),'source/'+self_name)
    for folder,label in [(admission_dir,'native-admission-matrix'),(completion_dir,'completion-matrix')]:
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,'evidence/'+label+'/'+path.relative_to(folder).as_posix())
    for folder,label in [(base/'admission-probe-1','initial-native-probe')]:
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,'evidence/'+label+'/'+path.relative_to(folder).as_posix())
    for name in ['admission-build-1.log','admission-build-2.log','admission-matrix-1.log','completion-build-1.log','completion-matrix-1.log']:add(base/name,'retained-attempts/'+name)
    for name in ['summary.json','papers.json','tool-inventory.json']:add(data/name,'catalog-snapshot/'+name)
    target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');archive=target/'controls.zip';archived={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as output:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;output.writestr(entry,raw);archived[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as check:
        assert len(check.infolist())==len(archived)==len(set(check.namelist())) and check.testzip() is None
        for name,value in archived.items():
            raw=check.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    result=dict(schema_version=1,status='passed-native-Nextflow-admission-parameters-and-completion-public-analysis-open',recorded_utc=datetime.now(timezone.utc).isoformat(),
        native_admission_validation=admission,completion_validation=completion,artifact=completion['artifact'],unchanged_parent_entries=len(old),new_completion_classes=sorted(added),post_matrix_exact_runtime_verification=post,
        validation_scope='85 actual-JAR admission/parameter/file/setup-export/prior-process checks on each of three profiles;29 additional actual-JAR completion checks per profile on the extended candidate. Every parent entry is byte-identical in the extended candidate. The21 completion reference receipts come from retained original engines, not new biological datasets. Windows validates portable metadata/export and explicit Linux-unavailable branches;original workflows run on Linux.',
        source_pins=sources,archive=dict(path=archive.name,**pin(archive),entries=len(archived)),archive_files=archived,
        completion_semantics='Recovered retries preserve failed attempts and are reconciled with terminal original-engine counters. Ignored failure remains incomplete despite exit0. EMPTY means no observed task execution; neither it nor successful execution metadata admits biological results or substitutes for declared-output verification.',
        catalog_snapshot=dict(new_scoped_integrations=63,native_scopes=15,upstream_adapters=48,including_existing_adapters=66,missing=257,Nextflow_status='missing'),
        claims=dict(native_runtime_admission_available=True,lossless_parameter_binding_available=True,native_completion_reconciliation_available=True,standalone_setup_export_available=True,typed_public_analysis_command_available=False,portable_analysis_bundle_verification_available=False,catalog_promoted=False,user_analysis_admitted=False,whole_paper_reproduced=False,whole_goal_complete=False,released_or_deployed=False),
        required_action='The project still lacks the typed public Nextflow analysis command, captured workflow/data/output bundles and portable analysis verification. Keep dependent analysis skipped until those are validated. Exact runtime setup/export/admission is separate from analysis. Install/download only where permitted; unavailable/forbidden required dependencies SKIP3, while integrity/input/process/numerical/capacity/cancellation failures FAIL2 without a successful prefix. Read retained diagnostics and WARNINGS.md, including the130 effective permission-mode departures on this filesystem; use new permitted paths for corrected runs. Cell Ranger remains excluded.')
    write(target/'VALIDATION.json',result);(target/'SHA256SUMS').write_text(''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in [archive,target/'VALIDATION.json']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive_entries=len(archived),archive_bytes=pin(archive)['bytes'],admission_checks_per_JVM=85,additional_completion_checks_per_JVM=29,catalog_promoted=False)))


if __name__=='__main__':main()
