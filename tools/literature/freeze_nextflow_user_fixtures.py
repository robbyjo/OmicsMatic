# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze six complete original-engine user-workflow/resume attempts as portable verifier test inputs."""
import argparse,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);args=parser.parse_args();matrix=args.matrix.resolve()
    target=ROOT/'src/test/resources/org/jlinalg/cli/nextflow-workflow';assert matrix.is_relative_to((ROOT/'build/literature-review/nextflow-runtime').resolve()) and not target.exists();target.mkdir()
    (target/'.gitattributes').write_text('* -text\n',encoding='utf-8',newline='\n')
    cases=[];source_files={}
    with zipfile.ZipFile(target/'initial-controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for version in ['21.04.1','22.10.6','24.04.4']:
            control=json.loads((matrix/version/'module-success/COMPLETED-CONTROL.json').read_bytes());assert control['passed'] and control['expected_exit']==0
            for attempt in ['000001','000002']:
                root=matrix/version/'module-success/retained/attempts'/attempt;marker=json.loads((root/'COMPLETED.json').read_bytes());manifest=(root/'manifest.json').read_bytes();assert marker['status']=='completed-declared-workflow-result' and marker['manifest']['sha256']==hashlib.sha256(manifest).hexdigest()
                key=version+'/'+attempt;cases.append(dict(key=key,version=version,attempt=attempt,output_files=1,output_bytes=19,cached=4 if attempt=='000002' else 0))
                for path in sorted(root.rglob('*')):
                    assert not path.is_symlink();name=key+'/'+path.relative_to(root).as_posix()+('/' if path.is_dir() else '');entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=(0o40755 if path.is_dir() else 0o100644)<<16;raw=b'' if path.is_dir() else path.read_bytes();archive.writestr(entry,raw)
                    if not path.is_dir():source_files[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
        entry=zipfile.ZipInfo('cases.json',(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,json.dumps(cases,indent=2)+'\n')
    raw=(target/'initial-controls.zip').read_bytes();record=dict(status='frozen-six-complete-original-engine-user-workflow-and-cache-attempts',archive=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()),cases=cases,source_files=source_files,producer_artifact=json.loads((matrix/'OWNER.json').read_bytes())['artifact'],whole_paper_reproduced=False,biological_accuracy_certified=False,scope='Six portable verifier inputs from original typed runs; three original engines, one reused tiny count population, plus real cached resumes. Not independent biological datasets.')
    (target/'provenance.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(cases=len(cases),archive=record['archive'],files=len(source_files))))
if __name__=='__main__':main()
