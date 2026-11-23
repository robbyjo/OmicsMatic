# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original whole-program outlier/shrinkage cases and undefined-branch witnesses."""
from pathlib import Path
import argparse,json,os,random,resource,shutil,subprocess,zipfile
from setup_smartpca_reference import COMMIT,pin,save
ROOT=Path(__file__).resolve().parents[2]
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists(),'Use NEW original-reference output';out.mkdir(parents=True)
    setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['source_commit']==COMMIT and setup['status']=='PASS_ORIGINAL_SMARTPCA721_PRIVATE_BUILD_ONLY'
    binary=Path(setup['binary']);assert pin(binary)==setup['binary_identity'];shutil.copyfile(Path(__file__),out/'generator.py')
    old=ROOT/'build/smartpca-1/original-program-reference-1';cases=[]
    definitions=[('shrink-complete',dict(shrink=True,complete=True)),('shrink-missing',dict(shrink=True)),
        ('shrink-no-targets',dict(shrink=True,all_basis=True)),('shrink-alternate-no',dict(shrink=True,alternate=False)),
        ('shrink-no-normalization',dict(shrink=True,use_norm=False)),('shrink-two-axes',dict(shrink=True,k=2)),
        ('shrink-reordered-samples',dict(shrink=True,reorder=True)),('shrink-single-axis',dict(shrink=True,k=1)),
        ('outlier-defaults',dict(strong=True,iterations=5,axes=10,sigma=6.0)),
        ('outlier-leave-one',dict(strong=True,iterations=3,axes=4,sigma=3.0,mode=1,targets=True)),
        ('outlier-disabled',dict(strong=True,iterations=5,axes=10,sigma=.5,mode=2)),
        ('outlier-zero-iterations',dict(strong=True,iterations=0,axes=10,sigma=.5)),
        ('outlier-one-pass',dict(strong=True,iterations=1,axes=4,sigma=2.5,targets=True)),
        ('outlier-and-shrink',dict(strong=True,iterations=2,axes=4,sigma=3.0,targets=True,shrink=True)),
        ('outlier-more-axes-than-output',dict(strong=True,iterations=2,axes=8,sigma=3.0,targets=True,k=2)),
        ('outlier-permanent-marker-filter',dict(strong=True,iterations=3,axes=4,sigma=3.0,targets=True,filter_rows=True)),
        ('unidentified-repeated-shrink-axes',dict(shrink=True,repeated=True,k=2,expected_native='REFUSE_UNIDENTIFIED_SHRINKAGE')),
        ('unidentified-empty-shrink-target',dict(shrink=True,empty_target=True,expected_native='REFUSE_UNIDENTIFIED_SHRINKAGE'))]
    for name,flags in definitions:
        folder=out/name;folder.mkdir()
        if flags.get('strong'):
            rng=random.Random(719328);n=64;m=512;matrix=[]
            for s in range(m):
                frequency=.18+.64*((s*7+3)%29)/28
                row=[int(rng.random()<frequency)+int(rng.random()<frequency) for _ in range(n)]
                row[0]=0;matrix.append(row)
            basis=list(range(48 if flags.get('targets') else n));ids=[f'sample{i:03d}' for i in range(n)]
            populations=['REF_A' if i<24 else 'REF_B' if i<48 else 'TARGET' for i in range(n)]
            if flags.get('filter_rows'):
                for s in range(0,16):
                    matrix[s][0]=9;matrix[s][1+(s%20)]=9
                matrix[16]=[0]*n;matrix[17]=[2]*n;matrix[18]=[9]*n
        else:
            key='complete-unprojected' if flags.get('complete') else 'least-squares-reference-projection'
            previous=json.loads((old/key/'reference.json').read_bytes());data=previous['input']
            matrix=[row.copy() for row in data['genotypes']];ids=data['sample_ids'].copy();populations=data['populations'].copy();basis=list(range(16 if flags.get('all_basis') else 12))
        if flags.get('repeated'):
            matrix=[[0,0,2,2],[0,2,0,2],[0,2,2,0]]*16;ids=[f'sample{i:03d}' for i in range(4)];populations=['REF_A','REF_A','REF_B','REF_B'];basis=list(range(4))
        if flags.get('empty_target'):
            for row in matrix:row[-1]=9
        if flags.get('reorder'):
            order=[12,0,6,13,1,7,14,2,8,15,3,9,4,10,5,11]
            matrix=[[row[index] for index in order] for row in matrix];ids=[ids[index] for index in order];populations=[populations[index] for index in order];basis=[i for i,index in enumerate(order) if index in basis]
        options=dict(components=flags.get('k',3),use_norm=flags.get('use_norm',True),alternate_norm_style=flags.get('alternate',True),
            minimum_allele_count=1,maximum_missing=1 if flags.get('filter_rows') else 9_999_999,
            maximum_outlier_iterations=flags.get('iterations',0),outlier_axes=flags.get('axes',10),outlier_threshold=flags.get('sigma',6.),outlier_mode=flags.get('mode',0),
            shrinkage=flags.get('shrink',False),least_squares=True)
        (folder/'input.geno').write_text(''.join(''.join(str(g) for g in row)+'\n' for row in matrix),encoding='ascii')
        (folder/'input.snp').write_text(''.join(f'rs{s:04d} 1 {s*.001:.6f} {1000+s*100} A G\n' for s in range(len(matrix))),encoding='ascii')
        (folder/'input.ind').write_text(''.join(f'{sample} U {population}\n' for sample,population in zip(ids,populations)),encoding='ascii')
        parameters=dict(genotypename='input.geno',snpname='input.snp',indivname='input.ind',evecoutname='original.evec',evaloutname='original.eval',outlieroutname='original.outliers',
            grmoutname='original.grm',numoutevec=options['components'],numoutlieriter=options['maximum_outlier_iterations'],numoutlierevec=options['outlier_axes'],
            outliersigmathresh=options['outlier_threshold'],outliermode=options['outlier_mode'],numthreads=1,hiprecision='YES',
            usenorm='YES' if options['use_norm'] else 'NO',altnormstyle='YES' if options['alternate_norm_style'] else 'NO',lsqproject='YES',
            shrinkmode='YES' if options['shrinkage'] else 'NO',minallelecnt=options['minimum_allele_count'],maxmissing=options['maximum_missing'])
        if len(basis)<len(ids):
            (folder/'basis.poplist').write_text('REF_A\nREF_B\n',encoding='ascii');parameters['poplistname']='basis.poplist'
        (folder/'parameters.txt').write_text(''.join(str(k)+': '+str(v)+'\n' for k,v in parameters.items()),encoding='ascii')
        def no_core():resource.setrlimit(resource.RLIMIT_CORE,(0,0))
        env=dict(os.environ,OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',LC_ALL='C')
        result=subprocess.run([str(binary),'-p','parameters.txt'],cwd=folder,env=env,capture_output=True,timeout=300,preexec_fn=no_core)
        (folder/'original.stdout').write_bytes(result.stdout);(folder/'original.stderr').write_bytes(result.stderr)
        record=dict(case=name,source_commit=COMMIT,binary_identity=pin(binary),exit_code=result.returncode,parameters=parameters,
            input=dict(genotypes=matrix,basis_indices=basis,sample_ids=ids,populations=populations),options=options,
            expected_native=flags.get('expected_native','COMPARE_FINITE_SOURCE_OUTPUTS'),original_coordinate_decimal_places=6 if options['shrinkage'] else 4,
            original_scientific_source_modified=False)
        for suffix in ('evec','eval','outliers','grm'):
            path=folder/('original.'+suffix)
            if path.is_file():record['original_'+suffix]=path.read_text(encoding='ascii')
        save(folder/'reference.json',record);cases.append(record)
        if record['expected_native']=='COMPARE_FINITE_SOURCE_OUTPUTS':assert result.returncode==0,(name,result.stderr.decode())
    assert pin(binary)==setup['binary_identity']
    with zipfile.ZipFile(out/'original-analysis-reference.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for path in sorted(out.rglob('*')):
            if path.is_file() and path.name!='original-analysis-reference.zip':archive.write(path,path.relative_to(out).as_posix())
    save(out/'COMPLETED.json',dict(status='PASS_EIGHTEEN_UNCHANGED_ORIGINAL_PROGRAM_CALLS',source_commit=COMMIT,binary_identity=pin(binary),
        cases=len(cases),finite_comparison_cases=sum(row['expected_native']=='COMPARE_FINITE_SOURCE_OUTPUTS' for row in cases),undefined_shrinkage_witnesses=2,
        archive=pin(out/'original-analysis-reference.zip'),workflow_qualified=False,catalog_credit=0,new_reviews=0,whole_goal_complete=False))
    print('PASS:',len(cases),'original program calls;16 finite comparison cases and2 undefined-shrinkage witnesses retained.')
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable reference producer checks')
    main()
