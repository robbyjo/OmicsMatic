# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit native markers against all original rows, independent exact tails and packaged failure behavior."""
import argparse,concurrent.futures,copy,csv,gzip,io,json,math,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def table(raw):return list(csv.DictReader(io.StringIO(raw.decode('utf8')),delimiter='\t'))
def close(expected,actual):
    expected=float(expected);actual=float(actual)
    if math.isnan(expected):assert math.isnan(actual)
    elif not math.isfinite(expected) or expected==0:assert expected==actual
    else:assert abs(actual-expected)<=max(64*math.ulp(expected),3e-12*abs(expected)),(expected,actual)


def audit(files,reference=None):
    state=json.loads(files['analysis-status.json']);assert state['result_admitted'] and state['status']=='PASS_NATIVE_TFIDF_MARKERS';request=json.loads(files['inputs/request.json']);genes=[r['gene_id'] for r in table(files['inputs/genes.tsv'])];cells=table(files['inputs/cells.tsv']);entries=table(files['inputs/counts.tsv']);groups=sorted({r['cluster'] for r in cells},key=lambda s:s.encode('utf8'));members={r['cell_id']:r['cluster'] for r in cells};sizes={c:sum(r['cluster']==c for r in cells) for c in groups};obs={(g,c):0 for g in genes for c in groups}
    for r in entries:
        if float(r['value'])>request['expression_cutoff']:obs[r['gene_id'],members[r['cell_id']]]+=1
    total={g:sum(obs[g,c] for c in groups) for g in genes};n=len(cells);all_rows=table(files['results/statistics.tsv']);markers=table(files['results/markers.tsv']);assert [(r['gene_id'],r['cluster']) for r in all_rows]==[(g,c) for c in groups for g in sorted(genes,key=lambda s:s.encode('utf8'))];by_key={(r['gene_id'],r['cluster']):r for r in all_rows};selected=[];outside=0
    for c in groups:
        # Independent combinatorial hypergeometric tails, not jdistlib or R.
        p={}
        for g in genes:
            K=total[g];m=sizes[c];x=obs[g,c];lo=max(x,0,m-(n-K));hi=min(K,m);den=math.comb(n,m);p[g]=sum(math.comb(K,t)*math.comb(n-K,m-t) for t in range(lo,hi+1))/den
        order=sorted(genes,key=p.__getitem__);minimum=1;q={}
        for at in reversed(range(len(order))):minimum=min(minimum,len(order)/(at+1)*p[order[at]]);q[order[at]]=minimum
        score={g:(obs[g,c]/sizes[c])*math.log(n/total[g]) if total[g] else math.nan for g in genes};rank=sorted(sorted(genes,key=lambda s:s.encode('utf8')),key=lambda g:(math.isnan(score[g]),-score[g] if not math.isnan(score[g]) else 0));passed=sum(v<request['fdr'] for v in q.values());limit=request['top_n']
        # Selection is also checked against original returned rows below.
        chosen=rank[:limit] if limit!='ALL' and passed>=limit else [g for g in rank if q[g]<request['fdr']]
        selected.extend((g,c) for g in chosen)
        for g in genes:
            r=by_key[g,c];other=[v for v in groups if v!=c];second=max(obs[g,v]/sizes[v] for v in other);second_name=next(v for v in other if obs[g,v]/sizes[v]==second)
            assert int(r['expressing_cells'])==obs[g,c] and int(r['cluster_cells'])==sizes[c] and int(r['global_expressing_cells'])==total[g] and r['second_best_cluster']==second_name
            for name,value in [('gene_frequency',obs[g,c]/sizes[c]),('outside_frequency',(total[g]-obs[g,c])/(n-sizes[c])),('second_best_frequency',second),('global_frequency',total[g]/n),('tfidf',score[g]),('idf',math.log(n/total[g]) if total[g] else math.inf),('p_value',p[g]),('bh_q_value',q[g])]:close(value,r[name])
            assert (r['selected']=='true')==(g in chosen)
    assert [(r['gene_id'],r['cluster']) for r in markers]==selected and all(r==by_key[r['gene_id'],r['cluster']] for r in markers)
    if reference is not None:
        original=reference['original'];assert original['returned'] and len(original['all_statistics'])==len(all_rows) and [(r['gene_id'],r['cluster']) for r in markers]==[(r['gene'],r['cluster']) for r in original['rows']]
        for r in original['all_statistics']:
            actual=by_key[r['gene'],r['cluster']]
            for name,key in [('gene_frequency','geneFrequency'),('outside_frequency','geneFrequencyOutsideCluster'),('second_best_frequency','geneFrequencySecondBest'),('global_frequency','geneFrequencyGlobal'),('tfidf','tfidf'),('idf','idf'),('bh_q_value','qval')]:close(r[key],actual[name])
            assert actual['second_best_cluster']==r['secondBestClusterName']
    outside=sum(float(r['bh_q_value'])>=request['fdr'] for r in markers);provenance=json.loads(files['provenance.json']);assert provenance['selected_outside_fdr']==outside and not provenance['ambient_rna_estimated_or_removed'];assert provenance['generated_table_bytes']==sum(len(files[n]) for n in ['results/statistics.tsv','results/markers.tsv']);warnings=files['WARNINGS.md'].decode('utf8');assert 'REQUIRED ACTION:' in warnings
    if outside:assert 'SELECTION WARNING:' in warnings
    if any(not math.isfinite(float(r['tfidf'])) for r in all_rows):assert 'NUMERIC DIAGNOSTIC:' in warnings
    return dict(statistics=len(all_rows),markers=len(markers),selected_outside_fdr=outside,complete_original_and_independent_tables_audited=reference is not None)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/soupx-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']};libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    test=ROOT/'src/test/java/org/jlinalg/cli/TfidfMarkersTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/singlecell/soupx-markers';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),test,*fixture.glob('*'),*libraries,*[p for p in (ROOT/'examples/tfidf-markers').glob('*') if p.is_file()]]}
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunMarkers.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunMarkers{public static void main(String[] a)throws Exception{if(!Class.forName("org.jlinalg.cli.TfidfMarkersCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(a[0]).toURI()))throw new AssertionError("Wrong JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.TfidfMarkersTest")).build(),listener);var r=listener.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=171||r.getTestsSucceededCount()!=171||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete marker checks");}}
''',encoding='utf8',newline='\n');command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(test),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-cp',os.pathsep.join(map(str,entries)),'RunMarkers',str(jar)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-cp',os.pathsep.join(map(str,entries)),'RunMarkers',str(jar)],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/java','-cp',':'.join(linux(p) for p in entries),'RunMarkers',linux(jar)]}
    def check(item):
        name,command=item;r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.stdout.decode('utf8',errors='replace')[-1800:],r.stderr.decode('utf8',errors='replace')[-1000:]);return dict(name=name,tests=171,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:jvms=list(pool.map(check,commands.items()))
    cases=json.loads(gzip.decompress((fixture/'original-1.6.2-v2.json.gz').read_bytes()))['cases'];by_id={r['id']:r for r in cases};reports=[]
    def run(name,case,changes=None,code=0,mutate=None,example=False):
        folder=out/name;folder.mkdir();request=dict(schema_version=1,mode='MARKERS',genes='genes.tsv',cells='cells.tsv',counts='counts.tsv',out='result',expression_units='synthetic supplied values',expression_cutoff=case['expressCut'],top_n=case['N'],fdr=case['FDR'])
        if example:
            for p in (ROOT/'examples/tfidf-markers').glob('*.tsv'):(folder/p.name).write_bytes(p.read_bytes())
            request=json.loads((ROOT/'examples/tfidf-markers/request.json').read_bytes())
        else:
            (folder/'genes.tsv').write_text('gene_id\n'+''.join(g+'\n' for g in case['genes']),encoding='utf8',newline='\n');(folder/'cells.tsv').write_text('cell_id\tcluster\n'+''.join(c+'\t'+g+'\n' for c,g in zip(case['cells'],case['clusters'])),encoding='utf8',newline='\n');(folder/'counts.tsv').write_text('gene_id\tcell_id\tvalue\n'+''.join(case['genes'][e['gene']]+'\t'+case['cells'][e['cell']]+'\t'+str(e['value'])+'\n' for e in case['entries']),encoding='utf8',newline='\n')
        request.update(changes or {});save(folder/'request.json',request)
        if mutate:mutate(folder)
        command=[str(JAVA21/'java.exe'),'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.TfidfMarkersCli','--request',str(folder/'request.json')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=60);(folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr);save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==code,(name,r.stderr.decode('utf8',errors='replace')[-1400:]);result=folder/'result'
        report=dict(id=name,exit_code=code,result_admitted=code==0)
        if result.exists():
            files={p.relative_to(result).as_posix():p.read_bytes() for p in result.rglob('*') if p.is_file()};state=json.loads(files['analysis-status.json']);assert state['result_admitted']==(code==0) and ('results/statistics.tsv' in files)==(code==0);assert 'REQUIRED ACTION:' in files['WARNINGS.md'].decode('utf8');assert (files['inputs/request.json']==(folder/'request.json').read_bytes())
            if code==0:report.update(audit(files,None if example else case))
        else:assert code==2 and 'FAIL2 NO RESULT ADMITTED' in r.stderr.decode('utf8')
        reports.append(report)
    for name in ['random-1','random-7','random-18','random-44','random-72','top-N-is-not-an-FDR-filter','threshold-0','threshold-0.9','threshold-1','ties-zero-gene','unicode-C-collation','no-significant-markers']:run(name,by_id[name])
    for name in ['single-cluster','one-gene','empty-expression-group','all-zero']:run(name,by_id[name],code=2)
    good=by_id['ties-zero-gene']
    for name,changes in [('input-capacity',dict(maximum_input_bytes=1)),('gene-capacity',dict(maximum_genes=2)),('entry-capacity',dict(maximum_entries=1)),('pair-capacity',dict(maximum_gene_cluster_pairs=1)),('operation-capacity',dict(maximum_operations=1)),('output-capacity',dict(maximum_output_table_bytes=1)),('unknown-option',dict(unknown=True)),('invalid-FDR',dict(fdr=0))]:run(name,good,changes,code=2)
    def duplicate(folder):
        path=folder/'counts.tsv';rows=path.read_text(encoding='utf8').splitlines();path.write_text('\n'.join([*rows,rows[1]])+'\n',encoding='utf8',newline='\n')
    def invalid(folder):
        path=folder/'counts.tsv';rows=path.read_text(encoding='utf8').splitlines();rows[1]='\t'.join([*rows[1].split('\t')[:2],'NaN']);path.write_text('\n'.join(rows)+'\n',encoding='utf8',newline='\n')
    run('duplicate-coordinate',good,code=2,mutate=duplicate);run('nonfinite-value',good,code=2,mutate=invalid)
    size=(out/'ties-zero-gene/result/results/statistics.tsv').stat().st_size;run('late-output-capacity',good,dict(maximum_output_table_bytes=size+10),code=2);assert (out/'late-output-capacity/result/unadmitted-partial-results/statistics.tsv').read_bytes()==(out/'ties-zero-gene/result/results/statistics.tsv').read_bytes()
    run('documented-example',good,example=True)
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='passed-native-marker-original-and-independent-packaged-controls',artifact=artifact,source_pins=pins,JVMs=jvms,protocols=reports,original_controls={v:pin(base/f'marker-controls-{v}-3/COMPLETED.json') for v in ['1.6.2','1.3.0']},component_admitted=True,implementation_credit=False,whole_goal_complete=False));print('Passed171 checks/JVM and',len(reports),'complete packaged protocols')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific/source checks')
    main()
