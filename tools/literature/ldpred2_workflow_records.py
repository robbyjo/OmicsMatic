#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read saved v2 workflow evidence without recomputing or refreshing fingerprints."""
from pathlib import Path
import argparse,difflib,hashlib,json,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/'docs/literature/ldpred2-workflow-v2-development'
PARENT='25523468590f9b9f96f1d3483fdc8109bc0e801a'
CHANGED=['src/main/java/org/jlinalg/cli/LdPred2OriginalRuntime.java','src/main/java/org/jlinalg/cli/LdPred2WorkflowCli.java','src/main/java/org/jlinalg/cli/LdPred2WorkflowFiles.java']

def identity(raw):return {'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
def pin(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):h.update(raw)
    return {'bytes':path.stat().st_size,'sha256':h.hexdigest()}
def original(name):return subprocess.check_output(['git','show',PARENT+':'+name],cwd=ROOT)

def verify(private_base=None,require_current=False):
    status=json.loads((EVIDENCE/'STATUS.json').read_bytes())
    assert status['status']=='PASS_SCOPED_LDPRED2_V2_WORKFLOW_CONTROLS'
    assert status['java_tests']=={'tests':22,'failures':0,'errors':0,'skipped':0}
    assert status['qualified_original_editions']==['1.12.2','1.12.4']
    assert status['new_catalog_credit']==0 and status['whole_goal_complete'] is False
    pins=json.loads((EVIDENCE/'SOURCE-PINS.json').read_bytes());current=all((ROOT/n).is_file() and pin(ROOT/n)==v for n,v in pins.items())
    if require_current:assert current,'Current implementation differs; preserve records and use the matching original artifact. No hash refresh.'
    for archive,expected in status['archives'].items():
        with zipfile.ZipFile(EVIDENCE/archive) as z:
            assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(expected)
            for n,v in expected.items():assert identity(z.read(n))==v,(archive,n)
    with zipfile.ZipFile(EVIDENCE/'source-snapshot.zip') as z:
        for n,v in pins.items():assert identity(z.read(n))==v,n
    # Historical source is read from the published commit, never injected into
    # the current checkout or passed as a replacement for scientific methods.
    legacy='docs/literature/ldpred2-workflow-development/'
    for n in ['SOURCE-PINS.json','STATUS.json','WARNINGS.md','validation-records.zip','.gitattributes']:
        assert (ROOT/(legacy+n)).read_bytes()==original(legacy+n),n
    old_pins=json.loads(original(legacy+'SOURCE-PINS.json'))
    for n,v in old_pins.items():assert identity(original(n))==v,n
    mismatch=sorted(n for n,v in old_pins.items() if pin(ROOT/n)!=v)
    if require_current:assert mismatch==sorted(CHANGED),mismatch
    if current:
        diff=''
        for n in CHANGED:diff+=''.join(difflib.unified_diff(original(n).decode().splitlines(True),(ROOT/n).read_text().splitlines(True),fromfile=PARENT+'/'+n,tofile='v2/'+n))
        assert diff==(EVIDENCE/'SOURCE-TRANSITION.diff').read_text()
        v1=(ROOT/'src/main/resources/org/jlinalg/omics/ldpred2-workflow-v1/Worker.R').read_bytes()
        v2=(ROOT/'src/main/resources/org/jlinalg/omics/ldpred2-workflow-v2/Worker.R').read_bytes()
        assert v1.replace(b'all(diag(matrix)==1),isSymmetric(matrix',b'all(Matrix::diag(matrix)==1),Matrix::isSymmetric(matrix')==v2
    private=json.loads((EVIDENCE/'PRIVATE-RECORDS.json').read_bytes());checked=0
    if private_base is not None:
        base=Path(private_base).resolve()
        actual={}
        for name in private['complete_roots']:
            folder=(base/name).resolve();assert folder.is_relative_to(base) and folder.is_dir(),name
            for path in folder.rglob('*'):
                assert not path.is_symlink(),str(path)
                if path.is_file():actual[path.relative_to(base).as_posix()]=pin(path)
        assert actual==private['files'],'Complete named private record bytes or membership differ'
        checked=len(actual)
    return dict(status='PASS_SAVED_LDPRED2_V2_WORKFLOW_RECORD_READBACK',current_project_sources_match_snapshot=current,historical_v1_sources_and_evidence_unchanged=True,historical_v1_current_source_mismatches=mismatch,complete_named_private_files_checked=checked,qualified_original_editions=status['qualified_original_editions'],native_workflow_scale_variants=1100000,native_worker_heap_mb=1024,original_auto_scale_qualified=False,full_paper_reproduction=False,new_catalog_credit=0,whole_goal_complete=False,fresh_scientific_execution_performed=False)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--private-base',type=Path);p.add_argument('--require-current-source',action='store_true');a=p.parse_args()
    try:print(json.dumps(verify(a.private_base,a.require_current_source),indent=2));return 0
    except Exception as error:print('FAIL2_LDPRED2_V2_RECORDS_NOT_ACCEPTED',repr(error),'Preserve all evidence; restore the matching original files/artifact or rerun analysis in NEW paths. Never refresh saved hashes to suppress refusal.');return 2
if __name__=='__main__':raise SystemExit(main())
