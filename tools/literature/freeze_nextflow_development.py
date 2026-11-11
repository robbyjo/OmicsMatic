# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve exact runtime/process development evidence without catalog promotion."""
import argparse
from datetime import datetime,timezone
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_nextflow_setup import check_report


def read(path):return json.loads(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable checkpoint assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/nextflow-runtime';run=base/'managed-setup-development-1';matrix=base/'process-development-1/matrix-1';target=a.out.resolve()
    assert os.uname().sysname=='Linux' and target.is_relative_to((ROOT/'docs/literature').resolve()) and not target.exists()
    setup=read(run/'setup-validation-1/COMPLETED.json');process=read(matrix/'COMPLETED.json');retry=read(base/'original-retries-1/COMPLETED.json')
    assert setup['status']=='passed-three-exact-Nextflow-runtime-setups-and-rechecks' and setup['synthetic_DAG_runs']==6 and setup['unit_controls']['tests']==16
    assert process['status']=='passed-three-JVM-Nextflow-owned-process-and-original-workflow-controls' and process['tests_per_JVM']==48 and process['fresh_guarded_original_workflows']==6 and len(process['runs'])==3
    assert retry['status']=='passed-six-original-Nextflow-retry-controls' and retry['cases']==6
    sources={**setup['source_pins'],**process['source_pins']}
    for name,value in sources.items():assert pin(ROOT/name)==value,name
    artifact=ROOT/process['artifact']['path'];assert pin(artifact)=={k:process['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==process['canonical_executable_unchanged']
    assert pin(ROOT/'tools/literature/collect_nextflow_retry_controls.py')==retry['collector_pin']
    locked=read(ROOT/'tools/literature/nextflow-managed-runtime-lock.json');installer_pin=pin(ROOT/'tools/literature/install_nextflow.py');post=[]
    for entry in setup['rechecks']:
        verified=check_report(Path(entry['report']),Path(entry['home']),entry['profile'],locked,installer_pin);assert verified['marker']==entry['marker'];post.append(verified)
    print('All three complete managed runtime payloads remain unchanged after the six native-guard original workflows.',flush=True)
    data=ROOT/'docs/literature/omics-2023-2026';summary=read(data/'summary.json');inventory=read(data/'tool-inventory.json')
    assert (summary['papers'],summary['implementation_status_counts']['native-subset-validated'],summary['implementation_status_counts']['upstream-adapter-validated'],summary['implementation_status_counts']['missing'])==(250,15,48,257)
    assert next(tool for tool in inventory if tool['id']=='nextflow')['implementation_status']=='missing'
    entries={};links={}
    def add(path,name):
        info=path.lstat()
        if stat.S_ISLNK(info.st_mode):links[name]=os.readlink(path);return
        assert stat.S_ISREG(info.st_mode) and name not in entries and info.st_size<8_388_608,(name,info.st_size)
        entries[name]=path
    for name in sources:add(ROOT/name,'source/'+name)
    for name in ['tools/literature/collect_nextflow_retry_controls.py','tools/literature/collect_nextflow_runtime_controls.py','tools/literature/audit_nextflow_roles.py','tools/literature/nextflow-use-reviews.json','tools/literature/curate_nextflow_uses.py','tools/literature/freeze_nextflow_development.py']:
        if name not in sources:sources[name]=pin(ROOT/name);add(ROOT/name,'source/'+name)
    groups=[(matrix,'process-matrix'),(base/'original-retries-1','original-retries'),(run/'setup-validation-1','setup-validation'),(run/'installer-tests-1','installer-tests')]
    groups += [(run/('report-'+suffix),'initial-setup/'+suffix) for suffix in ['21-v2','22','24']]
    groups += [(run/'report-21','retained-initial-mode-failure')]
    for folder,label in groups:
        for path in sorted(folder.rglob('*')):
            if not path.is_dir() or path.is_symlink():add(path,'evidence/'+label+'/'+path.relative_to(folder).as_posix())
    for suffix in ['21-v2','22','24']:add(run/('home-'+suffix)/'.omicsmatic-nextflow.json','runtime-metadata/'+suffix+'/marker.json')
    add(run/'download-probe-1/COMPLETED.json','evidence/permitted-download/COMPLETED.json')
    add(run/'install_nextflow-before-cleanup.py','retained-attempts/initial-installer.py')
    for path in sorted(run.glob('*.log')):add(path,'retained-attempts/'+path.name)
    for name in ['process-build-1.log','process-matrix-1.log','original-retries-1.log','process-guard-derivation.json']:add(base/name,'retained-attempts/'+name)
    for name in ['summary.json','papers.json','tool-inventory.json']:add(data/name,'catalog-snapshot/'+name)
    target.mkdir();(target/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');archive=target/'controls.zip';archived={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as output:
        raw_entries={name:path.read_bytes() for name,path in sorted(entries.items())};raw_entries['recorded-symbolic-links.json']=(json.dumps(links,indent=2)+'\n').encode()
        for name,raw in raw_entries.items():
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;output.writestr(info,raw);archived[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as check:
        assert len(check.infolist())==len(archived)==len(set(check.namelist())) and check.testzip() is None
        for name,value in archived.items():
            raw=check.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    result=dict(schema_version=1,status='passed-exact-Nextflow-runtime-setup-and-owned-process-development-public-analysis-open',recorded_utc=datetime.now(timezone.utc).isoformat(),
        runtime_setup=setup,native_process_validation=process,original_retry_controls=retry,post_native_execution_runtime_verification=post,
        source_pins=sources,artifact=process['artifact'],archive=dict(path=archive.name,**pin(archive),entries=len(archived)),archive_files=archived,recorded_symbolic_links=len(links),
        catalog_snapshot=dict(papers=250,new_scoped_integrations=63,native_scopes=15,upstream_adapters=48,including_preexisting_adapters=66,missing=257,Nextflow_status='missing'),
        evidence_scope='Six synthetic setup DAGs, six fresh exact-engine workflows through the native guard and six original retry controls reuse tiny controlled populations. Windows has30 portable trace checks and18 Linux-unavailable checks;Linux has30 trace checks,12 real process checks and6 actual original workflow checks. No biological benchmark or full paper is reproduced.',
        retained_warning_explanation='The three JUnit temporary-directory warnings are deliberate outside-link rejection controls. They report unlinking the test-created symlinks while leaving their external targets untouched. Separately,130 effective mode departures on the mounted filesystem are recorded in each runtime marker and prominently in setup WARNINGS.md; file/link byte pins and required executable access remain checked.',
        claims=dict(standalone_exact_runtime_setup_available=True,native_owned_process_component_available=True,typed_public_analysis_command_available=False,portable_analysis_bundle_verification_available=False,catalog_promoted=False,user_analysis_admitted=False,whole_paper_reproduced=False,whole_goal_complete=False,released_or_deployed=False),
        required_action='Use the standalone install_nextflow.py command only for exact runtime setup; require exit0, COMPLETED.json and full unchanged hashes. Install/download only where permitted. Missing/forbidden installation or download requires SKIP3 and skipped dependent analysis. Corrupt/incomplete data, integrity, process, cancellation or capacity failures require FAIL2 with retained diagnostics and rerun to new paths. Read every WARNINGS.md, including effective filesystem-mode departures. Public typed Nextflow analysis and portable output/bundle admission remain unimplemented; skip analyses depending on those unavailable project capabilities. Cell Ranger remains excluded.')
    write(target/'VALIDATION.json',result)
    (target/'SHA256SUMS').write_text(''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in [archive,target/'VALIDATION.json']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive_entries=len(archived),archive_bytes=pin(archive)['bytes'],source_files=len(sources),tests_per_JVM=48,catalog_promoted=False)))


if __name__=='__main__':main()
