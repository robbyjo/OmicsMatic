from pathlib import Path,PurePosixPath
import concurrent.futures,hashlib,json,stat,traceback,urllib.request,zipfile
R=Path('D:/projects/JLinAlg');G=R/'build/gs-1';B=G/'build/gsea-high-impact-1';O=B/'source-intake-1';O.mkdir(exist_ok=False)
metadata=json.loads((B/'upstream-metadata-1/tags.json').read_bytes())
tags={t['name']:t['commit']['sha'] for t in metadata}
def enc(v):return (json.dumps(v,indent=2)+'\n').encode()
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def task(version):
    folder=O/version;folder.mkdir();commit=tags['v'+version];url='https://codeload.github.com/GSEA-MSigDB/gsea-desktop/zip/'+commit
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-GSEA-BSD-source-intake'})
    archive=folder/'original-source.zip'
    with urllib.request.urlopen(request,timeout=60) as stream,archive.open('xb') as output:
        size=0
        while chunk:=stream.read(1024*1024):
            size+=len(chunk);assert size<=400000000,'Source archive exceeds fixed intake capacity';output.write(chunk)
    source=folder/'source';source.mkdir();members={}
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(set(z.namelist())) and len(z.namelist())<=50000
        assert sum(i.file_size for i in z.infolist())<=1000000000
        prefixes={PurePosixPath(i.filename).parts[0] for i in z.infolist()};assert len(prefixes)==1
        for info in z.infolist():
            p=PurePosixPath(info.filename);assert not p.is_absolute() and all(v not in ['..','.'] for v in p.parts) and '\\' not in info.filename and ':' not in info.filename
            assert not stat.S_ISLNK(info.external_attr>>16)
            if info.is_dir():continue
            name=PurePosixPath(*p.parts[1:]).as_posix();target=source/name;target.parent.mkdir(parents=True,exist_ok=True)
            raw=z.read(info);target.write_bytes(raw);members[name]=pin(raw)
    assert all(name in members for name in ['LICENSE.txt','LICENSE-3RD-PARTY.txt','build.gradle','gradle.properties','gradle/wrapper/gradle-wrapper.properties'])
    for name in ['LICENSE.txt','LICENSE-3RD-PARTY.txt','gradle.properties','gradle/wrapper/gradle-wrapper.properties']:
        (folder/Path(name).name).write_bytes((source/name).read_bytes())
    record=dict(status='PASS_ORIGINAL_GSEA_TAG_SOURCE_AND_BUNDLED_DEPENDENCY_INTAKE',requested_tag='v'+version,commit=commit,url=url,
        archive=pin(archive.read_bytes()),files=members,source_files=len(members),source_modified=False,original_runtime_built=False,
        MSigDB_collections_downloaded=False,registration_submitted=False)
    (folder/'COMPLETED.json').write_bytes(enc(record))
    print('PASS original source',version,commit,'files',len(members),'archive bytes',archive.stat().st_size,flush=True)
    return version,record
try:
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['4.4.0','4.3.2']))
    (O/'COMPLETED.json').write_bytes(enc(dict(status='PASS_TWO_ORIGINAL_GSEA_SOURCE_TAG_INTAKES',versions={v:dict(commit=r['commit'],source_files=r['source_files'],archive=r['archive']) for v,r in results.items()},source_modified=False)))
except Exception as error:
    (O/'FAILURE.json').write_bytes(enc(dict(error=repr(error),detail=traceback.format_exc())));raise
