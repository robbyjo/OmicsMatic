# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect complete original GENESIS matrix partition/R64 sum controls; explicit private runtime, no install or user-analysis admission."""
import argparse
import json
import os
import subprocess
import sys
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
BASE_PIN=dict(bytes=3568,sha256='d18a4a55f9a5db29edaea1b7231b0174cee4b0dba6d1b8734158e9504ad3d8ab')
ACTION='Missing/forbidden compatible prerequisites: SKIP dependent original-reference generation. Supply the exact permitted private Windows R4.6.1/GENESIS2.42.0 runtime and its unchanged closed library, prepared with setup_genesis_reference.py only where installation/downloads are allowed. Otherwise keep generation skipped. Analysis installs nothing. FAIL2 admits no reference: preserve the attempt, inspect its named source/process/input/integrity cause, correct it explicitly and retry NEW paths. Native Java saved verification is separate and requires no R; neither reference collection nor this component implements full PC-AiR, PC-Relate, association models or historical papers.'


def inventory(root):return {f.relative_to(root).as_posix():pin(f) for f in sorted(root.rglob('*')) if f.is_file()}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--setup',type=Path,default=BASE/'private-original-setup-1/COMPLETED.json');a=p.parse_args();out=a.out.resolve()
    if out.exists() or not out.is_relative_to(BASE):
        print('FAIL2: use a NEW output directory under build/literature-review/genesis-runtime; existing attempts stay intact. Required action: choose a new scoped path.',file=sys.stderr);return 2
    out.mkdir(parents=True);state=dict(status='SKIP3_ORIGINAL_GENESIS_REFERENCE_RUNTIME_UNAVAILABLE',required_action=ACTION,
        new_catalog_credit=False,whole_goal_complete=False,project_analysis_admitted=False,downloads_or_installation=False)
    library=home=None;protected={};before_home={};before_library={};code=3;started=False
    try:
        if sys.platform!='win32':raise FileNotFoundError('The original reference profile requires Windows R4.6.1; native Java verification remains portable')
        metadata=json.loads(a.setup.read_bytes());assert metadata['status']=='PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY' and metadata['version']=='2.42.0'
        library=Path(metadata['private_library']).resolve();rscript=Path(metadata['rscript']['path']).resolve();home=rscript.parents[1]
        assert pin(rscript)=={k:metadata['rscript'][k] for k in ['bytes','sha256']},'Exact original Rscript differs'
        if not library.is_dir():raise FileNotFoundError('Exact private GENESIS library is unavailable: '+str(library))
        before_library=inventory(library)
        missing=set(metadata['installed_files'])-set(before_library)
        if missing:raise FileNotFoundError('Required exact private GENESIS files are unavailable: '+', '.join(sorted(missing)[:5]))
        assert before_library==metadata['installed_files'],'Complete private GENESIS library differs'
        before_home=inventory(home);assert len(before_home)>3000
        fixture=ROOT/'src/test/resources/org/jlinalg/genetics/genesis-partition/declared-own-cases.rds';assert pin(fixture)==BASE_PIN
        worker=ROOT/'src/main/resources/org/jlinalg/omics/genesis/partition_reference.R';producer=pin(Path(__file__))
        protected={str(p):pin(p) for p in [Path(__file__).resolve(),worker,fixture,a.setup.resolve(),rscript]}
        inputs=out/'inputs';inputs.mkdir();(inputs/fixture.name).write_bytes(fixture.read_bytes());temp=out/'tmp';temp.mkdir()
        save(out/'runtime-profile.json',dict(schema_version=1,original_R_home=str(home),original_library=str(library),R_distribution_files=before_home,private_library_files=before_library,
            scope='Complete supplied roots before/after; system DLLs outside these roots are not independently closed. This is integrity, not producer authentication.'))
        def literal(value):return json.dumps(str(value).replace('\\','/'))
        script=out/'collect.R';script.write_text('private_library <- '+literal(library)+'\nout <- '+literal(out)+'\nprevious <- '+literal(inputs)+'\n'+worker.read_text(encoding='utf8'),encoding='utf8',newline='\n')
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp))
        command=[str(rscript),'--vanilla',str(script)];state['status']='FAIL2_ORIGINAL_GENESIS_REFERENCE';code=2;started=True
        with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:r=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=300)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original control process failed; inspect retained stderr'
        assert b'COMPLETE_ORIGINAL_GENESIS_PARTITION_AND_R64_SUM_CONTROLS' in (out/'stdout').read_bytes()
        import csv
        with (out/'original-case-dispositions.tsv').open(encoding='utf8',newline='') as stream:cases=list(csv.DictReader(stream,delimiter='\t'))
        with (out/'original-scalar-sums.tsv').open(encoding='utf8',newline='') as stream:sums=list(csv.DictReader(stream,delimiter='\t'))
        assert len(cases)==75 and len({r['case'] for r in cases})==75 and len(sums)==512
        assert sum(r['status']=='ORIGINAL_SUCCESS' for r in cases)==69 and sum(r['status']=='ORIGINAL_ERROR' for r in cases)==6
        assert sum(r['native_finite_profile']=='FALSE' for r in cases)==2
        state.update(status='PASS_ORIGINAL_GENESIS242_PARTITION_AND512_R64_REFERENCE_CONTROLS',producer=producer,
            source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),worker,fixture]},original_case_dispositions=cases,
            original_source_successes=69,original_source_failures=6,native_finite_profile_unsupported_cases=2,scalar_sum_controls=512,
            R_longdouble_significand_bits=64,original_outputs_unmodified=True);code=0
    except Exception as error:
        if started or not isinstance(error,FileNotFoundError):state['status']='FAIL2_ORIGINAL_GENESIS_REFERENCE';code=2
        state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n))==v for n,v in protected.items()),'Protected reference source/input changed'
            if before_home:assert inventory(home)==before_home,'Supplied R distribution changed'
            if before_library:assert inventory(library)==before_library,'Supplied private GENESIS library changed'
        except Exception as error:state.update(status='FAIL2_ORIGINAL_GENESIS_REFERENCE_RUNTIME_CHANGED',cause=repr(error));code=2
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+ACTION+'\n\nNamed cause: '+state.get('cause','None; inspect original per-case errors, warnings and identity/missingness flags even after a complete collection.')+'\n',encoding='utf8',newline='\n')
        state['files']={f.relative_to(out).as_posix():pin(f) for f in sorted(out.rglob('*')) if f.is_file()}
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:3000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact runtime, fixed-input, complete source population or mutation checks')
    raise SystemExit(main())
