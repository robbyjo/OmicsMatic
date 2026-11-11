# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay original tiny v2 BAMs and literal container-prefix controls on both compilers/three JVMs."""
import argparse,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_CHIPSEQ200_SMALL_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  reference=ROOT/'src/test/resources/org/jlinalg/workflow/nfcore-chipseq200/bam-controls.zip';source=ROOT/'src/test/resources/org/jlinalg/workflow/nfcore-chipseq200/BAM_SOURCE.json';metadata=json.loads(source.read_bytes());fixtures=out/'reference';fixtures.mkdir();before=pin(reference)
  with zipfile.ZipFile(reference) as z:
   assert len(z.namelist())==len(set(z.namelist()))
   for info in z.infolist():
    file=(fixtures/info.filename).resolve();assert file.is_relative_to(fixtures) and not info.is_dir() and info.file_size<1048576;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(z.read(info));assert pin(file)==metadata['reference_files'][info.filename]
  original=json.loads((fixtures/'COMPLETED.json').read_bytes());assert len(original['cases'])==10 and original['original_bedtools_overlap_count_sum']==3 and original['original_FRiP']==0.5
  base='singularity exec -B /tmp/work --userns --no-home --pid ';suffix=' /bin/bash -c true';containers=[('plain',base+'/tmp/image.sif'+suffix,'/tmp/image.sif',0),('escaped-space',base+r'/tmp/a\ b.sif'+suffix,'/tmp/a b.sif',0),('single-quoted',base+"'/tmp/a b.sif'"+suffix,'/tmp/a b.sif',0),('double-quoted',base+'"/tmp/a b.sif"'+suffix,'/tmp/a b.sif',0),('literal-dollar',base+"'/tmp/a$literal.sif'"+suffix,'/tmp/a$literal.sif',0),('escaped-dollar',base+r'/tmp/a\$literal.sif'+suffix,'/tmp/a$literal.sif',0),('embedded-single-quote',base+"'/tmp/a'\\''b.sif'"+suffix,"/tmp/a'b.sif",0),('missing-pid',base.replace('--pid ','')+'/tmp/image.sif'+suffix,'',2),('repeated-flag',base+'--userns /tmp/image.sif'+suffix,'',2),('unknown-flag',base+'--fakeroot /tmp/image.sif'+suffix,'',2),('dynamic-unquoted',base+'/tmp/$image.sif'+suffix,'',2),('dynamic-quoted',base+'"/tmp/$image.sif"'+suffix,'',2),('subshell',base+'/tmp/$(false).sif'+suffix,'',2),('shell-separator',base+'/tmp/image.sif; /bin/bash -c true','',2),('multiple-launches',base+'/tmp/image.sif'+suffix+'\n'+base+'/tmp/image.sif'+suffix,'',2),('continued-line',base+'/tmp/a\\\nb.sif'+suffix,'',2),('relative-image',base+'image.sif'+suffix,'',2)]
  jars={c:Path(getattr(a,'jar'+c)).resolve() for c in ['21','25']};artifacts={c:pin(p) for c,p in jars.items()};helper=ROOT/'tools/literature/java/NfcoreChipseq200SmallControl.java';helper_pin=pin(helper);checks=[]
  for compiler,jar in jars.items():
   java=JAVA if compiler=='21' else JAVA25;classes=out/('helper'+compiler);classes.mkdir();cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    cases=[(x['case'],['bam',linux(fixtures/x['case']/'original.bam') if runtime=='linux21' else str(fixtures/x['case']/'original.bam')],x['expected_native_exit'],'original-BAM') for x in original['cases']]+[(n,['container',s,w],e,'literal-prefix') for n,s,w,e in containers]
    for name,args,expected,kind in cases:
     label=compiler+'-'+runtime+'-'+name
     if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx256m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreChipseq200SmallControl',*args]
     else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx256m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.NfcoreChipseq200SmallControl',*args]
     r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=90);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(label,expected,r.returncode,r.stderr);checks.append(dict(case=name,kind=kind,compiler=compiler,runtime=runtime,expected_exit=expected,passed=True))
  assert pin(reference)==before and pin(helper)==helper_pin and all(pin(jars[c])==v for c,v in artifacts.items());state.update(status='PASS_CHIPSEQ200_COMPLETE_SMALL_CONTROLS_ONLY',checks=checks,artifacts=artifacts,reference=before,helper=helper_pin,original_BAM_populations=10,literal_container_cases=17,all_eight_original_records_checked=True,original_input_bytes_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original BAM/prefix expectations')
 raise SystemExit(main())
