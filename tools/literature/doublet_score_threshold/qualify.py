#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the independent Java cutoff selector with literal R comparisons."""
from pathlib import Path
import argparse,hashlib,json,os,random,subprocess,sys,traceback

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
JAVA=ROOT/'src/main/java/org/jlinalg/singlecell/DoubletScoreThreshold.java'
ACTION=('This component classifies supplied values only; it calculates no HICAT score and adds no second family credit. '
 'A score equal to the cutoff is retained; missing comparison is UNKNOWN. Original values and row order remain unchanged. '
 'Missing/forbidden Java/R/jsonlite requirements SKIP3 skip qualification; FAIL2 admits no qualification. '
 'Retain complete records, correct the named cause and retry NEW writable paths. No installation or download occurs.')

def pin(path):
    data=Path(path).read_bytes();return dict(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())

def save(path,value):
    with Path(path).open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--rscript',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--javac',default='javac');parser.add_argument('--java',default='java')
    args=parser.parse_args();out=args.out.absolute();owned=False
    try:
        if out!=out.resolve():raise ValueError('Use a canonical NEW output path')
        for protected in [HERE,JAVA,args.rscript.resolve()]:
            if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected source/runtime')
        out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf-8')
        sources={str(p.relative_to(ROOT)):pin(p) for p in [JAVA,*sorted(HERE.glob('*.*'))] if p.is_file()}
        save(out/'SOURCES.json',sources)
        bits=['fff0000000000000','ffefffffffffffff','bff0000000000000','8000000000000001','8000000000000000',
          '0000000000000000','0000000000000001','3fd3333333333332','3fd3333333333333','3fd3333333333334',
          '3ff0000000000000','7fefffffffffffff','7ff0000000000000','7ff00000000007a2','7ff8000000000000',
          '7ff0000000000001','fff8000000001234']
        rng=random.Random(20261115)
        save(out/'inputs.json',dict(schema='ieee754-literal-strict-threshold-fixtures-v1',
          value_bits=bits+[format(rng.getrandbits(64),'016x') for _ in range(1024)],cutoff_bits=bits,
          random_generator='Python random.Random(20261115).getrandbits(64)',HICAT_scoring_source_used=False))
        prefix=args.rscript.resolve().parents[1]
        env={k:v for k,v in os.environ.items() if k not in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE','LD_PRELOAD','LD_LIBRARY_PATH']}
        for name in ['home','tmp','classes']:(out/name).mkdir()
        env.update(HOME=str(out/'home'),TMPDIR=str(out/'tmp'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
          R_LIBS_SITE=str(prefix/'lib/R/library'),LD_LIBRARY_PATH=str(prefix/'lib'),OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1')
        def run(label,command):
            with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
                child=subprocess.run(list(map(str,command)),cwd=out,env=env,stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=300)
            save(out/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=child.returncode))
            if child.returncode:raise ValueError('Qualification step failed; inspect complete logs: '+label)
        run('R-oracle',[args.rscript,'--vanilla',HERE/'MakeOracle.R',out/'inputs.json',out/'oracle'])
        run('compile',[args.javac,'--release','17','-d',out/'classes',JAVA,HERE/'ThresholdHarness.java'])
        run('compare',[args.java,'-cp',out/'classes','ThresholdHarness',out/'oracle/classifications.tsv',out/'oracle/selections.tsv'])
        for name,identity in sources.items():
            if pin(ROOT/name)!=identity:raise ValueError('Source changed: '+name)
        result=dict(status='PASS_JAVA_STRICT_THRESHOLD_AGAINST_LITERAL_R_ORACLE',
          oracle=json.loads((out/'oracle/ORACLE.json').read_bytes()),java_comparison=(out/'compare.stdout').read_text(),
          sources=sources,native_scoring_calculated=False,new_family_credit=0,whole_goal_complete=False)
        save(out/'COMPLETED.json',result);print(json.dumps(result,indent=2));return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_THRESHOLD_QUALIFICATION_UNAVAILABLE' if code==3 else 'FAIL2_THRESHOLD_QUALIFICATION_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        return code

if __name__=='__main__':raise SystemExit(main())
