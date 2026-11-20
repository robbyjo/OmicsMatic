"""Read-only saved checkpoint integrity check; does not rerun scientific models."""
from pathlib import Path
import hashlib,json,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER=ROOT/'docs/literature/gsea-engine-development'
STATUS='PASS_PACKAGED_NATIVE_GSEA_ENGINE_THREE_JVMS'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def verify():
    q=json.loads((FOLDER/'QUALIFICATION.json').read_bytes())
    assert q['schema_version']==1 and q['status']==STATUS
    assert q['project_RUN_VERIFY_REPLAY_qualified'] is False and q['new_catalog_credit']==0
    assert q['exact_fields_per_JVM']==668739 and q['strict_refusals_per_JVM']==201
    for name,wanted in q['sources'].items():
        path=ROOT/name;assert not Path(name).is_absolute() and '..' not in Path(name).parts
        assert pin(path.read_bytes())==wanted,'Changed checkpoint source: '+name
    for name,record in q['archives'].items():
        path=ROOT/name;assert not Path(name).is_absolute() and '..' not in Path(name).parts
        assert pin(path.read_bytes())==record['archive'],'Changed checkpoint archive: '+name
        with zipfile.ZipFile(path) as z:
            names=z.namelist();assert len(names)==len(set(names)) and set(names)==set(record['files'])
            assert z.testzip() is None
            for n,wanted in record['files'].items():assert pin(z.read(n))==wanted,name+'/'+n
    with zipfile.ZipFile(FOLDER/'checks.zip') as z:
        complete=json.loads(z.read('packaged/COMPLETED.json'))
        assert complete['status']==STATUS and complete['exact_fields_per_JVM']==668739
        assert complete['strict_refusals_per_JVM']==201 and complete['producer']==q['producer']
        assert complete['unchanged_predecessor_members']==23490
        assert set(complete['results'])=={'windows-java21','windows-java25','linux-java21'}
        for result in complete['results'].values():
            assert result==dict(exit_code=0,complete_null_exact_fields=468270,sweep_exact_fields=200469,
                sweep_admitted=423,sweep_refused=201,upstream_runtime_on_classpath=False)
    return dict(status='PASS_SAVED_NATIVE_GSEA_ENGINE_CHECKPOINT_INTEGRITY',hosts=3,
        exact_fields_per_JVM=668739,strict_refusals_per_JVM=201,project_file_workflow_qualified=False,
        new_catalog_credit=0,scientific_models_rerun=False,producer=q['producer'])
if __name__=='__main__':
    try:print(json.dumps(verify(),indent=2))
    except Exception as error:
        print('FAIL2: checkpoint integrity check failed: '+str(error)+'. Restore the exact committed sources and evidence; do not refresh hashes to admit changed code.',file=sys.stderr)
        raise SystemExit(2)
