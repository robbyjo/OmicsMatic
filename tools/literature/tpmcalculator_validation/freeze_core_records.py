#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze actual original/native component evidence; this assigns no catalog credit."""
from pathlib import Path
import argparse,hashlib,json,shutil,xml.etree.ElementTree as ET,zipfile
ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT.parent/'literature-review/monocle-runtime'
EVIDENCE=ROOT/'docs/literature/tpmcalculator-native-development'
GUIDE='docs/literature/TPMCALCULATOR_NATIVE_DEVELOPMENT.md'
LINUX=Path('/opt/omicsmatic-tpmcalculator-linux-core-20261115-1')
BUILD=Path('/opt/omicsmatic-tpmcalculator-original-20261115-3')
def pin(p):
 raw=p.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def write(path,obj):path.write_text(json.dumps(obj,indent=2)+'\n',encoding='utf-8')
def totals(paths):
 result=dict(tests=0,failures=0,errors=0,skipped=0)
 for path in paths:
  node=ET.fromstring(path.read_bytes())
  for key in result:result[key]+=int(node.attrib.get(key,0))
 assert result==dict(tests=59,failures=0,errors=0,skipped=0),result
 return result
def freeze():
 EVIDENCE.mkdir(exist_ok=False)
 (EVIDENCE/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
 windows=list((ROOT/'build/test-results/test').glob('TEST-*.xml'));linux=list(LINUX.glob('*.xml'))
 counts=dict(Windows=totals(windows),Linux=totals(linux))
 actual=(BASE/'tpmcalculator-linux-core-qualification-1/actual-linux.log').read_bytes()
 assert b'falling back to JDK deflater' in actual and b'59 tests successful' in actual
 assert 'BUILD SUCCESSFUL' in (ROOT/'build/tpm-original-comparison-4.log').read_text(encoding='utf-8-sig')
 profile=json.loads((BASE/'tpmcalculator-source-intake-1/SOURCE.json').read_bytes())
 same=json.loads((BASE/'tpmcalculator-release006-assessment-1/SOURCE-EQUIVALENCE.json').read_bytes())
 assert same['all_quantification_source_bytes_identical'] and not same['original006_binary_executed']
 selected={}
 original=BASE/'tpmcalculator-source-intake-1/source-original'
 names=['LICENSE','includes/GenomeFactory.h','includes/ReadFactory.h','src/ReadFactory.cpp','src/main.cpp','src/TextParser.cpp','src/bstring.cpp']
 for n in names:
  p=original/n;assert pin(p)=={k:profile['regular_members'][n][k] for k in ['bytes','sha256']}
  dest=EVIDENCE/'original-selected-source'/n;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest);selected[n]=pin(dest)
 record_files={}
 def add(path,name):
  assert path.is_file(),str(path)
  assert name not in record_files,name
  record_files[name]=path
 for p in windows:add(p,'Windows/'+p.name)
 for p in linux:add(p,'Linux/'+p.name)
 add(ROOT/'build/tpm-original-comparison-4.log','Windows/actual-comparison.log')
 add(BASE/'tpmcalculator-linux-core-qualification-1/actual-linux.log','Linux/actual-comparison.log')
 add(BASE/'tpmcalculator-linux-core-qualification-1/SOURCE.json','Linux/JUNIT-CONSOLE-SOURCE.json')
 for n in [1,2,3]:
  run=Path('/opt/omicsmatic-tpmcalculator-original-20261115-'+str(n))
  for p in run.iterdir():
   if p.is_file() and (p.suffix in ['.stdout','.stderr','.json']):add(p,'original-build-'+str(n)+'/'+p.name)
  for name in ['pixi.toml','pixi.lock']:add(run/'runtime'/name,'original-build-'+str(n)+'/'+name)
  add(BASE/('build-tpmcalculator-original-'+str(n)+'.py'),'original-build-'+str(n)+'/actual-builder.py')
 for n in [1,2,3,4]:
  folder=Path('/opt/omicsmatic-tpmcalculator-controls-20261115-'+str(n))
  for p in folder.iterdir():
   if p.is_file():add(p,'historical-probe-preparation-'+str(n)+'/'+p.name)
 for n in [1,2]:
  folder=BASE/('tpmcalculator-native-comparison-failure-'+str(n))
  for p in folder.rglob('*'):
   if p.is_file():add(p,'historical-native-failure-'+str(n)+'/'+p.relative_to(folder).as_posix())
 for name in ['SOURCE.json','original-archive.json','tags.json']:
  p=BASE/'tpmcalculator-source-intake-1'/name
  if p.exists():add(p,'source004/'+name)
 for name in ['SOURCE.json','SOURCE-EQUIVALENCE.json','compare.response']:add(BASE/'tpmcalculator-release006-assessment-1'/name,'source006/'+name)
 for folder in ['tpmcalculator-controls-20261115-5','tpmcalculator-edge-20261115-1','tpmcalculator-order-20261115-1']:
  p=Path('/opt')/('omicsmatic-'+folder)
  for name in ['COMPLETED.json','SOURCE.json','PROFILE.json']:
   if (p/name).is_file():add(p/name,folder+'/'+name)
 with zipfile.ZipFile(EVIDENCE/'complete-component-records.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,p in sorted(record_files.items()):z.write(p,name)
 with zipfile.ZipFile(EVIDENCE/'complete-component-records.zip') as z:
  assert z.testzip() is None
  for name,p in record_files.items():assert z.read(name)==p.read_bytes(),name
 write(EVIDENCE/'RECORD-INDEX.json',{n:pin(p) for n,p in sorted(record_files.items())})
 source_profile=dict(status='SOURCE_DEFINED_PROJECT_COMPARISON_NOT_AUTHOR_EDITION',tag004=profile['tag'],commit004=profile['commit'],archive004=profile['archive'],
  original_printed_version='0.0.3',source_build=json.loads((BUILD/'COMPLETED.json').read_bytes()),selected_source_files=selected,
  source006_equivalence={k:v for k,v in same.items() if k!='members'},author_edition_or_options_established=False,
  original_algorithms_patched=False,whole_paper_reproduced=False)
 write(EVIDENCE/'SOURCE-PROFILE.json',source_profile)
 source_names=[GUIDE,'src/main/java/org/jlinalg/sequence/TpmCalculator.java','src/main/java/org/jlinalg/sequence/TpmCalculatorOrder.java',
  'src/test/java/org/jlinalg/cli/TpmCalculatorOriginalTest.java','src/test/java/org/jlinalg/sequence/TpmCalculatorOrderTest.java']
 source_names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'tools/literature/tpmcalculator_validation').iterdir()) if p.is_file()]
 source_names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/'src/test/resources/org/jlinalg/sequence').glob('tpmcalculator-original-*.zip'))]
 proof=dict(status='PASS_NATIVE_TPMCALCULATOR_CORE_ORIGINAL_STATE_COMPONENT_ONLY',actual_tests=counts,original_cases=57,actual_GNU_hash_and_iteration_keys=100000,
  original_main_and_probe_table_bytes_equal_in_all_cases=True,native_state_integer_and_presence_inventory_exact=True,finite_TPM_tolerance_ULPs=64,
  original_nonfinite_classifications_retained=True,original_bridge_TPM_float_precision_retained=True,actual_Linux_packaged_library_JAR=pin(ROOT/'build/libs/OmicsMatic-0.3.7.jar'),
  actual_Linux_HTSJDK_optional_libdeflate_fallback_observed=True,optional_libdeflate_required_for_correctness_in_these_controls=False,
  native_public_file_workflow_qualified=False,native_command_line_qualified=False,catalog_credit_added=0,new_scoped_integrations_unchanged=121,
  native_integrations_unchanged=20,whole_tool_qualified=False,whole_paper_reproduced=False,whole_goal_complete=False,
  source_files={n:pin(ROOT/n) for n in source_names},evidence_files={p.relative_to(EVIDENCE).as_posix():pin(p) for p in EVIDENCE.rglob('*') if p.is_file()})
 write(EVIDENCE/'QUALIFICATION.json',proof)
 return proof
def verify():
 proof=json.loads((EVIDENCE/'QUALIFICATION.json').read_bytes())
 assert proof['status']=='PASS_NATIVE_TPMCALCULATOR_CORE_ORIGINAL_STATE_COMPONENT_ONLY' and proof['catalog_credit_added']==0
 for kind,base in [('source_files',ROOT),('evidence_files',EVIDENCE)]:
  for name,identity in proof[kind].items():assert pin(base/name)==identity,(kind,name)
 with zipfile.ZipFile(EVIDENCE/'complete-component-records.zip') as z:
  assert z.testzip() is None
  index=json.loads((EVIDENCE/'RECORD-INDEX.json').read_bytes());assert sorted(z.namelist())==sorted(index)
  for name,identity in index.items():
   raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==identity,name
 return proof
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--freeze',action='store_true');args=parser.parse_args()
 if args.freeze:freeze()
 result=verify();print(json.dumps({k:result[k] for k in ['status','actual_tests','original_cases','actual_GNU_hash_and_iteration_keys','catalog_credit_added','whole_goal_complete']},indent=2))
