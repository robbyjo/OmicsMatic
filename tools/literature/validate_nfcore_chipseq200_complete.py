# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete immutable v2 source/mapping/image/FASTQ/BAM components on both builds/three JVMs."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--attempt',type=Path,required=True);p.add_argument('--image-set',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_COMPLETE_COMPONENT_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  attempt=a.attempt.resolve();images=a.image_set.resolve();before={n:pin(p) for n,p in [('manifest',attempt/'manifest.json'),('completed',attempt/'COMPLETED.json'),('images',images)]};jars={c:Path(getattr(a,'jar'+c)).resolve() for c in ['21','25']};artifacts={c:pin(p) for c,p in jars.items()};checks=[];reports=[]
  for compiler,jar in jars.items():
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;target=out/label
    if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-jar',linux(jar),'--engine-result',linux(attempt),'--image-set',linux(images),'--out',linux(target)]
    else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-jar',str(jar),'--engine-result',str(attempt),'--image-set',str(images),'--out',str(target)]
    with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
    save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,(out/(label+'.stderr')).read_text(encoding='utf8')[-2000:]);report=json.loads((target/'audit.json').read_bytes());assert report['source_files_checked']==203 and report['task_image_binding']['observed_successful_or_cached_tasks']==215 and len(report['complete_FASTQs'])==6 and len(report['complete_BAMs_and_FRiP'])==6 and not report['scientific_result_admitted'];reports.append(report);checks.append(dict(compiler=compiler,runtime=runtime,passed=True,audit=pin(target/'audit.json')))
  assert all(r==reports[0] for r in reports);assert {n:pin(p) for n,p in [('manifest',attempt/'manifest.json'),('completed',attempt/'COMPLETED.json'),('images',images)]}==before and all(pin(jars[c])==v for c,v in artifacts.items());state.update(status='PASS_COMPLETE_CHIPSEQ200_NATIVE_COMPONENT_CONTROLS_ONLY',checks=checks,artifacts=artifacts,complete_reports_identical=True,original_engine_and_image_declaration_unchanged=before,complete_source_tasks=215,complete_FASTQ_records=sum(x['read1_records']+x['read2_records'] for x in reports[0]['complete_FASTQs']),complete_BAM_records=sum(x['native_counts']['alignment_records'] for x in reports[0]['complete_BAMs_and_FRiP']),full_nfcore_profile_or_biological_admission=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete source/BAM/FASTQ scope checks')
 raise SystemExit(main())
