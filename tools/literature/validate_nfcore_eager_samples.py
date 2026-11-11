# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare all original eager row cases using both compilers on Windows21/25 and Linux21."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);a=p.parse_args();out=a.out.resolve();reference=a.reference.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_NATIVE_EAGER_SAMPLE_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  assert reference.is_relative_to(ROOT);proof=json.loads((reference/'COMPLETED.json').read_bytes());assert proof['status']=='PASS_TWO_ORIGINAL_EAGER_ROW_CLOSURES_ONLY';guards={str(p):pin(p) for p in reference.rglob('*') if p.is_file()};helper=ROOT/'tools/literature/java/NfcoreEagerSamplesControl.java';guards[str(helper)]=pin(helper);guards[str(Path(__file__).resolve())]=pin(Path(__file__).resolve());parent=ROOT/'docs/literature/nfcore-chipseq200-development/nfcore-chipseq200-javac21.jar';guards[str(parent)]=pin(parent);checks=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();guards[str(jar)]=pin(jar);classes=out/('helper'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar)+';'+str(parent),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime
    if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-cp',linux(classes)+':'+linux(jar)+':'+linux(parent),'org.jlinalg.cli.NfcoreEagerSamplesControl',linux(reference)]
    else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx256m','-cp',str(classes)+';'+str(jar)+';'+str(parent),'org.jlinalg.cli.NfcoreEagerSamplesControl',str(reference)]
    r=subprocess.run(cmd,capture_output=True,timeout=90);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,r.stderr);assert b'accepted=26 refused=72' in r.stdout;checks.append(dict(compiler=compiler,runtime=runtime,source_version_cases=98,passed=True))
  assert all(pin(Path(n))==v for n,v in guards.items());state.update(status='PASS_COMPLETE_NATIVE_EAGER_ROW_CASE_CONTROLS_ONLY',checks=checks,total_case_comparisons=588,original_cases_per_version=49,source_versions=2,originals_unchanged=guards,limits='Already parsed source row closures only; no TSV parser, workflow graph, runtime or scientific admission.')
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete population/source checks')
 raise SystemExit(main())
