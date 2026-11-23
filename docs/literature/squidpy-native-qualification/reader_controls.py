from pathlib import Path
import copy,hashlib,importlib.util,json,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';Q=T/'docs/literature/squidpy-native-qualification'
B=T/'build/squidpy-1/native-reader-controls-1';B.mkdir();scratch=B/'scratch';scratch.mkdir()
spec=importlib.util.spec_from_file_location('squidpy_saved_reader',T/'tools/literature/verify_squidpy_native_checkpoint.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
status=json.loads((Q/'STATUS.json').read_bytes());records=[]
def setup(name):
    root=scratch/name;root.mkdir()
    for relative in status['source_code']:
        path=root/relative;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/relative,path)
    target=root/'docs/literature/squidpy-native-qualification';shutil.copytree(Q,target)
    return root
def run(name,change,refuse,jar=None):
    root=setup(name);change(root)
    try:result=module.verify(root,jar);accepted=True;detail=result['status']
    except (AssertionError,KeyError,ValueError,FileNotFoundError,zipfile.BadZipFile) as error:accepted=False;detail=type(error).__name__+': '+str(error)
    assert accepted!=refuse,(name,detail)
    records.append(dict(case=name,expected_refused=refuse,refused=not accepted,detail=detail))
run('valid-copy',lambda root:None,False)
run('source-changed',lambda root:(root/'src/main/java/org/jlinalg/squidpy/SquidpyAutocorrelation.java').open('ab').write(b'\n// altered control\n'),True)
run('evidence-changed',lambda root:(root/'docs/literature/squidpy-native-qualification/evidence.zip').open('ab').write(b'altered control'),True)
def reseal_false_test_count(root):
    q=root/'docs/literature/squidpy-native-qualification';index=json.loads((q/'EVIDENCE.json').read_bytes())
    with zipfile.ZipFile(q/'evidence.zip') as z:
        raw={name:z.read('objects/'+identity['sha256']) for name,identity in index['files'].items()}
    name='packaged/windows21.stdout';assert b'"passed":728' in raw[name]
    raw[name]=raw[name].replace(b'"passed":728',b'"passed":727')
    index['files']={name:module.pin(value) for name,value in raw.items()}
    with zipfile.ZipFile(q/'evidence.zip','w',compression=zipfile.ZIP_DEFLATED) as z:
        for digest,value in {module.pin(value)['sha256']:value for value in raw.values()}.items():z.writestr('objects/'+digest,value)
    (q/'EVIDENCE.json').write_text(json.dumps(index,indent=2)+'\n')
    s=json.loads((q/'STATUS.json').read_bytes());s['evidence_archive']=module.pin((q/'evidence.zip').read_bytes());s['evidence_index']=module.pin((q/'EVIDENCE.json').read_bytes())
    (q/'STATUS.json').write_text(json.dumps(s,indent=2)+'\n')
run('resealed-false-test-count',reseal_false_test_count,True)
run('wrong-producer',lambda root:None,True,T/'src/test/resources/org/jlinalg/squidpy/autocorrelation-original.zip')
members={p.relative_to(scratch).as_posix():module.pin(p.read_bytes()) for p in sorted(scratch.rglob('*')) if p.is_file()}
with zipfile.ZipFile(B/'retained-control-copies.zip','x',compression=zipfile.ZIP_DEFLATED) as z:
    for name in members:z.write(scratch/name,name)
with zipfile.ZipFile(B/'retained-control-copies.zip') as z:
    assert set(z.namelist())==set(members)
    for name,identity in members.items():assert module.pin(z.read(name))==identity
result=dict(status='SAVED_READER_CONTROLS_PASSED',cases=records,scratch_members=members,
    replacement_archive=dict(path=str(B/'retained-control-copies.zip'),**module.pin((B/'retained-control-copies.zip').read_bytes())),
    scientific_code_or_original_results_reexecuted=False)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
public={k:v for k,v in result.items() if k!='scratch_members'}
(Q/'READER-CONTROLS.json').write_text(json.dumps(public,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(status=result['status'],cases=len(records),refused=sum(x['refused'] for x in records),scratch_files=len(members))),flush=True)
