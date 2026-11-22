"""Check published checkpoint bytes and its declared unfinished scope; no R fits."""
from pathlib import Path
import argparse,hashlib,json,zipfile

ROOT=Path(__file__).resolve().parents[3]
CHECKPOINT=ROOT/'docs/literature/skat-workflow-handover-20261122'

def pin(path):
    with path.open('rb') as stream:
        return {'bytes':path.stat().st_size,'sha256':hashlib.file_digest(stream,'sha256').hexdigest()}

def verify():
    status=json.loads((CHECKPOINT/'STATUS.json').read_bytes())
    assert status['SKAT_Java_adapter_qualified'] is False
    assert status['new_catalog_credit']==0 and status['whole_goal_complete'] is False
    for name,expected in status['file_pins'].items():
        path=ROOT/name
        assert path.resolve().is_relative_to(ROOT.resolve()),name
        assert pin(path)==expected,('Changed checkpoint file',name)
    source=ROOT/'src/main/resources/org/jlinalg/omics/skat-workflow'
    descriptor=json.loads((source/'SOURCE.json').read_bytes())
    assert descriptor['namespace_functions']==239
    assert len(descriptor['packaged_reference_files'])==302
    for name,expected in descriptor['packaged_reference_files'].items():
        assert pin(source/name)==expected,('Changed original reference',name)
    for name,expected in status['catalog_pins'].items():
        assert pin(ROOT/name)==expected,('Changed predecessor catalog snapshot',name)
    manifest=json.loads((CHECKPOINT/'EVIDENCE.json').read_bytes())
    archive=CHECKPOINT/manifest['archive']
    assert pin(archive)==manifest['archive_pin']
    with zipfile.ZipFile(archive) as z:
        actual=set(z.namelist())
        assert len(actual)==len(z.namelist()) and actual==set(manifest['members'])
        assert z.testzip() is None
        for name,expected in manifest['members'].items():
            data=z.read(name)
            assert {'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}==expected,name
    tests=status['normal_gradle_existing_ACAT_regression']
    assert tests['exit_code']==0 and tests['tests']==500 and tests['failures']==0
    assert status['current_worker_prototypes']['status']=='PASS_EIGHT_ORIGINAL_SKAT_WORKER_PROTOTYPES'
    assert status['parameterized_public_harness']['status']=='PASS_EIGHT_ORIGINAL_SKAT_WORKER_PROTOTYPES'
    print('PASS checkpoint source, 302 references, unchanged catalog and complete artificial evidence hashes')
    print('Recorded evidence: 500 existing ACAT regressions; eight original worker cases per successful attempt')
    print('UNFINISHED: no SKAT Java CLI, SKAT catalog admission, independent refit or whole-goal completion')

if __name__=='__main__':
    argparse.ArgumentParser(description=__doc__).parse_args()
    verify()
