# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original engine controls as a small offline Java trace-test fixture."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def main():
    if not __debug__:raise RuntimeError('Never disable fixture assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run',type=Path,required=True);args=parser.parse_args()
    run=args.run.resolve();base=ROOT/'build/literature-review/nextflow-runtime';assert run.is_relative_to(base.resolve())
    proof=json.loads((run/'COMPLETED.json').read_bytes());assert proof['status']=='passed-original-Nextflow-DAG-failure-resume-controls' and proof['cases']==15
    producer=ROOT/'tools/literature/collect_nextflow_runtime_controls.py';assert proof['collector_pin']==pin(producer.read_bytes())
    dest=ROOT/'src/test/resources/org/jlinalg/workflow/nextflow';assert not dest.exists()
    entries={};expected=['version\tmode\trows\tengine_exit\toutcome']
    for path in sorted(run.rglob('*')):
        relative=path.relative_to(run).as_posix()
        attributes=path.lstat()
        # Windows cannot dereference WSL's LX_SYMLINK reparse points. Skip all
        # links/reparse points before asking whether a target is a regular file.
        if stat.S_ISREG(attributes.st_mode) and not getattr(attributes,'st_reparse_tag',0) and (path.name in {'control.nf','control.config','COMPLETED.json','result.json','trace.tsv','stdout.log','stderr.log','nextflow.log','process.json','.command.sh','.command.run','.command.out','.command.err','.command.log','.exitcode','counts.tsv'} or relative.startswith('inputs/')):
            raw=path.read_bytes();assert len(raw)<2_097_152;entries[relative]=raw
    assert sum(len(raw) for raw in entries.values())<16_777_216
    for row in proof['runs']:
        version,mode=row['version'],row['mode'];assert row['control_expectation_passed'] and not row['timed_out']
        outcome='SUCCESSFUL_OBSERVED_TASKS' if mode in {'success','resume'} else 'EMPTY' if mode=='empty-workflow' else 'FAILED_OR_INCOMPLETE'
        expected.append(f"{version}\t{mode}\t{row['task_count']}\t{row['exit_code']}\t{outcome}")
        assert version+'/'+mode+'/trace.tsv' in entries
    entries['expected.tsv']=('\n'.join(expected)+'\n').encode();entries['producer.py']=producer.read_bytes()
    fixture_receipt=dict(scope='Fifteen actual original Nextflow runs across three exact engines. Synthetic DAG/counting, cached replay, failure, ignored failure and empty workflow. A native trace inspection foundation; not a packaged public adapter or whole-paper reproduction.',engines=proof['engines'],cases=15,engine_downloads=json.loads((base/'download-cache-1/downloads.json').read_bytes()),java_package=json.loads((base/'java11-download-1/download.json').read_bytes()),producer_pin=pin(producer.read_bytes()),files={name:pin(raw) for name,raw in entries.items()},ignored_failure_requires_explicit_failed_task_detection=True,empty_workflow_does_not_establish_a_biological_result=True,public_adapter_available=False)
    entries['receipt.json']=(json.dumps(fixture_receipt,indent=2)+'\n').encode()
    dest.mkdir(parents=True);(dest/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    target=dest/'original-engine-controls.zip'
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(entries.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    with zipfile.ZipFile(target) as archive:
        assert len(archive.infolist())==len(entries)==len({e.filename for e in archive.infolist()})
        assert all(archive.read(name)==raw for name,raw in entries.items())
    print(json.dumps(dict(status='frozen-fifteen-original-Nextflow-engine-controls',fixture=pin(target.read_bytes()),entries=len(entries),cases=15,public_adapter_available=False)))


if __name__=='__main__':main()
