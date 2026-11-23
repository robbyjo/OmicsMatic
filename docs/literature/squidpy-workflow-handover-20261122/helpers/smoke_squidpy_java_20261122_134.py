from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,time
parser=argparse.ArgumentParser();parser.add_argument('version');args=parser.parse_args()
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1';B=S/('workflow-smoke-'+args.version+'-1');B.mkdir()
versions={'1.1.2':('runtime-legacy-4/environment-1.1.2','3.8.20','runtime-legacy-4/1.1.2-probe.stdout'),
 '1.2.2':('runtime-legacy-2/environment-1.2.2','3.10.22','runtime-legacy-2/1.2.2-probe.stdout'),
 '1.8.4':('runtime-current-1/environment','3.12.3','runtime-current-1/probe.stdout')}
runtime,python,probe=versions[args.version];packages=json.loads((S/probe).read_text(encoding='utf-8-sig'))['packages']
def linux(path):s=path.as_posix();return '/mnt/'+s[0].lower()+s[2:]
request=dict(schema_version=1,mode='RUN',profile='squidpy-original-workflow-v1',operation='SPATIAL_NEIGHBORS',
 out=linux(B/'knn'),python=linux(S/runtime/'bin/python'),expected_python=python,expected_squidpy=args.version,expected_packages=packages,
 input=dict(format='prepared_json',file=linux(S/('operations-probes-'+args.version+'-2/prepared.json'))),
 options=dict(coord_type='generic',n_neighs=3),limits=dict(timeout_seconds=900),admission={})
(B/'request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8')
compile_root=S/'workflow-compile-2';classes=sorted(compile_root.rglob('*.class'))
source=compile_root/'source-context'
if not source.exists():
 source.mkdir()
 for path in sorted((T/'src/main/java/org/jlinalg/cli').glob('SquidpyWorkflow*.java')):shutil.copyfile(path,source/path.name)
 for path in (T/'tools/literature/squidpy_workflow').iterdir():
  if path.is_file():shutil.copyfile(path,source/path.name)
 (source/'FILES.json').write_text(json.dumps({p.name:dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in source.iterdir() if p.is_file()},indent=2)+'\n')
cp=':'.join(linux(p) for p in [compile_root,T/'src/main/resources',S/'native-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'])
command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.SquidpyWorkflowCli','--request',linux(B/'request.json')]
start=time.time()
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:
 p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
 (B/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid,arguments=command,started_unix=start),indent=2)+'\n');code=p.wait()
(B/'RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-start,java_source_prototype_only=True,new_family_credit=0),indent=2)+'\n')
print((B/'stdout').read_text(errors='replace')[-3000:],flush=True);print((B/'stderr').read_text(errors='replace')[-4000:],flush=True)
raise SystemExit(code)
