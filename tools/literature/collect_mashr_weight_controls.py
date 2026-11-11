#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original mashr/ashr optimizer fits on original component likelihoods."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile
from collect_mashr_gaussian_controls import BASE, ROOT, RSCRIPT, pin


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path)
    parser.add_argument('--runtime',required=True,type=Path)
    parser.add_argument('--gaussian-controls',required=True,type=Path)
    parser.add_argument('--optimizer-source',required=True,type=Path)
    args=parser.parse_args()
    out,runtime,controls=args.out.resolve(),args.runtime.resolve(),args.gaussian_controls.resolve()
    target=ROOT/'src/test/resources/org/jlinalg/shrinkage/mashr-original/weight-controls.zip'
    if not out.is_relative_to(BASE.resolve()) or out.exists() or target.exists():
        raise ValueError('Require NEW private output and absent retained weight-controls ZIP')
    reference=json.loads((controls/'foundation-reference.json').read_text())
    assert reference['status']=='retained-original-gaussian-reference-with-explicit-boundaries'
    assert json.loads((runtime/'COMPLETED.json').read_text())['status']=='COMPLETED_PRIVATE_REFERENCE_SETUP_ONLY'
    assert pin(RSCRIPT)['sha256']=='d829bcf7e9fa1d7e3e828c565c3cdbb1ed416f551f4fa6fd4dfcdf231e33e5e8'
    optimizer_source=args.optimizer_source.resolve()
    assert pin(optimizer_source)==dict(bytes=935117,sha256='7fa1957ed3cf0f1f20939b6ebf71fc04ab983ca77616f7b80ce9e9e6fad457c9')
    names=['shared-analytic']+['canonical-'+str(n)+'-alpha-'+str(alpha) for n in (2,3,5,9) for alpha in (0.,1.)]+['noncanonical-'+str(n) for n in (2,3,5,9)]
    out.mkdir(parents=True)
    try:
        shutil.copyfile(Path(__file__),out/'collector.py')
        shutil.copyfile(ROOT/'tools/literature/collect_mashr_gaussian_controls.py',out/'gaussian-collector-helper.py.txt')
        shutil.copyfile(optimizer_source,out/'ashr-2.2-63-original-source.tar.gz')
        shutil.copyfile(optimizer_source.parent/'receipt.json',out/'ashr-source-download-receipt.json')
        shutil.copyfile(optimizer_source.parent/'DESCRIPTION',out/'ashr-DESCRIPTION.txt')
        shutil.copyfile(ROOT/'LICENSE',out/'ashr-GPL-3.0.txt')
        shutil.copyfile(BASE/'source-intake-1/github/LICENSE.md',out/'mashr-BSD-NOTICE.txt')
        script=ROOT/'tools/literature/mashr-original-weight-controls.R'
        shutil.copyfile(script,out/'original-weight-controls.R')
        shutil.copyfile(runtime/'downloaded-packages.json',out/'reference-packages.json')
        shutil.copyfile(controls/'foundation-reference.json',out/'gaussian-reference.json')
        with (out/'cases.tsv').open('w',encoding='utf-8',newline='\n') as table:
            table.write('case\tsource\tprior\n')
            for name in names:
                source=controls/name/'Rcpp/component-loglik.tsv'
                assert pin(source)==reference['files'][name+'/Rcpp/component-loglik.tsv']
                for prior in ('uniform','nullbiased'):
                    case=name+'-'+prior;folder=out/case;folder.mkdir()
                    shutil.copyfile(source,folder/'component-loglik.tsv')
                    table.write(case+'\t'+name+'\t'+prior+'\n')
        command=[str(RSCRIPT),'--vanilla',str(out/'original-weight-controls.R'),str(runtime/'library'),str(out)]
        overrides=dict(LANG='C',LC_ALL='C',R_LIBS_USER=str(runtime/'library'),R_LIBS_SITE='',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        (out/'command.json').write_text(json.dumps(dict(argv=command,environment_overrides=overrides,timeout_seconds=600),indent=2)+'\n')
        with (out/'process.stdout').open('wb') as stdout,(out/'process.stderr').open('wb') as stderr:
            process=subprocess.run(command,cwd=out,env=dict(os.environ,**overrides),stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=600,check=False)
        (out/'process.json').write_text(json.dumps(dict(exit_code=process.returncode))+'\n')
        assert process.returncode==0,'Original weight collector failed; preserve process evidence'
        statuses={}
        for name in names:
            for prior in ('uniform','nullbiased'):
                for method in ('mixEM','mixSQP'):
                    case=name+'-'+prior
                    statuses[case+'/'+method]=(out/case/method/'status.txt').read_text().strip()
        assert all(s=='COMPLETED' for s in statuses.values()),statuses
        (out/'README.txt').write_text('Original mashr0.2.79 / ashr2.2-63 optimizer controls.\n'
          'All inputs are complete original Gaussian likelihood matrices, with uniform or nullbiased Dirichlet priors.\n'
          'Original mixEM uses SQUAREM acceleration; native plain EM is compared by concave objective and a dual-gap certificate, not iteration identity or nonidentified weights.\n'
          'No covariance learning, automatic training-population selection, patient data or public user-analysis workflow.\n'
          'Required action: retain source convergence values, independent dual gaps and any warnings; an iteration limit is never a convergence certificate.\n',encoding='utf-8')
        (out/'record.json').write_text(json.dumps(dict(schema_version=1,status='captured-original-mixture-weight-controls',
             cases=len(names)*2,original_calls=len(statuses),original_statuses=statuses,
             source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in (Path(__file__),script)},
             files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}),indent=2)+'\n')
        with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
            for path in sorted(out.rglob('*')):
                if path.is_file():
                    item=zipfile.ZipInfo(path.relative_to(out).as_posix(),date_time=(1980,1,1,0,0,0));item.create_system=3;item.external_attr=0o100644<<16;item.compress_type=zipfile.ZIP_DEFLATED
                    archive.writestr(item,path.read_bytes())
        print(json.dumps(dict(status='captured-original-mixture-weight-controls',cases=len(names)*2,original_calls=len(statuses),archive=target.relative_to(ROOT).as_posix(),**pin(target)),indent=2))
    except Exception as error:
        (out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_ORIGINAL_WEIGHT_COLLECTION',message=str(error)),indent=2)+'\n')
        (out/'WARNINGS.md').write_text('# FAIL2: incomplete original weight controls\n\n'+str(error)+'\n\nRequired action: preserve this attempt; inspect original errors and use a NEW directory after correction. No result is admitted.\n')
        raise


if __name__=='__main__':
    main()
