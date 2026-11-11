# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test explicit scientific requests, lossless native input/QC gates and missing-runtime skips without running R."""
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
public final class SnprelateAdapterTestRunner {
  private SnprelateAdapterTestRunner() { }
  public static void main(String[] args) {
    var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.SnprelateRequestTest")).build();
    var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);launcher.execute(request);var summary=listener.getSummary();
    summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
    if(summary.getTestsFoundCount()!=20||summary.getTestsSucceededCount()!=20||summary.getTestsSkippedCount()!=0
      ||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
  }
}
'''


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_ADAPTER_UNIT_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False)
    try:
        canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical)
        test=ROOT/'src/test/java/org/jlinalg/cli/SnprelateRequestTest.java';source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [test,Path(__file__)]}
        for name in source_pins:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
        cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');dependencies=[]
        for group,artifact,version in [('org.junit.jupiter','junit-jupiter-api','5.13.4'),('org.junit.jupiter','junit-jupiter-engine','5.13.4'),
                ('org.junit.platform','junit-platform-engine','1.13.4'),('org.junit.platform','junit-platform-launcher','1.13.4'),
                ('org.junit.platform','junit-platform-commons','1.13.4'),('org.apiguardian','apiguardian-api','1.1.2'),('org.opentest4j','opentest4j','1.3.0')]:
            found=list((cache/group/artifact/version).glob('*/*.jar'));assert len(found)==1,(artifact,version);dependencies+=found
        dependency_pins={str(f):pin(f) for f in dependencies};results=[];classes21=None;jar21=None
        def run(command,stem):
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
            stem.with_suffix('.stdout').write_bytes(r.stdout);stem.with_suffix('.stderr').write_bytes(r.stderr)
            save(stem.with_suffix('.process.json'),dict(command=command,exit_code=r.returncode))
            assert r.returncode==0,(r.returncode,r.stdout[-5000:],r.stderr[-2500:])
        for compiler,build in [(21,a.build21.resolve()),(25,a.build25.resolve())]:
            metadata=json.loads((build/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
            jar=ROOT/metadata['artifact']['path'];assert pin(jar)=={k:metadata['artifact'][k] for k in ['bytes','sha256']}
            assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
            folder=out/('javac'+str(compiler));folder.mkdir();classes=folder/'classes';classes.mkdir();temp=folder/'tmp';temp.mkdir()
            runner=folder/'SnprelateAdapterTestRunner.java';runner.write_text(RUNNER,encoding='utf8',newline='\n')
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            cp=';'.join(map(str,[jar,*dependencies]))
            run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(test),str(runner)],folder/'compile')
            run([str(tools/'java.exe'),'-Djava.io.tmpdir='+str(temp),'-Xmx512m','-cp',str(classes)+';'+cp,'SnprelateAdapterTestRunner'],folder/'tests')
            results.append(dict(compiler=compiler,host='windows',test_methods=20,skipped=0))
            if compiler==21:classes21=classes;jar21=jar
        temp=out/'linux-tmp';temp.mkdir()
        run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Djava.io.tmpdir='+linux(temp),'-Xmx512m','-cp',':'.join(linux(f) for f in [classes21,jar21,*dependencies]),'SnprelateAdapterTestRunner'],out/'linux21-tests')
        results.append(dict(compiler=21,host='linux',test_methods=20,skipped=0))
        assert all(pin(ROOT/name)==value for name,value in source_pins.items()) and all(pin(Path(name))==value for name,value in dependency_pins.items()) and pin(canonical)==before
        state.update(status='PASS_SNPRELATE60_ADAPTER_JUNIT_EXECUTIONS_WITHOUT_R',test_methods=20,test_executions=60,results=results,
            source_pins=source_pins,dependency_pins=dependency_pins,original_R_analysis_executed=False,canonical_executable_unchanged=before)
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:4000],flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable typed scientific/input/missing-runtime contracts')
    raise SystemExit(main())
