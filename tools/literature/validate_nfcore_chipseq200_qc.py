# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay every saved-QC cell plus adverse/sign/degenerate controls on both compilers and three JVMs."""
import argparse,json,subprocess,traceback,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--attempt',type=Path,required=True);p.add_argument('--facts',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_COMPLETE_QC_NATIVE_CONTROLS',new_tool_credit=0)
 try:
  facts=a.facts.resolve();attempt=a.attempt.resolve();original={x.relative_to(facts).as_posix():pin(x) for x in facts.rglob('*') if x.is_file()};guards={n:pin(attempt/n) for n in ['manifest.json','COMPLETED.json']};helper=ROOT/'tools/literature/java/NfcoreChipseq200QcControl.java';checks=[];reports={mode:[] for mode in ['complete','controls']}
  for compiler in ['21','25']:
   jar=BASE/f'chipseq200-qc-build{compiler}-1/nfcore-chipseq200-qc.jar';artifact=pin(jar);classes=out/('classes-'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25
   cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac-'+compiler+'.stderr')).write_bytes(r.stderr);save(out/('javac-'+compiler+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    for mode in ['complete','controls']:
     label=compiler+'-'+runtime+'-'+mode;target=out/(label+'.json')
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-XX:ActiveProcessorCount=4','-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.NfcoreChipseq200QcControl','--'+mode,*([linux(attempt)] if mode=='complete' else []),linux(facts),linux(target)]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-XX:ActiveProcessorCount=4','-cp',str(jar)+';'+str(classes),'org.jlinalg.cli.NfcoreChipseq200QcControl','--'+mode,*([str(attempt)] if mode=='complete' else []),str(facts),str(target)]
     with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=900)
     save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,(out/(label+'.stderr')).read_text(encoding='utf8')[-2500:]);report=json.loads(target.read_bytes());assert report['status'].startswith('PASS_') and report['new_tool_credit']==0
     if mode=='controls':assert len(report['checks'])==20 and sum(x['status']=='PASS' for x in report['checks'])==4 and report['original_genes']==1472 and report['original_samples']==4
     else:assert len(report['antibodies'])==1 and report['antibodies'][0]['native_complete_QC']['normalized_cells_checked']==5888 and report['antibodies'][0]['native_complete_QC']['complete_distance_cells_checked']==16
     reports[mode].append(report);checks.append(dict(mode=mode,compiler=compiler,runtime=runtime,report=pin(target)));print('PASS',label,flush=True)
   assert pin(jar)==artifact
  assert all(all(x==values[0] for x in values) for values in reports.values());assert {x.relative_to(facts).as_posix():pin(x) for x in facts.rglob('*') if x.is_file()}==original and {n:pin(attempt/n) for n in guards}==guards
  state.update(status='PASS_COMPLETE_CHIPSEQ200_SAVED_QC_NATIVE_CONTROLS_ONLY',checks=checks,complete_native_replays=6,small_checks=120,original_fact_files=original,complete_reports_identical=True,full_nfcore_profile_or_biological_admission=False,source_pins={helper.relative_to(ROOT).as_posix():pin(helper)})
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original numerical population checks')
 raise SystemExit(main())
