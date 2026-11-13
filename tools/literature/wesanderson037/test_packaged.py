#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the packaged API against all original cases and the CLI against complete original vectors."""
from pathlib import Path
import argparse,csv,hashlib,json,os,shutil,struct,subprocess,time,traceback,zipfile
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION='Required action: supply permitted JDK21/25, the exact built --jar, complete original 6108-case --reference and NEW output. Missing/forbidden prerequisites SKIP3 skip dependent checks; FAIL2 retains the original output, failed checks and diagnostics and admits no qualification. Do not replace original expected colors with Java output, repair source failures, infer author colors or describe native R graphics as implemented.'

def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def tree(p):return {n.relative_to(p).as_posix():pin(n) for n in sorted(p.rglob('*')) if n.is_file()}
def seal(p):
 (p/'SHA256SUMS').write_text(''.join(pin(n)['sha256']+'  '+n.name+'\n' for n in sorted(p.iterdir()) if n.name!='SHA256SUMS'),encoding='utf8',newline='\n')

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--java-bin',required=True,type=Path);parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--reference',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=a.java_bin.resolve();jar=a.jar.resolve();ref=a.reference.resolve();suffix='.exe' if os.name=='nt' else '';env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
  paths=[HERE/'test_packaged.py',HERE/'NativeControl.java',jar,ref/'complete-original-cases.tsv',ref/'complete-original-palettes.tsv',ref/'COMPLETED.txt'];before={str(n):pin(n) for n in paths};save(out/'BEFORE.json',before)
  rows=list(csv.reader((ref/'complete-original-cases.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'));palettes=[line.split('\t') for line in (ref/'complete-original-palettes.tsv').read_text(encoding='utf8').splitlines()]
  if len(rows)!=6108 or any(len(r)!=10 for r in rows) or len(palettes)!=24:raise ValueError('Incomplete original case or namespace population')
  lookup={(r[1],r[2],r[3]):r for r in rows}
  def run(label,command,expected=0):
   command=list(map(str,command));start=time.time();result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=180);(out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr);row=dict(label=label,command=command,exit_code=result.returncode,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   if result.returncode!=expected:raise ValueError('Packaged control failed: '+label+'; inspect complete logs')
  classes=out/'classes';classes.mkdir();run('compile-controller',[jdk/('javac'+suffix),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-proc:none','-sourcepath','','-classpath',jar,'-d',classes,HERE/'NativeControl.java']);run('complete-packaged-api',[jdk/('java'+suffix),'-Xmx512m','-cp',str(classes)+os.pathsep+str(jar),'NativeControl',ref/'complete-original-cases.tsv',ref/'complete-original-palettes.tsv'])
  prefix=[jdk/('java'+suffix),'-Xmx512m','-jar',jar];catalog=out/'catalog';run('catalog',[*prefix,'palettes','--out',catalog]);expected='name\tindex\tcolor\n'+''.join(name+'\t'+str(i)+'\t'+color+'\n' for name,colors in palettes for i,color in enumerate(colors.split(','),1))
  if (catalog/'colors.tsv').read_text(encoding='utf8')!=expected:raise ValueError('Complete original namespace/order/color case differs')
  run('catalog-replay',[*prefix,'verify','--bundle',catalog,'--out',out/'catalog-replay'])
  selected=[]
  for name,_ in palettes:selected.append((name,None,None))
  for name in ['Zissou1','AsteroidCity2','Rushmore','GrandBudapest1','BottleRocket1']:
   selected.extend((name,n,t) for n,t in [(None,'continuous'),(257,'continuous'),(0,'continuous'),(0,'discrete'),(-0.99999999,'discrete'),(-0.999,'continuous'),(1.99999999,'discrete'),(1.5,'continuous')])
  for name in ['Zissou1','AsteroidCity2']:selected.extend((name,3,t) for t in ['d','disc','c','cont'])
  admitted=[];original_failures=0;complete_colors=0
  for i,(name,n,t) in enumerate(selected):
   key=(name,'MISSING' if n is None else struct.pack('>d',float(n)).hex(),'MISSING' if t is None else t);original=lookup[key];good=original[4]=='OK';result=out/('case-'+str(i));args=[*prefix,'generate','--name',name]
   if n is not None:args+=['--n',repr(n)]
   if t is not None:args+=['--type',t]
   args+=['--out',result];run('case-'+str(i),args,0 if good else 2)
   if good:
    wanted=original[6].split(',') if original[6] else [];actual=list(csv.reader((result/'colors.tsv').read_text(encoding='utf8').splitlines(),delimiter='\t'))
    if actual!=[['index','color']]+[[str(j),v] for j,v in enumerate(wanted,1)]:raise ValueError('Complete original CLI color vector differs: '+str(i))
    if (result/'attributes.tsv').read_text(encoding='utf8')!='name\t'+original[7]+'\nclass\t'+original[8]+'\nlength\t'+original[5]+'\n':raise ValueError('Complete original CLI attributes differ')
    if not json.loads((result/'analysis-status.json').read_bytes())['result_admitted']:raise ValueError('Complete result not admitted')
    run('case-'+str(i)+'-replay',[*prefix,'verify','--bundle',result,'--out',out/('case-'+str(i)+'-replay')]);admitted.append(result);complete_colors+=len(wanted)
   else:
    if json.loads((result/'FAILURE.json').read_bytes())['result_admitted'] or json.loads((result/'analysis-status.json').read_bytes())['result_admitted'] or not (result/'WARNINGS_FAILURE.md').is_file():raise ValueError('Original failure admission or recovery notice differs')
    original_failures+=1
  original_tree=tree(admitted[0]);original_catalog=tree(catalog)
  run('overlap-refusal',[*prefix,'verify','--bundle',admitted[0],'--out',admitted[0]/'nested'],2)
  run('existing-output-refusal',[*prefix,'generate','--name','Zissou1','--out',catalog],2)
  if tree(admitted[0])!=original_tree or tree(catalog)!=original_catalog:raise ValueError('Protected earlier bundle changed during refusal')
  mutations={
   'color':('colors.tsv',lambda b:b.replace(b'#A42820',b'#FFFFFF',1)),
   'attribute':('attributes.tsv',lambda b:b.replace(b'BottleRocket1',b'UnknownPalette')),
   'request':('request.tsv',lambda b:b.replace(b'number\tMISSING',b'number\t0x0.0p0')),
   'admission':('analysis-status.json',lambda b:b.replace(b'"result_admitted":true',b'"result_admitted":false')),
   'source':('source-profile.json',lambda b:b+b'\n'),
   'implementation':('implementation-SHA256SUMS',lambda b:b+b'Not the qualified implementation\n'),
   'warning':('WARNINGS.md',lambda b:b'Warnings removed by a controlled refusal test\n')}
  for label,(name,change) in mutations.items():
   altered=out/('mutated-'+label);shutil.copytree(admitted[0],altered);old=(altered/name).read_bytes();new=change(old)
   if new==old:raise ValueError('Mutation fixture did not change bytes: '+label)
   (altered/name).write_bytes(new);seal(altered);run('resealed-'+label+'-refusal',[*prefix,'verify','--bundle',altered,'--out',out/('resealed-'+label+'-refusal')],2)
  extra=out/'mutated-extra';shutil.copytree(admitted[0],extra);(extra/'extra.txt').write_text('not in complete inventory',encoding='utf8');run('extra-file-refusal',[*prefix,'verify','--bundle',extra,'--out',out/'extra-file-refusal'],2)
  missing=out/'mutated-missing';shutil.copytree(admitted[0],missing);(missing/'colors.tsv').unlink();run('missing-file-refusal',[*prefix,'verify','--bundle',missing,'--out',out/'missing-file-refusal'],2)
  invalid=[['generate','--name','unknown'],['generate','--name','Zissou1','--type','MISSING'],['generate','--name','Zissou1','--n','1,2'],['generate','--name','Zissou1','--maximum-colors','0'],['generate','--name','Zissou1','--maximum-colors','1000001'],['generate','--name','Zissou1','--n','257','--type','continuous','--maximum-colors','256'],['generate','--name','Zissou1','--name','Royal1'],['generate','--unknown','x'],['generate'],['unknown']]
  for i,args in enumerate(invalid):run('invalid-'+str(i),[*prefix,*args,'--out',out/('invalid-'+str(i))],2)
  broken=out/'broken-packaging-no-profile.jar'
  with zipfile.ZipFile(jar) as source,zipfile.ZipFile(broken,'w') as target:
   for entry in source.infolist():
    if entry.filename!='org/jlinalg/plot/wesanderson037/source-profile.json':target.writestr(entry,source.read(entry))
  run('missing-packaged-provenance',[jdk/('java'+suffix),'-jar',broken,'generate','--name','Zissou1','--out',out/'missing-packaged-provenance'],3)
  if (out/'missing-packaged-provenance/colors.tsv').exists() or json.loads((out/'missing-packaged-provenance/analysis-status.json').read_bytes())['result_admitted']:raise ValueError('Palette analysis began or result admitted without packaged prerequisites')
  if any(pin(Path(n))!=wanted for n,wanted in before.items()):raise ValueError('Source/artifact/reference changed during complete packaged comparison')
  result=dict(status='PASS_PACKAGED_WESANDERSON037_COMPLETE_API_CLI_AND_REFUSALS',source_pins=before,calls=calls,original_API_cases=6108,complete_original_palette_entries=24,original_CLI_cases=len(selected),original_CLI_successes=len(admitted),original_CLI_failures=original_failures,complete_CLI_colors_compared=complete_colors,resealed_computational_or_provenance_refusals=len(mutations),protected_output_refusals=2,missing_extra_file_refusals=2,invalid_requests=len(invalid),missing_provenance_analysis_skipped=True,complete_saved_bundle_replays=1+len(admitted),original_R_graphics_ported=False,paper_palette_inferred=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_PACKAGED_CONTROL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_PACKAGED_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(result['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
