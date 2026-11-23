"""Create NEW synthetic requests from a chosen local Squidpy runtime inventory."""
from pathlib import Path
import argparse,json,os,subprocess,sys


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--python',required=True,help='Local permitted interpreter; keep its virtual-environment path')
    parser.add_argument('--edition',required=True,choices=('1.1.2','1.2.2','1.8.4'))
    parser.add_argument('--out',type=Path,required=True,help='NEW directory for requests and results parent')
    args=parser.parse_args();python=Path(os.path.abspath(args.python));output=args.out.absolute()
    if output.exists():raise ValueError('Output already exists; preserve it and choose NEW --out')
    if not python.is_file() or not os.access(python,os.X_OK):
        print('SKIP3: request generation skipped. Provide an executable private Python on this host; use Linux paths and run this helper inside WSL for a Linux runtime.',file=sys.stderr);return 3
    # Do not resolve the executable symlink: that can discard virtual-env selection.
    packages=('anndata','numpy','scipy','pandas','scanpy','numba','scikit-learn','statsmodels','omnipath')
    code="import importlib.metadata as m,json,platform;print(json.dumps(dict(python=platform.python_version(),squidpy=m.version('squidpy'),packages={p:m.version(p) for p in "+repr(packages)+"})))"
    probe=subprocess.run([str(python),'-s','-B','-X','utf8','-c',code],stdin=subprocess.DEVNULL,capture_output=True,text=True)
    if probe.returncode:
        print('SKIP3: request generation skipped. Install the permitted original runtime/core dependencies in that private prefix, or retain the skip.\n'+probe.stderr,file=sys.stderr);return 3
    runtime=json.loads(probe.stdout)
    if runtime['squidpy']!=args.edition:
        print('SKIP3: chosen interpreter has Squidpy'+runtime['squidpy']+'; requested '+args.edition+'. Provide the matching permitted prefix or choose its edition explicitly.',file=sys.stderr);return 3
    example=Path(__file__).resolve().parent;output.mkdir(parents=True);(output/'results').mkdir()
    seed='rng' if args.edition=='1.8.4' else 'seed'
    base=dict(schema_version=1,mode='RUN',profile='squidpy-original-workflow-v1',python=str(python),expected_python=runtime['python'],expected_squidpy=args.edition,
              expected_packages=runtime['packages'],input=dict(format='prepared_json',file=str(example/'prepared.json')),admission={},limits=dict(threads=1,timeout_seconds=900,ack_timeout_seconds=600))
    operations=[('neighbors','SPATIAL_NEIGHBORS',dict(coord_type='generic',n_neighs=3)),
                ('moran','SPATIAL_AUTOCORR',dict(genes=['LigA','RecB','LIGC','RECD'],n_perms=19,n_jobs=1,show_progress_bar=False,**{seed:17})),
                ('ligrec','LIGREC',dict(cluster_key='cluster',use_raw=False,n_perms=19,n_jobs=1,show_progress_bar=False,corr_method=None,**{seed:17}))]
    if args.edition!='1.1.2':operations.append(('nanostring','READ_NANOSTRING',dict(counts_file='counts.csv',meta_file='metadata.csv',fov_file='fov.csv')))
    files=[]
    for name,operation,options in operations:
        request=dict(base,operation=operation,options=options,out=str(output/'results'/name))
        if operation=='LIGREC':request['interactions_file']=str(example/'interactions.json')
        if operation=='READ_NANOSTRING':request.pop('input');request['nanostring_directory']=str(example/'nanostring-input')
        path=output/(name+'.json');path.write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8');files.append(path.name)
        replay=dict(schema_version=1,mode='REPLAY',analysis=request['out'],out=str(output/'results'/(name+'-replay')))
        (output/(name+'-replay.json')).write_text(json.dumps(replay,indent=2)+'\n',encoding='utf-8')
    notice='Artificial data only. No biological inference or whole-paper reproduction. Coordinates use arbitrary synthetic units.'
    if args.edition=='1.1.2':notice+=' SKIP3: original1.1.2 has no Nanostring reader; that request was not generated.'
    (output/'RUNTIME-AND-NOTICES.json').write_text(json.dumps(dict(runtime=runtime,run_requests=files,notice=notice),indent=2)+'\n',encoding='utf-8')
    print('Created '+str(len(files))+' RUN requests and matching REPLAY requests in '+str(output)+'. '+notice);return 0


if __name__=='__main__':
    try:raise SystemExit(main())
    except Exception as error:
        print('FAIL2: no requests admitted: '+str(error)+'. Preserve partial files and retry NEW output.',file=sys.stderr);raise SystemExit(2)
