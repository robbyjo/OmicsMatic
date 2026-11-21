from pathlib import Path
import hashlib,json,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/mr-4';D=T/'tools/literature/twosamplemr_clump/development';B=T/'build/twosamplemr-clump-adapter-1'
assert not B.exists();B.mkdir(parents=True)
pins={}
for src in (D/'java/org/jlinalg/cli').glob('*.java'):
    dst=T/'src/main/java/org/jlinalg/cli'/src.name;assert not dst.exists();shutil.copyfile(src,dst);pins[dst.relative_to(T).as_posix()]=hashlib.sha256(dst.read_bytes()).hexdigest()
src=D/'resources/twosamplemr-clump';dst=T/'src/main/resources/org/jlinalg/omics/twosamplemr-clump';assert not dst.exists();shutil.copytree(src,dst)
for p in dst.rglob('*'):
    if p.is_file():pins[p.relative_to(T).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
(B/'INITIAL-COPIES.json').write_text(json.dumps(pins,indent=2)+'\n',encoding='utf-8',newline='\n')
print('Copied drafts for implementation into',T)
