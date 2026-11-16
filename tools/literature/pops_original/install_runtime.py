#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install an isolated selected original PoPS environment; this performs no analysis."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,time,traceback,urllib.request

HERE=Path(__file__).resolve().parent
PIXI_SHA='50571451e96f4a82cf675cd83baaf66fff9b718f0aaf153900b9faf4c29bd401'
PINS={
    '0.1/pops.feature_selection.py':('3a0c031f74fb7d6570ce48561ddb32f4c43ec3b4','833c2569dff7e13f0f987534d52849d99270992fbdb46045dbc5dfb4a2b2d4bc'),
    '0.1/pops.predict_scores.py':('3a0c031f74fb7d6570ce48561ddb32f4c43ec3b4','5b1f7399151863a7aa756c6cd8b367b620067cf89e164703d54392e4f475fb75'),
    '0.1/LICENSE':('3a0c031f74fb7d6570ce48561ddb32f4c43ec3b4','3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986'),
    '0.2/pops.py':('76eb86cba10254490003c8f4dc7ff5ce492d3667','35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'),
    '0.2/LICENSE':('76eb86cba10254490003c8f4dc7ff5ce492d3667','3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986'),
}
ACTION='Install only in permitted environments. Missing/forbidden prerequisites are SKIP3 and dependent analysis must remain skipped. Supply a permitted Linux x86-64 system, the pinned Pixi bootstrap and network access, or leave it skipped. Retain all failures; retry NEW paths rather than replacing a runtime fingerprint. Installation readiness is not scientific qualification or author-environment reproduction.'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--pixi',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--cache-dir',type=Path,required=True);p.add_argument('--source-cache',type=Path);a=p.parse_args();out=None
    try:
        if platform.system()!='Linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Run permitted installation inside Linux x86-64, including WSL')
        if not a.pixi.is_file() or not os.access(a.pixi,os.X_OK):raise FileNotFoundError('Pinned Pixi bootstrap unavailable: '+str(a.pixi))
        if sha(a.pixi.read_bytes())!=PIXI_SHA:raise ValueError('Pixi bootstrap identity differs; do not substitute an unreviewed executable')
        a.out.absolute().mkdir(parents=True,exist_ok=False);out=a.out.resolve();(out/'WARNINGS.md').write_text('# Runtime setup warnings and required actions\n\n'+ACTION+'\n',encoding='utf-8')
        (out/'pixi.toml').write_bytes((HERE/'runtime-pixi.toml').read_bytes());original=out/'original';original.mkdir()
        records={}
        for name,(commit,digest) in PINS.items():
            version,filename=name.split('/');destination=original/name;destination.parent.mkdir(exist_ok=True)
            url='https://raw.githubusercontent.com/FinucaneLab/pops/'+commit+'/'+filename
            cache=a.source_cache/version/filename if a.source_cache else None
            if cache is not None and cache.is_file():raw=cache.read_bytes();origin='exact-local-source-cache'
            else:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-PoPS-setup'}),timeout=60) as response:raw=response.read(1048576)
                origin='pinned-official-source-download'
            if sha(raw)!=digest:raise ValueError('Exact original source bytes differ: '+name)
            destination.write_bytes(raw);records[name]=dict(commit=commit,url=url,sha256=digest,bytes=len(raw),origin=origin)
        # The original munger is pinned by the same source audit as pops.py.
        manifest_path=HERE/'source-pins.json'
        if not manifest_path.is_file():raise FileNotFoundError('Selected source manifest unavailable: source-pins.json')
        more=json.loads(manifest_path.read_bytes())['0.2/munge_feature_directory.py'];name='0.2/munge_feature_directory.py'
        cache=a.source_cache/name if a.source_cache else None
        if cache is not None and cache.is_file():raw=cache.read_bytes();origin='exact-local-source-cache'
        else:
            with urllib.request.urlopen(urllib.request.Request(more['url'],headers={'User-Agent':'OmicsMatic-original-PoPS-setup'}),timeout=60) as response:raw=response.read(1048576)
            origin='pinned-official-source-download'
        if sha(raw)!=more['sha256']:raise ValueError('Original munger identity differs')
        (original/name).write_bytes(raw);records[name]=dict(more,bytes=len(raw),origin=origin)
        save(out/'SOURCE.json',dict(profile='original-pops-selected-source-v1',files=records,selected_source_is_author_commit=False,author_runtime_reproduced=False))
        a.cache_dir.absolute().mkdir(parents=True,exist_ok=True);env=os.environ.copy();env['PIXI_CACHE_DIR']=str(a.cache_dir.resolve());env['PYTHONDONTWRITEBYTECODE']='1'
        command=[str(a.pixi.resolve()),'install','--manifest-path',str(out/'pixi.toml')];start=time.monotonic()
        with (out/'install.stdout').open('xb') as so,(out/'install.stderr').open('xb') as se:r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
        save(out/'INSTALL-PROCESS.json',dict(command=command,exit_code=r.returncode,seconds=round(time.monotonic()-start,3),pixi_sha256=PIXI_SHA,manifest_sha256=sha((out/'pixi.toml').read_bytes())))
        if r.returncode:raise RuntimeError('Runtime installation failed; inspect install.stdout and install.stderr. No analysis ran.')
        python=out/'.pixi/envs/default/bin/python'
        command=[str(python),'-I','-B',str(HERE/'runtime_health.py'),str(original)];start=time.monotonic()
        with (out/'health.stdout').open('xb') as so,(out/'health.stderr').open('xb') as se:r=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=180)
        save(out/'HEALTH-PROCESS.json',dict(command=command,exit_code=r.returncode,seconds=round(time.monotonic()-start,3)))
        if r.returncode:raise RuntimeError('Installed runtime health failed; retain health.stdout and health.stderr. No analysis ran.')
        save(out/'INSTALLED.json',dict(status='INSTALLED_ORIGINAL_POPS_DEPENDENCIES_NOT_ADOPTED_NOT_ANALYSIS',python=str(python),source_sha256=sha((out/'SOURCE.json').read_bytes()),pixi_lock_sha256=sha((out/'pixi.lock').read_bytes()),full_runtime_adoption_complete=False,scientific_analysis_performed=False))
        print(json.dumps(dict(status='INSTALLED_ORIGINAL_POPS_DEPENDENCIES_NOT_ADOPTED_NOT_ANALYSIS',out=str(out))),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None:
            (out/('SKIPPED.json' if code==3 else 'FAILURE.json')).write_text(json.dumps(dict(status='SKIP3_ORIGINAL_POPS_SETUP' if code==3 else 'FAIL2_ORIGINAL_POPS_SETUP',reason=repr(error),required_action=ACTION),indent=2)+'\n',encoding='utf-8');(out/'traceback.txt').write_text(traceback.format_exc(),encoding='utf-8')
        print(('SKIP3' if code==3 else 'FAIL2'),repr(error),ACTION,flush=True);return code

if __name__=='__main__':raise SystemExit(main())
