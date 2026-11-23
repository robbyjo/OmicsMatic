# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture unchanged original FST/inbreeding and direct weighted-jackknife calls."""
from pathlib import Path
import argparse,json,math,os,random,re,resource,shutil,subprocess,zipfile
from setup_smartpca_reference import COMMIT,pin,save
ROOT=Path(__file__).resolve().parents[2]

def special(value):
    if isinstance(value,float) and not math.isfinite(value):return 'NaN' if math.isnan(value) else 'Inf' if value>0 else '-Inf'
    if isinstance(value,list):return [special(x) for x in value]
    if isinstance(value,dict):return {k:special(v) for k,v in value.items()}
    return value

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--setup',type=Path,required=True);parser.add_argument('--intake',type=Path,required=True);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    out=a.out.resolve();runtime=a.runtime.resolve();assert not out.exists() and not runtime.exists();out.mkdir(parents=True);runtime.mkdir(parents=True)
    resource.setrlimit(resource.RLIMIT_CORE,(0,0));resource.setrlimit(resource.RLIMIT_AS,(2_147_483_648,2_147_483_648))
    source=json.loads((a.intake/'SOURCE.json').read_bytes());assert source['commit']==COMMIT;original=a.intake/'original-source'
    for row in source['members']:assert pin(original/row['path'])=={k:row[k] for k in ('bytes','sha256')}
    setup=json.loads((a.setup/'COMPLETED.json').read_bytes());binary=Path(setup['binary']);assert setup['source_commit']==COMMIT and pin(binary)==setup['binary_identity']
    assert setup['binary_identity']['sha256']=='db36b911ef813604a871bfed6437211a51e0aea721cc364dc122c1121435a37d'
    prefix=Path(setup['dependency_prefix']);library=a.setup/'source-build/src/nicksrc/libnick.a';before=pin(library)
    harness=ROOT/'tools/literature/smartpca_reference/jackknife_harness.c';shutil.copyfile(harness,out/harness.name);shutil.copyfile(Path(__file__),out/'generator.py')
    env=dict(os.environ,OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',LC_ALL='C')
    def execute(folder,name,arguments,raw=None,timeout=60):
        process=subprocess.run(arguments,cwd=folder,env=env,input=raw,capture_output=True,timeout=timeout);(folder/(name+'.stdout')).write_bytes(process.stdout);(folder/(name+'.stderr')).write_bytes(process.stderr);save(folder/(name+'.json'),dict(command=list(map(str,arguments)),exit_code=process.returncode));return process
    commands=[('compile-original-qpsubs',['gcc','-O2','-fcommon','-ffunction-sections','-fdata-sections','-I'+str(original/'include'),'-I'+str(prefix/'include'),'-c',str(original/'src/qpsubs.c'),'-o',str(runtime/'qpsubs.o')]),
        ('link-jackknife-caller',['gcc','-O2','-fcommon','-I'+str(original/'include'),'-I'+str(prefix/'include'),str(harness),str(runtime/'qpsubs.o'),str(library),'-Wl,--gc-sections','-L'+str(prefix/'lib'),'-Wl,-rpath,'+str(prefix/'lib'),'-lgsl','-lopenblas','-lm','-lpthread','-o',str(runtime/'jackknife-reference')])]
    for name,args in commands:
        p=execute(out,name,args,timeout=120);assert p.returncode==0,p.stderr
    weighted=[('equal',.3,[.2,.4,.25],[1.,1.,1.]),('unequal',.3,[.2,.4,.25],[1.,3.,7.]),('zero-filter',.3,[.2,99.,.25],[1.,0.,3.]),('threshold-kept',.3,[.2,.4,.25],[1.,1e-6,3.]),('below-threshold',.3,[.2,.4,.25],[1.,math.nextafter(1e-6,0.),3.]),
        ('one-block-refusal',.3,[.2],[2.]),('one-retained-block-refusal',.3,[.2,.4],[1.,0.]),('constant',-.2,[-.2,-.2,-.2],[4.,4.,4.]),('tiny-scale',1e-200,[1.1e-200,1.2e-200,.7e-200],[2.,5.,9.]),('nonfinite-mean',math.nan,[.2,.4],[1.,2.]),('nonfinite-weight',.3,[.2,.4],[1.,math.nan])]
    rng=random.Random(392801)
    for i in range(12):
        n=3+i%5;weighted.append(('random-'+str(i),rng.uniform(-1,1),[rng.uniform(-1,1) for _ in range(n)],[float(rng.randrange(1,20)) for _ in range(n)]))
    for name,mean,deleted,weights in weighted:
        folder=out/'jackknife'/name;folder.mkdir(parents=True);raw=(str(len(weights))+' '+format(mean,'.17g')+'\n'+' '.join(format(v,'.17g') for v in deleted)+'\n'+' '.join(format(v,'.17g') for v in weights)+'\n').encode();(folder/'input.txt').write_bytes(raw)
        p=execute(folder,'original',[str(runtime/'jackknife-reference')],raw);expected=not name.endswith('refusal');assert (p.returncode==0)==expected
        save(folder/'reference.json',dict(case=name,kind='ORIGINAL_WJACKEST',input=special(dict(mean=mean,deleted=deleted,weights=weights)),result=json.loads(p.stdout) if expected else None,expected_native='COMPARE' if expected else 'REFUSE',exit_code=p.returncode,source_commit=COMMIT,original_source_modified=False))
    definitions=[('diploid-complete',{}),('diploid-missing',dict(missing=True)),('inbreed-complete',dict(inbreed=True)),('inbreed-missing',dict(inbreed=True,missing=True)),
        ('unequal-blocks',dict(unequal=True)),('negative-diploid',dict(identical=True)),('negative-inbreed',dict(identical=True,inbreed=True)),('single-block',dict(single=True)),
        ('source-physical-fallback',dict(fallback=True)),('source-small-position-fallback',dict(tiny=True)),('above-fallback-threshold',dict(above=True)),('source-centimorgans',dict(cm=True)),
        ('nonmonotone-genetic-position',dict(nonmonotone=True)),('selected-populations',dict(selected=True)),('reversed-population-order',dict(reverse=True)),('more-than-ten-populations',dict(many=True)),
        ('nonautosomal-influences-unit-detection',dict(nonauto=True)),('nodata-monomorphic-markers',dict(filtered=True)),('all-missing-individual',dict(empty=True)),
        ('missing-looking-identifiers',dict(literal=True)),('unidentified-fixed-same',dict(fixed=True)),('inbreed-one-person-group',dict(one_person=True,inbreed=True)),('boundary-block-length',dict(boundary=True)),('single-block-inbreed',dict(single=True,inbreed=True))]
    for name,flags in definitions:
        folder=out/'program'/name;folder.mkdir(parents=True);groups=11 if flags.get('many') else 2 if flags.get('identical') else 3;n=groups*4;m=48
        populations=['GROUP_'+str(i//4) for i in range(n)];ids=[f'ind{i:03}' for i in range(n)]
        if flags.get('literal'):
            populations=[('NA','NaN','Inf')[i//4] for i in range(n)];ids[:3]=['NA','NaN','Inf']
        if flags.get('one_person'):populations[-3:]=[populations[0]]*3
        rng=random.Random(318921);calls=[]
        for row in range(m):
            line=[]
            for i in range(n):
                f=.18+.6*(i//4)/max(1,groups-1);v=int(rng.random()<f)+int(rng.random()<f)
                if flags.get('missing') and rng.random()<.18:v=9
                if flags.get('identical'):v=[0,1,2,1][i%4]
                if flags.get('fixed'):v=0
                if flags.get('empty') and i==0:v=9
                line.append(v)
            calls.append(line)
        chromosomes=[1]*m;physical=[1_000_000*(i+1) for i in range(m)];genetic=[.01*i for i in range(m)]
        if flags.get('single'):genetic=[.0001*i for i in range(m)]
        if flags.get('fallback'):genetic=[0.]*m
        if flags.get('tiny'):genetic=[1e-5]*m
        if flags.get('above'):genetic=[math.nextafter(1e-5,math.inf)]*m
        if flags.get('cm'):genetic=[101.+i for i in range(m)]
        if flags.get('unequal'):genetic=[0.]*7+[.06]*13+[.12]*(m-20)
        if flags.get('nonmonotone'):genetic=[.02*(i%7) for i in range(m)]
        if flags.get('boundary'):genetic=[.05*(i//4) for i in range(m)]
        if flags.get('filtered'):
            calls[0]=[9]*n;calls[1]=[0]+[9]*(n-1);calls[2]=[0]*n;calls[3]=[2]*n
        markerids=[f'marker{i:03}' for i in range(m)]
        if flags.get('nonauto'):
            chromosomes.append(23);physical.append(90_000_000);genetic.append(150.);calls.append([1]*n);markerids.append('excludedX')
        selected=None
        if flags.get('selected'):selected=['GROUP_0','GROUP_2']
        if flags.get('reverse'):selected=list(reversed(list(dict.fromkeys(populations))))
        names=selected if selected is not None else list(dict.fromkeys(populations));sample_pop=[names.index(p) if p in names else -1 for p in populations]
        (folder/'input.geno').write_text(''.join(''.join(str(v) for v in row)+'\n' for row in calls));(folder/'input.ind').write_text(''.join(f'{i} U {p}\n' for i,p in zip(ids,populations)))
        (folder/'input.snp').write_text(''.join(f'{id} {chrom} {g:.17g} {pos} A G\n' for id,chrom,g,pos in zip(markerids,chromosomes,genetic,physical)))
        parameters=dict(genotypename='input.geno',snpname='input.snp',indivname='input.ind',fstonly='YES',fstdetailsname='original.fst',numthreads=1,blgsize=.05,inbreed='YES' if flags.get('inbreed') else 'NO')
        if selected is not None:(folder/'populations.txt').write_text('\n'.join(selected)+'\n');parameters['poplistname']='populations.txt'
        (folder/'parameters.txt').write_text(''.join(str(k)+': '+str(v)+'\n' for k,v in parameters.items()))
        p=execute(folder,'original',[str(binary),'-p','parameters.txt'])
        if flags.get('single') and flags.get('inbreed'):
            assert p.returncode!=0 and b'(wjackest) number of blocks <= 1' in p.stderr
            save(folder/'reference.json',dict(case=name,kind='ORIGINAL_FSTONLY_REFUSAL',source_commit=COMMIT,binary=pin(binary),exit_code=p.returncode,original_source_modified=False,
                input=dict(genotypes=calls,sample_population=sample_pop,populations=names,positions=[dict(chromosome=c,genetic_position=g,physical_position=p) for c,g,p in zip(chromosomes,genetic,physical)],marker_ids=markerids),inbreed=True,block_length=.05))
            print(name,'SOURCE_REFUSAL_RETAINED',flush=True);continue
        assert p.returncode==0,(name,p.stderr)
        details=(folder/'original.fst').read_text();pairs=[];loci=[]
        for line in details.splitlines():
            fields=line.split()
            if not fields or fields[0]=='##':continue
            if fields[0]=='F_st':pairs.append(dict(first=fields[1],second=fields[2],estimate=fields[3],standard_error=fields[4]))
            else:loci.append(dict(first=fields[0],second=fields[1],marker=fields[2],numerator=fields[3],denominator=fields[4],ratio=fields[5]))
        assert len(pairs)==len(names)*(len(names)-1)//2
        stdout=p.stdout.decode();blocks=[int(v) for v in re.findall(r'number of blocks for moving block jackknife: (\d+)',stdout)]
        assert blocks and len(set(blocks))==1
        inbreeding=[]
        if flags.get('inbreed'):
            block=stdout.split('## inbreeding coeffs:   inbreed    std error\n')[1].splitlines()
            for line in block[:len(names)]:
                values=line.split();inbreeding.append(dict(population=values[0],estimate=values[1],standard_error=values[2]))
        save(folder/'reference.json',dict(case=name,kind='ORIGINAL_FSTONLY_PROGRAM',source_commit=COMMIT,binary=pin(binary),exit_code=p.returncode,original_source_modified=False,
            input=dict(genotypes=calls,sample_population=sample_pop,populations=names,positions=[dict(chromosome=c,genetic_position=g,physical_position=p) for c,g,p in zip(chromosomes,genetic,physical)],marker_ids=markerids),
            inbreed=bool(flags.get('inbreed')),block_length=.05,blocks=blocks[0],pairs=pairs,loci=loci,inbreeding=inbreeding))
        print(name,blocks[0],len(pairs),'PASS',flush=True)
    for row in source['members']:assert pin(original/row['path'])=={k:row[k] for k in ('bytes','sha256')}
    assert pin(library)==before and pin(binary)==setup['binary_identity']
    with zipfile.ZipFile(out/'original-fst-reference.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for path in sorted(out.rglob('*')):
            if path.is_file() and path.name!='original-fst-reference.zip':z.write(path,path.relative_to(out).as_posix())
    save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_FST_AND_WEIGHTED_JACKKNIFE_REFERENCES',program_calls=len(definitions),direct_function_calls=len(weighted),source_commit=COMMIT,original_source_files_unchanged=159,original_scientific_source_modified=False,original_binary=pin(binary),jackknife_caller=pin(runtime/'jackknife-reference'),original_static_library=before,runtime=str(runtime),archive=pin(out/'original-fst-reference.zip'),workflow_qualified=False,catalog_credit=0,new_reviews=0,whole_goal_complete=False))
    print('PASS',len(definitions),'original FST programs and',len(weighted),'direct source jackknives.')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Do not disable original-source checks')
    main()
