# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only integrity and scope checks for the original DESeq2 postfit component, without rerunning analysis."""
import hashlib,json,stat,sys,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin

def main():
 data=ROOT/'docs/literature/nfcore-deseq126-development';proof=json.loads((data/'VALIDATION.json').read_bytes())
 assert proof['status']=='PASS_ORIGINAL_DESEQ126_POSTFIT_COMPONENT_ONLY'
 assert proof['claims']==dict(new_tool_credit=0,total_new_integrations=99,native=19,upstream_adapters=80,including_three_existing=102,missing=244,nfcore_status='missing',full_nfcore_adapter=False,fitted_model_admitted=False,whole_pipeline_admitted=False,biological_accuracy_certified=False,whole_paper_reproduction=False,whole_goal_complete=False)
 expected=set(proof['artifacts'])|{'controls.zip','VALIDATION.json'};seen=set()
 for line in (data/'SHA256SUMS').read_text().splitlines():
  digest,name=line.split('  ',1);assert name in expected and name not in seen and pin(data/name)['sha256']==digest;seen.add(name)
 assert seen==expected and pin(data/'controls.zip')==proof['archive']
 for name,v in proof['artifacts'].items():assert pin(data/name)==v
 for name,v in proof['source_pins'].items():assert pin(ROOT/name)==v,('Current postfit source/reference differs',name)
 for name,v in proof['catalog_unchanged'].items():assert pin(ROOT/name)==v
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
 assert pin(ROOT/'docs/literature/source-numerics-development/VALIDATION.json')==proof['predecessor_proof']
 native=proof['native'];runtime=proof['runtime'];assert len(native['positive_controls'])==18 and len(native['negative_controls'])==19 and len(runtime['checks'])==9
 assert all(r['passed'] for r in native['positive_controls']+native['negative_controls']+runtime['checks'])
 assert not runtime['host_security_configuration_changed'] and not runtime['namespace_prohibition_branch_exercised'] and not runtime['original_analysis_parameters_reduced']
 with zipfile.ZipFile(data/'controls.zip') as z:
  names=z.namelist();assert len(names)==len(set(names)) and set(names)==set(proof['archive_files'])
  for item in z.infolist():
   name=item.filename;path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name and str(path)==name and not item.is_dir() and not stat.S_ISLNK(item.external_attr>>16)
   raw=z.read(item);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==proof['archive_files'][name]
  for name,v in proof['source_pins'].items():assert proof['archive_files']['sources/'+name]==v
  assert json.loads(z.read('evidence/deseq126-controls-2/COMPLETED.json'))==native and json.loads(z.read('evidence/deseq126-runtime-controls-1/COMPLETED.json'))==runtime
  assert b'Unassessable original replacement flags' in z.read('evidence/deseq126-synthetic-original-1/outlier_replacement-postfit-worker.stderr')
  assert b'1e+08' in z.read('evidence/deseq126-native-controls-3/outlier_replacement.stderr')
  for compiler in ['21','25']:
   build=proof['builds'][compiler];assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
   with zipfile.ZipFile(data/('nfcore-deseq126-javac'+compiler+'.jar')) as jar:
    assert b'Main-Class: org.jlinalg.cli.NfcoreDeseq126AuditCli' in jar.read('META-INF/MANIFEST.MF')
    for origin,v in build['predecessors'].items():assert pin(ROOT/origin)==v
    predecessor=ROOT/f'docs/literature/nfcore-consensus-development/nfcore-consensus-javac{compiler}.jar'
    with zipfile.ZipFile(predecessor) as old:assert all(old.read(n)==jar.read(n) for n in old.namelist() if n!='META-INF/MANIFEST.MF')
    for name,v in build['inherited_added_class_pins'].items():assert hashlib.sha256(jar.read(name)).hexdigest()==v['sha256']
    for name in ['GPL-3','LGPL-3','Artistic-2.0','NOTICE.txt']:assert jar.read('META-INF/source-numerics/'+name)
    for name in ['NfcoreDeseq126Audit','NfcoreDeseq126Runtime','NfcoreDeseq126AuditCli']:
     path='src/main/java/org/jlinalg/cli/'+name+'.java';assert jar.read('META-INF/nfcore-deseq126/sources/'+path)==(ROOT/path).read_bytes()
    for name in ['inspect.R','worker-lock.json','runtime-inventory.json','NOTICE.txt']:assert jar.read('org/jlinalg/omics/nfcore-deseq126/'+name)==(ROOT/'src/main/resources/org/jlinalg/omics/nfcore-deseq126'/name).read_bytes()
 print('PASS original postfit component:3 complete populations;18 packaged native replays;19 refusals;9 runtime controls;retained warnings/failures;0 tool credit.',flush=True);return 0

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source, complete-population or scope checks')
 try:raise SystemExit(main())
 except Exception as error:
  print('FAIL2 NO POSTFIT CHECKPOINT ADMISSION: '+repr(error)+'\nRequired action: retain original data and failures, inspect the named discrepancy and validate a new checkpoint. Do not rewrite old hashes or drop hypotheses to hide a failure.',file=sys.stderr);raise SystemExit(2)
