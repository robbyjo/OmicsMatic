"""Verify the complete native CADD1.6 linear decision component; raw-VCF prediction stays unqualified."""
import argparse,array,gzip,hashlib,json,math,sys,zipfile
from pathlib import Path
from verify_cadd_workflows import verify as verify_prior
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/cadd-linear-validation/QUALIFICATION.json'
def read(p):return json.loads(Path(p).read_bytes())
def pin(p):
 h=hashlib.sha256();n=0
 with Path(p).open('rb')as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())
def require(value,message):
 if not value:raise ValueError(message)
def doubles(p):
 values=array.array('d');values.frombytes(p.read_bytes())
 if sys.byteorder!='little':values.byteswap()
 return values
def data(p,compressed=False):
 text=gzip.open(p,'rt',encoding='utf-8').read()if compressed else p.read_text(encoding='utf-8');return[x.split('\t')for x in text.splitlines()if x and not x.startswith('#')]
def science(private):
 matrices=private/'model-controls-2';require(read(matrices/'COMPLETED.json')['all_published_annotation_rows_retained'],'Complete original model inputs missing');numerical=read(private/'numerical-comparison-1/COMPLETED.json');require(numerical['no_model_input_row_or_feature_reduction']and numerical['native_Windows_Linux_outputs_byte_identical'],'Model/matrix reduction or host discrepancy');families={}
 for host in ['Windows','Linux']:
  rows=values=0;families[host]={}
  for assembly,features,annotations in [('GRCh37',1029,18),('GRCh38',1028,12)]:
   source=matrices/assembly;actual=private/('native-'+host+'-1')/assembly/'results';manifest=read(source/'RESULT.json');require(manifest['features']==features and not manifest['feature_and_model_reduction'],'Wrong complete original model');p=read(source/'model-parameters.json');require(len(p['coefficients'])==len(p['scales'])==len(p['means'])==len(p['variances'])==features and p['with_mean']is False and p['with_std']is True and p['copy']is False,'Incomplete/wrong source scaler/model parameters');require(read(matrices/('POPULATION-'+assembly+'.json'))['complete_annotation_rows']==annotations,'Published annotation rows were reduced')
   for name,case in manifest['cases'].items():
    for suffix in ['input-f32.bin','scaled-f32.bin','raw-f64.bin']:
     expected_pin=case[{'input-f32.bin':'input','scaled-f32.bin':'scaled','raw-f64.bin':'raw'}[suffix]];require(pin(source/(name+'.'+suffix))=={k:expected_pin[k]for k in ['bytes','sha256']},'Original complete matrix bytes differ')
    require(pin(actual/(name+'.scaled-f32.bin'))==pin(source/(name+'.scaled-f32.bin')),'Any float32 scaled feature differs');expected=doubles(source/(name+'.raw-f64.bin'));observed=doubles(actual/(name+'.raw-f64.bin'));bounds=doubles(actual/(name+'.envelope-f64.bin'));require(len(expected)==len(observed)==len(bounds)==case['raw']['shape'][0],'Native/source row population differs');require(all(math.isfinite(x)and math.isfinite(y)and math.isfinite(b)and b>=0 and abs(x-y)<=b for x,y,b in zip(expected,observed,bounds)),'Original raw decision outside declared numerical envelope');rows+=len(expected);values+=len(expected)*features;families[host][assembly+'/'+name]={p.name:pin(p)for p in actual.glob(name+'.*.bin')}
   for method in ['published-reconstruction-1','native-reconstruction-1']:
    result=private/method/((host+'-'if method.startswith('native')else'')+'calibrate-'+assembly)/'stdout.log';expected=data(matrices/('whole-original-annotated-'+assembly+'.tsv.gz'),True);require(len(expected)==annotations and data(result)==expected,'Any published original annotation/raw/PHRED field differs')
  require(rows==8252 and values==8487186,'Complete real/dense/all-feature input population differs')
 require(families['Windows']==families['Linux'],'Native Windows/Linux output bytes differ');negative=read(private/'negative-native-1/COMPLETED.json');require(len(negative['cases'])==4 and all(len(x['result']['cases'])==3 and all(not r['complete_prediction_returned']for r in x['result']['cases'])for x in negative['cases']),'Complete original real-model numerical failures differ');return dict(rows_per_host=8252,float32_values_per_host=8487186,published_annotation_rows_per_host=30,full_model_features=[1029,1028],native_real_model_failure_cases=12)
def verify(root,private=None):
 q=read(root/Q);require(q['status']=='PASS_NATIVE_CADD16_LINEAR_DECISION_COMPONENT'and q['linear_model_component_qualified']and not q['raw_variant_prediction_qualified']and q['new_family_credit']==q['new_source_reviews']==q['recovered_observations']==0 and not q['whole_goal_complete'],'CADD linear component qualification scope differs');verify_prior(root)
 for name,value in q['sources'].items():require(pin(root/name)==value,'Native source/test/guide/qualification tool differs: '+name)
 require(q['unit_tests']==dict(tests=10,failures=0,errors=0,skipped=0)and q['rows_per_host']==8252 and q['float32_values_per_host']==8487186 and q['published_annotation_rows_per_host']==30 and q['native_real_model_failure_cases']==12,'Qualified full-model scientific populations differ')
 if private is not None:
  private=Path(private)
  for name,value in q['private_evidence'].items():require(pin(private/name)==value,'Retained complete model/source/numerical/failure evidence differs: '+name)
  for name,value in q['producing_jars'].items():require(pin(private/name)==value,'Producing native JAR differs')
  for name,value in q['producing_driver_classes'].items():require(pin(private/name)==value,'Required scientific driver class differs')
  with zipfile.ZipFile(private/q['final_producer'])as z:
   for name,digest in q['final_producer_members'].items():require(hashlib.sha256(z.read(name)).hexdigest()==digest,'Native producing JAR member differs')
  science(private);classes=read(private/'class-comparison-1/COMPLETED.json');require(classes['changed_old_classes']==[]and classes['other_old_classes_identical'],'An unrelated previous class changed')
 return dict(status=q['status'],unit_tests=10,rows_per_host=8252,float32_values_per_host=8487186,published_annotation_rows_per_host=30,native_real_model_failure_cases=12,private_evidence_checked=private is not None,new_family_credit=0,linear_model_component_qualified=True,raw_variant_prediction_qualified=False,whole_goal_complete=False)
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
 try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
 except Exception as e:raise SystemExit('FAIL2: native CADD model component not qualified; preserve source/data/failure evidence and correct cause: '+str(e))
