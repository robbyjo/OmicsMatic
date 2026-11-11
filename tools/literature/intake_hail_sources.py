# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact official Hail wheels, MIT notices and original QC/Batch source before runtime preparation. No analysis credit."""
import argparse,ast,concurrent.futures,hashlib,json,shutil,urllib.request,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--metadata',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--reuse-intake',type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);sources={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))};(out/'intake-at-attempt.py').write_bytes(Path(__file__).read_bytes());state=dict(status='SKIP3_ORIGINAL_HAIL_SOURCE_UNAVAILABLE',source_pins=sources,versions={},implementation_credit=False,whole_goal_complete=False)
    def fetch(url,destination,maximum):
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-source-intake'}),timeout=120) as response:raw=response.read(maximum+1)
        assert len(raw)<=maximum;destination.write_bytes(raw);return raw
    def capture(version):
        home=out/version;home.mkdir();metadata=a.metadata/(version+'.json');raw=metadata.read_bytes();m=json.loads(raw);(home/'pypi-metadata.json').write_bytes(raw);wheel=[f for f in m['urls'] if f['filename'].endswith('.whl')];assert len(wheel)==1;row=wheel[0];assert row['size']<=200000000 and row['url'].startswith('https://files.pythonhosted.org/');target=home/row['filename'];h=hashlib.sha256();count=0
        if a.reuse_intake:
            original=a.reuse_intake/version/row['filename'];assert pin(original)==dict(bytes=row['size'],sha256=row['digests']['sha256']);shutil.copyfile(original,target);count=target.stat().st_size;h.update(target.read_bytes())
        else:
            with urllib.request.urlopen(row['url'],timeout=120) as response,target.open('xb') as output:
                while block:=response.read(1048576):count+=len(block);assert count<=row['size'];h.update(block);output.write(block)
        assert count==row['size'] and h.hexdigest()==row['digests']['sha256'];members={}
        with zipfile.ZipFile(target) as archive:
            assert archive.testzip() is None;names=archive.namelist();licenses=[n for n in names if '.dist-info/' in n and any(n.rsplit('/',1)[-1].upper().startswith(t) for t in ['LICENSE','COPYING','NOTICE'])];selected=[n for n in names if n.endswith('.py') and n.startswith(('hail/','hailtop/'))];selected += licenses+[n for n in names if '.dist-info/' in n and n.rsplit('/',1)[-1] in ['METADATA','RECORD']]+[n for n in names if any(n.endswith('/'+suffix) for suffix in ['hail_version','hail_pip_version','hail_revision','revision','hail_build_info.json'])]
            for name in sorted(set(selected)):
                assert not name.startswith('/') and '\\' not in name and '..' not in Path(name).parts;raw=archive.read(name);assert len(raw)<=8000000;destination=home/'source'/name;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(raw);members[name]=pin(destination)
        if (home/'source/hail/version.py').exists():
            definitions={n.targets[0].id:ast.literal_eval(n.value) for n in ast.parse((home/'source/hail/version.py').read_text(encoding='utf8')).body if isinstance(n,ast.Assign)};revision=definitions['__revision__'];assert definitions['__pip_version__']==version
        else:revision=(home/'source/hail/hail_revision').read_text(encoding='utf8').strip();assert (home/'source/hail/hail_pip_version').read_text(encoding='utf8').strip()==version
        assert len(revision)==40 and all(c in '0123456789abcdef' for c in revision);tag=json.loads(fetch('https://api.github.com/repos/hail-is/hail/git/ref/tags/'+version,home/'official-git-tag.json',131072));obj=tag['object']
        if obj['type']=='tag':obj=json.loads(fetch(obj['url'],home/'official-annotated-tag.json',131072))['object']
        assert obj['type']=='commit' and obj['sha']==revision,'Official tag and wheel revision differ';license_url='https://raw.githubusercontent.com/hail-is/hail/'+revision+'/LICENSE';license_raw=fetch(license_url,home/'upstream-LICENSE',131072);assert b'Permission is hereby granted' in license_raw and b'MIT' in license_raw;upstream={}
        for name in ['hail/methods/qc.py','hail/backend/spark_backend.py','hailtop/batch/backend.py','hailtop/batch/batch.py']:
            destination=home/('upstream-'+name.replace('/','_'));url='https://raw.githubusercontent.com/hail-is/hail/'+revision+'/hail/python/'+name;original=fetch(url,destination,2000000);assert original==(home/'source'/name).read_bytes(),('Official source and wheel member differ',name);upstream[name]=dict(url=url,**pin(destination))
        result=dict(status='PASS_EXACT_ORIGINAL_HAIL_SOURCE_AND_LICENSE_CAPTURE_ONLY',revision=revision,wheel=dict(url=row['url'],file=target.relative_to(ROOT).as_posix(),**pin(target)),metadata=pin(home/'pypi-metadata.json'),members=members,license=dict(url=license_url,**pin(home/'upstream-LICENSE')),wheel_embedded_license_members=licenses,official_source_members=upstream,required_python=m['info']['requires_python'],dependencies=m['info']['requires_dist']);save(home/'COMPLETED.json',result);print(version,'captured original wheel and',len(members),'source/license members',flush=True);return version,result
    try:
        assert a.allow_download,'Downloading not allowed for this invocation; keep dependent analysis skipped'
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:state['versions']=dict(pool.map(capture,['0.2.139','0.2.130']))
        state['status']='PASS_TWO_ORIGINAL_HAIL_EDITIONS_SOURCE_CAPTURE_ONLY'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Source/license capture only; no Hail scientific workflow or cloud batch was executed.')+'\n\nSKIP3: where downloading/installing is forbidden or the exact source cannot be captured, keep dependent analysis skipped. Preserve partial downloads and errors; correct the named permission/network/integrity issue and retry NEW paths. Source capture grants no implementation credit.\n',encoding='utf8',newline='\n');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original wheel/license identity checks')
    raise SystemExit(main())
