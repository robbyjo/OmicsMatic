# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Intake official bioinformatics Palantir wheels and immutable upstream source; install nothing."""
import argparse,hashlib,json,traceback,urllib.request,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def fetch(url,maximum):
    assert url.startswith(('https://pypi.org/','https://files.pythonhosted.org/','https://api.github.com/repos/dpeerlab/Palantir/','https://raw.githubusercontent.com/dpeerlab/Palantir/'))
    with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'}),timeout=60) as response:
        raw=response.read(maximum+1);assert len(raw)<=maximum;return raw

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_OFFICIAL_SOURCE_INTAKE',implementation_credit=False,whole_goal_complete=False)
    try:
        current=fetch('https://pypi.org/pypi/palantir/json',8388608);(out/'official-current-pypi.json').write_bytes(current);published=json.loads(current);latest=published['info']['version'];versions=list(dict.fromkeys(['1.4.4',latest]));assert published['info']['name'].lower()=='palantir' and 'dpeerlab' in json.dumps(published['info']['project_urls']).lower();records={}
        for version in versions:
            home=out/version;home.mkdir();metadata=fetch('https://pypi.org/pypi/palantir/'+version+'/json',8388608);(home/'official-pypi.json').write_bytes(metadata);dist=json.loads(metadata);wheels=[r for r in dist['urls'] if r['filename'].endswith('-py3-none-any.whl')];assert len(wheels)==1 and not wheels[0]['yanked'];wheel=wheels[0];raw=fetch(wheel['url'],33554432);assert len(raw)==wheel['size'] and hashlib.sha256(raw).hexdigest()==wheel['digests']['sha256'];file=home/wheel['filename'];file.write_bytes(raw);members={}
            with zipfile.ZipFile(file) as z:
                assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
                for name in z.namelist():
                    assert name and not name.startswith('/') and '..' not in Path(name).parts and '\\' not in name
                    if name.endswith('/'):continue
                    assert name.endswith(('.py','METADATA','WHEEL','RECORD','top_level.txt','entry_points.txt','LICENSE','LICENSE.txt')) or '.dist-info/licenses/' in name,'Review unexpected package member: '+name
                    target=home/'wheel-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name));members[name]=pin(target)
            tag_url='https://api.github.com/repos/dpeerlab/Palantir/git/ref/tags/v'+version;tag_raw=fetch(tag_url,1048576);(home/'git-tag.json').write_bytes(tag_raw);ref=json.loads(tag_raw)['object']
            if ref['type']=='tag':tag=fetch(ref['url'],1048576);(home/'git-tag-object.json').write_bytes(tag);ref=json.loads(tag)['object']
            assert ref['type']=='commit' and len(ref['sha'])==40;commit=ref['sha'];tree_raw=fetch('https://api.github.com/repos/dpeerlab/Palantir/git/trees/'+commit+'?recursive=1',8388608);(home/'git-tree.json').write_bytes(tree_raw);tree=json.loads(tree_raw);assert not tree.get('truncated');source_members={}
            for item in tree['tree']:
                name=item['path']
                if item['type']!='blob' or not(name.startswith('src/palantir/') or name.startswith('palantir/') or name in ['LICENSE','LICENSE.txt','README.md','pyproject.toml','setup.py','requirements.txt']):continue
                assert item['mode'] in ['100644','100755'] and item['size']<=4194304;data=fetch('https://raw.githubusercontent.com/dpeerlab/Palantir/'+commit+'/'+name,4194304);assert len(data)==item['size'] and hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()==item['sha'];target=home/'git-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);source_members[name]=dict(**pin(target),git_blob=item['sha'],original_git_mode=item['mode'])
            licenses=[(home/'git-source'/n).read_text(encoding='utf8') for n in source_members if n in ['LICENSE','LICENSE.txt']];assert len(licenses)==1 and 'Permission is hereby granted, free of charge' in licenses[0] and 'MIT' in licenses[0];records[version]=dict(version=version,revision=commit,official_pypi_metadata=pin(home/'official-pypi.json'),wheel=dict(filename=wheel['filename'],url=wheel['url'],**pin(file)),wheel_members=members,git_source_members=source_members)
        save(out/'SOURCE.json',dict(schema_version=1,status='PASS_EXACT_OFFICIAL_PALANTIR_SOURCE_ONLY',versions=records,current_version=latest,package_role='Bioinformatics continuous cell-state/fate inference, dpeerlab/Palantir; unrelated Palantir enterprise products are excluded.',paper_versions_unreported=True,namespace_patched=False,software_installed=False,implementation_credit=False,whole_goal_complete=False));state.update(status='PASS_EXACT_OFFICIAL_PALANTIR_SOURCE_ONLY',source=pin(out/'SOURCE.json'),versions=versions,current_version=latest,software_installed=False,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('versions'),state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact upstream intake checks')
    raise SystemExit(main())
