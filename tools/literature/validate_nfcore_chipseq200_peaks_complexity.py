# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay complete immutable v2 peak/annotation/complexity outputs on both compilers and three JVMs."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--attempt',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_PEAK_COMPLEXITY_CONTROLS',new_tool_credit=0)
 try:
  attempt=a.attempt.resolve();before={n:pin(attempt/n) for n in ['manifest.json','COMPLETED.json']};checks=[]
  for component,main_class in [('peaks','NfcoreChipseq200PeaksAuditCli'),('complexity','NfcoreChipseq200ComplexityAuditCli')]:
   reports=[]
   for compiler in ['21','25']:
    edition='3' if component=='complexity' and compiler=='21' else '1';jar=BASE/f'chipseq200-{component}-build{compiler}-{edition}/nfcore-chipseq200-{component}.jar';artifact=pin(jar)
    for runtime in ['windows21','windows25','linux21']:
     label=component+'-'+compiler+'-'+runtime;target=out/label
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-XX:ActiveProcessorCount=4','-cp',linux(jar),'org.jlinalg.cli.'+main_class,'--engine-result',linux(attempt),'--out',linux(target)]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-XX:ActiveProcessorCount=4','-cp',str(jar),'org.jlinalg.cli.'+main_class,'--engine-result',str(attempt),'--out',str(target)]
     with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
     save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,(out/(label+'.stderr')).read_text(encoding='utf8')[-2500:]);report=json.loads((target/'audit.json').read_bytes());assert not report['scientific_result_admitted'] and report['nfcore_tool_credit']==0;reports.append(report);checks.append(dict(component=component,compiler=compiler,runtime=runtime,audit=pin(target/'audit.json'),artifact=artifact));print('PASS',label,flush=True)
    assert pin(jar)==artifact
   assert all(x==reports[0] for x in reports),component
   if component=='complexity':
    assert report['literal_picard_provider_version']=='2.27.4-SNAPSHOT' and len(report['libraries'])==6
    assert all(x['original_Preseq']['complete_curve_rows']==10000 for x in report['libraries'])
    state['complete_marked_BAM_records']=sum(x['complete_marked_BAM_records'] for x in report['libraries']);state['complete_Preseq_rows']=60000;state['complete_ROI_bins']=sum(x['complete_ROI_bins_checked'] for x in report['libraries'])
  assert {n:pin(attempt/n) for n in before}==before;state.update(status='PASS_COMPLETE_CHIPSEQ200_PEAK_COMPLEXITY_COMPONENT_CONTROLS_ONLY',checks=checks,all_complete_reports_identical_per_component=True,source_attempt_unchanged=before,full_nfcore_profile_or_biological_admission=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete scientific population checks')
 raise SystemExit(main())
