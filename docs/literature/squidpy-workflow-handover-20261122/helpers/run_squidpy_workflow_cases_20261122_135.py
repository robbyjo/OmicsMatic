from pathlib import Path
import argparse,copy,json,subprocess,time
parser=argparse.ArgumentParser();parser.add_argument('version');args=parser.parse_args()
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1';B=S/('workflow-cases-'+args.version+'-1');B.mkdir()
install=S/'linux-runtime-install-1';environments=json.loads((install/'ENVIRONMENTS.json').read_bytes());binding=environments[args.version]
packages=json.loads((install/(args.version+'-probe.stdout')).read_text(encoding='utf-8-sig'))['packages']
def linux(p):s=p.as_posix();return '/mnt/'+s[0].lower()+s[2:]
cp=':'.join(linux(p) for p in [S/'workflow-compile-5',S/'workflow-compile-5/resources',S/'native-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'])
java=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.SquidpyWorkflowCli']
base=dict(schema_version=1,mode='RUN',profile='squidpy-original-workflow-v1',operation='SPATIAL_NEIGHBORS',
 python=binding['python'],expected_python=binding['expected_python'],expected_squidpy=args.version,expected_packages=packages,
 input=dict(format='prepared_json',file=linux(S/('operations-probes-'+args.version+'-2/prepared.json'))),options={},limits=dict(timeout_seconds=900,ack_timeout_seconds=600),admission={})
genes=['LigA','RecB','LIGC','RECD'];seed='rng' if args.version=='1.8.4' else 'seed';records=[]
def execute(name,request,expected=0):
 request=copy.deepcopy(request);request['out']=linux(B/name);file=B/(name+'.json');file.write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8');command=java+['--request',linux(file)];start=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
  (B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=command,started_unix=start),indent=2)+'\n');code=p.wait()
 row=dict(case=name,exit_code=code,expected=expected,seconds=time.time()-start);records.append(row);(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');print(json.dumps(row),flush=True)
 if code!=expected:
  print((B/(name+'.stderr')).read_text(errors='replace')[-4500:],flush=True)
  p=B/name/'worker/report.json'
  if p.exists():print(p.read_text(errors='replace')[-3000:],flush=True)
  raise SystemExit(1)
 return B/name
def operation(name,op,options,expected=0,admission=None):
 r=copy.deepcopy(base);r['operation']=op;r['options']=options
 if admission is not None:r['admission']=admission
 if op=='LIGREC':r['interactions_file']=linux(S/('operations-probes-'+args.version+'-2/interactions.json'))
 if op=='READ_NANOSTRING':r.pop('input');r['nanostring_directory']=linux(S/('operations-probes-'+args.version+'-2/nanostring-input'))
 return execute(name,r,expected)
knn=operation('knn','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3))
operation('radius','SPATIAL_NEIGHBORS',dict(coord_type='generic',radius=1.1))
operation('delaunay','SPATIAL_NEIGHBORS',dict(coord_type='generic',delaunay=True,radius=[0.,2.]))
operation('grid','SPATIAL_NEIGHBORS',dict(coord_type='grid',n_neighs=4,n_rings=2,set_diag=True))
operation('moran','SPATIAL_AUTOCORR',dict(genes=genes,n_perms=19,n_jobs=1,show_progress_bar=False,**{seed:17}))
operation('geary','SPATIAL_AUTOCORR',dict(genes=genes,mode='geary',transformation=False,corr_method=None))
operation('undefined-strict','SPATIAL_AUTOCORR',dict(genes=genes+['flat','Other'],corr_method='fdr_bh'),2)
operation('undefined-allowed','SPATIAL_AUTOCORR',dict(genes=genes+['flat','Other'],corr_method='fdr_bh'),0,dict(allow_no_result_rows=True))
operation('ligrec','LIGREC',dict(cluster_key='cluster',use_raw=False,n_perms=19,n_jobs=1,show_progress_bar=False,corr_method=None,**{seed:17}))
operation('ligrec-fdr','LIGREC',dict(cluster_key='cluster',use_raw=False,n_perms=19,n_jobs=1,show_progress_bar=False,corr_method='fdr_bh',corr_axis='clusters',**{seed:17}))
if args.version!='1.1.2':
 operation('library','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3,library_key='library'))
 operation('nanostring','READ_NANOSTRING',dict(counts_file='counts.csv',meta_file='metadata.csv',fov_file='fov.csv'))
operation('spectral','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3,transform='spectral'),0 if args.version=='1.8.4' else 2)
r=copy.deepcopy(base);r['operation']='SPATIAL_AUTOCORR';r['input']=dict(format='h5ad',file=linux(knn/'worker/out/state/original-state.h5ad'));r['options']=dict(genes=genes,corr_method=None)
execute('moran-on-built-graph',r)
for name in ['knn','moran','ligrec']:
 execute(name+'-replay',dict(schema_version=1,mode='REPLAY',analysis=linux(B/name)))
if args.version=='1.1.2':
 r=copy.deepcopy(base);r['operation']='READ_NANOSTRING';r.pop('input');r['nanostring_directory']='/absent-not-used';r['options']=dict(counts_file='counts.csv',meta_file='metadata.csv');execute('unsupported-original-reader',r,3)
r=copy.deepcopy(base);r['python']='/nonexistent-original-python';execute('missing-python',r,3)
r=copy.deepcopy(base);r['expected_python']='0.0.0';execute('wrong-python',r,3)
(B/'RESULT.json').write_text(json.dumps(dict(status='DEVELOPMENT_JAVA_ORIGINAL_WORKFLOW_CASES_MATCH_EXPECTATIONS',version=args.version,cases=records,new_family_credit=0,normal_project_build_run=False),indent=2)+'\n')
