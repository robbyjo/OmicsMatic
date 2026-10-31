"""Actual executable enrichment workflows on independent known fixtures."""
from pathlib import Path
import argparse,csv,datetime as dt,hashlib,json,shutil,subprocess,traceback,time

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def rows(path):
    with Path(path).open(encoding='utf-8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))
def main():
    p=argparse.ArgumentParser();p.add_argument('--jar',type=Path,required=True);p.add_argument('--rscript',default='C:/Program Files/R/R-4.6.1/bin/Rscript.exe');p.add_argument('--library',type=Path,default=Path('build/omics-enrichment-r'));p.add_argument('--dose-library',type=Path,default=Path('build/omics-dose-legacy-r'));p.add_argument('--report',type=Path,required=True);p.add_argument('--command-timeout',type=int,default=600);a=p.parse_args()
    if not 1<=a.command_timeout<=3600:p.error('--command-timeout must be 1..3600 seconds')
    root=Path(__file__).resolve().parents[2];cache=root/'build/literature-review/enrichment-runtime';folder=cache/('packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'));folder.mkdir(parents=True)
    source=cache/'original-clusterprofiler-probe';inputs=folder/'input data';inputs.mkdir()
    for name in ['ranking.tsv','memberships.tsv','names.tsv','selected.tsv','universe.tsv']:shutil.copy2(source/name,inputs/name)
    initial={p:sha(p) for p in inputs.iterdir()};digest=sha(a.jar)
    runtime_initial={}
    for home,marker in [(a.library,'.jlinalg-enrichment-runtime.json'),(a.dose_library,'.jlinalg-dose-legacy-runtime.json')]:
        record=json.loads((home/marker).read_text(encoding='utf-8'))
        expected={home/name:value for name,value in record['files'].items()};expected[home/marker]=sha(home/marker)
        assert {p for p in home.rglob('*') if p.is_file()}==set(expected)
        for path,value in expected.items():assert sha(path)==value,path
        runtime_initial.update(expected)
    report=dict(status='running',jar_sha256=digest,verifier_sha256=sha(__file__),directory=str(folder),command_timeout_seconds=a.command_timeout,steps=[],checks=[])
    a.report.parent.mkdir(parents=True,exist_ok=True)
    def save():a.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    def run(name,method,extra,expected=0,max_size=50,fixture=None,overrides=None):
        output=folder/name
        base=inputs if fixture is None else fixture
        common=['--method',method,'--memberships',str(base/'memberships.tsv'),'--set-format','tsv','--term-names',str(base/'names.tsv'),'--annotation-id','synthetic-v1','--min-size','3','--max-size',str(max_size),'--duplicate-membership','error','--unmapped-members','drop','--empty','error','--out',str(output),'--no-config']
        for key,value in (overrides or {}).items():
            key='--'+key
            if key in common:
                at=common.index(key)
                if value is None:del common[at:at+2]
                else:common[at+1]=str(value)
            else:common.extend([key,str(value)])
        command=['java','-jar',str(a.jar.resolve()),'enrichment-tools',*common,*map(str,extra)]
        started=time.monotonic()
        child=subprocess.run(command,cwd=root,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=a.command_timeout)
        console=child.stdout+child.stderr;(folder/(name+'.console')).write_text(console,encoding='utf-8')
        entry=dict(name=name,method=method,command=command,exit_code=child.returncode,expected_exit=expected,duration_seconds=round(time.monotonic()-started,3),console_sha256=sha(folder/(name+'.console')));report['steps'].append(entry);save()
        print(name,child.returncode,flush=True)
        if child.returncode!=expected:entry['last_diagnostics']=console[-2200:];return None
        return output
    ranking=['--ranking',inputs/'ranking.tsv','--ranking-format','tsv','--ties','error','--exponent','1','--tail','std','--zero-weight','error']
    external=['--rscript',a.rscript,'--r-library',a.library.resolve(),'--seed','77','--p-adjust','BH','--p-threshold','.05','--undefined','error']
    multi=['--algorithm','multilevel','--eps','1e-5','--sample-size','101','--simple-permutations','500']
    outputs={}
    try:
        for convention in ['fgsea','enrichit-running','enrichit-multilevel']:
            result=run('native-'+convention,'score',[*ranking,'--score-convention',convention]);outputs['native-'+convention]=result
            if result:
                values={x['term']:float(x['enrichment_score']) for x in rows(result/'results.tsv')};assert abs(values['top']-1)<1e-12 and abs(values['bottom']+1)<1e-12
                assert values['middle']==0 if convention=='fgsea' else abs(abs(values['middle'])-.5)<1e-12
        for engine in ['clusterprofiler','dose']:
            runtime=[*external,'--engine',engine,*(['--dose-library',a.dose_library.resolve()] if engine=='dose' else [])]
            result=run(engine+'-ora','ora',[*runtime,'--selected',inputs/'selected.tsv','--universe',inputs/'universe.tsv','--universe-policy','supplied','--query-unmapped','error'],max_size=500);outputs[engine+'-ora']=result
            if result:
                table={r['term']:r for r in rows(result/'results.tsv')};assert set(table)=={'top','mixed','background'}
                assert table['top']['overlap']=='3' and table['top']['query_size']=='3' and table['top']['universe_size']=='100'
                assert abs(float(table['top']['p_value'])-120/161700)<1e-14
                assert abs(float(table['top']['adjusted_p_value'])-360/161700)<1e-14
            result=run(engine+'-multilevel','gsea',[*runtime,*ranking,*multi]);outputs[engine+'-multilevel']=result
            algorithm='permute' if engine=='clusterprofiler' else 'dose'
            result=run(engine+'-'+algorithm,'gsea',[*runtime,*ranking,'--algorithm',algorithm,'--permutations','500']);outputs[engine+'-'+algorithm]=result
        for algorithm in ['multilevel','simple']:
            result=run('fgsea-'+algorithm,'gsea',[*external,'--engine','fgsea',*ranking,*(multi if algorithm=='multilevel' else ['--algorithm','simple','--permutations','500'])]);outputs['fgsea-'+algorithm]=result
        for name,output in outputs.items():
            if output and not name.startswith('native-'):
                assert (output/'upstream/original-object.rds').is_file();assert (output/'upstream/results.tsv').is_file();assert (output/'reported-family.tsv').is_file()
                if 'ora' not in name:
                    table={r['term']:r for r in rows(output/'results.tsv')};assert set(table)=={'top','bottom','mixed','middle'}
                    assert abs(float(table['top']['enrichment_score'])-1)<1e-12 and abs(float(table['bottom']['enrichment_score'])+1)<1e-12
                    if name.startswith('fgsea')or name=='dose-multilevel':assert float(table['middle']['enrichment_score'])==0
                    elif name=='clusterprofiler-multilevel':assert abs(float(table['middle']['enrichment_score'])-.5)<1e-12
                    else:assert abs(float(table['middle']['enrichment_score'])+.5)<1e-12
                    if name.startswith('dose-'):
                        leading=rows(output/'upstream/leading-edges.tsv');bottom=[r['gene'] for r in leading if r['term']=='bottom'];middle=[r['gene'] for r in leading if r['term']=='middle']
                        assert bottom==[f'gene{i:03d}' for i in range(92,101)]
                        assert middle==[f'gene{i:03d}' for i in range(47,56)]
                        legacy={r['term']:r for r in rows(output/'legacy-leading-audit.tsv')};assert legacy['bottom']['original_first_hit_excluded']=='true'
                        assert float(legacy['middle']['raw_helper_es'])==-.5
                    elif name.startswith('fgsea'):
                        leading=rows(output/'upstream/leading-edges.tsv');assert not any(r['term']=='middle' for r in leading)
        # Independent literal-identifier/Unicode fixtures, including headerless
        # RNK whose first gene and statistic are both numeric-looking.
        edge=folder/'edge source';edge.mkdir()
        def write(name,text):
            path=edge/name;path.write_text(text,encoding='utf-8');initial[path]=sha(path);return path
        rank=write('ranking.tsv','gene\tstatistic\n4\t4\n001\t3\nNA\t2\na\t1\nb\t-1\nc\t-2\nd\t-3\ne\t-4\n')
        rnk=write('rank.rnk',rank.read_text(encoding='utf-8').split('\n',1)[1])
        write('memberships.tsv','term\tgene\ntop\t4\ntop\t001\nbottom\td\nbottom\te\ncenter\ta\ncenter\tb\nall\t4\nall\t001\nall\tNA\nall\ta\nall\tb\nall\tc\nall\td\nall\te\n')
        write('names.tsv','term\tname\ntop\tβ activity\nbottom\t日本語 🧬\ncenter\tbalanced set\nall\tcomplete background\n')
        selected=write('selected.tsv','gene\n4\n001\nNA\n');universe=write('universe.tsv','gene\n4\n001\nNA\na\nb\nc\nd\ne\n')
        gmt=write('sets.gmt','top\tβ activity\t4\t001\nbottom\t日本語 🧬\td\te\ncenter\tbalanced set\ta\tb\nall\tcomplete background\t4\t001\tNA\ta\tb\tc\td\te\n')
        erank=['--ranking',rank,'--ranking-format','tsv','--ties','error','--exponent','1','--tail','std','--zero-weight','error']
        small=dict(fixture=edge,overrides={'min-size':1},max_size=3)
        native=run('literal-native-tsv','score',[*erank,'--score-convention','fgsea'],**small)
        rnk_output=run('literal-native-rnk','score',['--ranking',rnk,'--ranking-format','rnk',*erank[4:],'--score-convention','fgsea'],**small)
        gmt_output=run('literal-native-gmt','score',[*erank,'--score-convention','fgsea'],fixture=edge,max_size=3,overrides={'min-size':1,'memberships':gmt,'set-format':'gmt','term-names':None})
        assert native and rnk_output and gmt_output
        assert (native/'results.tsv').read_bytes()==(rnk_output/'results.tsv').read_bytes()==(gmt_output/'results.tsv').read_bytes()
        for engine in ['clusterprofiler','dose']:
            runtime=[*external,'--engine',engine,*(['--dose-library',a.dose_library.resolve()] if engine=='dose' else [])]
            ora=[*runtime,'--selected',selected,'--universe',universe,'--universe-policy','supplied','--query-unmapped','error']
            output=run('literal-'+engine+'-ora','ora',ora,**small);assert output
            table={r['term']:r for r in rows(output/'results.tsv')};assert table['top']['description']=='β activity' and abs(float(table['top']['p_value'])-3/28)<1e-13
            output=run('literal-'+engine+'-gsea','gsea',[*runtime,*erank,*multi],**small);assert output
            table={r['term']:r for r in rows(output/'results.tsv')};assert table['bottom']['description']=='日本語 🧬' and table['top']['description']=='β activity'
            bad=run('q-required-'+engine,'ora',[*ora,'--q-threshold','.01'],expected=2,**small);assert bad
            assert not (bad/'results.tsv').exists() and (bad/'partial/upstream/original-object.rds').exists()
            retained_ora=list(ora);retained_ora[retained_ora.index('--undefined')+1]='retain'
            retained=run('q-retained-'+engine,'ora',[*retained_ora,'--q-threshold','.01'],**small)
            if retained is None:raise AssertionError('Explicit undefined retention failed: '+engine)
            assert all(r['q_value']=='NA' and r['passes_threshold']=='NA' for r in rows(retained/'results.tsv'))
        # Repeat the same seed exactly; a changed seed is recorded explicitly.
        original=outputs['fgsea-simple'];assert original
        repeated=run('fgsea-seed-repeat','gsea',[*external,'--engine','fgsea',*ranking,'--algorithm','simple','--permutations','500']);assert repeated
        assert (original/'results.tsv').read_bytes()==(repeated/'results.tsv').read_bytes()
        changed=list(external);changed[changed.index('--seed')+1]='78'
        changed=run('fgsea-seed-changed','gsea',[*changed,'--engine','fgsea',*ranking,'--algorithm','simple','--permutations','500']);assert changed
        assert json.loads((changed/'upstream/metadata.json').read_text())['seed']==78
        for engine,algorithm in [('clusterprofiler','sample'),('dose','simple')]:
            runtime=[*external,'--engine',engine,*(['--dose-library',a.dose_library.resolve()] if engine=='dose' else [])]
            assert run(engine+'-'+algorithm,'gsea',[*runtime,*ranking,'--algorithm',algorithm,'--permutations','500'])
        for engine in ['clusterprofiler','fgsea','dose']:
            runtime=[*external,'--engine',engine,*(['--dose-library',a.dose_library.resolve()] if engine=='dose' else [])]
            for tail in ['pos','neg']:
                ranked=list(ranking);ranked[ranked.index('--tail')+1]=tail
                omitted=engine=='clusterprofiler' and tail=='neg'
                output=run(engine+'-tail-'+tail,'gsea',[*runtime,*ranked,*multi],expected=2 if omitted else 0);assert output
                if omitted:
                    assert not (output/'results.tsv').exists() and (output/'partial/upstream/original-object.rds').exists()
                    retained_runtime=list(runtime);retained_runtime[retained_runtime.index('--undefined')+1]='retain'
                    retained=run(engine+'-tail-'+tail+'-retained','gsea',[*retained_runtime,*ranked,*multi]);assert retained
                    family={r['term']:r['status'] for r in rows(retained/'reported-family.tsv')}
                    assert family['top']==family['mixed']=='unreported-eligible'
                    assert all(r['adjusted_p_value']=='NA' and r['passes_threshold']=='NA' for r in rows(retained/'results.tsv'))
        for policy,exit_code in [('error',2),('retain',0)]:
            runtime=list(external);runtime[runtime.index('--undefined')+1]=policy
            output=run('fgsea-one-null-'+policy,'gsea',[*runtime,'--engine','fgsea',*ranking,'--algorithm','simple','--permutations','1'],expected=exit_code);assert output
            if policy=='error':assert not (output/'results.tsv').exists() and (output/'partial/upstream/original-object.rds').exists()
            else:assert all(r['adjusted_p_value']=='NA' and r['passes_threshold']=='NA' for r in rows(output/'results.tsv'))
        empty=run('empty-original-retained','gsea',[*external,'--engine','fgsea',*ranking,*multi],overrides={'min-size':1000,'max-size':1000,'empty':'retain'});assert empty
        assert rows(empty/'results.tsv')==[] and not (empty/'upstream').exists()
        missing=run('missing-original-runtime','gsea',['--engine','fgsea','--rscript',folder/'absent-Rscript.exe','--r-library',folder/'absent-library','--seed','77','--p-adjust','BH','--p-threshold','.05','--undefined','error',*ranking,*multi],expected=3);assert missing
        assert 'ANALYSIS SKIPPED' in (missing/'WARNINGS.md').read_text() and not (missing/'results.tsv').exists()
        report['checks'].extend(['numeric/NA identifiers and Unicode descriptions preserved across native TSV/RNK/GMT and both original generic APIs','undefined requested q-values are not BH substitutes; failed raw objects retained','same-seed reproducibility and explicit changed-seed forwarding','all exposed sampling algorithms and signed tails execute original APIs','one-null undefined family suppresses all native adjusted values/significance under explicit retention','empty external analysis records no hypotheses and skips inference; missing runtime returns3 with recovery'])
        assert all(x['exit_code']==x['expected_exit'] for x in report['steps']),[(x['name'],x['exit_code'],x.get('last_diagnostics','')) for x in report['steps'] if x['exit_code']!=x['expected_exit']]
        for path,before in initial.items():assert sha(path)==before,path
        for path,before in runtime_initial.items():assert sha(path)==before,path
        for home in [a.library,a.dose_library]:assert {p for p in home.rglob('*') if p.is_file()}=={p for p in runtime_initial if p.is_relative_to(home)}
        assert sha(a.jar)==digest
        report['checks']+=['native signed/zero/balanced conventions','actual original ORA counts and independent exact hypergeometric/BH values','original full GSEA multilevel/permutation/simple engine differences','original RDS/raw tables and complete family records retained','source inputs/current executable and complete runtime file inventories unchanged'];report['status']='passed-current-original-engine-subsets'
    except BaseException:report.update(status='failed',error=traceback.format_exc());print(report['error'],flush=True)
    save();return 0 if report['status'].startswith('passed')else 1
if __name__=='__main__':raise SystemExit(main())
