# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Native SNPRelate arithmetic/QC contracts and independent public API consumers; no R execution."""
import argparse
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'
RUNNER='''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class SnprelateTestRunner {
  private SnprelateTestRunner() { }
  public static void main(String[] args) {
    var request=LauncherDiscoveryRequestBuilder.request().selectors(
      DiscoverySelectors.selectClass("org.jlinalg.genetics.KingRobustKinshipTest"),
      DiscoverySelectors.selectClass("org.jlinalg.genetics.SnprelateProfilesTest")).build();
    var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);launcher.execute(request);var summary=listener.getSummary();
    summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
    if(summary.getTestsFoundCount()!=32||summary.getTestsSucceededCount()!=32||summary.getTestsSkippedCount()!=0
      ||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
  }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--api21',type=Path,required=True);p.add_argument('--api25',type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_SNPRELATE_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');dependencies=[]
        for group,artifact,version in [('org.junit.jupiter','junit-jupiter-api','5.13.4'),('org.junit.jupiter','junit-jupiter-engine','5.13.4'),
                ('org.junit.platform','junit-platform-engine','1.13.4'),('org.junit.platform','junit-platform-launcher','1.13.4'),
                ('org.junit.platform','junit-platform-commons','1.13.4'),('org.apiguardian','apiguardian-api','1.1.2'),('org.opentest4j','opentest4j','1.3.0')]:
            found=list((cache/group/artifact/version).glob('*/*.jar'));assert len(found)==1,(artifact,version);dependencies+=found
        dependency_pins={str(f):pin(f) for f in dependencies}
        sources=[ROOT/name for name in ['src/main/java/org/jlinalg/genetics/KingRobustKinship.java',
            'src/main/java/org/jlinalg/genetics/SnprelateKingRobust.java','src/main/java/org/jlinalg/genetics/SnprelateVariantQc.java',
            'src/test/java/org/jlinalg/genetics/KingRobustKinshipTest.java','src/test/java/org/jlinalg/genetics/SnprelateProfilesTest.java']]
        resource=ROOT/'src/test/resources/org/jlinalg/genetics/native-king-original-reference.zip'
        example=ROOT/'examples/native-king/SnprelateProfileExample.java'
        pins={f.relative_to(ROOT).as_posix():pin(f) for f in [*sources,resource,example,Path(__file__)]}
        for name in pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
        def run(command,stem):
            result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            stem.with_suffix('.stdout').write_bytes(result.stdout);stem.with_suffix('.stderr').write_bytes(result.stderr)
            save(stem.with_suffix('.process.json'),dict(command=command,exit_code=result.returncode))
            assert result.returncode==0,(command,result.stdout[-3000:],result.stderr[-2500:]);return result.stdout
        tests=[];consumers=[];class21=None;api_pins={}
        for compiler,api_home in [(21,a.api21.resolve()),(25,a.api25.resolve())]:
            proof=json.loads((api_home/'COMPLETED.json').read_bytes())
            assert proof['status']=='PASS_NATIVE_SNPRELATE146_COMPLETE_ORIGINAL_MATRIX_QC_AND_TRANSPORT_FIELDS'
            assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
            api=api_home/'snprelate-native-api.jar';assert pin(api)==proof['artifacts'][api.name];api_pins[str(api)]=pin(api)
            folder=out/('javac'+str(compiler));folder.mkdir();classes=folder/'classes';classes.mkdir()
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            runner=folder/'SnprelateTestRunner.java';runner.write_text(RUNNER,encoding='utf8',newline='\n')
            cp=';'.join(map(str,dependencies))
            run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',cp,'-d',str(classes),*map(str,sources),str(runner)],folder/'compile')
            target=classes/resource.relative_to(ROOT/'src/test/resources');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(resource,target)
            run([str(tools/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+cp,'SnprelateTestRunner'],folder/'tests')
            tests.append(dict(compiler=compiler,host='windows',test_methods=32,skipped=0))
            if compiler==21:class21=classes
            # The consumer sees the standalone API JAR only, never project/test classes.
            consumer=folder/'consumer';consumer.mkdir()
            run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(api),'-d',str(consumer),str(example)],folder/'consumer-compile')
            for host in ['windows','linux']:
                launcher=[str(tools/'java.exe')] if host=='windows' else ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java']
                cp=str(consumer)+';'+str(api) if host=='windows' else linux(consumer)+':'+linux(api)
                stdout=run([*launcher,'-cp',cp,'SnprelateProfileExample'],folder/('consumer-'+host))
                assert b'WITHIN_FAMILY kinship=0.0' in stdout and b'BETWEEN_OR_SINGLETONS kinship=NaN raw=-Infinity' in stdout
                assert b'SNP_BY_SAMPLE missing_rate=0x1.999999999999ap-4 selection=ABOVE_MISSING_RATE' in stdout
                assert b'SAMPLE_BY_SNP missing_rate=0x1.9999999999998p-4 selection=SELECTED_AFTER_QC' in stdout
                assert b'WARNING: Uninformative chosen denominator' in stdout
                consumers.append(dict(compiler=compiler,host=host,api_jar_only=True))
        run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(f) for f in [class21,*dependencies]),'SnprelateTestRunner'],out/'linux21-tests')
        tests.append(dict(compiler=21,host='linux',test_methods=32,skipped=0))
        assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(Path(name))==value for name,value in dependency_pins.items())
        assert all(pin(Path(name))==value for name,value in api_pins.items()) and pin(canonical)==before
        state.update(status='PASS_NATIVE_SNPRELATE96_JUNIT_EXECUTIONS_AND4_INDEPENDENT_API_CONSUMERS',
            test_methods=32,new_test_methods=23,test_executions=96,old_original_pair_checks=2880,tests=tests,consumers=consumers,
            source_pins=pins,dependency_pins=dependency_pins,api_pins=api_pins,canonical_executable_unchanged=before,original_R_or_KING_analysis_executed=False)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native contracts and public consumer validation')
    raise SystemExit(main())
