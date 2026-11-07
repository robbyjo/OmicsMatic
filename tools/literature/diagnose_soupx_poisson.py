# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect retained original Poisson GLMs and preserve analytical-versus-iterative differences."""
import argparse,gzip,json,os,subprocess,math
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();script=ROOT/'tools/literature/diagnose_soupx_poisson.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script]};chains={};libraries=[]
    for version,reference_attempt,controls_attempt in [('1.6.2',1,2),('1.3.0',2,3)]:
        ref=base/f'reference-{version}-{reference_attempt}';controls=base/f'ambient-controls-{version}-{controls_attempt}';chain=[ref/'R-lib',*map(Path,json.loads((ref/'STARTED.json').read_bytes())['libraries'])];chains[version]=(chain,controls);libraries.extend(chain)
        for name in ['manual-automatic-mask-cell-only','manual-automatic-mask-supplied-clusters','manual-supplied-mask']:
            path=controls/name/'original-return.rds';pins[path.relative_to(ROOT).as_posix()]=pin(path)
    before=inventory(list(dict.fromkeys(libraries)));save(out/'LIBRARIES-BEFORE.json',before)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    results={}
    for version,(chain,controls) in chains.items():
        folder=out/version;folder.mkdir();command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(folder),str(controls),version,*map(str,chain)];r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,chain)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1'),stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr);save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;report=json.loads((folder/'diagnosis.json').read_bytes());assert report['status']=='diagnosed-preserved-original-global-Poisson-objects-without-refitting'
        for row in report['cases'].values():
            observed=math.fsum(map(float,row['complete_y']));exposure=math.fsum(math.exp(float(v)) for v in row['complete_log_exposure']);assert observed==float(row['observed']) and abs(exposure-float(row['exposure']))<=4*math.ulp(exposure);assert abs(observed/exposure-float(row['analytical_rho']))<=4*math.ulp(observed/exposure);assert float(row['original_rho'])!=float(row['analytical_rho']) and row['original_converged'] and row['original_object_not_modified']
        results[version]=report
    after=inventory(list(dict.fromkeys(libraries)));save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='diagnosed-original-global-Poisson-iteration-discrepancy-with-independent-score-audit',source_pins=pins,versions=results,original_fit_objects_preserved=True,preexisting_libraries_unchanged=True,implementation_credit=False,whole_goal_complete=False));target=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-ambient/poisson-diagnosis-v1.json.gz';assert not target.exists();target.write_bytes(gzip.compress((json.dumps(results,indent=2)+'\n').encode('utf8'),mtime=0));print(json.dumps({v:{k:row['original_minus_analytical'] for k,row in r['cases'].items()} for v,r in results.items()}))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/score checks')
    main()
