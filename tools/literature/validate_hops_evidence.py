# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare both native HOPS builds on three JVMs with complete original R source/boundary controls."""
import argparse,json,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['out','jar21','jar25','source','boundaries','reference','class-cases','metadata','metadata-reference']:p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();state=dict(status='FAIL2_NATIVE_HOPS_SOURCE_CONTROLS',new_tool_credit=0,full_hops_adapter_admitted=False,biological_result_admitted=False)
 try:
  reference=a.reference.resolve();original=json.loads((reference/'COMPLETED.json').read_bytes());assert original['status']=='PASS_ORIGINAL_UNMODIFIED_HOPS_AST_6_SOURCE_PAIRS_2005_NUMERIC_BOUNDARIES_AND_420_CLASSIFICATION_BOUNDARIES_ONLY';source=a.source.resolve();boundaries=a.boundaries.resolve();metadata=a.metadata.resolve();metareference=a.metadata_reference.resolve();extra=json.loads((metareference/'COMPLETED.json').read_bytes());assert extra['status']=='PASS_ORIGINAL_HOPS_METADATA_AND_SAVED_REPORT_CHECKS_WITH_RETAINED_DEFAULT_MODE_FAILURE_ONLY' and extra['metadata_pairs']==890;classcases=a.class_cases.resolve();helper=ROOT/'tools/literature/java/HopsEvidenceControl.java';guards={str(p):pin(p) for folder in [source,boundaries,reference,metadata,metareference] for p in folder.rglob('*') if p.is_file()};guards.update({str(p):pin(p) for p in [helper,Path(__file__).resolve(),classcases]});results=[]
  for compiler in ['21','25']:
   jar=Path(getattr(a,'jar'+compiler)).resolve();guards[str(jar)]=pin(jar);classes=out/('helper'+compiler);classes.mkdir();java=JAVA if compiler=='21' else JAVA25;cmd=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',str(jar),'-d',str(classes),str(helper)];r=subprocess.run(cmd,capture_output=True,timeout=90);(out/('javac'+compiler+'.stderr')).write_bytes(r.stderr);assert r.returncode==0,r.stderr
   for runtime in ['windows21','windows25','linux21']:
    label=compiler+'-'+runtime;convert=linux if runtime=='linux21' else str;config=out/(label+'.json');save(config,dict(groups=[dict(label='complete-source',data=convert(source),expected=convert(reference/'complete-source')),dict(label='boundaries',data=convert(boundaries),expected=convert(reference/'boundaries')),dict(label='metadata',data=convert(metadata),expected=convert(metareference/'metadata'))],classification_cases=convert(classcases),classification_expected=convert(reference/'complete-source/classification.tsv')));target=out/label
    if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.HopsEvidenceControl',linux(config),linux(target)]
    else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.HopsEvidenceControl',str(config),str(target)]
    r=subprocess.run(cmd,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==0,(label,r.stderr);report=json.loads((target/'COMPLETED.json').read_bytes());assert report['sample_node_pairs']==2901 and report['classification_boundaries']==420 and report['refusals']==7;results.append(dict(compiler=compiler,runtime=runtime,sample_node_pairs=2901,classification_boundaries=420,refusals=7,proof=pin(target/'COMPLETED.json')));print('Complete native HOPS source/boundary controls passed',label,flush=True)
  assert all(pin(Path(p))==v for p,v in guards.items());state.update(status='PASS_17406_NATIVE_HOPS_SOURCE_NUMERIC_PAIRS_2520_CLASS_BOUNDARIES_AND_42_REFUSALS_ONLY',sample_node_pairs=17406,classification_boundaries=2520,refusals=42,results=results,originals_unchanged=guards)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original source/numeric comparisons')
 raise SystemExit(main())
