# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run both native builds on every complete original filter case across three JVMs, then compare all BAM records with original pysam."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux,wsl
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--jar21',required=True,type=Path);p.add_argument('--jar25',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);a=p.parse_args();out=a.out.resolve();reference=a.reference.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_NATIVE_EAGER_FILTER_CONTROLS',new_tool_credit=0,full_eager_adapter_admitted=False)
 try:
  source=json.loads((reference/'COMPLETED.json').read_bytes());assert source['status']=='PASS_36_COMPLETE_ORIGINAL_EAGER_FILTER_POPULATIONS_ONLY';assert pin(reference/'observations.json')==source['observations'];cases=json.loads((reference/'cases.json').read_bytes());observations=json.loads((reference/'observations.json').read_bytes());assert len(cases)==len(observations)==36;fixtures=out/'reference';(fixtures/'inputs').mkdir(parents=True);input_names={};native=[]
  for case,observed in zip(cases,observations):
   assert case['name']==observed['case'];name=case['input']
   if name not in input_names:
    member='inputs/input'+str(len(input_names))+'.bam';dest=fixtures/member;wsl('/usr/bin/python3','-c','from pathlib import Path; import shutil,sys; shutil.copyfile(sys.argv[1],sys.argv[2])',name,linux(dest));assert pin(dest)==source['input_pins'][name];input_names[name]=member
   native.append(dict(name=case['name'],input_member=input_names[name],minimum_length=case['minimum_length'],include_unmapped=case['include_unmapped'],input_records=observed['input']['records'],output_records=observed['output']['records'],output_unmapped=observed['output']['unmapped']))
  save(fixtures/'native-cases.json',native);save(fixtures/'original-observations.json',observations);guards={str(p):pin(p) for p in [reference/'cases.json',reference/'observations.json',reference/'COMPLETED.json',*fixtures.rglob('*.bam'),fixtures/'native-cases.json']};helper=ROOT/'tools/literature/java/NfcoreEagerFilterControl.java';guards[str(helper)]=pin(helper);guards[str(Path(__file__).resolve())]=pin(Path(__file__).resolve());populations=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();guards[str(jar)]=pin(jar);classes=out/('helper'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25;r=subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)],capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;destination=out/label
    if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.NfcoreEagerFilterControl',linux(fixtures),linux(destination)]
    else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.NfcoreEagerFilterControl',str(fixtures),str(destination)]
    r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,r.stderr);proof=json.loads((destination/'NATIVE_FILTER_COMPLETED.json').read_bytes());assert len(proof['cases'])==36 and proof['refusals']==4;populations.append(dict(label=label,output_directory=linux(destination),completion=pin(destination/'NATIVE_FILTER_COMPLETED.json')));print('Native full-case counts and refusal controls passed',label,flush=True)
  # Parse both native outputs and the original reference with the very same
  # pinned original provider. All headers and canonical SAM records are compared.
  script='''import hashlib,json,pysam,sys
from pathlib import Path
expected=json.load(open(sys.argv[1]));populations=json.load(open(sys.argv[2]));results=[]
def canonical(path):
 h=hashlib.sha256();n=0;unmapped=0
 with pysam.AlignmentFile(path,'rb') as bam:
  header=bam.header.to_dict()
  for read in bam.fetch(until_eof=True):
   fields=read.to_string().split('\\t');fields=fields[:11]+sorted(fields[11:]);h.update(('\\t'.join(fields)+'\\n').encode());n+=1;unmapped+=int(read.is_unmapped)
 return dict(header=header,records=n,unmapped=unmapped,complete_canonical_SAM_sha256=h.hexdigest())
for population in populations:
 for observation in expected:
  native=canonical(Path(population['output_directory'])/(observation['case']+'.bam'))
  if native!=observation['output']:raise RuntimeError('Full native/original BAM semantic mismatch: '+population['label']+' '+observation['case']+' '+repr(native)+' expected '+repr(observation['output']))
  results.append(dict(population=population['label'],case=observation['case'],passed=True,records=native['records']))
Path(sys.argv[3]).write_text(json.dumps(dict(status='all-complete-semantic-BAM-populations-match',cases=results),indent=2)+'\\n')
''';(out/'provider-compare.py').write_text(script);save(out/'populations.json',populations);setup=json.loads((BASE/'eager-image-setup-2/COMPLETED.json').read_bytes());image=next(x for x in setup['images'] if x['version']=='2.4.4');assert image['pin']==source['original_image'];command=['/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer','exec','--cleanenv','--userns','--no-home',image['image'],'python',linux(out/'provider-compare.py'),linux(reference/'observations.json'),linux(out/'populations.json'),linux(out/'complete-semantic-comparison.json')];save(out/'provider-compare-command.json',command);r=subprocess.run(['wsl.exe','-d',DISTRO,'--exec',*command],stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/'provider-compare.stdout').write_bytes(r.stdout);(out/'provider-compare.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr;report=json.loads((out/'complete-semantic-comparison.json').read_bytes());assert len(report['cases'])==216 and all(x['passed'] for x in report['cases']);assert all(pin(Path(n))==v for n,v in guards.items());state.update(status='PASS_216_COMPLETE_NATIVE_EAGER_FILTER_SEMANTIC_COMPARISONS_AND_24_REFUSALS_ONLY',comparisons=216,refusal_controls=24,complete_canonical_record_and_header_comparison=True,populations=populations,originals_unchanged=guards,source_reference=pin(reference/'COMPLETED.json'),semantic_report=pin(out/'complete-semantic-comparison.json'),limitations='Native helper only. Preserves every mandatory SAM field, record order, header field and auxiliary value including NaN/Inf; optional tag ordering is canonicalized for comparison. Not a MAPQ filter, native full eager workflow or paper-role admission.')
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original/source comparisons')
 raise SystemExit(main())
