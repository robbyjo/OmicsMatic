# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run GENESIS request/identity/covariance contracts on Java without R, downloads or installation."""
import argparse
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import DISTRO, linux
BASE = ROOT / 'build/literature-review/genesis-runtime'
RUNNER = '''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class GenesisRequestTestRunner {
    private GenesisRequestTestRunner() { }
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.cli.GenesisRequestTest")).build();
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(request);
        var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
        if(summary.getTestsFoundCount()!=10||summary.getTestsSucceededCount()!=10||summary.getTestsSkippedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
    }
}
'''


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--jar', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); jar = a.jar.resolve(); out = a.out.resolve(); assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)
    state = dict(status='FAIL2_GENESIS_REQUEST_CONTRACTS', original_analysis_executed=False, new_catalog_credit=False, whole_goal_complete=False)
    try:
        test = ROOT / 'src/test/java/org/jlinalg/cli/GenesisRequestTest.java'; canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
        protected = {str(f): pin(f) for f in [jar, test, canonical, Path(__file__).resolve()]}; cache = Path('C:/Users/User/.gradle/caches/modules-2/files-2.1'); dependencies = []
        for group, artifact, version in [('org.junit.jupiter','junit-jupiter-api','5.13.4'),('org.junit.jupiter','junit-jupiter-engine','5.13.4'),('org.junit.platform','junit-platform-engine','1.13.4'),
            ('org.junit.platform','junit-platform-launcher','1.13.4'),('org.junit.platform','junit-platform-commons','1.13.4'),('org.apiguardian','apiguardian-api','1.1.2'),('org.opentest4j','opentest4j','1.3.0')]:
            found = list((cache/group/artifact/version).glob('*/*.jar')); assert len(found) == 1; dependencies += found
        protected.update({str(f): pin(f) for f in dependencies}); records = []
        def run(command, label):
            r = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
            (out/(label+'.stdout')).write_bytes(r.stdout); (out/(label+'.stderr')).write_bytes(r.stderr); save(out/(label+'.json'),dict(command=command, exit_code=r.returncode))
            assert r.returncode == 0, (label, r.stdout[-5000:], r.stderr[-5000:])
        for compiler in [21, 25]:
            home = out/('javac'+str(compiler)); home.mkdir(); classes = home/'classes'; classes.mkdir(); runner = home/'GenesisRequestTestRunner.java'; runner.write_text(RUNNER, encoding='utf8', newline='\n')
            tools = Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler == 21 else 'Common Files/Oracle/Java/javapath')
            cp = ';'.join(map(str,[jar,*dependencies])); run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-d',str(classes),'-cp',cp,str(test),str(runner)],'compile'+str(compiler))
            run([str(tools/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+cp,'GenesisRequestTestRunner'],'windows'+str(compiler)); records.append(dict(host='windows',compiler=compiler,tests=10))
            if compiler == 21:
                run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(f) for f in [classes,jar,*dependencies]),'GenesisRequestTestRunner'],'linux21'); records.append(dict(host='linux',compiler=compiler,tests=10))
        assert all(pin(Path(n)) == v for n,v in protected.items()); state.update(status='PASS30_GENESIS_REQUEST_CONTRACT_EXECUTIONS', records=records, protected=protected)
    except Exception as error:
        state['error'] = repr(error); (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state); print(state['status'],state.get('error','')[:7000],flush=True); return 0 if state['status'].startswith('PASS30_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable preservation/contract checks')
    raise SystemExit(main())
