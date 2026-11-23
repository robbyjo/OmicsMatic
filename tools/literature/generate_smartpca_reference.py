# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture synthetic unchanged original smartpca runs; no project workflow admission."""
from pathlib import Path
import argparse,hashlib,json,os,random,resource,subprocess,time,zipfile
from setup_smartpca_reference import COMMIT,pin,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--setup',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert not out.exists(),'Use NEW reference output';out.mkdir(parents=True)
    setup=json.loads((args.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_ORIGINAL_SMARTPCA721_PRIVATE_BUILD_ONLY' and setup['source_commit']==COMMIT
    binary=Path(setup['binary']);assert pin(binary)==setup['binary_identity']
    generator=Path(__file__).resolve();(out/'generator.py').write_bytes(generator.read_bytes());source_before=pin(generator)
    cases=[]
    def dataset(seed,missing=False,projection=False,empty_projection=False,filter_rows=False):
        rng=random.Random(seed);rows=[];n=16
        for s in range(96):
            row=[]
            for i in range(n):
                p=(0.13+0.67*((s*3+7)%17)/16) if i<6 else (0.15+0.65*((s*5+2)%19)/18)
                value=int(rng.random()<p)+int(rng.random()<p)
                if missing and rng.random()<(.04 if i<12 else .32):value=9
                row.append(value)
            rows.append(row)
        if filter_rows:
            rows[0]=[0]*n;rows[1]=[2]*n;rows[2]=[1]*n;rows[3]=[9]*n
            rows[4]=[1]+[0]*(n-1);rows[5]=[9]*5+[0,1,2,1,0,2,1,2,0,1,2]
        if empty_projection:
            for row in rows:row[-1]=9
        return rows,list(range(12 if projection else n))
    definitions=[
        ('complete-unprojected',dict()),('missing-unprojected',dict(missing=True)),
        ('orthogonal-reference-projection',dict(missing=True,projection=True)),
        ('least-squares-reference-projection',dict(missing=True,projection=True,least_squares=True)),
        ('least-squares-insufficient-sample',dict(missing=True,projection=True,least_squares=True,empty_projection=True)),
        ('pseudocount-normalization',dict(missing=True,projection=True,least_squares=True,alternate=False)),
        ('no-allele-normalization',dict(missing=True,projection=True,least_squares=True,use_norm=False)),
        ('allele-and-missing-filters',dict(missing=True,projection=True,least_squares=True,filter_rows=True,min_alleles=4,max_missing=2)),
        ('no-normalization-alt-disabled',dict(missing=True,projection=True,least_squares=True,use_norm=False,alternate=False)),
        ('shrinkage-original-only',dict(missing=True,projection=True,least_squares=True,shrink=True)),
        ('fst-only-original',dict(missing=True,fst_only=True)),
        ('zero-trace-original-error',dict(zero_trace=True))]
    for name,settings in definitions:
        folder=out/name;folder.mkdir();flags=dict(settings)
        rows,basis=dataset(86129,missing=flags.get('missing',False),projection=flags.get('projection',False),empty_projection=flags.get('empty_projection',False),filter_rows=flags.get('filter_rows',False))
        if flags.get('zero_trace'):rows=[[0]*16 for _ in range(96)]
        (folder/'input.geno').write_text(''.join(''.join(str(g) for g in row)+'\n' for row in rows),encoding='ascii')
        (folder/'input.snp').write_text(''.join(f'rs{s:04d} 1 {s*.001:.6f} {1000+s*100} A G\n' for s in range(len(rows))),encoding='ascii')
        populations=['REF_A' if i<6 else 'REF_B' if i<12 else 'PROJECTED' for i in range(16)]
        (folder/'input.ind').write_text(''.join(f'sample{i:03d} U {populations[i]}\n' for i in range(16)),encoding='ascii')
        parameters=dict(genotypename='input.geno',snpname='input.snp',indivname='input.ind',evecoutname='original.evec',evaloutname='original.eval',
            grmoutname='original.grm',numoutevec=3,numoutlieriter=0,usenorm='YES' if flags.get('use_norm',True) else 'NO',
            altnormstyle='YES' if flags.get('alternate',True) else 'NO',lsqproject='YES' if flags.get('least_squares') else 'NO',
            shrinkmode='YES' if flags.get('shrink') else 'NO',numthreads=1,minallelecnt=flags.get('min_alleles',1),maxmissing=flags.get('max_missing',9_999_999))
        if flags.get('projection'):
            (folder/'basis.poplist').write_text('REF_A\nREF_B\n',encoding='ascii');parameters['poplistname']='basis.poplist'
        if flags.get('fst_only'):parameters['fstonly']='YES'
        (folder/'parameters.txt').write_text(''.join(str(k)+': '+str(v)+'\n' for k,v in parameters.items()),encoding='ascii')
        env=dict(os.environ,OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',LC_ALL='C')
        def no_core():resource.setrlimit(resource.RLIMIT_CORE,(0,0))
        start=time.time()
        with (folder/'original.stdout').open('wb') as stdout,(folder/'original.stderr').open('wb') as stderr:
            process=subprocess.run([str(binary),'-p','parameters.txt'],cwd=folder,env=env,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,timeout=300,preexec_fn=no_core)
        row=dict(case=name,source_commit=COMMIT,binary_identity=pin(binary),exit_code=process.returncode,parameters=parameters,
            input=dict(genotypes=rows,basis_indices=basis,sample_ids=[f'sample{i:03d}' for i in range(16)],populations=populations),
            options=dict(components=3,use_norm=flags.get('use_norm',True),alternate_norm_style=flags.get('alternate',True),
                min_alleles=flags.get('min_alleles',1),max_missing=flags.get('max_missing',9_999_999),least_squares=flags.get('least_squares',False)),
            native_comparison_required=not any(flags.get(k) for k in ('shrink','fst_only','zero_trace')),
            explicit_native_scope_exclusion=[k for k in ('shrink','fst_only','zero_trace') if flags.get(k)],elapsed_seconds=time.time()-start,
            original_source_modified=False,source_seed_for_synthetic_inputs=86129,core_dump_disabled_for_owned_process=True)
        for suffix in ('eval','evec','grm'):
            path=folder/('original.'+suffix)
            if path.exists():row['original_'+suffix]=path.read_text(encoding='ascii')
        save(folder/'reference.json',row);cases.append(row)
        expected_error=flags.get('zero_trace',False)
        assert (process.returncode!=0)==expected_error,(name,process.returncode,(folder/'original.stderr').read_text())
    assert pin(binary)==setup['binary_identity'] and pin(generator)==source_before
    with zipfile.ZipFile(out/'original-reference.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(out.rglob('*')):
            if path.is_file() and path.name!='original-reference.zip':archive.write(path,path.relative_to(out).as_posix())
    save(out/'COMPLETED.json',dict(status='PASS_TWELVE_ORIGINAL_SMARTPCA_PUBLIC_PROGRAM_CALLS_NOT_PROJECT_WORKFLOW',source_commit=COMMIT,
        binary=setup['binary_identity'],cases=len(cases),finite_native_comparisons=sum(x['native_comparison_required'] for x in cases),
        original_only_shrinkage_and_fst_cases=2,original_error_cases=1,original_source_modified=False,generator=source_before,
        archive=pin(out/'original-reference.zip'),catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
    print(json.dumps(dict(status='PASS',original_calls=len(cases),finite_native_comparisons=sum(x['native_comparison_required'] for x in cases)),indent=2))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable original-source/producer checks')
    main()
