# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete immutable ChIP-seq1.2.1 objects on both compilers and host JVMs."""
import argparse,json,shutil,subprocess,sys,time,traceback,hashlib
sys.stdout.reconfigure(encoding="utf8")
from pathlib import Path
sys.path.insert(0,str(Path('tools/literature').resolve()))
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,DISTRO,JAVA,JAVA25,linux,wsl
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--out',type=Path,required=True)
parser.add_argument('--complete-attempt',type=Path,required=True,help='Already complete standalone attempt, accessible to Windows and WSL; no producer files consulted')
parser.add_argument('--artifact21',type=Path,required=True)
parser.add_argument('--artifact25',type=Path,required=True)
a=parser.parse_args()
assert __debug__, 'Never disable source/component or complete-population assertions'
out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state={'status':'FAIL2_NATIVE_COMPONENT_CONTROLS','new_catalog_credit':0}
try:
 jars={v:Path(getattr(a,'artifact'+v)).resolve() for v in ['21','25']}
 artifact_pins={v:pin(jar) for v,jar in jars.items()}
 sources=[*sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Nfcore*Scientific*.java')),*(ROOT/'src/main/java/org/jlinalg/cli'/n for n in ['NfcoreChipseq121BamAudit.java','NfcoreChipseq121AuditCli.java']),*(ROOT/'src/main/java/org/jlinalg/workflow'/n for n in ['NfcoreChipseq121Frip.java','NfcoreChipseq121Mappings.java']),Path(__file__)]
 source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
 assert all(j.is_file() for j in jars.values())
 copy=a.complete_attempt.resolve();assert copy.is_relative_to(BASE) and copy.is_dir()
 def run(label,command,expected):
  start=time.time()
  with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
   r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=600)
  save(out/(label+'.process.json'),{'command':list(map(str,command)),'expected_exit':expected,'exit_code':r.returncode,'seconds':time.time()-start})
  assert r.returncode==expected,(label,r.returncode,(out/(label+'.stderr')).read_text(errors='replace')[-2500:])
  print('PASS',label,flush=True)
 cases=[]
 for label,compiler,vm in [('windows21','21',JAVA/'java.exe'),('windows25','25',JAVA25/'java.exe')]:
  target=out/label
  cmd=[str(vm),'-Xmx1024m','-jar',str(jars[compiler]),'--engine-result',str(copy),'--peak-type','broadPeak','--read-mode','paired','--out',str(target)]
  run(label,cmd,0)
  d=json.loads((target/'audit.json').read_bytes());assert d['nfcore_tool_credit']==0 and not d['scientific_result_admitted']
  assert len(d['complete_fastq_streams'])==6 and len(d['BAM_and_FRiP'])==6 and all(r['read1_records']==116195 and r['read2_records']==116195 for r in d['complete_fastq_streams'])
  cases.append({'platform':label,'compiler':compiler,'artifact':pin(jars[compiler]),'audit':pin(target/'audit.json')})
  # Declared capacity is a refusal, never silent truncation.
  for mode,extra in [('named-capacity',['--named-view-bytes','1']),('BAM-capacity',['--max-bam-records','1'])]:
   cmd2=cmd[:-1]+[str(out/(label+'-'+mode))]+extra;run(label+'-'+mode,cmd2,2)
   fail=json.loads((out/(label+'-'+mode)/'FAILURE.json').read_bytes());assert fail['status']=='FAIL2_NO_COMPONENT_RESULT'
  before=pin(target/'audit.json');run(label+'-existing-output',cmd,2);assert pin(target/'audit.json')==before
 for compiler in ['21','25']:
  label='linux21-compiler'+compiler;target=out/label
  run(label,['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1024m','-jar',linux(jars[compiler]),'--engine-result',linux(copy),'--peak-type','broadPeak','--read-mode','paired','--out',linux(target)],0)
  cases.append({'platform':'linux21','compiler':compiler,'artifact':pin(jars[compiler]),'audit':pin(target/'audit.json')})
 reports=[json.loads((out/n/'audit.json').read_bytes()) for n in ['windows21','windows25','linux21-compiler21','linux21-compiler25']]
 assert all(d==reports[0] for d in reports)
 assert all(pin(jar)==artifact_pins[v] for v,jar in jars.items()) and all(pin(ROOT/n)==v for n,v in source_pins.items())
 state.update(source_pins=source_pins,artifact_pins=artifact_pins,status='PASS_FOUR_CROSS_PLATFORM_COMPLETE_COMPONENT_AUDITS_ONLY',cases=cases,exact_reports_agree=True,complete_FASTQ_streams=12,records_per_mate=116195,libraries=6,IP_FRiP_ratios=4,negative_checks=6,original_attempt_manifest=pin(copy/'manifest.json'),component_only=True,biological_accuracy_certified=False)
except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True)
if not state['status'].startswith('PASS_'):
 (out/'WARNINGS.md').write_text('# FAIL2 NO COMPONENT RESULT\n\n'+state.get('cause','')+'\n\nRequired action: preserve all evidence, inspect the named cause and rerun corrected controls to a new path. Do not shorten data, infer identifiers or weaken verification.\n',encoding='utf8')
raise SystemExit(0 if state['status'].startswith('PASS_') else 2)
