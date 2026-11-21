"""Freeze complete original/Java pilot captures for R-free admission controls."""
from pathlib import Path
import hashlib,json,zipfile
ROOT=Path(__file__).resolve().parents[4]
B=ROOT/'build/monocle100-neighbors-qualification-1'
out=ROOT/'src/test/resources/org/jlinalg/cli/monocle100-neighbors.zip'
assert not out.exists();out.parent.mkdir(parents=True,exist_ok=True)
inventory={}
with zipfile.ZipFile(out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for engine in ['original','java']:
        root=B/('pilot-'+engine+'-1/capture');assert json.loads((root/'analysis.json').read_bytes())['weights_admitted'] is True
        for path in sorted(root.rglob('*')):
            if not path.is_file():continue
            assert not path.is_symlink();name=engine+'/'+path.relative_to(root).as_posix();raw=path.read_bytes()
            info=zipfile.ZipInfo(name,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw)
            inventory[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
(B/'FIXTURE.json').write_text(json.dumps(dict(quick_compiled_captures=True,scientific_test_admitted=False,cells_per_case=27,genes_per_case=3,arcs_per_case=74,files=inventory,archive=dict(bytes=out.stat().st_size,sha256=hashlib.sha256(out.read_bytes()).hexdigest())),indent=2)+'\n',encoding='utf-8',newline='\n')
print('Saved',len(inventory),'complete captured files for two explicit engines')
