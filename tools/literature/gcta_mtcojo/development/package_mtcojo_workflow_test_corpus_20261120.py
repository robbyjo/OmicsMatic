from pathlib import Path
import hashlib,json,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1';O=B/'workflow-test-corpus-1';O.mkdir(exist_ok=False);A=B/'prototype-workflow-1';paths={};blobs={}
for host in ['windows','linux']:
    for row in json.loads((A/host/'RESULTS.json').read_bytes()):
        assert row['exit_code']==row['verify_exit']==0 and row['all_original_table_tokens_equal'];root=A/host/row['name'];seal=json.loads((root/'capture-files.json').read_bytes())
        observed={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()}-{'capture-files.json'};assert observed==set(seal['files'])
        for name in sorted(observed|{'capture-files.json'}):
            file=root/name;raw=file.read_bytes();sha=hashlib.sha256(raw).hexdigest()
            if name!='capture-files.json':assert seal['files'][name]==sha
            paths[host+'/'+row['name']+'/'+name]=dict(bytes=len(raw),sha256=sha);blobs.setdefault(sha,raw)
dest=T/'src/test/resources/org/jlinalg/cli/mtcojo1941-workflow-captures.zip';dest.parent.mkdir(parents=True,exist_ok=True)
metadata=dict(schema_version=1,source_profile='GCTA-1.94.1',source_commit='6da64e9d8be838d01c34efb974e69c72e861f4c2',storage='Exact raw synthetic capture files mapped to blobs by SHA256; no model or input rewrite',source_owner=str(B/'compile-1'),normal_producer_claimed=False,participant_data=False,cases=[host+'/'+row['name'] for host in ['windows','linux'] for row in json.loads((A/host/'RESULTS.json').read_bytes())],files=paths)
with zipfile.ZipFile(dest,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    archive.writestr('FILES.json',json.dumps(metadata,indent=2)+'\n')
    for sha,raw in sorted(blobs.items()):archive.writestr('blobs/'+sha,raw)
(O/'CORPUS.json').write_text(json.dumps(dict(resource=dict(bytes=dest.stat().st_size,sha256=hashlib.sha256(dest.read_bytes()).hexdigest()),logical_files=len(paths),blobs=len(blobs),cases=8),indent=2)+'\n',encoding='utf-8');print('Saved eight mtCOJO complete test captures',dest.stat().st_size,'bytes;',len(paths),'logical files;',len(blobs),'blobs',flush=True)
