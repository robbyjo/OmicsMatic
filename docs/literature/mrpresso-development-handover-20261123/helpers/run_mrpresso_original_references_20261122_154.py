from pathlib import Path
import hashlib,json,os,subprocess,sys
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'original-reference-windows-1';D.mkdir()
r=T/'tools/literature/mrpresso_workflow/original_reference.R';frozen=D/'generator.R';frozen.write_bytes(r.read_bytes());out=D/'outputs';out.mkdir()
library=B/'runtime-install-windows-1/library';original=B/'upstream-intake-1/source/R/mr_presso.R'
args=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(frozen),str(library),str(out),str(original)]
env=dict(os.environ,R_LIBS_USER=str(library),R_LIBS_SITE='',LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
with (D/'stdout').open('xb') as stdout,(D/'stderr').open('xb') as stderr:
    process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
    save(D/'PROCESS.json',dict(pid=process.pid,command=args,generator=pin(frozen),original_source=pin(original)));code=process.wait()
save(D/'PROCESS-RESULT.json',dict(exit_code=code,producer='Original MRPRESSO::mr_presso in private source-checked package1.0',
    files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()},new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(exit_code=code,output=str(out))),flush=True);assert code==0,(D/'stderr').read_text(errors='replace')[-2500:]
