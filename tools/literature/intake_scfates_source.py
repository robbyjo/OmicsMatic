# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Download official scFates/SimplePPT wheels and immutable source tags without installing or executing them."""
import argparse,email,hashlib,json,stat,sys,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

REPOSITORIES={'scfates':'LouisFaure/scFates','simpleppt':'LouisFaure/simpleppt'}
def fetch(url,maximum):
 allowed=['https://pypi.org/','https://files.pythonhosted.org/']+[prefix+repo+'/' for repo in REPOSITORIES.values() for prefix in ['https://api.github.com/repos/','https://raw.githubusercontent.com/']];assert any(url.startswith(p) for p in allowed)
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review','Accept':'application/vnd.github+json' if 'api.github.com' in url else '*/*'}),timeout=60) as response:
  raw=response.read(maximum+1);assert len(raw)<=maximum;return raw

def safe(name):
 p=PurePosixPath(name);assert name and not p.is_absolute() and '..' not in p.parts and '\\' not in name and ':' not in name and not any(ord(c)<32 for c in name);return p

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_OFFICIAL_SOURCE_INTAKE',software_installed=False,source_executed=False,implementation_credit=False,whole_goal_complete=False)
 try:
  records={}
  for package,repo in REPOSITORIES.items():
   home=out/package;home.mkdir();raw=fetch('https://pypi.org/pypi/'+package+'/json',8388608);(home/'official-current-pypi.json').write_bytes(raw);current=json.loads(raw);version=current['info']['version'];assert current['info']['name'].lower()==package and repo.lower() in json.dumps(current['info']['project_urls']).lower();raw=fetch('https://pypi.org/pypi/'+package+'/'+version+'/json',8388608);(home/'official-version-pypi.json').write_bytes(raw);published=json.loads(raw);wheels=[v for v in published['urls'] if v['filename'].endswith('-py3-none-any.whl') and not v['yanked']];assert len(wheels)==1;wheel=wheels[0];raw=fetch(wheel['url'],33554432);assert len(raw)==wheel['size'] and hashlib.sha256(raw).hexdigest()==wheel['digests']['sha256'];file=home/wheel['filename'];file.write_bytes(raw);members={};folded=set();total=0
   with zipfile.ZipFile(file) as z:
    assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
    for item in z.infolist():
     name=item.filename;safe(name);mode=item.external_attr>>16;assert not stat.S_ISLNK(mode)
     if item.is_dir():continue
     assert name.casefold() not in folded;folded.add(name.casefold());assert item.file_size<=16777216 and total+item.file_size<=134217728;total+=item.file_size;target=home/'wheel-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(z.read(name));members[name]=pin(target)
   metadata_names=[n for n in members if n.endswith('.dist-info/METADATA')];assert len(metadata_names)==1;metadata=email.message_from_bytes((home/'wheel-source'/metadata_names[0]).read_bytes());assert metadata['Name'].lower()==package and metadata['Version']==version;licenses=[n for n in members if 'license' in PurePosixPath(n).name.lower() or 'copying' in PurePosixPath(n).name.lower()];assert licenses,'Original distribution lacks a clear license member; review before runtime preparation'
   tags_raw=fetch('https://api.github.com/repos/'+repo+'/git/refs/tags',8388608);(home/'git-tags.json').write_bytes(tags_raw);tags=json.loads(tags_raw);tag=[t for t in tags if t['ref'].split('/')[-1] in [version,'v'+version]];assert len(tag)==1,'Resolve ambiguous/missing current release tag explicitly';ref=tag[0]['object']
   if ref['type']=='tag':raw=fetch(ref['url'],1048576);(home/'git-tag-object.json').write_bytes(raw);ref=json.loads(raw)['object']
   assert ref['type']=='commit' and len(ref['sha'])==40;commit=ref['sha'];tree_raw=fetch('https://api.github.com/repos/'+repo+'/git/trees/'+commit+'?recursive=1',8388608);(home/'git-tree.json').write_bytes(tree_raw);tree=json.loads(tree_raw);assert not tree.get('truncated');source={}
   for item in tree['tree']:
    name=item['path'];safe(name)
    if item['type']!='blob' or not (name.lower().startswith((package+'/', 'src/'+package+'/')) or name in ['LICENSE','LICENSE.txt','LICENSE.md','COPYING','README.md','pyproject.toml','setup.py','setup.cfg','requirements.txt']):continue
    assert item['mode'] in ['100644','100755'] and item['size']<=16777216;raw=fetch('https://raw.githubusercontent.com/'+repo+'/'+commit+'/'+name,16777216);assert len(raw)==item['size'] and hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==item['sha'];target=home/'git-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);source[name]=dict(**pin(target),git_blob=item['sha'],original_git_mode=item['mode'])
   comparison={}
   for name,want in members.items():
    if not name.endswith('.py') or '.dist-info/' in name:continue
    candidates=[n for n in [name,'src/'+name] if n in source];comparison[name]=dict(status='identical-wheel-and-tag' if len(candidates)==1 and source[candidates[0]]['sha256']==want['sha256'] else 'requires-source-difference-review',git_candidates=candidates)
   records[package]=dict(version=version,repository='https://github.com/'+repo,revision=commit,official_metadata=pin(home/'official-version-pypi.json'),wheel=dict(filename=wheel['filename'],url=wheel['url'],**pin(file)),wheel_members=members,git_source_members=source,license_members=licenses,requires_python=metadata['Requires-Python'],requires_dist=metadata.get_all('Requires-Dist',[]),scientific_source_comparison=comparison)
  save(out/'SOURCE.json',dict(schema_version=1,status='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY',packages=records,paper_version_unreported=True,software_installed=False,source_executed=False,implementation_credit=False,whole_goal_complete=False));state.update(status='PASS_OFFICIAL_SCFATES_SIMPLEPPT_SOURCE_ONLY',source=pin(out/'SOURCE.json'),versions={n:r['version'] for n,r in records.items()},source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))})
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 save(out/'COMPLETED.json',state);print(state['status'],state.get('versions'),state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source/archive guards')
 raise SystemExit(main())
