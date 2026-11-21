"""Run complete synthetic calculateLW controls in the exact installed original namespace."""
from pathlib import Path
import argparse,json,subprocess,time
from setup import ACTION,ROOT,linux,pin,require,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    runtime=args.runtime.resolve();record=json.loads((runtime/'RESULT.json').read_bytes())
    require(record['status']=='READY_EXACT_ORIGINAL_MONOCLE100_NAMESPACE_NOT_ANALYSIS' and record['runtime_admitted'] and record['preexisting_protected_files_unchanged'],'Use a complete exact original runtime')
    require(pin(runtime/'READINESS.json')==record['readiness'],'Changed readiness receipt')
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW build output');out.mkdir(parents=True,exist_ok=False)
    prefix=Path(str(next(iter(record['base_pins'])))).parent.parent
    # The lock receipt is the first base pin; the actual prefix is explicitly retained by setup.
    rscript=record['rscript'];prefix_linux=rscript.rsplit('/bin/Rscript',1)[0]
    environment=['/usr/bin/env','PATH='+prefix_linux+'/bin:/usr/bin:/bin','R_HOME='+prefix_linux+'/lib/R','LD_LIBRARY_PATH='+prefix_linux+'/lib','R_ENVIRON_USER=/dev/null','R_PROFILE_USER=/dev/null','R_LIBS_SITE=','R_LIBS_USER='+record['libraries'][0],'OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','MKL_NUM_THREADS=1','LANG=C','LC_ALL=C']
    for name in ['home','tmp','cache']:(out/name).mkdir()
    environment+=['HOME='+linux(out/'home'),'TMPDIR='+linux(out/'tmp'),'R_USER_CACHE_DIR='+linux(out/'cache')]
    source=Path(__file__).with_name('probe_weights.R')
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--user','root','--exec','/usr/bin/timeout','--signal=TERM','--kill-after=15','600',*environment,rscript,'--vanilla',linux(source),linux(out),*record['libraries'],record['source_root']]
    started=time.time();save(out/'STARTED.json',dict(command=command,probe_source=pin(source),runtime_receipt=pin(runtime/'RESULT.json'),started_unix=started))
    with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=660)
    save(out/'PROCESS.json',dict(command=command,exit_code=process.returncode,elapsed_seconds=time.time()-started))
    if process.returncode:
        save(out/'FAILURE.json',dict(status='FAIL2_ORIGINAL_CONTROL_NOT_COMPLETED',complete_result=False,required_action=ACTION));print((out/'stderr').read_text(errors='replace')[-2500:]);return 2
    result=json.loads((out/'RESULT.json').read_bytes());require(result['status']=='COMPLETED_ORIGINAL_MONOCLE100_WEIGHT_SOURCE_CONTROLS','Different control completion');print(json.dumps(result));return 0

if __name__=='__main__':raise SystemExit(main())
