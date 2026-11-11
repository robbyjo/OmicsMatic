# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate analytical normal MLE against actual original controls and every packaged table row."""
import argparse
import concurrent.futures
import csv
import io
import json
import math
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,LIBS,linux


def table(raw):return [line.removesuffix('\r').split('\t') for line in raw.decode('utf8').removesuffix('\n').split('\n')]
def close(expected,actual):
    expected=float(expected);actual=float(actual)
    assert actual==expected if not math.isfinite(expected) or expected==0 else abs(actual-expected)<=max(64*math.ulp(expected),3e-12*max(abs(expected),1e-300))


def audit(files,reference):
    state=json.loads(files['analysis-status.json']);assert state['result_admitted'] and state['status']=='PASS_NATIVE_NORMAL_FIT'
    request=json.loads(files['inputs/request.json']);samples=table(files['inputs/samples.tsv']);x=[float(row[1]) for row in samples[1:]]
    mu=math.fsum(x)/len(x);mu+=math.fsum(v-mu for v in x)/len(x);variance=math.fsum((v-mu)**2 for v in x)/len(x);sd=math.sqrt(variance)
    loglik=math.fsum(-.5*math.log(2*math.pi)-math.log(sd)-.5*((v-mu)/sd)**2 for v in x)
    fit=dict(zip(*table(files['results/fit.tsv'])));assert fit['estimator']=='normal-mle' and int(fit['observations'])==len(x)
    for k,v in [('mean',mu),('standard_deviation',sd),('population_variance',variance),('log_likelihood',loglik),('aic',-2*loglik+4),('bic',-2*loglik+2*math.log(len(x)))]:close(v,fit[k])
    count=0
    if request['mode']=='BACKGROUND':
        tested=table(files['inputs/tests.tsv']);actual=table(files['results/tests.tsv']);assert len(actual)==len(tested) and [r[0] for r in actual[1:]]==[r[0] for r in tested[1:]]
        assert [r[1] for r in actual[1:]]==[r[1] for r in tested[1:]]
        p=[float(r[3]) for r in actual[1:]];logp=[float(r[4]) for r in actual[1:]];q=[float(r[5]) for r in actual[1:]];logq=[float(r[6]) for r in actual[1:]];n=len(p)
        order=sorted(range(n),key=p.__getitem__);minimum=1
        for rank in reversed(range(n)):minimum=min(minimum,n/(rank+1)*p[order[rank]]);close(minimum,q[order[rank]])
        order=sorted(range(n),key=logp.__getitem__);minimum=0
        for rank in reversed(range(n)):minimum=min(minimum,math.log(n)-math.log(rank+1)+logp[order[rank]]);close(minimum,logq[order[rank]])
        for row,value in zip(actual[1:],q):assert (row[7]=='true')==(value<=request['cutoff'] if request['cutoff_rule']=='inclusive' else value<request['cutoff'])
        # This packaged family deliberately uses mean0/sd1, bound to original R tails.
        if reference is not None:
            for row,i in zip(actual[1:],range(n)):
                for name,at in [('p',3),('logp',4),('q',5),('logq',6)]:close(reference[name][i],row[at])
        count=n
    assert 'REQUIRED ACTION:' in files['WARNINGS.md'].decode('utf8')
    provenance=json.loads(files['provenance.json']);assert not provenance['rows_filtered'] and not provenance['optimizer_or_paper_replayed']
    assert provenance['generated_table_bytes']==sum(len(raw) for n,raw in files.items() if n.startswith('results/') and n.endswith('.tsv'))
    return dict(observations=len(x),tests=count,complete_fit_and_family_audited=True)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/fitdistrplus-runtime';assert out.is_relative_to(base) and not out.exists();out.mkdir()
    build=json.loads((a.build/'COMPLETED.json').read_bytes());artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    test=ROOT/'src/test/java/org/jlinalg/cli/NormalFitTest.java';fixture=ROOT/'src/test/resources/org/jlinalg/distributional/normal-fit/original-controls-v1.json.gz';libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),test,fixture,*libraries]}
    for name in pins:
        if name.startswith('build/'):continue
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    classes=out/'test-classes';classes.mkdir();runner=out/'RunNormal.java';runner.write_text('''import java.io.*;
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.SummaryGeneratingListener;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class RunNormal{public static void main(String[] args)throws Exception{if(!Class.forName("org.jlinalg.cli.NormalFitToolsCli").getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.NormalFitTest")).build(),listener);var r=listener.getSummary();r.printTo(new PrintWriter(System.out));r.printFailuresTo(new PrintWriter(System.out));if(r.getTestsFoundCount()!=86||r.getTestsSucceededCount()!=86||r.getTestsSkippedCount()!=0||r.getTestsAbortedCount()!=0||r.getTestsFailedCount()!=0||r.getContainersFailedCount()!=0)throw new AssertionError("Incomplete normal checks");}}
''',encoding='utf8',newline='\n')
    command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',str(classes),str(test),str(runner)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(r.stdout);(out/'compile.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    entries=[classes,jar,ROOT/'src/test/resources',*libraries];commands={'windows-java21':[str(JAVA21/'java.exe'),'-cp',os.pathsep.join(map(str,entries)),'RunNormal',str(jar)],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-cp',os.pathsep.join(map(str,entries)),'RunNormal',str(jar)],'linux-java21':['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',':'.join(linux(p) for p in entries),'RunNormal',linux(jar)]}
    def execute(command,label,expected=0):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
        save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode,expected=expected));assert r.returncode==expected,label+': inspect retained diagnostics'
    def contracts(label):execute(commands[label],label);print(label+':86 tests passed',flush=True);return dict(JVM=label,tests=86,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:jvms=list(pool.map(contracts,commands))
    reference=json.loads((base/'original-controls-1/controls.json').read_bytes());reports=[]
    def run(label,rows,tests=None,changes=None,code=0,ref=None):
        folder=out/label;folder.mkdir();(folder/'samples.tsv').write_text('observation_id\tvalue\n'+''.join('o'+str(i)+'\t'+str(v)+'\n' for i,v in enumerate(rows)),encoding='utf8',newline='\n')
        request=dict(schema_version=1,mode='FIT' if tests is None else 'BACKGROUND',estimator='normal-mle',samples='samples.tsv',value_units='synthetic-continuous',out='result')
        if tests is not None:
            (folder/'tests.tsv').write_text('test_id\tvalue\n'+''.join(('NA' if i==0 else ' p"β '+str(i))+'\t'+str(v)+'\n' for i,v in enumerate(tests)),encoding='utf8',newline='\n');request.update(tests='tests.tsv',background_origin='caller-supplied-reference',tail='upper',adjustment='BH',cutoff=0.001,cutoff_rule='strict')
        request.update(changes or {});save(folder/'request.json',request);execute([str(JAVA21/'java.exe'),'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.NormalFitToolsCli','--request',str(folder/'request.json')],label,code)
        state=json.loads((folder/'result/analysis-status.json').read_bytes());assert state['result_admitted'] is (code==0) and (folder/'result/results').exists() is (code==0);result=dict(id=label,exit_code=code,result_admitted=state['result_admitted'])
        if code==0:result.update(audit({p.relative_to(folder/'result').as_posix():p.read_bytes() for p in (folder/'result').rglob('*') if p.is_file()},ref))
        else:result['cause']=state['cause'];assert 'REQUIRED ACTION:' in (folder/'result/WARNINGS.md').read_text(encoding='utf8')
        reports.append(result)
    for name,row in reference['fits'].items():run('fit-'+name,row['data'],code=0 if row['native_positive_finite_variance_domain'] else 2)
    for tail in ['upper','lower','two-sided']:
        ref=next(r for r in reference['probabilities'] if float(r['mean'])==0 and float(r['sd'])==1 and r['tail']==tail);run('background-'+tail,[-1,1],ref['values'],dict(tail=tail),ref=ref)
    for name,changes in [('input-capacity',dict(maximum_input_bytes=1)),('row-capacity',dict(maximum_observations=2)),('operation-capacity',dict(maximum_operations=1)),('output-capacity',dict(maximum_output_table_bytes=1))]:run(name,[-1,0,1],changes=changes,code=2)
    fit_bytes=sum(p.stat().st_size for p in (out/'background-upper/result/results').glob('fit.tsv'));run('late-table-capacity',[-1,1],[0,1],dict(maximum_output_table_bytes=fit_bytes+150),code=2)
    assert (out/'late-table-capacity/result/unadmitted-partial-results/fit.tsv').read_bytes()==(out/'background-upper/result/results/fit.tsv').read_bytes()
    assert all(pin(ROOT/n)==v for n,v in [*pins.items(),*build['source_pins'].items()]) and pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    save(out/'COMPLETED.json',dict(status='passed-native-normal-reference-and-complete-packaged-controls',artifact=artifact,source_pins=pins,JVMs=jvms,protocols=reports,original_control=pin(base/'original-controls-1/COMPLETED.json'),optimizer_replay_claimed=False,implementation_credit=False,whole_goal_complete=False));print('Passed',len(reports),'packaged protocols; original optimizer differences retained')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/reference checks')
    main()
