from pathlib import Path,PurePosixPath
import concurrent.futures,hashlib,json,urllib.parse,urllib.request
R=Path('D:/projects/JLinAlg');O=R/'build/twosamplemr-author-code-intake-20261121-1';O.mkdir(exist_ok=False)
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def get(url,cap=16000000):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-primary-source-audit','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(request,timeout=90) as response:
        raw=response.read(cap+1);assert len(raw)<=cap;return raw
def run(case):
    paper,repo=case;folder=O/paper/repo.split('/')[1];folder.mkdir(parents=True);api='https://api.github.com/repos/'+repo;metadata=json.loads(get(api));commit=json.loads(get(api+'/commits/'+metadata['default_branch']));sha=commit['sha'];tree=json.loads(get(api+'/git/trees/'+sha+'?recursive=1'));assert not tree.get('truncated');save(folder/'repository.json',metadata);save(folder/'commit.json',commit);save(folder/'tree.json',tree)
    files=[];total=0
    for item in tree['tree']:
        name=item['path'];path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
        if item['type']!='blob' or item.get('size',0)>2000000:continue
        if path.suffix.lower() not in {'.r','.rmd','.py','.sh','.md'} and path.name.lower() not in {'license','licence','copying'}:continue
        total+=item['size'];assert total<=20000000,'Inspect source-only capacity before additional downloads'
        url='https://raw.githubusercontent.com/'+repo+'/'+sha+'/'+urllib.parse.quote(name);raw=get(url,2000000);assert len(raw)==item['size'];target=folder/'files'/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);files.append(dict(path=name,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),git_blob=item['sha'],url=url))
    result=dict(status='READ_ONLY_AUTHOR_SOURCE_INTAKE_NO_EXECUTION_OR_REVIEW_CREDIT',paper=paper,repository=repo,commit=sha,commit_date=commit['commit']['committer']['date'],code_files=files,license_metadata=metadata.get('license'),execution_commit_for_paper_proven=False,public_redistribution_of_downloaded_source=False,new_catalog_credit=0);save(folder/'RESULT.json',result);print(paper,repo,sha,'source files',len(files),'bytes',total,flush=True);return repo,result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(run,[('P157','QingboWang/japan_covid_taskforce_multi_omics'),('P177','hhoulei/TEMR'),('P177','hhoulei/TEMR_Simul')]))
save(O/'RESULT.json',dict(status='PRIMARY_AUTHOR_CODE_PINNED_FOR_STATIC_ATTRIBUTION',results=results,new_catalog_credit=0,scientific_execution=False))
