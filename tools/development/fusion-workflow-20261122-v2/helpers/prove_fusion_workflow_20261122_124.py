from pathlib import Path
import csv,hashlib,json,xml.etree.ElementTree as ET,zipfile
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-independent-checks-1';B.mkdir();P=F/'workflow-packaged-4';N=F/'workflow-normal-build-6';jar=N/'project-build/cli/omicsmatic-0.4.0.jar';old=F/'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'
def payloads(p):
 with zipfile.ZipFile(p) as archive:
  names=[n for n in archive.namelist() if not n.endswith('/')];assert len(names)==len(set(names));return {n:hashlib.sha256(archive.read(n)).hexdigest() for n in names}
prior=payloads(old);current=payloads(jar);changed=sorted(n for n,v in prior.items() if current.get(n)!=v);assert changed==['org/jlinalg/cli/JLinAlgCli.class'],changed
maps=dict(previous=prior,current=current);(B/'PAYLOADS.json').write_text(json.dumps(maps,sort_keys=True,indent=2)+'\n');tests=[ET.parse(p).getroot() for p in (N/'project-build/test-results/test').glob('TEST-*.xml')];assert sum(int(t.attrib['tests']) for t in tests)==75 and all(int(t.attrib[k])==0 for t in tests for k in ['failures','errors','skipped'])
rows=[]
for case,reference in [('assoc-w21','complete'),('missing-w21','missing'),('top1-w21','forced-top1'),('coloc-w21','coloc'),('beta-se-w21','beta-se')]:
 actual=list(csv.DictReader((P/case/'worker/out/result').open(encoding='utf-8'),delimiter='\t'));expected=list(csv.DictReader((F/'original-association-1'/(reference+'.out.tsv')).open(encoding='utf-8'),delimiter='\t'));assert len(actual)==len(expected)==3
 for a,b in zip(actual,expected):
  assert a.keys()==b.keys();assert a['FILE'].replace('\\','/').rsplit('/',1)[-1].strip()==b['FILE'].replace('\\','/').rsplit('/',1)[-1].strip()
  assert {k:v for k,v in a.items() if k!='FILE'}=={k:v for k,v in b.items() if k!='FILE'},(case,a,b)
 rows.append(dict(case=case,reference=reference,rows=3,all_scientific_fields_identical=True,only_declared_FILE_prefix_transport_excluded=True))
exports=[]
for actual,reference,suffix in [('post-correlation','correlation','.CORR.dat'),('post-omnibus','omnibus','.CORR.dat'),('post-omnibus','omnibus','.omnibus.pv')]:
 a=P/actual/'worker/out'/('result'+suffix);b=F/'post-panel-reference-2'/(reference+suffix);assert a.read_bytes()==b.read_bytes(),(actual,reference,suffix);exports.append(dict(case=actual,original_file=reference+suffix,bytes=a.stat().st_size,sha256=hashlib.sha256(a.read_bytes()).hexdigest(),byte_identical=True))
source=F/'upstream-intake-1/source';resource=T/'src/main/resources/org/jlinalg/omics/fusion-9346c122/original'
for name in ['FUSION.assoc_test.R','FUSION.compute_weights.R','FUSION.post_process.R','utils/plink_utils.R','LICENSE']:assert (source/name).read_bytes()==(resource/name).read_bytes()
new=sorted(set(current)-set(prior));result=dict(status='PASS_FULL_PAYLOAD_AND_UNCHANGED_ORIGINAL_EXPORT_COMPARISONS',normal_tests=75,previous_payloads=len(prior),previous_payloads_identical=len(prior)-1,only_changed_previous_payloads=changed,new_payloads=new,association_comparisons=rows,multi_panel_original_export_comparisons=exports,original_scripts_and_license_exact=True,model_or_p_value_repair=False,biological_reproduction=False,new_family_credit=0)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(dict(status=result['status'],normal_tests=75,previous_payloads=len(prior),preserved=len(prior)-1,new_payloads=len(new),association_rows=15,complete_multi_panel_exports=3)),flush=True)