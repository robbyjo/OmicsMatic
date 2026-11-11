# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the native finite GENESIS rank API with original R controls, including grouped ranks and nonfinite refusals."""
import argparse
import csv
import importlib.util
import json
import math
import os
import random
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
from build_genesis_rank_native import DEPENDENCY,DEPENDENCY_SHA
BASE=ROOT/'build/literature-review/genesis-runtime'
ACTION='SKIP dependent original-reference validation when the exact private R runtime, Java runtimes, JUnit dependencies or JDistlib0.10.2 are unavailable or forbidden. Supply permitted prerequisites and retry NEW paths. FAIL2: retain the attempt and correct the reported source/integrity/numerical cause without replacing original values. A rank-component comparison grants no model, tool-catalog or whole-goal completion credit.'


class Unavailable(RuntimeError):pass
RUNNER='''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class GenesisRankTestRunner {
    private GenesisRankTestRunner() { }
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.GenesisRankNormalisationTest")).build();
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(request);
        var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
        if(summary.getTestsFoundCount()!=8||summary.getTestsSucceededCount()!=8||summary.getTestsSkippedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
    }
}
'''


def rows(file):
    with file.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def table(file,header,data):
    with file.open('w',encoding='utf8',newline='') as stream:
        writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(header);writer.writerows(data)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--profile',type=Path,required=True);p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_GENESIS_RANK_VALIDATION',new_catalog_credit=False,whole_goal_complete=False,models_fitted=False);code=2;runtime_before=None;protected={};roots=[]
    try:
        if not a.profile.is_file() or not DEPENDENCY.is_file():raise Unavailable('The exact runtime profile or pinned JDistlib dependency is missing')
        profile=json.loads(a.profile.read_bytes());assert profile['tool']=='GENESIS' and profile['version']=='2.42.0' and profile['r_version']=='4.6.1'
        helper=ROOT/'src/main/resources/org/jlinalg/omics/genesis/prepare_genesis_runtime.py';spec=importlib.util.spec_from_file_location('runtime_closure',helper);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        roots=[Path(profile['r_home']),Path(profile['private_library'])]
        if not all(p.is_dir() for p in roots) or not Path(profile['rscript']).is_file():raise Unavailable('The pinned R executable or complete private library is missing')
        for executable in ['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','C:/Program Files/Common Files/Oracle/Java/javapath/java.exe']:
            if not Path(executable).is_file():raise Unavailable('A required selected Java executable is missing: '+executable)
        cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');dependencies=[]
        for group,artifact,ver in [('org.junit.jupiter','junit-jupiter-api','5.13.4'),('org.junit.jupiter','junit-jupiter-engine','5.13.4'),('org.junit.platform','junit-platform-engine','1.13.4'),('org.junit.platform','junit-platform-launcher','1.13.4'),('org.junit.platform','junit-platform-commons','1.13.4'),('org.apiguardian','apiguardian-api','1.1.2'),('org.opentest4j','opentest4j','1.3.0')]:
            matches=list((cache/group/artifact/ver).glob('*/*.jar'))
            if not matches:raise Unavailable('A required cached JUnit validation dependency is missing: '+artifact+' '+ver)
            assert len(matches)==1;dependencies+=matches
        try:java_probe=subprocess.run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-version'],stdin=subprocess.DEVNULL,capture_output=True,timeout=30)
        except OSError as error:raise Unavailable('The selected Linux Java runtime cannot be launched: '+repr(error)) from error
        (out/'linux-java-version.stdout').write_bytes(java_probe.stdout);(out/'linux-java-version.stderr').write_bytes(java_probe.stderr)
        if java_probe.returncode:raise Unavailable('The selected Linux Java runtime is unavailable; inspect linux-java-version output')
        runtime_before=module.closure(roots);assert runtime_before==profile['files']
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';reference_worker=ROOT/'src/main/resources/org/jlinalg/omics/genesis/rank_reference.R'
        protected={str(f.resolve()):pin(f) for f in [a.profile,helper,canonical,reference_worker,Path(__file__),DEPENDENCY]};assert pin(DEPENDENCY)['sha256']==DEPENDENCY_SHA
        builds={};source_pins={}
        for compiler,home in [(21,a.build21.resolve()),(25,a.build25.resolve())]:
            metadata=json.loads((home/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_NATIVE_GENESIS_RANK_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY';builds[compiler]=metadata;source_pins.update(metadata['source_pins'])
            for artifact in metadata['artifacts'].values():assert pin(ROOT/artifact['path'])=={k:artifact[k] for k in ['bytes','sha256']};protected[str(ROOT/artifact['path'])]=pin(ROOT/artifact['path'])
        assert all(pin(ROOT/n)==v for n,v in source_pins.items())
        inputs=out/'inputs';inputs.mkdir();cases=[];rng=random.Random(20261110)
        def add(name,values,mode='all',groups=None):
            labels=groups if groups is not None else ['unused']*len(values);assert len(labels)==len(values)
            unsupported=any(not isinstance(x,(int,float)) or not math.isfinite(x) for x in values)
            table(inputs/(name+'.tsv'),['input_row','group','value_hex'],[(i+1,labels[i],v if isinstance(v,str) else float(v).hex()) for i,v in enumerate(values)])
            cases.append(dict(case=name,mode=mode,input=name+'.tsv',rows=len(values),native_finite_profile_unsupported=unsupported))
        add('empty',[]);add('singleton',[7]);add('ties',[3,1,1,5]);add('signed-zero',[-0.,0.,-0.,0.]);add('large-signed',[1e300,-1e300,1e-300,-1e-300,0]);add('adjacent-doubles',[math.nextafter(1,0),1,math.nextafter(1,2),1])
        add('literal-unicode-groups',[4,1,8,2,3,9],'by-group',['NA','NaN','家族','NA','NaN','家族'])
        add('all-singleton-groups',[4,1,8,2],'by-group',['NA','NaN','家族','B'])
        for n in [3,7,31,64,101,5000]:
            for repetition in range(10):
                values=[rng.randint(-20,20) if repetition%2==0 else rng.gauss(0,1) for _ in range(n)]
                mode='by-group' if repetition%3==0 else 'all';groups=[['NA','NaN','家族','B'][i%4] for i in range(n)] if mode=='by-group' else None
                add('random-'+str(n)+'-'+str(repetition),values,mode,groups)
        add('tail-100000',list(range(100000)));add('grouped-100000',list(reversed(range(100000))),'by-group',['A' if i%2 else 'B' for i in range(100000)])
        for name,value in [('source-na','NA'),('source-nan','NaN'),('source-positive-inf','Inf'),('source-negative-inf','-Inf')]:add(name,[1,value,2])
        table(inputs/'manifest.tsv',['case','mode','input'],[(r['case'],r['mode'],r['input']) for r in cases]);save(out/'declared-cases.json',cases)
        protected.update({str(f):pin(f) for f in inputs.iterdir() if f.is_file()})
        def run(command,label,timeout=180,environment=None):
            r=subprocess.run(command,cwd=ROOT,env=environment,stdin=subprocess.DEVNULL,capture_output=True,timeout=timeout)
            (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(label,r.stdout[-5000:],r.stderr[-6000:]);return r
        reference=out/'original-reference';reference.mkdir();temp=out/'tmp';temp.mkdir()
        environment=dict(os.environ,R_HOME=profile['r_home'],R_LIBS_USER=profile['private_library'],R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp),LC_ALL='C',LANG='C')
        run([profile['rscript'],'--vanilla',str(reference_worker),profile['private_library'],str(inputs/'manifest.tsv'),str(reference)],'original-reference',environment=environment)
        protected.update({str(f):pin(f) for f in reference.iterdir() if f.is_file()});records=[]
        for compiler,host in [(21,'windows'),(25,'windows'),(21,'linux')]:
            artifacts=builds[compiler]['artifacts'];api=ROOT/artifacts['genesis-rank-api.jar']['path'];consumer=ROOT/artifacts['genesis-rank-consumer.jar']['path'];home=out/('native-'+host+str(compiler))
            if host=='windows':
                tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
                command=[str(tools/'java.exe'),'-Xmx512m','-cp',';'.join(map(str,[api,consumer,DEPENDENCY])),'org.jlinalg.genetics.GenesisRankReferenceProbe','batch',str(inputs/'manifest.tsv'),str(home)]
            else:command=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(f) for f in [api,consumer,DEPENDENCY]),'org.jlinalg.genetics.GenesisRankReferenceProbe','batch',linux(inputs/'manifest.tsv'),linux(home)]
            run(command,'native-'+host+str(compiler));dispositions={r['case']:r for r in rows(home/'native-dispositions.tsv')};compared=0;maximum=0.;unsupported=0
            for case in cases:
                got=dispositions[case['case']]
                if case['native_finite_profile_unsupported']:
                    assert got['status']=='NATIVE_REFUSAL' and 'Nonfinite original residual' in bytes.fromhex(got['cause_utf8_hex']).decode('utf8') and not (home/case['input']).exists();unsupported+=1;continue
                assert got['status']=='NATIVE_SUCCESS';expected=rows(reference/case['input']);actual=rows(home/case['input']);assert len(actual)==len(expected)==case['rows']
                for x,y in zip(expected,actual,strict=True):
                    assert {k:v for k,v in x.items() if k not in ['average_rank_hex','score_hex']}=={k:v for k,v in y.items() if k not in ['average_rank_hex','score_hex']}
                    assert float.fromhex(x['average_rank_hex'])==float.fromhex(y['average_rank_hex']);a_score,b_score=float.fromhex(x['score_hex']),float.fromhex(y['score_hex']);error=abs(a_score-b_score)/max(1,abs(a_score));assert error<=2e-14;maximum=max(maximum,error);compared+=1
            record=dict(host=host,compiler=compiler,cases=len(cases),complete_native_successes=len(cases)-unsupported,unsupported_nonfinite_refusals=unsupported,complete_scores_compared=compared,maximum_scaled_error=maximum);records.append(record);print('PASS_NATIVE_RANK',json.dumps(record),flush=True)
        protected.update({str(f):pin(f) for f in dependencies});units=[];test=ROOT/'src/test/java/org/jlinalg/genetics/GenesisRankNormalisationTest.java'
        for compiler in [21,25]:
            home=out/('unit'+str(compiler));home.mkdir();classes=home/'classes';classes.mkdir();runner=home/'GenesisRankTestRunner.java';runner.write_text(RUNNER,encoding='utf8',newline='\n');api=ROOT/builds[compiler]['artifacts']['genesis-rank-api.jar']['path'];cp=';'.join(map(str,[api,DEPENDENCY,*dependencies]))
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(test),str(runner)],'unit-compile'+str(compiler))
            run([str(tools/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+cp,'GenesisRankTestRunner'],'unit-windows'+str(compiler));units.append(dict(host='windows',compiler=compiler,tests=8))
            if compiler==21:run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(f) for f in [classes,api,DEPENDENCY,*dependencies]),'GenesisRankTestRunner'],'unit-linux21');units.append(dict(host='linux',compiler=21,tests=8))
        assert all(pin(Path(n))==v for n,v in protected.items()) and all(pin(ROOT/n)==v for n,v in source_pins.items())
        state.update(status='PASS_COMPLETE_NATIVE_GENESIS_RANK_REFERENCE_AND_API_CONTRACTS',cases=len(cases),records=records,unit=units,source_pins=source_pins,producer=pin(Path(__file__)),reference_worker=pin(reference_worker),dependency=pin(DEPENDENCY),canonical_executable_unchanged=pin(canonical),score_tolerance=2e-14);code=0
    except Unavailable as error:state.update(status='SKIP3_GENESIS_RANK_REFERENCE_UNAVAILABLE',cause=repr(error));code=3
    except Exception as error:state['cause']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n))==v for n,v in protected.items())
            if runtime_before is not None:assert module.closure(roots)==runtime_before
        except Exception as error:state.update(status='FAIL2_GENESIS_RANK_REFERENCE_PRESERVATION',cause=repr(error));code=2
        state['required_action']=ACTION
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','The finite rank component passed the stated reference/API checks; no model was fitted.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n')
        save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:7000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/source/dependency/canonical preservation checks')
    raise SystemExit(main())
