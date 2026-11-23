from pathlib import Path
import json,zipfile
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'original-reference-windows-1';out=D/'outputs';fixture=T/'src/test/resources/org/jlinalg/mrpresso/original-public-api-reference.zip'
assert json.loads((D/'PROCESS-RESULT.json').read_bytes())['exit_code']==0
files=[*sorted(out.glob('*/original.json')),out/'RUNTIME.txt',out/'SESSION.txt',out/'ORIGINAL-FUNCTION-BODY.txt',D/'generator.R']
assert len([p for p in files if p.name=='original.json'])==25
with zipfile.ZipFile(fixture,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in files:archive.write(path,path.relative_to(out).as_posix() if path.is_relative_to(out) else path.name)
save(B/'ORIGINAL-FIXTURE.json',dict(fixture=pin(fixture),original_calls=25,original_returns=21,original_errors=4,
    source_rank_deficient_return_separately_unreportable=1,core_test_finite_returns=20,original_source_commit='3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87',
    source_package_patched=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(status='FROZEN_ORIGINAL_PUBLIC_API_REFERENCES',fixture=pin(fixture))),flush=True)
