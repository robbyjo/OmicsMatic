from pathlib import Path,PurePosixPath
import hashlib,io,json,re,subprocess,urllib.request,zipfile
R=Path('D:/projects/JLinAlg');O=R/'build/twosamplemr-historical-intake-20261121-1'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
tags=(O/'tags.stdout').read_bytes()
refs=dict((name,sha) for sha,name in (line.split() for line in tags.decode().splitlines()));results={}
for version in ['0.4.9','0.5.6','0.5.7','0.6.8']:
    directory=O/version;directory.mkdir(exist_ok=True)
    if (directory/'RESULT.json').exists():
        prior=json.loads((directory/'RESULT.json').read_bytes());assert prior['version']==version and prior['status']=='PINNED_EXACT_OFFICIAL_RELEASE_TAG_SOURCE_ONLY';results[version]=prior;continue
    assert not any(directory.iterdir()),'Inspect partial unrecorded source before retry'
    candidates=[name for name in ['v'+version,version] if 'refs/tags/'+name in refs]
    if not candidates:
        result=dict(status='NO_EXACT_OFFICIAL_TAG_FOUND_NOT_A_PROVEN_SOURCE_ABSENCE',version=version,tag_snapshot=pin(tags),next_action='Inspect official historical DESCRIPTION commits and source identity; do not substitute a neighboring edition or infer the paper execution commit');save(directory/'RESULT.json',result);results[version]=result;print(version,result['status'],flush=True);continue
    assert len({refs.get('refs/tags/'+name+'^{}',refs['refs/tags/'+name]) for name in candidates})==1,('Different commits share the reported version; audit all variants before choosing',candidates)
    tag=candidates[0];ref='refs/tags/'+tag;sha=refs.get(ref+'^{}',refs[ref]);url='https://codeload.github.com/MRCIEU/TwoSampleMR/zip/'+sha
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-edition-audit'})
    with urllib.request.urlopen(request,timeout=90) as response:raw=response.read(32000000)
    assert len(raw)<32000000;(directory/'source.zip').write_bytes(raw);selected={}
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and names
        prefix=names[0].split('/')[0]+'/'
        for entry in archive.infolist():
            assert entry.filename.startswith(prefix);name=entry.filename[len(prefix):]
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name and not (entry.external_attr>>16)&0o170000==0o120000
            if entry.is_dir():continue
            if name in ['DESCRIPTION','NAMESPACE','LICENSE','LICENSE.md','NEWS.md','.Rbuildignore'] or name.startswith('R/'):
                content=archive.read(entry);target=directory/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content);selected[name]=pin(content)
    desc=(directory/'source/DESCRIPTION').read_text(encoding='utf-8');assert re.search(r'^Version:\s*'+re.escape(version)+r'\s*$',desc,re.M),desc[:200]
    result=dict(status='PINNED_EXACT_OFFICIAL_RELEASE_TAG_SOURCE_ONLY',version=version,tag=tag,tag_object=refs[ref],tag_aliases=candidates,commit=sha,archive=dict(url=url,**pin(raw)),files=selected,paper_execution_commit_established=False,compiled_or_installed=False,new_catalog_credit=0,whole_goal_complete=False);save(directory/'RESULT.json',result);results[version]=result;print(version,tag,sha,'files',len(selected),flush=True)
save(O/'RESULT.json',dict(status='OFFICIAL_HISTORICAL_SOURCE_INTAKE_NOT_RUNTIME_QUALIFICATION',versions=results,new_catalog_credit=0,whole_goal_complete=False))
