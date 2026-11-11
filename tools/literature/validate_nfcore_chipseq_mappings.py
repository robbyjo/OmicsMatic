# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare native literal mapping intake with the original source checker and original Python image."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,DISTRO,JAVA,JAVA25,linux,wsl

HEADER='group,replicate,fastq_1,fastq_2,antibody,control\n'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
 source=ROOT/'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Mappings.java';test=ROOT/'tools/literature/java/NfcoreChipseqMappingsControl.java'
 source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [source,test,Path(__file__)]};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
 state=dict(status='FAIL2_NFCORE_CHIPSEQ_MAPPING_CONTROLS',new_catalog_credit=False)
 try:
  jar=ROOT/'docs/literature/nfcore-engine-development/nfcore-engine-javac21.jar';proof=json.loads((jar.parent/'VALIDATION.json').read_bytes());assert pin(jar)==proof['artifacts'][jar.name]
  classes=out/'classes';classes.mkdir();c=subprocess.run([str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),str(source),str(test)],capture_output=True,timeout=90);(out/'javac.stderr').write_bytes(c.stderr);assert c.returncode==0,c.stderr
  for platform,exe in [('windows21',JAVA/'java.exe'),('windows25',JAVA25/'java.exe')]:
   r=subprocess.run([str(exe),'-cp',str(jar)+';'+str(classes),'org.jlinalg.cli.NfcoreChipseqMappingsControl','--portable'],capture_output=True,timeout=60);(out/(platform+'.stdout')).write_bytes(r.stdout);(out/(platform+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,(platform,r.stderr)
  (out/'linux-portable.stdout').write_bytes(wsl('/usr/bin/java','-cp',linux(jar)+':'+linux(classes),'org.jlinalg.cli.NfcoreChipseqMappingsControl','--portable'))
  def row(group,rep,antibody='',control='',second=True):return ','.join([group,str(rep),'/reads/'+group+str(rep)+' first.fq.gz','/reads/'+group+str(rep)+' second.fq.gz' if second else '',antibody,control])+'\n'
  cases={
   'technical-vs-biological':(HEADER+row('C',1)+row('IP',1,'A','C')+row('IP',1,'A','C')+row('IP',2,'A','C'),0,0),
   'one-biological-two-technical':(HEADER+row('C',1)+row('IP',1,'A','C')+row('IP',1,'A','C'),0,0),
   'self-control':(HEADER+row('IP',1,'A','IP'),0,0),
   'zero-gap-source-check':(HEADER+row('C',0)+row('C',2)+row('IP',0,'A','C')+row('IP',2,'A','C'),0,0),
   'last-antibody-assignment':(HEADER+row('C',1)+row('D',1)+row('IP',1,'A','C')+row('IP',2,'B','D'),0,0),
   'unicode-literals':(HEADER+row('α',1)+row('β',1,'γ','α'),0,0),
   'mixed-mate-metadata':(HEADER+row('C',1,second=False)+row('IP',1,'A','C'),0,0),
   'source-T10-control-bug':(HEADER+row('C',1)+''.join(row('IP',1,'A','C') for _ in range(10)),0,2),
   'source-absent-fallback':(HEADER+row('C',0)+row('C',2)+row('IP',1,'A','C'),0,2),
   'missing-control-group':(HEADER+row('C',1)+row('IP',1,'A','MISSING'),1,None),
   'space-group-rejected':(HEADER+row('C',1)+row('IP SPACE',1,'A','C'),1,None)}
  records=[]
  for name,(design,original_exit,native_exit) in cases.items():
   folder=out/name;folder.mkdir();(folder/'design.csv').write_text(design,encoding='utf8',newline='\n');reads=folder/'design_reads.csv';controls=folder/'design_controls.csv'
   command=['wsl.exe','-d',DISTRO,'--exec','/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer','exec','--userns',
    '--bind','/var/tmp/omicsmatic-nfcore-sources-1:/source:ro','--bind',linux(folder)+':/case',
    '/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif','python','/source/chipseq-1.2.1/bin/check_design.py','/case/design.csv','/case/design_reads.csv','/case/design_controls.csv']
   r=subprocess.run(command,capture_output=True,timeout=90);(folder/'original.stdout').write_bytes(r.stdout);(folder/'original.stderr').write_bytes(r.stderr);save(folder/'original.process.json',dict(command=command,exit_code=r.returncode,expected_exit=original_exit));assert r.returncode==original_exit,(name,r.stderr)
   if native_exit is not None:
    projections=[]
    for platform,exe in [('windows21',JAVA/'java.exe'),('windows25',JAVA25/'java.exe')]:
     r=subprocess.run([str(exe),'-cp',str(jar)+';'+str(classes),'org.jlinalg.cli.NfcoreChipseqMappingsControl',str(reads),str(controls)],capture_output=True,timeout=60);(folder/(platform+'.stdout')).write_bytes(r.stdout);(folder/(platform+'.stderr')).write_bytes(r.stderr);assert r.returncode==native_exit,(name,platform,r.stderr)
     if native_exit==0:projections.append(json.loads(r.stdout))
    if native_exit==0:
     assert projections[0]==projections[1]
     cells=[line.split(',') for line in reads.read_text(encoding='utf8').splitlines()[1:]]
     pairs=[line.split(',') for line in controls.read_text(encoding='utf8').splitlines()[1:]]
     assert [[r['sample_id'],r['fastq_1'],r['fastq_2']] for r in projections[0]['reads']]==cells
     assert [[r['sample_id'],r['control_id'],r['antibody'],str(int(r['replicatesExist'])),str(int(r['multipleGroups']))] for r in projections[0]['controls']]==pairs
   records.append(dict(case=name,original_exit=original_exit,native_exit=native_exit,passed=True));print(json.dumps(records[-1]),flush=True)
  assert all(pin(ROOT/n)==v for n,v in source_pins.items())
  state.update(status='PASS_ORIGINAL_CHIPSEQ121_CHECKED_MAPPING_GRAPH_CONTROLS',cases=records,source_pins=source_pins,
   known_source_invalid_graphs_retained=['source-T10-control-bug','source-absent-fallback'],original_source_modified=False,scientific_analysis=False)
 except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original-source, literal-identity or mapping-graph checks')
 raise SystemExit(main())
