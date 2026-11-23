# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the actual packaged native smartpca protocol against saved originals.

Run with a NEW --out directory. This reuses preserved original C observations;
it does not rerun C or claim historical-edition or whole-paper reproduction.
"""
from pathlib import Path
import argparse,csv,hashlib,io,json,math,re,shutil,subprocess,time,zipfile

ROOT=Path(__file__).resolve().parents[2]
PROFILE='eig-7.2.1-smartpca-16000-native-pca-v1'
FIXTURE='src/test/resources/org/jlinalg/genetics/smartpca-original-analysis-reference.zip'

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def save(path,value):
    path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')

def load(path):return json.loads(path.read_bytes())

def inventory(root):
    files={};directories=[]
    for path in sorted(root.rglob('*')):
        name=path.relative_to(root).as_posix()
        if path.is_dir():directories.append(name)
        elif name not in ('capture-files.json','COMPLETED.json'):files[name]=pin(path)
    return dict(files=files,directories=directories)

def reseal(root):
    save(root/'capture-files.json',inventory(root))
    save(root/'COMPLETED.json',dict(schema_version=1,profile=PROFILE,results_admitted=True,seal_identity=pin(root/'capture-files.json')))

def compare_original(root,reference):
    result=load(root/'native-result.json');k=reference['options']['components']
    expected_eigenvalues=[float(x) for x in reference['original_eval'].split()]
    assert len(result['all_eigenvalues'])==len(expected_eigenvalues)
    assert all(abs(a-b)<=.50001e-6 for a,b in zip(result['all_eigenvalues'],expected_eigenvalues))
    source={}
    for line in reference['original_evec'].splitlines():
        if not line.strip() or line.lstrip().startswith('#'):continue
        fields=line.split();source[fields[0]]=[float(v) for v in fields[1:1+k]]
    with (root/'coordinates.tsv').open(encoding='utf-8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
    actual={r['sample_id']:r for r in rows};basis=[rows[i]['sample_id'] for i in result['final_basis_indices']]
    assert set(source)<=set(actual)
    tolerance=.50001*10**(-reference['original_coordinate_decimal_places'])
    for axis in range(k):
        sign=1 if sum(float(actual[name]['PC'+str(axis+1)])*source[name][axis] for name in basis)>=0 else -1
        for name,coordinates in source.items():assert abs(sign*float(actual[name]['PC'+str(axis+1)])-coordinates[axis])<=tolerance,(reference['case'],name,axis)
    for name,row in actual.items():
        if name not in source:assert row['source_status'] in ('REMOVED_OUTLIER','INSUFFICIENT_NONMISSING_MARKERS') and row['coordinate_fields_admitted']=='false'
        if row['source_status']!='COMPUTED':assert row['coordinate_fields_admitted']=='false'
    removed=re.findall(r'REMOVED outlier (\S+) iter (\d+) evec (\d+) sigmage (\S+) pop: (\S+)',reference['original_outliers'])
    observed=result['removals'];assert len(observed)==len(removed)
    for row,expected in zip(observed,removed):
        assert (row['sample_id'],row['iteration'],row['zero_based_axis'])==(expected[0],int(expected[1]),int(expected[2]))
        assert abs(abs(row['signed_deviation'])-abs(float(expected[3])))<=.50001e-3
    return dict(case=reference['case'],source_rows=len(source),native_rows=len(rows),removed=len(removed),coordinate_rounding_tolerance=tolerance)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--baseline',type=Path)
    args=parser.parse_args();args.jar=args.jar.resolve()
    if args.baseline:args.baseline=args.baseline.resolve()
    out=args.out.resolve();assert not out.exists(),'Use NEW output';out.mkdir(parents=True)
    producer=pin(args.jar);calls=[];comparisons=[];start=time.monotonic();save(out/'START.json',dict(java=args.java,producer=producer,original_C_executed=False,whole_goal_complete=False))
    shutil.copyfile(Path(__file__),out/'runner.py')
    def execute(label,arguments,expected=0):
        folder=out/'commands'/label;folder.mkdir(parents=True)
        command=[args.java,'-ea','-cp',str(args.jar),'org.jlinalg.cli.SmartpcaToolsCli',*arguments]
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:
            process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(folder/'STARTED.json',dict(pid=process.pid,command=command));code=process.wait(timeout=180)
        record=dict(label=label,exit_code=code,expected_exit=expected);save(folder/'RESULT.json',record);calls.append(record)
        assert code==expected,(label,code,(folder/'stderr').read_text(errors='replace')[-2500:])
        if code:assert ('SKIP3:' if code==3 else 'FAIL2:') in (folder/'stderr').read_text(encoding='utf-8')
        return record
    def request(label,raw,expected=0):
        path=out/(label+'.json');save(path,raw);return execute(label,['--request',str(path)],expected)
    execute('help',['--help'])
    references={};captures={}
    with zipfile.ZipFile(ROOT/FIXTURE) as archive:
        for name in archive.namelist():
            if not name.endswith('/reference.json'):continue
            reference=json.loads(archive.read(name));case=reference['case'];references[case]=reference
            folder=out/'cases'/case;folder.mkdir(parents=True);capture=folder/'capture';captures[case]=capture
            for file in ('input.geno','input.ind','input.snp'): (folder/file).write_bytes(archive.read(case+'/'+file))
            poplist=case+'/basis.poplist';basis=archive.read(poplist).decode().split() if poplist in archive.namelist() else None
            parameters=reference['parameters'];options={key:parameters[key] for key in ('numoutevec','numoutlieriter','numoutlierevec','outliersigmathresh','outliermode','minallelecnt','maxmissing')}
            for key in ('usenorm','altnormstyle','lsqproject','shrinkmode'):options[key]=parameters[key]=='YES'
            raw=dict(schema_version=1,mode='RUN',engine='native',profile=PROFILE,operation='PCA',out=capture.as_posix(),inputs=dict(genotypes=(folder/'input.geno').as_posix(),markers=(folder/'input.snp').as_posix(),samples=(folder/'input.ind').as_posix(),reference_populations=basis,genome_build='synthetic'),options=options)
            finite=reference['expected_native']=='COMPARE_FINITE_SOURCE_OUTPUTS'
            request('run-'+case,raw,0 if finite else 2)
            if finite:
                comparisons.append(compare_original(capture,reference))
                before=inventory(capture);execute('verify-'+case,['--verify',str(capture)]);assert before==inventory(capture),'VERIFY modified capture'
            else:
                assert not (capture/'COMPLETED.json').exists() and load(capture/'analysis-status.json')['results_admitted'] is False
                execute('verify-failed-'+case,['--verify',str(capture)],2)
            print(case,'PASS',flush=True)
    assert len(comparisons)==16 and len(references)==18
    source=captures['shrink-complete'];source_before=inventory(source)
    request('json-verify',dict(schema_version=1,mode='VERIFY',analysis=source.as_posix()))
    children=[]
    for case in ('shrink-complete','outlier-and-shrink','outlier-permanent-marker-filter'):
        child=out/('replay-'+case);request('replay-'+case,dict(schema_version=1,mode='REPLAY',analysis=captures[case].as_posix(),out=child.as_posix()))
        execute('verify-replay-'+case,['--verify',str(child)]);children.append(child)
    if args.baseline:
        previous=load(args.baseline/'COMPLETED.json');assert previous['producer']==producer
        for case,reference in references.items():
            if reference['expected_native']=='COMPARE_FINITE_SOURCE_OUTPUTS':execute('cross-host-'+case,['--verify',str(args.baseline/'cases'/case/'capture')])
        child=out/'cross-host-replay';request('cross-host-replay',dict(schema_version=1,mode='REPLAY',analysis=(args.baseline/'cases/shrink-complete/capture').as_posix(),out=child.as_posix()))
        execute('verify-cross-host-replay',['--verify',str(child)])
    def control(label,change,seal=True,parent=source):
        root=out/'controls'/label;root.parent.mkdir(exist_ok=True);shutil.copytree(parent,root);change(root)
        if seal:reseal(root)
        before=inventory(root);execute('control-'+label,['--verify',str(root)],2);assert before==inventory(root),'Refused VERIFY modified evidence'
    def edit(root,name,action):
        value=load(root/name);action(value);save(root/name,value)
    control('missing-seal',lambda r:(r/'capture-files.json').unlink(),False)
    control('missing-completion',lambda r:(r/'COMPLETED.json').unlink(),False)
    control('resealed-extra-directory',lambda r:(r/'extra').mkdir())
    control('resealed-extra-file',lambda r:(r/'extra.txt').write_text('extra'))
    control('resealed-missing-source',lambda r:(r/'source/LICENSE.txt').unlink())
    control('resealed-missing-input',lambda r:(r/'inputs/genotypes.geno').unlink())
    control('resealed-literal-ID',lambda r:(r/'coordinates.tsv').write_text((r/'coordinates.tsv').read_text().replace('sample000','SAMPLE000'),encoding='utf-8'))
    def wrong_coordinate(root):
        edit(root,'coordinates/000000.json',lambda d:d['coordinates'][0].__setitem__(0,d['coordinates'][0][0]+.01))
        table=(root/'coordinates.tsv').read_text().splitlines();row=table[1].split('\t');row[7]=str(float(row[7])+.01);table[1]='\t'.join(row);(root/'coordinates.tsv').write_text('\n'.join(table)+'\n',encoding='utf-8')
    control('resealed-coherent-false-coordinate',wrong_coordinate)
    control('resealed-false-eigenvalue',lambda r:edit(r,'native-result.json',lambda d:d['all_eigenvalues'].__setitem__(0,d['all_eigenvalues'][0]+.01)))
    control('resealed-pass',lambda r:edit(r,'passes/0001.json',lambda d:d.__setitem__('used_markers',d['used_markers']+1)))
    control('resealed-admission',lambda r:edit(r,'analysis-status.json',lambda d:d.__setitem__('results_admitted',False)))
    control('resealed-deadline',lambda r:edit(r,'compute-process.json',lambda d:d.__setitem__('deadline_exceeded',True)))
    control('resealed-live-owner',lambda r:edit(r,'compute-process.json',lambda d:d.__setitem__('owned_thread_stopped',False)))
    control('resealed-warnings',lambda r:(r/'WARNINGS.md').write_text('Everything is reportable.'))
    control('resealed-original-choice',lambda r:edit(r,'source-request.json',lambda d:d['options'].__setitem__('usenorm',False)))
    def coherent_false_options(root):
        edit(root,'source-request.json',lambda d:d['options'].__setitem__('usenorm',False))
        edit(root,'canonical-request.json',lambda d:d['options'].__setitem__('usenorm',False))
        def binding(d):
            d['source_request']['options']['usenorm']=False;d['canonical_request']['options']['usenorm']=False
        edit(root,'request-binding.json',binding)
        edit(root,'native-result.json',lambda d:d['options'].__setitem__('usenorm',False))
    control('resealed-coherent-false-options',coherent_false_options)
    control('resealed-false-producer-backend',lambda r:edit(r,'producer.json',lambda d:d.__setitem__('backend','EXTERNAL_RUNTIME')))
    control('resealed-replay-parent-result',lambda r:edit(r,'replay-parent-result.json',lambda d:d.__setitem__('requested_axes',999)),parent=children[0])
    def coherent_false_parent(root):
        edit(root,'replay-parent-result.json',lambda d:d.__setitem__('requested_axes',999))
        edit(root,'replay-parent-capture-files.json',lambda d:d['files'].__setitem__('native-result.json',pin(root/'replay-parent-result.json')))
        edit(root,'replay-parent-COMPLETED.json',lambda d:d.__setitem__('seal_identity',pin(root/'replay-parent-capture-files.json')))
        def update(d):
            d['parent_seal_identity']=pin(root/'replay-parent-capture-files.json');d['parent_completion_identity']=pin(root/'replay-parent-COMPLETED.json')
        edit(root,'replay.json',update)
    control('resealed-coherent-false-replay-parent',coherent_false_parent,parent=children[0])
    execute('missing-capture',['--verify',str(out/'absent')],3)
    execute('missing-request',['--request',str(out/'absent.json')],3)
    template=load(out/'run-shrink-complete.json')
    def bad_run(label,change,expected=2):
        raw=json.loads(json.dumps(template));capture=out/('bad-'+label);raw['out']=capture.as_posix();change(raw);request('bad-'+label,raw,expected)
        assert not (capture/'COMPLETED.json').exists()
        if capture.exists():assert load(capture/'analysis-status.json')['results_admitted'] is False
        return capture
    bad_run('missing-input',lambda d:d['inputs'].__setitem__('genotypes',(out/'absent.geno').as_posix()),3)
    bad_run('unknown-option',lambda d:d['options'].__setitem__('r2thresh',.7))
    bad_run('work-cap',lambda d:d.__setitem__('limits',dict(maximum_estimated_work=1)))
    bad_run('output-cap',lambda d:d.__setitem__('limits',dict(maximum_output_bytes=1)))
    request('existing-output',template,2);assert inventory(source)==source_before
    request('nested-replay-output',dict(schema_version=1,mode='REPLAY',analysis=source.as_posix(),out=(source/'child').as_posix()),2);assert inventory(source)==source_before
    # Multiple coordinate chunks and literal missing-looking identifiers in a real CLI run.
    multi=out/'multi-input';multi.mkdir();base=out/'cases/shrink-complete'
    original=(base/'input.geno').read_text().splitlines();n=550
    (multi/'input.geno').write_text('\n'.join(row[:12]+''.join(row[12+i%4] for i in range(n-12)) for row in original)+'\n')
    (multi/'input.snp').write_bytes((base/'input.snp').read_bytes());names=['NA','NaN','Inf']+[f'target{i:04}' for i in range(3,n)]
    (multi/'input.ind').write_text(''.join(f'{name} U '+('REF_A' if i<6 else 'REF_B' if i<12 else 'PROJECTED')+'\n' for i,name in enumerate(names)))
    raw=json.loads(json.dumps(template));raw['out']=(out/'multi-capture').as_posix();raw['inputs'].update({k:(multi/v).as_posix() for k,v in [('genotypes','input.geno'),('markers','input.snp'),('samples','input.ind')]})
    raw['options']['shrinkmode']=False;request('multi-chunk-run',raw);execute('multi-chunk-verify',['--verify',raw['out']]);assert load(Path(raw['out'])/'native-result.json')['coordinate_chunks']==2
    control('resealed-second-chunk',lambda r:edit(r,'coordinates/000001.json',lambda d:d['coordinates'][0].__setitem__(0,d['coordinates'][0][0]+.01)),parent=Path(raw['out']))
    # A real deadline cancellation, with bounded retained storage and the whole input preserved.
    timeout_input=out/'timeout-input';timeout_input.mkdir();samples=2000;markers=1000
    (timeout_input/'input.ind').write_text(''.join(f's{i:04} U '+('REF_A' if i<1000 else 'REF_B')+'\n' for i in range(samples)))
    (timeout_input/'input.snp').write_text(''.join(f'm{i:04} 1 0 {i+1} A G\n' for i in range(markers)))
    (timeout_input/'input.geno').write_text(''.join(''.join(str(((i*73+j*19)^(i+j*7))%3) for j in range(samples))+'\n' for i in range(markers)))
    def deadline(d):
        d['inputs'].update({k:(timeout_input/v).as_posix() for k,v in [('genotypes','input.geno'),('markers','input.snp'),('samples','input.ind')]});d['inputs']['reference_populations']=None
        d['options'].update(shrinkmode=False,numoutevec=3,numoutlieriter=0);d['limits']=dict(timeout_seconds=1,maximum_estimated_work=10_000_000_000_000)
    timed=bad_run('actual-deadline',deadline);receipt=load(timed/'compute-process.json');assert receipt['deadline_exceeded'] and receipt['owned_thread_stopped']
    assert pin(args.jar)==producer and source_before==inventory(source)
    result=dict(status='PASS_PACKAGED_SMARTPCA_PROTOCOL_WITH_ORIGINAL_COMPARISONS',producer=producer,java=args.java,commands=calls,original_comparisons=comparisons,
                reused_original_program_cases=18,new_original_C_calls=0,source_capture_unchanged=True,actual_deadline_owned_thread_stopped=True,seconds=time.monotonic()-start,
                workflow_catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
    save(out/'COMPLETED.json',result);print(json.dumps(dict(status=result['status'],commands=len(calls),original_comparisons=len(comparisons),seconds=result['seconds']),indent=2))

if __name__=='__main__':main()
