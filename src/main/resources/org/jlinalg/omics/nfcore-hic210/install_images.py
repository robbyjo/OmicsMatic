# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install/check exactly fourteen private source images; never execute an analysis."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,sys,urllib.request,urllib.error,uuid
PROFILE_SHA256='1b1cf58637b4dc57a3eb26f412469d41db9a1e4c97cbf3d5d05120f7529a79ef'
ACTION='SKIP3/exit3: dependent analysis stays skipped; use permitted Linux/WSL Python3.11+, restore the exact cache/runtime or provide explicit allowed installation/download flags. Preserve failed/partial homes and use NEW home/report paths after a failed setup. FAIL2/exit2: no complete setup admitted; preserve partials, correct the named integrity/input/capacity cause and use new paths. This installs private image files only; qualify the exact engine, source, container runtime, complete references/cohort and outputs separately. No source image, reference or model is substituted.'
class Unavailable(Exception):pass
def pin(p):
    h=hashlib.sha256();n=0
    with p.open('rb')as stream:
        for raw in iter(lambda:stream.read(4194304),b''):h.update(raw);n+=len(raw)
    return dict(bytes=n,sha256=h.hexdigest())
def put(p,value):
    with p.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,indent=2,allow_nan=False);stream.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',required=True,type=Path);p.add_argument('--report',required=True,type=Path);p.add_argument('--cache',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();home,report,cache=[v.absolute()for v in [a.home,a.report,a.cache]]
    if any(x==y or x.is_relative_to(y)or y.is_relative_to(x)for i,x in enumerate([home,report,cache])for y in[home,report,cache][i+1:]):raise ValueError('Home/report/cache must be disjoint')
    if any(v.resolve()!=v for v in[home,report,cache]):raise ValueError('Use canonical private paths without symlink/dot aliases')
    report.mkdir(parents=True,exist_ok=False)
    try:
        if sys.platform!='linux'or sys.version_info<(3,11):raise Unavailable('Run permitted Linux/WSL Python3.11+; no user analysis was started')
        profile=Path(__file__).with_name('IMAGES.json')
        if pin(profile)['sha256']!=PROFILE_SHA256:raise ValueError('Packaged original image profile changed')
        images=json.loads(profile.read_bytes())['images'];assert len(images)==14;existing=home.exists();put(report/'REQUEST.json',dict(home=str(home),cache=str(cache),allow_install=a.allow_install,allow_download=a.allow_download,image_profile=pin(profile),existing_home=existing,user_analysis_admitted=False));cache.mkdir(parents=True,exist_ok=True)
        if existing:
            expected_names={row['pin']['sha256']+'.sif'for row in images.values()}|{'declaration.json','.omicsmatic-hic-images.json'}
            if not home.is_dir()or{v.name for v in home.iterdir()}!=expected_names:raise ValueError('Existing source image home namespace differs; no overwrite permitted')
        else:
            if not a.allow_install:raise Unavailable('Exact source image home absent and installation not allowed; leave dependent analysis skipped')
            home.mkdir(parents=True,exist_ok=False)
        declaration={};records=[]
        for i,(uri,row)in enumerate(sorted(images.items()),1):
            expected=row['pin'];name=expected['sha256']+'.sif';target=home/name;method='existing-exact-image'
            if not existing:
                stored=cache/name
                if not stored.exists():
                    if not a.allow_download:raise Unavailable('Exact original image cache missing: '+str(stored)+'; provide it or explicit allowed download flag')
                    partial=cache/(name+'.download-'+uuid.uuid4().hex);total=0
                    try:
                        request=urllib.request.Request(uri,headers={'User-Agent':'OmicsMatic-explicit-private-original-HiC-image-setup'})
                        with urllib.request.urlopen(request,timeout=120)as response,partial.open('xb')as stream:
                            for raw in iter(lambda:response.read(4194304),b''):
                                total+=len(raw)
                                if total>expected['bytes']:raise ValueError('Original image download exceeds exact complete size')
                                stream.write(raw)
                        if pin(partial)!=expected:raise ValueError('Original image download size/hash differs; partial retained')
                        partial.rename(stored)
                    except(urllib.error.HTTPError,urllib.error.URLError,TimeoutError)as error:raise Unavailable('Exact source image download unavailable; partial retained: '+repr(error))
                if not stored.is_file()or stored.is_symlink()or pin(stored)!=expected:raise ValueError('Complete original cached image bytes differ; no overwrite or version substitution')
                shutil.copyfile(stored,target);method='exact-private-cache-copy'
            if not target.is_file()or target.is_symlink()or pin(target)!=expected:raise ValueError('Installed exact source image differs: '+uri)
            declaration[uri]=str(target);record=dict(uri=uri,file=str(target),pin=expected,method=method);put(report/('%02d-IMAGE.json'%i),record);records.append(record)
        declared=dict(schema_version=1,images=declaration)
        if existing:
            if any(not v.is_file()or v.is_symlink()for v in[home/'declaration.json',home/'.omicsmatic-hic-images.json']):raise ValueError('Existing source setup metadata must be regular files')
            if json.loads((home/'declaration.json').read_bytes())!=declared:raise ValueError('Existing original image declaration differs')
            marker=json.loads((home/'.omicsmatic-hic-images.json').read_bytes())
            if marker!=dict(schema_version=1,pipeline_profile='nf-core/hic-2.1.0',profile_sha256=PROFILE_SHA256,user_analysis_admitted=False):raise ValueError('Existing source image setup marker differs')
        else:
            put(home/'declaration.json',declared);put(home/'.omicsmatic-hic-images.json',dict(schema_version=1,pipeline_profile='nf-core/hic-2.1.0',profile_sha256=PROFILE_SHA256,user_analysis_admitted=False))
        (report/'WARNINGS.md').write_text('# Source image setup only\n\n'+ACTION+'\n',encoding='utf-8');put(report/'COMPLETED.json',dict(status='COMPLETE_PRIVATE_ORIGINAL_HIC_IMAGES_SETUP_ONLY',images=records,declaration=pin(home/'declaration.json'),image_profile=pin(profile),pipeline_execution_or_namespace_health_claimed=False,user_analysis_admitted=False));print('COMPLETE_PRIVATE_IMAGE_SETUP_ONLY',home/'declaration.json');return 0
    except BaseException as failure:
        code=3 if isinstance(failure,(Unavailable,PermissionError))else 2;label='SKIP3_ANALYSIS_SKIPPED'if code==3 else'FAIL2_NO_RESULT_ADMITTED';put(report/'FAILURE.json',dict(status=label,exit_code=code,error=repr(failure),required_action=ACTION,user_analysis_admitted=False));(report/'WARNINGS.md').write_text('# '+label+'\n\n'+repr(failure)+'\n\n'+ACTION+'\n',encoding='utf-8');print(label+': '+repr(failure)+'\n'+ACTION,file=sys.stderr);return code
if __name__=='__main__':
    try:raise SystemExit(main())
    except PermissionError as failure:print('SKIP3_ANALYSIS_SKIPPED: '+repr(failure)+'\n'+ACTION,file=sys.stderr);raise SystemExit(3)
    except Exception as failure:print('FAIL2_NO_RESULT_ADMITTED: '+repr(failure)+'\n'+ACTION,file=sys.stderr);raise SystemExit(2)
