"""Read the qualified-workflow handover without analyses or network access."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,sys


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(root):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    folder=root/'docs/literature/squidpy-workflow-qualified-handover-20261122'
    files=json.loads((folder/'FILES.json').read_bytes())
    actual={p.relative_to(folder).as_posix() for p in folder.rglob('*') if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts}
    assert set(files)==actual,'Changed handover packet population'
    for name,identity in files.items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts
        assert pin((folder/path).read_bytes())==identity,name
    baseline=json.loads((folder/'CATALOG-BASELINE.json').read_bytes())
    for name,identity in baseline['files'].items():assert pin((root/name).read_bytes())==identity,name
    draft=json.loads((folder/'DRAFT-SYNTAX.json').read_bytes())
    assert draft['status']=='PARSED_ONLY_NOT_ADMITTED_OR_TRANSACTION_TESTED'
    for row in draft['files']:assert pin((root/row['path']).read_bytes())=={k:row[k] for k in ('bytes','sha256')}
    checks=json.loads((folder/'CHECKS.json').read_bytes())
    assert len(checks['cases'])==5 and all(r['exit_code']==r['expected']==0 for r in checks['cases'])
    assert checks['total_registered_worktrees']==19 and checks['other18_worktrees_unchanged']
    assert (checks['scoped_families'],checks['native'],checks['upstream'],checks['reviewed_uses'],checks['pending_uses'])==(151,27,124,1679,1043)
    assert checks['whole_goal_complete'] is False
    controls=json.loads((folder/'READER-CONTROLS.json').read_bytes())
    assert controls['checks']==len(controls['cases'])==10
    assert sum(c['expected']==0 for c in controls['cases'])==2
    assert sum(c['expected']==1 for c in controls['cases'])==8
    assert all(c['exit_code']==c['expected'] for c in controls['cases'])
    spec=importlib.util.spec_from_file_location('saved_squidpy',root/'tools/literature/verify_squidpy_workflow_checkpoint.py')
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    qualified=module.verify(root)
    return dict(status='PASS_SQUIDPY_QUALIFIED_HANDOVER_WITH_CATALOG_STILL_PENDING',packet_files=len(files),
        qualification=qualified,reader_controls=10,scoped_families=151,reviewed_uses=1679,pending_uses=1043,
        catalog_admission_complete=False,whole_goal_complete=False,analysis_or_network_executed=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[3]);args=parser.parse_args()
    try:print(json.dumps(verify(args.root),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved handover check failed: '+str(error)) from error
