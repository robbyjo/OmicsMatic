# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Fixed driver for the unchanged private SpliceAI1.3 module; no supplied Python code."""
import json,os,sys,traceback
from pathlib import Path

def run():
    if len(sys.argv)!=2:raise ValueError('Require one fixed worker request path')
    with open(sys.argv[1],encoding='utf-8')as stream:request=json.load(stream)
    if set(request)!={'input','output','reference','annotation','distance','mask','threads','report'}:raise ValueError('Unexpected fixed SpliceAI worker fields')
    for key in ['input','output','reference','annotation','report']:
        if not isinstance(request[key],str)or not request[key]or'\0'in request[key]:raise ValueError('Invalid local worker path/annotation')
    if type(request['distance'])is not int or not 0<=request['distance']<=4999:raise ValueError('Original1.3 distance must be0..4999')
    if type(request['mask'])is not int or request['mask']not in[0,1]:raise ValueError('Original1.3 mask must be0/1')
    if type(request['threads'])is not int or not 1<=request['threads']<=64:raise ValueError('Invalid owned CPU budget')
    available=sorted(os.sched_getaffinity(0));cpus=available[:request['threads']]
    if len(cpus)!=request['threads']:raise RuntimeError('Requested complete CPU budget unavailable')
    os.sched_setaffinity(0,set(cpus));report={'schema_version':1,'backend':'original-SpliceAI1.3-CPU','source_modified':False,'main_returned_normally':False,'allowed_cpus':cpus,'upstream_module':'spliceai.__main__','model_assets_required':5}
    try:
        import pkg_resources,numpy,pandas,pysam,pyfaidx,h5py,keras,tensorflow
        expected={'spliceai':'1.3','tensorflow':'1.15.5','Keras':'2.2.4','numpy':'1.18.5','pandas':'0.25.3','h5py':'2.10.0','pysam':'0.16.0.1','pyfaidx':'0.5.9.5','protobuf':'3.20.3','scipy':'1.4.1'}
        versions={name:pkg_resources.get_distribution(name).version for name in expected}
        if versions!=expected:raise RuntimeError('Exact qualified SpliceAI dependency editions differ: '+repr(versions))
        report['versions']=versions;report['tensorflow_gpu_available']=bool(tensorflow.test.is_gpu_available())
        if report['tensorflow_gpu_available']:raise RuntimeError('This profile requires original CPU execution; no unqualified GPU fallback')
        from spliceai.__main__ import main
        sys.argv=['spliceai','-I',request['input'],'-O',request['output'],'-R',request['reference'],'-A',request['annotation'],'-D',str(request['distance']),'-M',str(request['mask'])]
        main();report['main_returned_normally']=True
    except BaseException as failure:
        report['original_failure_type']=type(failure).__name__;report['original_failure']=str(failure)
        if isinstance(failure,SystemExit):report['original_system_exit_code']=failure.code
        raise
    finally:
        modules={}
        for name,module in sorted(sys.modules.items()):
            value=getattr(module,'__file__',None)
            modules[name]=str(Path(value).resolve())if isinstance(value,str)and value else None
        report['loaded_modules']=modules;libraries=set();unresolved=[]
        with open('/proc/self/maps',encoding='utf-8')as stream:
            for line in stream:
                columns=line.rstrip('\n').split(None,5)
                if len(columns)==6 and'x'in columns[1]and columns[5].startswith('/'):
                    path=Path(columns[5])
                    if path.is_file():libraries.add(str(path.resolve()))
                    else:unresolved.append(columns[5])
        report['loaded_executable_libraries']=sorted(libraries);report['unresolved_executable_library_paths']=sorted(set(unresolved))
        with open(request['report'],'x',encoding='utf-8')as stream:json.dump(report,stream,ensure_ascii=False,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')

if __name__=='__main__':run()
