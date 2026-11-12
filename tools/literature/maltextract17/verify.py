#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Rebuild and replay complete MaltExtract1.7 native evidence; no original fitting."""
from pathlib import Path,PurePosixPath
from collections import Counter
import argparse,gzip,hashlib,itertools,json,lzma,os,re,subprocess,sys,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
DATA=ROOT/'docs/literature/maltextract17-development'
MAX_FILE=128*1024*1024;MAX_UNIQUE=512*1024*1024;MAX_RESTORED=2*1024*1024*1024
READER=dict(bytes=10770756,sha256='8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca')
ACTION='Required action: retain failed replay outputs and diagnostics. Supply this checkout\'s matching complete evidence archive and source, a permitted JDK21 or JDK25, the exact HOPS0.35 MaltExtract1.7 reader ROOT, and a NEW output directory with at least2GiB free space. Obtain the reader with tools/literature/maltextract17/setup.py only where download/install is permitted. If a required runtime/compiler/install is unavailable or forbidden, skip dependent analysis. Resolve the named integrity, format, full-input or capacity issue; do not repair or replace evidence silently. Whole MaltExtract/HOPS and biological paper reproduction remain incomplete.'
def pin(path):
 h=hashlib.sha256();n=0
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def member(name):
 p=PurePosixPath(name)
 if not name or p.is_absolute() or '\\' in name or ':' in name or any(ord(c)<32 or ord(c)==127 for c in name) or any(t in ['', '.', '..'] for t in name.split('/')):raise ValueError('Unsafe complete evidence member')
 return name
def fasta(path):
 raw=path.read_text(encoding='utf8')
 if raw in ['', 'None\n']:return Counter()
 lines=raw.splitlines()
 if len(lines)%2:raise ValueError('Incomplete original/native FASTA control')
 pairs=Counter()
 for i in range(0,len(lines),2):
  if not lines[i].startswith('>'):raise ValueError('Nonliteral FASTA header in source control')
  pairs[(lines[i],lines[i+1])]+=1
 return pairs
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',required=True,type=Path);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();owned=False
 state=dict(status='FAIL2_MALTEXTRACT17_COMPLETE_NATIVE_REPLAY',original_fitting_repeated=False,whole_goal_complete=False)
 try:
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  out.mkdir(parents=True,exist_ok=False);owned=True;runtime=a.runtime.resolve()
  if not (runtime/'MaltExtract1.7.jar').is_file() or pin(runtime/'MaltExtract1.7.jar')!=READER:raise OSError('Exact permitted MaltExtract1.7 reader is unavailable; do not substitute a version')
  validation=json.loads((DATA/'VALIDATION.json').read_bytes());archive=DATA/'controls.zip.xz'
  if pin(archive)!=validation['archive']:raise ValueError('Complete evidence archive bytes/hash differ')
  for name,expected in validation['sources'].items():
   if pin(ROOT/member(name))!=expected:raise ValueError('Current build/replay source differs: '+name)
  container=out/'controls.zip';expanded=0
  with lzma.open(archive,'rb') as source,container.open('xb') as target:
   for block in iter(lambda:source.read(1048576),b''):
    expanded+=len(block)
    if expanded>MAX_UNIQUE:raise ValueError('Complete evidence container exceeds capacity')
    target.write(block)
  evidence=out/'evidence';evidence.mkdir();restored=0
  with zipfile.ZipFile(container) as z:
   names=set()
   for info in z.infolist():
    name=member(info.filename)
    if name in names or info.is_dir() or info.file_size>MAX_FILE or (info.external_attr>>16)&0o170000 not in [0,0o100000]:raise ValueError('Duplicate/nonregular/over-capacity complete object')
    names.add(name)
   if len(names)>10000 or 'MANIFEST.json' not in names or z.getinfo('MANIFEST.json').file_size>16777216:raise ValueError('Invalid complete manifest capacity')
   manifest=json.loads(z.read('MANIFEST.json'))
   if manifest.get('schema')!='MALTEXTRACT17_COMPLETE_NATIVE_EVIDENCE_V1':raise ValueError('Unsupported complete evidence schema')
   files={member(name):value for name,value in manifest['files'].items()};objects={}
   for value in files.values():
    if set(value)!=set(['bytes','sha256']) or type(value['bytes']) is not int or not 0<=value['bytes']<=MAX_FILE or not re.fullmatch('[0-9a-f]{64}',value['sha256']):raise ValueError('Invalid complete evidence fingerprint')
    if value['sha256'] in objects and objects[value['sha256']]!=value:raise ValueError('Conflicting shared object')
    objects[value['sha256']]=value
   if names!=set(['MANIFEST.json'])|{'objects/'+digest for digest in objects} or sum(value['bytes'] for value in objects.values())>MAX_UNIQUE:raise ValueError('Missing/extra complete objects or unique byte capacity exceeded')
   for digest,value in objects.items():
    h=hashlib.sha256();n=0
    with z.open('objects/'+digest) as source:
     for block in iter(lambda:source.read(1048576),b''):h.update(block);n+=len(block)
    if dict(bytes=n,sha256=h.hexdigest())!=value:raise ValueError('Complete shared evidence object differs')
   for name,value in files.items():
    restored+=value['bytes']
    if restored>MAX_RESTORED:raise ValueError('Complete restored evidence exceeds capacity')
    target=evidence/name;target.parent.mkdir(parents=True,exist_ok=True)
    with z.open('objects/'+value['sha256']) as source,target.open('xb') as destination:
     for block in iter(lambda:source.read(1048576),b''):destination.write(block)
    if pin(target)!=value:raise ValueError('Complete restored member differs: '+name)
  commands=[];env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS']}
  def run(label,command,expected=0):
   command=list(map(str,command));r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=300)
   (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);receipt=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);save(out/(label+'.process.json'),receipt);commands.append(receipt)
   if r.returncode!=expected:raise ValueError('Complete replay failed at '+label+'; inspect retained diagnostics')
  build=out/'build';run('build',[sys.executable,ROOT/'tools/literature/maltextract17/build.py','--java-bin',a.java_bin,'--out',build]);suffix='.exe' if os.name=='nt' else '';java=a.java_bin/('java'+suffix);javac=a.java_bin/('javac'+suffix);artifact=build/'maltextract17-reads-development.jar'
  classes=out/'probes';classes.mkdir();module=ROOT/'tools/literature/maltextract17/java'
  probes=['MaltExtractRmaNativeProbe','MaltExtractPrimitiveNativeProbe','MaltExtractFilterNativeProbe','MaltExtractTaxonomyNativeProbe']
  run('probe-javac',[javac,'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',build/'classes','-d',classes,*[module/(name+'.java') for name in probes]])
  cp=str(build/'classes')+os.pathsep+str(classes);field_checks=0
  for case in ['A','B','dna5','support59']:
   for profile in ['RAW','EXTRACTION']:
    label=case+'-'+profile;target=out/('reader-'+label)
    run('reader-'+label,[java,'-Xmx1g','-cp',cp,'MaltExtractRmaNativeProbe',runtime,evidence/'inputs'/(case+'.rma6'),profile,target])
    for name in ['reads.tsv','matches.tsv','identities.tsv','classes.tsv','counts.tsv']:
     if (target/name).read_bytes()!=(evidence/'oracles/reader'/label/name).read_bytes():raise ValueError('Complete direct-original RMA field oracle differs: '+label+'/'+name)
     field_checks+=1
  for label,probe in [('primitives','MaltExtractPrimitiveNativeProbe'),('filters','MaltExtractFilterNativeProbe')]:
   target=out/(label+'.tsv');run(label,[java,'-Xmx1g','-cp',cp,probe,evidence/'oracles'/label/'inputs.tsv',target])
   if target.read_bytes()!=(evidence/'oracles'/label/'source.tsv').read_bytes():raise ValueError('Complete original primitive/filter oracle differs: '+label)
  vector=out/'taxonomy.tsv.gz';run('taxonomy',[java,'-Xmx3g','-cp',cp,'MaltExtractTaxonomyNativeProbe',evidence/'original-resources/ncbi.tre',vector]);nodes=0;decoded=0;h=hashlib.sha256()
  with gzip.open(vector,'rb') as actual,gzip.open(evidence/'oracles/taxonomy/source.tsv.gz','rb') as expected:
   for i,(row,gold) in enumerate(itertools.zip_longest(actual,expected)):
    if row!=gold:raise ValueError('Complete original weighted taxonomy oracle differs at row'+str(i))
    decoded+=len(row)
    if decoded>100000000:raise ValueError('Complete decoded taxonomy vector exceeds capacity')
    h.update(row)
    if i:nodes+=1
  if nodes!=2258978 or h.hexdigest()!='b5d6ee33ed41716de364de7ea92e77b756dc83259c1f4565cee3e9df4335976f':raise ValueError('Complete original taxonomy population/digest differs')
  names={8574:'Boa_constrictor',9606:'Homo_sapiens',37349:'Mammuthus_primigenius',40674:'Mammalia',160:'Treponema_pallidum'};comparisons=[]
  for case in ['A','B','dna5','support59']:
   bundle=evidence/'bundles'/case;run('verify-'+case,[java,'-Xmx3g','-jar',artifact,'--verify',bundle,'--runtime',runtime])
   for mode in ['default','ancient']:
    for taxon,name in names.items():
     actual=bundle/'derived'/mode/(str(taxon)+'.fasta');gold=evidence/'original/defanc-complete'/mode/'reads'/(case+'.rma6')/(name+'.fasta')
     if fasta(actual)!=fasta(gold):raise ValueError('Complete original FASTA population differs: '+case+'/'+mode+'/'+name)
     comparisons.append(dict(case=case,mode=mode,taxon=taxon,reads=sum(fasta(actual).values())))
  for label in ['false-tool-credit','false-whole-tool','insufficient-reads','false-projection-count']:
   run('refuse-'+label,[java,'-Xmx3g','-jar',artifact,'--verify',evidence/'damaged'/label,'--runtime',runtime],2)
  for name,value in files.items():
   if pin(evidence/name)!=value:raise ValueError('Complete saved evidence changed during replay: '+name)
  if pin(runtime/'MaltExtract1.7.jar')!=READER:raise ValueError('Original reader changed during replay')
  for name,value in validation['sources'].items():
   if pin(ROOT/name)!=value:raise ValueError('Current replay/build source changed')
  state.update(status='PASS_COMPLETE_MALTEXTRACT17_NATIVE_SAVED_EVIDENCE_REPLAY',restored_members=len(files),restored_bytes=restored,unique_objects=len(objects),reader_field_checks=field_checks,primitive_cases=4018,filter_cases=2640,taxonomy_nodes=nodes,taxonomy_decoded_sha256=h.hexdigest(),class_mode_fasta_comparisons=comparisons,saved_bundles=4,metadata_refusals=4,commands=commands,new_tool_credit=0)
  save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Complete native saved replay only\n\nOriginal Java8 execution is not repeated. Native read-projection choices are explicit alternatives to stock removal/sampling. Full target-species selection, whole MaltExtract/HOPS and biological reproduction remain incomplete.\n\n'+ACTION+'\n',encoding='utf8',newline='\n');print(state['status'],flush=True);return 0
 except Exception as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;state.update(status='SKIP3_REPLAY_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_MALTEXTRACT17_COMPLETE_NATIVE_REPLAY',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,dependent_analysis_skipped=True)
  if owned:save(out/'FAILURE.json',state);(out/'WARNINGS_FAILURE.md').write_text('# '+state['status']+'\n\n'+str(error)+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(state['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable full saved-evidence checks')
 raise SystemExit(main())
