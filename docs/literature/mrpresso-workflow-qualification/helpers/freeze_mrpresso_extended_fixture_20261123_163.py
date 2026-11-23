from pathlib import Path
import json,zipfile
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'extended-reference-windows-1';out=D/'outputs'
assert json.loads((D/'PROCESS-RESULT.json').read_bytes())['exit_code']==0
def field(record,key):return record['values'][record['names'].index(key)]
def optional(record,key):return field(record,key) if key in record['names'] else None
records=[]
for path in sorted(out.glob('*/original.json')):
 record=json.loads(path.read_bytes());name=field(record,'case')['values'][0];result=field(record,'result');tests=field(result,'MR-PRESSO results');d=optional(tests,'Distortion Test')
 row=dict(case=name,returned=field(record,'returned')['values'][0],sample_kind=field(record,'rng_kind')['values'][2],
  omitted=field(record,'omitted_arguments')['values'],global_p=field(field(tests,'Global Test'),'Pvalue')['values'],
  distortion_p=field(d,'Pvalue')['values'] if d else None,outlier_ordinals=field(d,'Outliers Indices')['values'] if d else None)
 if name in ('zero-corrected-inference','essentially-perfect-raw'):
  row['main_fields']={x['name']:x['data']['values'] for x in field(result,'Main MR results')['columns']}
 records.append(row)
assert len(records)==20 and all(x['returned'] for x in records)
fixture=T/'src/test/resources/org/jlinalg/mrpresso/extended-public-api-reference.zip'
files=[*sorted(out.glob('*/original.json')),out/'RUNTIME.txt',out/'SESSION.txt',out/'ORIGINAL-FUNCTION-BODY.txt',out/'RESULT.json',D/'generator.R']
with zipfile.ZipFile(fixture,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for path in files:
  name=path.relative_to(out).as_posix() if path.is_relative_to(out) else path.name
  info=zipfile.ZipInfo(name,(2026,11,23,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
  z.writestr(info,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
save(B/'EXTENDED-FIXTURE.json',dict(fixture=pin(fixture),original_calls=20,original_errors=0,source_package_patched=False,
 cases=records,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(fixture=pin(fixture),cases=records),indent=2))
