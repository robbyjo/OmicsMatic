from pathlib import Path
import concurrent.futures,hashlib,json,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-refusal-controls-2';B.mkdir();parent=F/'workflow-packaged-4/assoc-w21';assert (parent/'COMPLETED.json').is_file();jar=F/'workflow-normal-build-6/project-build/cli/omicsmatic-0.4.0.jar';parent_pin=hashlib.sha256((parent/'manifest.json').read_bytes()).hexdigest()
def read(p):return json.loads(p.read_bytes())
def write(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def reseal(root):
 m=read(root/'manifest.json');files={};dirs=[]
 for p in root.rglob('*'):
  name=p.relative_to(root).as_posix()
  if p.is_dir():dirs.append(name)
  elif name not in ['manifest.json','COMPLETED.json']:files[name]=pin(p)
 m['inventory']={'files':dict(sorted(files.items())),'directories':sorted(dirs)};write(root/'manifest.json',m);marker=read(root/'COMPLETED.json');marker['manifest_sha256']=pin(root/'manifest.json')['sha256'];write(root/'COMPLETED.json',marker)
def change(root,name,fn):
 p=root/name;obj=read(p);fn(obj);write(p,obj)
def scientific_summary(root,fn):
 for name in ['summary.json','worker/summary.json']:change(root,name,fn)
def norm(v):return v.replace('\\','/') if len(v)>2 and v[1]==':' else v
def omit_runtime(root,package=False):
 r=read(root/'runtime-before.json')
 if package:
  meta=read(root/'worker/runtime.json');target=next(x['path'] for x in meta['packages'] if x['name']=='here');resolved=next(v for k,v in r['addresses'].items() if norm(k)==norm(target));r['files']={k:v for k,v in r['files'].items() if not norm(k).startswith(norm(resolved)+'/')}
 else:
  target=read(root/'canonical-request.json')['rscript'];r['addresses']={k:v for k,v in r['addresses'].items() if norm(k)!=norm(target)}
 for name in ['runtime-before.json','runtime-after.json']:write(root/name,r)
def final_capacity(root):
 def update(cap):
  c=read(root/'canonical-request.json');c['limits']['maximum_output_bytes']=cap;write(root/'canonical-request.json',c)
  raw=read(root/'source-request.json');raw.setdefault('limits',{})['maximum_output_bytes']=cap;write(root/'source-request.json',raw)
  binding=read(root/'request-binding.json');binding['original']=raw;binding['canonical']=c;write(root/'request-binding.json',binding)
  w=read(root/'worker/request.json');w['limits']['maximum_output_bytes']=cap;write(root/'worker/request.json',w)
 for _ in range(3):
  body=sum(p.stat().st_size for p in root.rglob('*') if p.is_file() and p.name not in ['manifest.json','COMPLETED.json']);update(body+128)
 reseal(root);body=sum(p.stat().st_size for p in root.rglob('*') if p.is_file() and p.name not in ['manifest.json','COMPLETED.json']);total=sum(p.stat().st_size for p in root.rglob('*') if p.is_file());cap=read(root/'canonical-request.json')['limits']['maximum_output_bytes'];assert body<=cap<total
 return dict(nonseal_bytes=body,all_bytes=total,declared_cap=cap)
cases=[]
def prepare(name,mutate,expected=2):
 root=B/name;shutil.copytree(parent,root);extra=mutate(root);reseal(root);cases.append((name,root,expected,extra))
prepare('valid-copy',lambda root:None,0)
def source_mismatch(root):
 raw=read(root/'source-request.json');raw['options']['force_model']='blup';write(root/'source-request.json',raw);change(root,'request-binding.json',lambda x:x.update(original=raw))
prepare('source-canonical-contradiction',source_mismatch)
def invalid_canonical(root):
 c=read(root/'canonical-request.json');c['unknown_scientific_setting']=True;write(root/'canonical-request.json',c);change(root,'request-binding.json',lambda x:x.update(canonical=c))
prepare('unknown-canonical-setting',invalid_canonical)
prepare('unindexed-captured-input',lambda root:(root/'inputs/unindexed').write_bytes(b'not indexed'))
prepare('missing-input-index-entry',lambda root:change(root,'input-inventory.json',lambda x:x.pop(next(iter(x)))))
prepare('missing-original-export-index',lambda root:scientific_summary(root,lambda x:x.update(original_export_files=[])))
prepare('unindexed-original-export',lambda root:(root/'worker/out/unindexed.tsv').write_bytes(b'A\tB\n1\t2\n'))
def admitted_mismatch(root):
 name=read(root/'summary.json')['original_export_files'][0];p=root/'admitted'/name;p.write_bytes(p.read_bytes()+b'\n')
prepare('admitted-copy-differs',admitted_mismatch)
def wrong_command(root):
 for name in ['manifest.json','worker-process.json']:change(root,name,lambda x:x['command'].__setitem__(0,'not-declared-Rscript'))
prepare('contradictory-actual-command',wrong_command)
prepare('unfingerprinted-Rscript',lambda root:omit_runtime(root))
prepare('unfingerprinted-loaded-package',lambda root:omit_runtime(root,True))
prepare('contradictory-result-count',lambda root:scientific_summary(root,lambda x:x.update(finite_result_rows=999)))
prepare('manifest-marker-exceed-final-cap',final_capacity)
# Portable offline readers do not resolve the absent original host runtime.
rows=[]
def verify(item,platform='windows21'):
 name,root,expected,extra=item
 args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx256m','-jar',str(jar),'fusion-tools','--verify',str(root)]
 if platform=='windows25':args[0]='C:/Program Files/Java/jdk-25/bin/java.exe'
 if platform=='linux21':
  def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
  args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','java','-Xmx256m','-jar',linux(jar),'fusion-tools','--verify',linux(root)]
 result=subprocess.run(args,cwd=T,capture_output=True);suffix=name+'-'+platform;(B/(suffix+'.stdout')).write_bytes(result.stdout);(B/(suffix+'.stderr')).write_bytes(result.stderr);record=dict(case=name,platform=platform,exit_code=result.returncode,expected=expected,metadata=extra,pass_check=result.returncode==expected);assert record['pass_check'],(record,result.stderr.decode(errors='replace')[-1500:]);return record
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
 for record in pool.map(verify,cases):rows.append(record);print(record['case'],record['exit_code'],flush=True)
for platform in ['windows25','linux21']:
 record=verify(('cross-host-valid',parent,0,None),platform);rows.append(record);print(platform,'offline',record['exit_code'],flush=True)
assert parent_pin==hashlib.sha256((parent/'manifest.json').read_bytes()).hexdigest()
write(B/'RESULT.json',dict(status='PASS_ACTUAL_PACKAGED_OFFLINE_AND_RESEALED_CONTROLS',cases=rows,parent_unchanged=True,scientific_refits=False,authentication_claim=False))