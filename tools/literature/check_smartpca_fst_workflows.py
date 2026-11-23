# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test the actual public smartpca-tools FST/PCA route and saved original FST cases."""
from pathlib import Path
import argparse,csv,hashlib,io,json,shutil,subprocess,time,zipfile
ROOT=Path(__file__).resolve().parents[2]
PROFILE='eig-7.2.1-smartpca-16000-native-fst-v1'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')
def load(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def inventory(root):
    return dict(files={p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file() and p.name not in ('COMPLETED.json','capture-files.json')},directories=[p.relative_to(root).as_posix() for p in sorted(root.rglob('*')) if p.is_dir()])
def snapshot(root):return {p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
def reseal(root):
    save(root/'capture-files.json',inventory(root));save(root/'COMPLETED.json',dict(schema_version=1,profile=PROFILE,results_admitted=True,seal_identity=pin(root/'capture-files.json')))
def compare_original(root,reference):
    result=load(root/'native-result.json');names=reference['input']['populations'];assert result['populations']==names and result['blocks']['count']==reference['blocks']
    for actual,expected in zip(result['pairs'],reference['pairs']):
        assert (actual['first_population'],actual['second_population'])==(expected['first'],expected['second'])
        assert abs(actual['estimate']-float(expected['estimate']))<=.50001e-6 and abs(actual['standard_error']-float(expected['standard_error']))<=.50001e-6
    assert len(result['pairs'])==len(reference['pairs'])
    for actual,expected in zip(result['inbreeding'],reference['inbreeding']):
        assert actual['first_population']==expected['population'] and abs(actual['estimate']-float(expected['estimate']))<=.50001e-4 and abs(actual['standard_error']-float(expected['standard_error']))<=.50001e-4
    assert len(result['inbreeding'])==len(reference['inbreeding'])
    with (root/'locus-contributions.tsv').open(encoding='utf-8',newline='') as stream:rows=[r for r in csv.DictReader(stream,delimiter='\t') if r['kind']=='PAIR_FST']
    assert len(rows)==len(reference['loci'])
    for actual,expected in zip(rows,reference['loci']):
        assert (actual['first_population'],actual['second_population'],actual['marker_id'])==(expected['first'],expected['second'],expected['marker'])
        assert abs(float(actual['numerator'])-float(expected['numerator']))<=.50001e-6 and abs(float(actual['denominator'])-float(expected['denominator']))<=.50001e-6
        if expected['ratio']=='-':assert actual['ratio']=='-'
        else:assert abs(float(actual['ratio'])-float(expected['ratio']))<=.50001e-6
    return dict(case=reference['case'],pairs=len(result['pairs']),locus_pairs=len(rows),source_blocks=reference['blocks'])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--baseline',type=Path);a=p.parse_args();a.jar=a.jar.resolve()
    if a.baseline:a.baseline=a.baseline.resolve()
    out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);shutil.copyfile(Path(__file__),out/'runner.py');producer=pin(a.jar);calls=[];comparisons=[];captures={};references={};start=time.monotonic()
    save(out/'START.json',dict(java=a.java,producer=producer,original_C_executed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
    def execute(label,args,expected=0):
        folder=out/'commands'/label;folder.mkdir(parents=True);command=[a.java,'-ea','-jar',str(a.jar),'smartpca-tools',*args]
        with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:
            process=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);save(folder/'STARTED.json',dict(pid=process.pid,command=command));code=process.wait(timeout=180)
        record=dict(label=label,exit_code=code,expected_exit=expected);save(folder/'RESULT.json',record);calls.append(record)
        assert code==expected,(label,code,(folder/'stderr').read_text(errors='replace')[-2000:])
        if code:assert ('SKIP3:' if code==3 else 'FAIL2:') in (folder/'stderr').read_text(encoding='utf-8')
    def request(label,raw,expected=0):
        path=out/(label+'.json');save(path,raw);execute(label,['--request',str(path)],expected)
    def verify(label,root,expected=0):
        before=snapshot(root);execute(label,['--verify',str(root)],expected);assert snapshot(root)==before,'VERIFY changed capture'
    execute('help',['--help'])
    with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/genetics/smartpca-original-fst-reference.zip') as archive:
        for name in archive.namelist():
            if not name.startswith('program/') or not name.endswith('/reference.json'):continue
            reference=json.loads(archive.read(name));case=reference['case'];references[case]=reference;folder=out/'cases'/case;folder.mkdir(parents=True)
            for file in ('input.geno','input.snp','input.ind'):(folder/file).write_bytes(archive.read('program/'+case+'/'+file))
            raw=dict(schema_version=1,mode='RUN',engine='native',profile=PROFILE,operation='FST',out=(folder/'capture').as_posix(),inputs=dict(genotypes=(folder/'input.geno').as_posix(),markers=(folder/'input.snp').as_posix(),samples=(folder/'input.ind').as_posix(),populations=reference['input']['populations'],genome_build='synthetic'),options=dict(inbreed=reference['inbreed'],blgsize=reference['block_length']))
            if reference['kind']=='ORIGINAL_FSTONLY_REFUSAL':
                request('run-'+case,raw,2);assert not Path(raw['out'],'COMPLETED.json').exists();raw['out']=(folder/'retention-cannot-override-refusal').as_posix();raw['policies']=dict(allow_unreportable_fields=True);request('refusal-even-under-retention-'+case,raw,2);continue
            positive={(r['first'],r['second']) for r in reference['loci'] if float(r['denominator'])>0};admitted=reference['blocks']>1 and all((r['first'],r['second']) in positive for r in reference['pairs'])
            request('run-'+case,raw,0 if admitted else 2);capture=Path(raw['out']);comparisons.append(compare_original(capture,reference))
            if not admitted:
                assert not (capture/'COMPLETED.json').exists() and load(capture/'analysis-status.json')['results_admitted'] is False;verify('verify-failed-'+case,capture,2)
                raw['out']=(folder/'diagnostic-retention').as_posix();raw['policies']=dict(allow_unreportable_fields=True);request('retain-'+case,raw);capture=Path(raw['out']);compare_original(capture,reference)
                assert any(not row['fields_admitted'] for row in load(capture/'native-result.json')['pairs'])
            captures[case]=capture;verify('verify-'+case,capture);print(case,'PASS',flush=True)
    assert len(references)==24 and len(comparisons)==23
    source=captures['diploid-complete'];before_source=snapshot(source);children=[]
    request('json-verify',dict(schema_version=1,mode='VERIFY',analysis=source.as_posix()))
    for case in ('diploid-complete','inbreed-missing','negative-diploid','single-block'):
        child=out/('replay-'+case);request('replay-'+case,dict(schema_version=1,mode='REPLAY',analysis=captures[case].as_posix(),out=child.as_posix()));verify('verify-replay-'+case,child);children.append(child)
    if a.baseline:
        assert load(a.baseline/'COMPLETED.json')['producer']==producer
        for case in captures:
            folder=a.baseline/'cases'/case;parent=folder/'diagnostic-retention' if (folder/'diagnostic-retention').exists() else folder/'capture';verify('cross-host-'+case,parent)
        child=out/'cross-host-replay';request('cross-host-replay',dict(schema_version=1,mode='REPLAY',analysis=(a.baseline/'cases/diploid-complete/capture').as_posix(),out=child.as_posix()));verify('verify-cross-host-replay',child)
    def edit(root,name,change):
        value=load(root/name);change(value);save(root/name,value)
    def control(label,change,seal=True,parent=source):
        root=out/'controls'/label;root.parent.mkdir(exist_ok=True);shutil.copytree(parent,root);change(root)
        if seal:reseal(root)
        verify('control-'+label,root,2)
    control('missing-seal',lambda r:(r/'capture-files.json').unlink(),False)
    control('missing-completion',lambda r:(r/'COMPLETED.json').unlink(),False)
    control('resealed-extra-directory',lambda r:(r/'extra').mkdir())
    control('resealed-extra-file',lambda r:(r/'extra.txt').write_text('extra'))
    control('resealed-missing-source',lambda r:(r/'source/LICENSE.txt').unlink())
    control('resealed-missing-input',lambda r:(r/'inputs/genotypes.geno').unlink())
    def false_point(root):
        edit(root,'native-result.json',lambda d:d['pairs'][0].__setitem__('estimate',d['pairs'][0]['estimate']+.01))
        rows=(root/'pairs.tsv').read_text().splitlines();row=rows[1].split('\t');row[4]=str(float(row[4])+.01);rows[1]='\t'.join(row);(root/'pairs.tsv').write_text('\n'.join(rows)+'\n')
    control('resealed-coherent-false-FST',false_point)
    def false_locus(root):
        rows=(root/'locus-contributions.tsv').read_text().splitlines();row=rows[1].split('\t');row[6]=str(float(row[6])+.01)
        if float(row[7])>0:row[8]=str(float(row[6])/float(row[7]))
        rows[1]='\t'.join(row);(root/'locus-contributions.tsv').write_text('\n'.join(rows)+'\n')
    control('resealed-false-locus',false_locus)
    control('resealed-population-ID',lambda r:(r/'pairs.tsv').write_text((r/'pairs.tsv').read_text().replace('GROUP_0','group_0')))
    control('resealed-block-weight',lambda r:(r/'blocks.tsv').write_text((r/'blocks.tsv').read_text().replace('0\t5','0\t6',1)))
    control('resealed-negative-estimate-repair',false_point,parent=captures['negative-diploid'])
    control('resealed-diagnostic-admission',lambda r:edit(r,'native-result.json',lambda d:d['pairs'][0].__setitem__('fields_admitted',True)),parent=captures['single-block'])
    control('resealed-deadline',lambda r:edit(r,'compute-process.json',lambda d:d.__setitem__('deadline_exceeded',True)))
    control('resealed-live-owner',lambda r:edit(r,'compute-process.json',lambda d:d.__setitem__('owned_thread_stopped',False)))
    control('resealed-producer',lambda r:edit(r,'producer.json',lambda d:d.__setitem__('backend','EXTERNAL_RUNTIME')))
    control('resealed-warnings',lambda r:(r/'WARNINGS.md').write_text('All zero estimates are identified.'))
    def false_options(root):
        edit(root,'source-request.json',lambda d:d['options'].__setitem__('inbreed',True));edit(root,'canonical-request.json',lambda d:d['options'].__setitem__('inbreed',True))
        def binding(d):d['source_request']['options']['inbreed']=True;d['canonical_request']['options']['inbreed']=True
        edit(root,'request-binding.json',binding);edit(root,'native-result.json',lambda d:d['options'].__setitem__('inbreed',True))
    control('resealed-coherent-false-options',false_options)
    def false_parent(root):
        edit(root,'replay-parent-result.json',lambda d:d['pairs'][0].__setitem__('estimate',d['pairs'][0]['estimate']+.01))
        edit(root,'replay-parent-capture-files.json',lambda d:d['files'].__setitem__('native-result.json',pin(root/'replay-parent-result.json')))
        edit(root,'replay-parent-COMPLETED.json',lambda d:d.__setitem__('seal_identity',pin(root/'replay-parent-capture-files.json')))
        def binding(d):d['parent_seal_identity']=pin(root/'replay-parent-capture-files.json');d['parent_completion_identity']=pin(root/'replay-parent-COMPLETED.json')
        edit(root,'replay.json',binding)
    control('resealed-coherent-false-replay-parent',false_parent,parent=children[0])
    execute('missing-capture',['--verify',str(out/'absent')],3);execute('missing-request',['--request',str(out/'absent.json')],3)
    template=load(out/'run-diploid-complete.json')
    def bad_run(label,change,expected=2):
        raw=json.loads(json.dumps(template));capture=out/('bad-'+label);raw['out']=capture.as_posix();change(raw);request('bad-'+label,raw,expected);assert not (capture/'COMPLETED.json').exists()
        if capture.exists():assert load(capture/'analysis-status.json')['results_admitted'] is False
        return capture
    bad_run('missing-input',lambda d:d['inputs'].__setitem__('genotypes',(out/'absent.geno').as_posix()),3)
    bad_run('PCA-option-in-FST',lambda d:d['options'].__setitem__('maxmissing',0))
    bad_run('work-cap',lambda d:d.__setitem__('limits',dict(maximum_estimated_work=1)))
    bad_run('output-cap',lambda d:d.__setitem__('limits',dict(maximum_output_bytes=1)))
    bad_run('missing-mode',lambda d:d.pop('mode'))
    request('existing-output',template,2);assert snapshot(source)==before_source
    request('nested-replay-output',dict(schema_version=1,mode='REPLAY',analysis=source.as_posix(),out=(source/'child').as_posix()),2);assert snapshot(source)==before_source
    # Actual PCA through the same public route; previous PCA scientific payloads are byte-identical.
    with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/genetics/smartpca-original-analysis-reference.zip') as archive:
        for case in ('shrink-complete','outlier-and-shrink'):
            folder=out/'PCA'/case;folder.mkdir(parents=True)
            for file in ('input.geno','input.snp','input.ind'):(folder/file).write_bytes(archive.read(case+'/'+file))
            ref=json.loads(archive.read(case+'/reference.json'));p=ref['parameters'];options={key:p[key] for key in ('numoutevec','numoutlieriter','numoutlierevec','outliersigmathresh','outliermode','minallelecnt','maxmissing')}
            for key in ('usenorm','altnormstyle','lsqproject','shrinkmode'):options[key]=p[key]=='YES'
            raw=dict(schema_version=1,mode='RUN',engine='native',profile='eig-7.2.1-smartpca-16000-native-pca-v1',operation='PCA',out=(folder/'capture').as_posix(),inputs=dict(genotypes=(folder/'input.geno').as_posix(),markers=(folder/'input.snp').as_posix(),samples=(folder/'input.ind').as_posix(),reference_populations=['REF_A','REF_B'],genome_build='synthetic'),options=options)
            request('PCA-run-'+case,raw);verify('PCA-verify-'+case,Path(raw['out']));child=folder/'replay';request('PCA-replay-'+case,dict(schema_version=1,mode='REPLAY',analysis=raw['out'],out=child.as_posix()));verify('PCA-verify-replay-'+case,child)
    # A real native read/calculation timeout with a bounded complete synthetic input.
    timed=out/'timeout-input';timed.mkdir();samples=4000;markers=2000
    (timed/'input.ind').write_text(''.join(f'i{i:05} U G{i//40:03}\n' for i in range(samples)))
    (timed/'input.snp').write_text(''.join(f'm{i:05} 1 {i*.01:.17g} {(i+1)*1_000_000} A G\n' for i in range(markers)))
    (timed/'input.geno').write_text(''.join(''.join(str(((m*17+i*73)^(m+i*7))%3) for i in range(samples))+'\n' for m in range(markers)))
    def deadline(d):
        d['inputs'].update({k:(timed/v).as_posix() for k,v in [('genotypes','input.geno'),('markers','input.snp'),('samples','input.ind')]});d['inputs']['populations']=None;d['limits']=dict(timeout_seconds=1)
    capture=bad_run('actual-deadline',deadline);receipt=load(capture/'compute-process.json');assert receipt['deadline_exceeded'] and receipt['owned_thread_stopped'] and not receipt['calculation_returned']
    assert pin(a.jar)==producer and snapshot(source)==before_source
    result=dict(status='PASS_GLOBAL_NATIVE_SMARTPCA_FST_PROTOCOL',producer=producer,java=a.java,commands=calls,original_comparisons=comparisons,unique_original_program_calls_reused=24,new_original_C_calls=0,source_capture_unchanged=True,actual_deadline_owned_thread_stopped=True,seconds=time.monotonic()-start,workflow_qualified=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
    save(out/'COMPLETED.json',result);print(json.dumps(dict(status=result['status'],commands=len(calls),original_comparisons=len(comparisons),seconds=result['seconds']),indent=2))

if __name__=='__main__':main()
