"""Capture exact original reference functions for the Monocle1.0.0 neighbor adapter."""
from pathlib import Path,PurePosixPath
import argparse,hashlib,io,json,re,tarfile,urllib.request
ROOT=Path(__file__).resolve().parents[3]

def require(ok,message):
    if not ok:raise ValueError(message)
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def unpack(raw,package):
    result={}
    with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as archive:
        for member in archive.getmembers():
            parts=PurePosixPath(member.name).parts
            require(parts and parts[0]==package and '..' not in parts and not PurePosixPath(member.name).is_absolute() and '\\' not in member.name,'Unsafe original archive path')
            if member.isdir():continue
            require(member.isfile() and not member.issym() and not member.islnk() and member.size<=16*1024*1024,'Unsupported original source member')
            name=PurePosixPath(*parts[1:]).as_posix();require(name not in result,'Duplicate source member');result[name]=archive.extractfile(member).read()
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--monocle-candidate',type=Path,required=True);parser.add_argument('--spdep-archive',type=Path,required=True);parser.add_argument('--fingerprints',type=Path);parser.add_argument('--allow-download',action='store_true');parser.add_argument('--write-resources',action='store_true');args=parser.parse_args()
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW build output');out.mkdir(parents=True,exist_ok=False)
    require(args.allow_download,'SKIP3 source intake requires permitted download selection')
    locked=json.loads((ROOT/'tools/literature/monocle100/source-profile.json').read_bytes());candidate=args.monocle_candidate.resolve()
    require(all(pin((candidate/'source'/name).read_bytes())==wanted for name,wanted in locked['source_members'].items()),'Changed original Monocle source')
    members={'monocle3':{name:(candidate/'source'/name).read_bytes() for name in locked['source_members']}};downloads={}
    for package,version in [('RANN','2.6.2'),('slam','0.1-55')]:
        attempts=[];raw=None
        for url in ['https://cran.r-project.org/src/contrib/Archive/'+package+'/'+package+'_'+version+'.tar.gz','https://cran.r-project.org/src/contrib/'+package+'_'+version+'.tar.gz']:
            try:
                request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-neighbor-source'})
                with urllib.request.urlopen(request,timeout=60) as response:value=response.read(8*1024*1024+1);resolved=response.url
                require(len(value)<=8*1024*1024,'Source archive capacity exceeded');tree=unpack(value,package)
                require(re.search(r'(?m)^Version:\s*'+re.escape(version)+r'\s*$',tree['DESCRIPTION'].decode()) is not None,'Different source version')
                raw=value;members[package]=tree;attempts.append(dict(url=url,resolved_url=resolved,archive=pin(raw)));break
            except Exception as error:attempts.append(dict(url=url,error=repr(error)))
        save(out/(package+'-FETCH.json'),attempts);require(raw is not None,'SKIP3 pinned source download unavailable: '+package)
        (out/(package+'_'+version+'.tar.gz')).write_bytes(raw);downloads[package]=dict(version=version,archive=pin(raw),members={name:pin(value) for name,value in members[package].items()})
    spdep=args.spdep_archive.read_bytes();provenance=json.loads((ROOT/'src/test/resources/org/jlinalg/spatial/regression-moran/PROVENANCE.json').read_bytes())['upstream_source']
    require(pin(spdep)=={key:provenance[key] for key in ['bytes','sha256']},'Changed pinned original spdep source');members['spdep']=unpack(spdep,'spdep');(out/'spdep_1.4-1.tar.gz').write_bytes(spdep)
    guards={'monocle3':['calculateLW','jaccard_coeff','new_cell_data_set'],'RANN':['nn2'],'spdep':['nb2listw','card'],'slam':['rowapply_simple_triplet_matrix']}
    selected={}
    for package,names in guards.items():
        for name in names:
            found=[path for path,raw in members[package].items() if path.startswith('R/') and re.search(rb'(?m)^'+re.escape(name.encode())+rb'\s*(?:<-|=)\s*function',raw)]
            require(len(found)==1,'Original reference definition is not unique: '+package+'::'+name)
            if package=='slam':
                (out/'private-slam-reference.R').write_bytes(members[package][found[0]])
            else:selected['reference/'+package+'/'+found[0]]=members[package][found[0]]
    fingerprints=json.loads(args.fingerprints.read_bytes()) if args.fingerprints is not None else {}
    profile=dict(schema_version=1,profile='monocle100-original-neighbors-r43-v1',monocle_commit=locked['commit'],R='4.3.3',platform='x86_64-conda-linux-gnu',packages={'monocle3':'1.0.0','RANN':'2.6.2','spdep':'1.4.1','slam':'0.1.55','Matrix':'1.6.5','igraph':'2.0.3','jsonlite':'2.0.0','digest':'0.6.37'},guards=guards,function_fingerprints=fingerprints,files={name:pin(raw) for name,raw in selected.items()},scope='Original calculateLW on a complete caller-prepared in-memory Monocle1.0.0 CDS; no graph learning, expression/covariate transformation or paper reproduction.',runtime_authentication=False)
    save(out/'PROFILE.json',profile);save(out/'DOWNLOADS.json',downloads)
    if args.write_resources:
        require(set(fingerprints)=={'slam'} and fingerprints['slam']['rowapply_simple_triplet_matrix']['original_body_formals_identical'],'Generate canonical fingerprints against original private SLAM source first')
        resources=ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors';resources.mkdir(parents=True,exist_ok=False)
        for name,raw in selected.items():target=resources/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
        save(resources/'PROFILE.json',profile)
        (resources/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
        (resources/'NOTICE.txt').write_text('Original Monocle3 1.0.0 commit004c096f3a3bdaf1e6709a65fb36f40ba775f1ab reference R files are MIT + file LICENSE,2019 Cole Trapnell. RANN2.6.2 is GPL>=3; spdep1.4-1 is GPL>=2. Original source/license archives and full notices accompany validation evidence. SLAM0.1-55 is GPL-2; its source is not redistributed here, and only canonical original-function fingerprints are packaged. Project workers/Java are GPL-3.0-or-later. References are parsed for selected function-body/formal checks; no author pipeline source is bundled. Declared versions and fingerprints establish the recorded runtime context, not producer or complete host authentication.\n',encoding='utf-8',newline='\n')
    print('Captured',len(selected),'reference source files for',sum(map(len,guards.values())),'original function guards');return 0

if __name__=='__main__':raise SystemExit(main())
