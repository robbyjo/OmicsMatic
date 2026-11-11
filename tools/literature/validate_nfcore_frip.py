# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate native complete BAM/FRiP auditing against original SAMtools1.10 and BEDTools2.29.2."""
import argparse,json,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,DISTRO,JAVA,JAVA25,linux

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
 jar=ROOT/'docs/literature/nfcore-engine-development/nfcore-engine-javac21.jar';canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
 sources=[ROOT/'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Mappings.java',ROOT/'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Frip.java',ROOT/'src/main/java/org/jlinalg/cli/NfcoreChipseq121BamAudit.java',ROOT/'tools/literature/java/NfcoreChipseqFripControl.java',Path(__file__)]
 pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};state=dict(status='FAIL2_NFCORE_NATIVE_FRIP_CONTROLS',new_catalog_credit=False)
 try:
  for path in sources:
   target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
  classes=out/'classes';classes.mkdir();cp=str(jar)+';'+str(canonical)
  c=subprocess.run([str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',cp,'-d',str(classes),*map(str,sources[:-1])],capture_output=True,timeout=120);(out/'javac.stderr').write_bytes(c.stderr);assert c.returncode==0,c.stderr
  cp=str(classes)+';'+cp;records=[]
  cases={'overlap-and-qcfail':(5,2,0),'fraction-boundary':(1,1,0),'bookended':(0,1,0),'unsplit-cigar-span':(1,1,0),'b-fraction-default':(1,1,0),'undefined-denominator':(1,0,0),'contig-alias-refused':(0,1,2),'wrong-sample':(5,2,2)}
  for mode,(numerator,denominator,native_exit) in cases.items():
   folder=out/mode;folder.mkdir()
   r=subprocess.run([str(JAVA/'java.exe'),'-cp',cp,'org.jlinalg.cli.NfcoreChipseqFripControl','--make',mode,str(folder)],capture_output=True,timeout=90);(folder/'make.stderr').write_bytes(r.stderr);assert r.returncode==0,(mode,r.stderr)
   prefix=['wsl.exe','-d',DISTRO,'--exec','/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer','exec','--userns','--bind',linux(folder)+':/case:ro','/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif']
   def original(label,argv,data=None):
    r=subprocess.run(prefix+argv,input=data,capture_output=True,timeout=90);(folder/(label+'.stdout')).write_bytes(r.stdout);(folder/(label+'.stderr')).write_bytes(r.stderr);save(folder/(label+'.process.json'),dict(command=prefix+argv,exit_code=r.returncode));return r
   intersect=original('bedtools',['intersectBed','-a','/case/input.bam','-b','/case/peaks.bed','-bed','-c','-f','0.20']);assert intersect.returncode==0,(mode,intersect.stderr)
   observed_num=sum(int(line.split(b'\t')[-1]) for line in intersect.stdout.splitlines() if line)
   flagstat=original('samtools',['samtools','flagstat','/case/input.bam']);assert flagstat.returncode==0,(mode,flagstat.stderr)
   selected=[line for line in flagstat.stdout.splitlines() if b'mapped (' in line];assert len(selected)==1,selected
   observed_den=int(selected[0].split()[0]);assert (observed_num,observed_den)==(numerator,denominator),(mode,observed_num,observed_den)
   scalar=original('source-awk-ratio',['awk','-v','a='+str(observed_num),'{print a/$1}'],selected[0]+b'\n')
   if denominator:assert scalar.returncode==0,(mode,scalar.stderr)
   native=[]
   for platform,exe in [('windows21',JAVA/'java.exe'),('windows25',JAVA25/'java.exe')]:
    r=subprocess.run([str(exe),'-cp',cp,'org.jlinalg.cli.NfcoreChipseqFripControl',str(folder)],capture_output=True,timeout=90);(folder/(platform+'.stdout')).write_bytes(r.stdout);(folder/(platform+'.stderr')).write_bytes(r.stderr);assert r.returncode==native_exit,(mode,platform,r.stderr)
    if not native_exit:native.append(json.loads(r.stdout))
   if not native_exit:
    assert native[0]==native[1] and native[0]['bedtools_count_sum_numerator']==numerator and native[0]['qc_passing_mapped_denominator']==denominator
    if denominator:assert native[0]['frip']==numerator/denominator
    else:assert native[0]['frip'] is None and native[0]['assessment']=='UNASSESSABLE_NO_QC_PASSING_MAPPED_DENOMINATOR'
   records.append(dict(case=mode,source_numerator=numerator,source_denominator=denominator,native_exit=native_exit,source_ratio_exit=scalar.returncode,source_ratio_lexeme=scalar.stdout.decode(errors='strict').strip(),passed=True));print(json.dumps(records[-1]),flush=True)
  assert all(pin(ROOT/n)==v for n,v in pins.items())
  state.update(status='PASS_NATIVE_FRIP_AND_BAM_IDENTITY_AGAINST_ORIGINAL_TOOLS',cases=records,source_pins=pins,original_software_modified=False,biological_validation=False,whole_pipeline_admitted=False)
 except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
 assert pin(canonical)==before;state['canonical_executable_unchanged']=before;save(out/'COMPLETED.json',state)
 if not state['status'].startswith('PASS_'):(out/'WARNINGS.md').write_text('# FAIL2 native FRiP validation\n\n'+state.get('cause','')+'\n\nRequired action: preserve original outputs and flags. Inspect the named denominator, overlap, reference or read-group discrepancy. No result admitted; never clamp fractions, repair identifiers or substitute source ratios.\n')
 print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Do not disable original-output, flag, overlap or identity checks')
 raise SystemExit(main())
