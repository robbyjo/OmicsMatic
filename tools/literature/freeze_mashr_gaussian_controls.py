#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze a complete original mashr capture; preserve numerical departures explicitly."""
import argparse
from decimal import Decimal, localcontext
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/mashr-runtime'
TARGET = ROOT / 'src/test/resources/org/jlinalg/shrinkage/mashr-original/original-controls.zip'


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def boundary_oracle():
    # U=[[1,1],[1,1]], SE=[1e-6,2e-6], Bhat=[1,2] (or its negative).
    # det(U+diag(SE^2))=5a+4a^2, quadratic=(1+8a)/(5a+4a^2), a=1e-12.
    # Decimal precision is deliberately independent of the Java/R decompositions.
    with localcontext() as context:
        context.prec = 80
        a = Decimal('1e-12')
        pi = Decimal('3.141592653589793238462643383279502884197169399375105820974944592307816406286208998628')
        determinant = 5*a+4*a*a
        quadratic = (1+8*a)/determinant
        loglik = -(2*(2*pi).ln()+determinant.ln()+quadratic)/2
        return dict(precision=80, determinant=str(determinant), quadratic=str(quadratic), log_density=str(loglik))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source',required=True,type=Path)
    parser.add_argument('--out',required=True,type=Path)
    args=parser.parse_args()
    source,out=args.source.resolve(),args.out.resolve()
    if not source.is_relative_to(BASE.resolve()) or not out.is_relative_to(BASE.resolve()) or out==BASE.resolve() or out.exists() or TARGET.exists():
        raise ValueError('Require an existing private capture, NEW private output, and absent retained ZIP')
    record=json.loads((source/'record.json').read_text(encoding='utf-8'))
    assert record['status']=='captured-original-gaussian-controls' and record['cases']==43 and record['rows']==346
    expected=set(record['files'])|{'record.json','WARNINGS.md'}
    assert {p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}==expected
    for name,expected_pin in record['files'].items():
        path=PurePosixPath(name)
        assert name==path.as_posix() and not path.is_absolute() and '..' not in path.parts
        assert pin(source/name)==expected_pin and not (source/name).is_symlink()
    failed=[r for r in record['outcomes'] if r['status']!='COMPLETED']
    assert failed==[dict(case='explicit-large-SE-'+str(n),algorithm='R',status='FAILED') for n in (2,3,5)]
    shutil.copytree(source,out)
    oracle=boundary_oracle()
    (out/'boundary-oracle.tsv').write_text('log_density\t'+oracle['log_density']+'\n',encoding='utf-8')
    (out/'boundary-oracle.json').write_text(json.dumps(oracle,indent=2)+'\n',encoding='utf-8')
    shutil.copyfile(Path(__file__),out/'freeze-collector.py')
    intake=BASE/'source-intake-1'
    shutil.copyfile(intake/'cran-source.tar.gz',out/'mashr-0.2.79-original-source.tar.gz')
    shutil.copyfile(intake/'github/LICENSE.md',out/'mashr-BSD-NOTICE.txt')
    (out/'README.txt').write_text('Original mashr0.2.79 Gaussian fixed-mixture controls, 43 models / 346 complete feature rows.\n'
        'Contains both original algorithms, every input/output cell, original RDS fits, commands, logs, package census and original CRAN source.\n'
        'Three R calls with explicitly supplied SE1e10 fail as computationally singular; preserve those outcomes.\n'
        'Both algorithms depart from the 80-digit analytic component log density in boundary-small-SE; this is explicitly NOT source equivalence.\n'
        'No upstream binary, patient data, public user-analysis workflow, learned covariance certificate or whole-paper reproduction.\n'
        'Required action: retain failure/warning files and respect native numerical admission; never infer missing directions or treat this as full mashr integration.\n',encoding='utf-8')
    files={p.relative_to(out).as_posix():pin(p) for p in sorted(out.rglob('*')) if p.is_file()}
    (out/'foundation-reference.json').write_text(json.dumps(dict(schema_version=1,status='retained-original-gaussian-reference-with-explicit-boundaries',
       capture_record=pin(out/'record.json'),cases=43,rows=346,original_completed_calls=83,original_failed_calls=3,
       original_likelihood_boundary_cases=1,biological_accuracy_certified=False,user_analysis_admitted=False,files=files),indent=2)+'\n',encoding='utf-8')
    TARGET.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(TARGET,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(out.rglob('*')):
            if path.is_file():
                item=zipfile.ZipInfo(path.relative_to(out).as_posix(),date_time=(1980,1,1,0,0,0))
                item.create_system=3;item.external_attr=0o100644<<16;item.compress_type=zipfile.ZIP_DEFLATED
                archive.writestr(item,path.read_bytes())
    print(json.dumps(dict(archive=TARGET.relative_to(ROOT).as_posix(),**pin(TARGET),oracle=oracle),indent=2))


if __name__=='__main__':
    main()
