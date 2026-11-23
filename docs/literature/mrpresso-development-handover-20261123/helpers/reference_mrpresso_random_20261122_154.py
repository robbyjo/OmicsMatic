from pathlib import Path
import hashlib,json,os,subprocess,sys,zipfile
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'random-reference-windows-1';D.mkdir();out=D/'outputs';out.mkdir()
source=T/'tools/literature/mrpresso_workflow/random_reference.R';frozen=D/'generator.R';frozen.write_bytes(source.read_bytes())
args=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(frozen),str(out)]
process=subprocess.run(args,cwd=T,capture_output=True,timeout=30)
(D/'stdout').write_bytes(process.stdout);(D/'stderr').write_bytes(process.stderr);assert process.returncode==0,process.stderr
files={p.name:pin(p) for p in sorted(out.iterdir()) if p.is_file()};assert len([n for n in files if n.endswith('.tsv')])==12
save(D/'RESULT.json',dict(status='ORIGINAL_R461_MT_INVERSION_ROUNDING_REJECTION_REFERENCES',cases=12,files=files,generator=pin(frozen),original_source_patched=False))
dest=T/'src/test/resources/org/jlinalg/mrpresso';dest.mkdir(parents=True)
with zipfile.ZipFile(dest/'r461-random-reference.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(out.iterdir()):archive.write(path,path.name)
    archive.write(D/'RESULT.json','PROVENANCE.json');archive.write(frozen,'generator.R')
print(json.dumps(dict(status='GENERATED_TWELVE_ORIGINAL_R_STREAM_REFERENCES',fixture=pin(dest/'r461-random-reference.zip'))),flush=True)
