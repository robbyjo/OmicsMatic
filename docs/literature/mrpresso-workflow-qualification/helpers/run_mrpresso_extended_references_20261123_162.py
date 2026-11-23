from pathlib import Path
import json,os,subprocess
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'extended-reference-windows-1';D.mkdir()
source=T/'tools/literature/mrpresso_workflow/extended_reference.R';frozen=D/'generator.R';frozen.write_bytes(source.read_bytes());out=D/'outputs';out.mkdir()
library=B/'runtime-install-windows-1/library';original=B/'upstream-intake-1/source/R/mr_presso.R'
assert pin(original)['sha256']=='fd3c83f4d591b43a1355a02a69df2257ae47c10efad5ae85b17591c4d7ef808b'
args=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(frozen),str(library),str(out),str(original)]
env=dict(os.environ,R_LIBS_USER=str(library),R_LIBS_SITE='',LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
with (D/'stdout').open('xb') as stdout,(D/'stderr').open('xb') as stderr:
 process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
 save(D/'PROCESS.json',dict(pid=process.pid,command=args,generator=pin(frozen),original_source=pin(original)));code=process.wait()
assert pin(source)==pin(frozen),'Reference generator changed during execution'
save(D/'PROCESS-RESULT.json',dict(exit_code=code,producer='Unchanged original MRPRESSO::mr_presso; private package1.0; R4.6.1',
 files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()},new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(exit_code=code,output=str(out))),flush=True)
assert code==0,(D/'stderr').read_text(errors='replace')[-2500:]
