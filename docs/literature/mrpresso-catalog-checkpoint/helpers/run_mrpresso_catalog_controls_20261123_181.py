from pathlib import Path
import concurrent.futures,gzip,hashlib,json,shutil,subprocess,sys,time
T=Path('D:/projects/JLinAlg/build/fu-1');R=T.parents[1];B=T/'build/mrpresso-catalog-178';D=B/('controls-'+sys.argv[1]);D.mkdir()
sys.path.insert(0,str(T/'tools/literature'));from mrpresso_catalog import model as m
def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
before={n:pin(T/n) for n in m.META};cache=R/'build/literature-review/xml'
jar=T/'build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar'
badcache=D/'private-altered-primary';badcache.mkdir();paper=json.loads((T/m.META[0]).read_bytes())[0]
with gzip.open(cache/(paper['pmcid']+'.xml.gz'),'rb') as stream:original=stream.read()
(badcache/(paper['pmcid']+'.xml.gz')).write_bytes(gzip.compress(original+b'\n'))
(D/'wrong-producer.jar').write_bytes(b'Not the qualified producer.\n')
plan=B/'plan-windows-2/result'
for case in ('changed-plan','changed-preview'):
    dest=D/case;dest.mkdir()
    for name in ('PLAN.json','metadata-before.zip','PREVIOUS-SHA256SUMS','QUALIFICATION.json','REGISTRY.json'):shutil.copyfile(plan/name,dest/name)
    if case=='changed-plan':
        path=dest/'PLAN.json';value=json.loads(path.read_bytes());value['source_code']['tools/literature/mrpresso_catalog/catalog.py']['sha256']='0'*64
        path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    else:
        shutil.copytree(plan/'preview',dest/'preview')
        path=dest/'preview/site/literature-review.html';path.write_bytes(path.read_bytes()+b'\n<!-- contradictory preview bytes -->\n')
rows=[]
for host in ('windows','linux'):
    for case,mode,actualcache,actualjar,planned,expected in (
        ('missing-primary','plan',D/'absent-primary',jar,None,3),
        ('wrong-producer','plan',cache,D/'wrong-producer.jar',None,2),
        ('changed-primary','plan',badcache,jar,None,2),
        ('changed-plan','inspect',cache,jar,D/'changed-plan',2),
        ('changed-preview','inspect',cache,jar,D/'changed-preview',2)):
        rows.append((host,case,mode,actualcache,actualjar,planned,expected))
def run(row):
    host,case,mode,actualcache,actualjar,planned,expected=row;folder=D/(host+'-'+case);folder.mkdir()
    args=[str(T/'tools/literature/close_mrpresso_catalog.py'),'--'+mode,'--cache',str(actualcache),'--jar',str(actualjar),'--out',str(folder/'result')]
    if planned:args+=['--plan-dir',str(planned)]
    if host=='windows':command=[sys.executable,'-X','utf8','-B',*args]
    else:
        def linux(n):
            value=str(n).replace('\\','/');return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
        command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','env',
            'GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE='+linux(T),'GIT_OPTIONAL_LOCKS=0','python3','-B',*[linux(n) for n in args]]
    start=time.monotonic()
    with (folder/'stdout').open('xb') as out,(folder/'stderr').open('xb') as err:
        p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
        (folder/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid,command=command),indent=2)+'\n');code=p.wait()
    failure=json.loads((folder/'result/FAILURE.json').read_bytes());assert code==expected and not failure['complete_result'],(case,host,code,failure['error'])
    record=dict(host=host,case=case,exit_code=code,expected=expected,seconds=time.monotonic()-start,error=failure['error'],complete_result=False)
    print(json.dumps(record),flush=True);return record
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(run,rows))
assert before=={n:pin(T/n) for n in m.META}
(D/'RESULT.json').write_text(json.dumps(dict(status='PASS_TEN_ACTUAL_CLI_SOURCE_PRODUCER_PLAN_PREVIEW_REFUSALS',cases=records,
    canonical_bytes_unchanged=True,full_primary_text_private_and_not_for_publication=True),indent=2)+'\n')
