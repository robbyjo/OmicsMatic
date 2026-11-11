# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete native finite-matrix partitions and R64 sums, then test public API contracts without R."""
import argparse
import csv
import json
import math
import struct
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/genesis-runtime'
RUNNER='''import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class GenesisPartitionTestRunner {
  private GenesisPartitionTestRunner() { }
  public static void main(String[] args) {
    var request=LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("org.jlinalg.genetics.GenesisPcairPartitionTest")).build();
    var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
    launcher.registerTestExecutionListeners(listener);launcher.execute(request);var summary=listener.getSummary();
    summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
    if(summary.getTestsFoundCount()!=10||summary.getTestsSucceededCount()!=10||summary.getTestsSkippedCount()!=0
      ||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)System.exit(2);
  }
}
'''


def rows(path):
    with path.open(encoding='utf8',newline='') as stream:return list(csv.DictReader(stream,delimiter='\t'))


def number(value):
    special={'Inf':math.inf,'-Inf':-math.inf,'NaN':math.nan,'Infinity':math.inf,'-Infinity':-math.inf}
    return special[value] if value in special else float.fromhex(value)


def compare(reference,native):
    expected=rows(reference/'original-case-dispositions.tsv');actual=rows(native/'native-case-dispositions.tsv')
    assert len(expected)==len(actual)==75;successes=source_failures=unsupported=0;source_warnings=0;flagged=0
    for first,second in zip(expected,actual,strict=True):
        name=first['case'];assert name==second['case'];source=reference/name;produced=native/name
        if first['native_finite_profile']=='FALSE':
            assert second['status']=='UNSUPPORTED_OR_INVALID_PROFILE';unsupported+=1;continue
        output=rows(produced/'native-partition.tsv');warnings=[r['id_utf8_hex'] for r in rows(source/'original-warnings.tsv')]
        assert [r['id_utf8_hex'] for r in output if r['kind']=='warning']==warnings,('Original warning identity/order differs',name);source_warnings+=len(warnings)
        if first['status']=='ORIGINAL_ERROR':
            assert second['status']=='SOURCE_REFUSAL';message=(source/'original-error.txt').read_text(encoding='utf8')
            assert ('dim(X)' in message and second['error_code']=='MATRIX_DIMENSION_DROP') or ('All samples related at threshold' in message and second['error_code']=='ALL_SAMPLES_RELATED'),name
            source_failures+=1;continue
        assert second['status']=='NATIVE_SUCCESS';original=rows(source/'original-partition.tsv');assert all(r['id_na']=='FALSE' for r in original)
        assert [(r['set'],r['result_row'],r['id_utf8_hex']) for r in original]==[(r['kind'],r['result_row'],r['id_utf8_hex']) for r in output if r['kind']!='warning'],('Complete source partition/identity/order differs',name)
        identities=rows(source/'kinship-ids.tsv');id_rows={r['id_utf8_hex']:int(r['row']) for r in identities};matrix={(int(r['row']),int(r['column'])):number(r['value_hex']) for r in rows(source/'kinship.tsv')}
        threshold=number(next(r['value'] for r in rows(source/'options.tsv') if r['key']=='kinship_threshold_hex'))
        unrel=[r['id_utf8_hex'] for r in original if r['set']=='unrelated'];violations=sum(matrix[(id_rows[a],id_rows[b])]>threshold for i,a in enumerate(unrel) for b in unrel[i+1:])
        metadata=rows(produced/'native-metadata.tsv')[0]
        assert int(metadata['selected'])==len(original) and int(metadata['unrelated_pairs_above_threshold'])==violations
        flagged+=violations>0;successes+=1
    assert (successes,source_failures,unsupported)==(67,6,2)
    sums=rows(reference/'original-scalar-sums.tsv');actual_sums=rows(native/'native-scalar-sums.tsv');assert len(sums)==len(actual_sums)==512
    for first,second in zip(sums,actual_sums,strict=True):
        assert (first['case'],first['count'])==(second['case'],second['count'])
        assert struct.pack('>d',number(first['source_sum_hex']))==struct.pack('>d',number(second['native_sum_hex'])),('Original R64 sum differs',first,second)
    return dict(source_case_dispositions=75,complete_finite_source_partitions=67,reproduced_original_failures=6,unsupported_nonfinite_inputs_refused=2,
        original_warnings_compared=source_warnings,unchanged_source_partitions_flagged_for_related_unrelated_pairs=flagged,bit_exact_R64_scalar_sums=512)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--build21',type=Path,required=True);p.add_argument('--build25',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_NATIVE_GENESIS_PARTITION_REFERENCE_OR_API_CONTRACTS',new_catalog_credit=False,whole_goal_complete=False,original_R_analysis_executed=False)
    try:
        reference=BASE/'original-partition-controls-4';original=json.loads((reference/'COMPLETED.json').read_bytes())
        assert original['status']=='PASS_ORIGINAL_GENESIS242_MATRIX_PARTITION_AND512_R64_SUM_CONTROLS'
        protected={str(reference/n):v for n,v in original['files'].items()};protected[str(reference/'COMPLETED.json')]=pin(reference/'COMPLETED.json')
        assert all(pin(Path(n))==v for n,v in protected.items())
        producer=pin(Path(__file__));canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_pin=pin(canonical);records=[];builds={};source_pins={}
        def run(command,name):
            r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
            save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stdout[-4500:],r.stderr[-4000:])
        for compiler,folder in [(21,a.build21.resolve()),(25,a.build25.resolve())]:
            build=json.loads((folder/'COMPLETED.json').read_bytes());assert build['status']=='PASS_ISOLATED_NATIVE_GENESIS_PARTITION_API_AND_EXTERNAL_CONSUMER_BUILD'
            assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());source_pins.update(build['source_pins']);builds[compiler]=build
            api=ROOT/build['artifacts']['genesis-partition-api.jar']['path'];consumer=ROOT/build['artifacts']['genesis-partition-consumer.jar']['path']
            assert all(pin(ROOT/v['path'])=={k:v[k] for k in ['bytes','sha256']} for v in build['artifacts'].values())
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            for host in ['windows','linux']:
                label=f'javac{compiler}-{host}';destination=out/label
                command=([str(tools/'java.exe'),'-Xmx512m','-cp',str(consumer)+';'+str(api),'org.jlinalg.genetics.GenesisPartitionReferenceProbe',str(reference),str(destination)] if host=='windows' else
                    ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',linux(consumer)+':'+linux(api),'org.jlinalg.genetics.GenesisPartitionReferenceProbe',linux(reference),linux(destination)])
                run(command,label);compared=compare(reference,destination);save(destination/'COMPARISON.json',compared)
                records.append(dict(compiler=compiler,host=host,**compared));print('PASS_NATIVE_COMPLETE_REFERENCE',label,flush=True)
        cache=Path('C:/Users/User/.gradle/caches/modules-2/files-2.1');dependencies=[]
        for group,artifact,version in [('org.junit.jupiter','junit-jupiter-api','5.13.4'),('org.junit.jupiter','junit-jupiter-engine','5.13.4'),('org.junit.platform','junit-platform-engine','1.13.4'),
            ('org.junit.platform','junit-platform-launcher','1.13.4'),('org.junit.platform','junit-platform-commons','1.13.4'),('org.apiguardian','apiguardian-api','1.1.2'),('org.opentest4j','opentest4j','1.3.0')]:
            matches=list((cache/group/artifact/version).glob('*/*.jar'));assert len(matches)==1;dependencies+=matches
        dependency_pins={str(p):pin(p) for p in dependencies};test=ROOT/'src/test/java/org/jlinalg/genetics/GenesisPcairPartitionTest.java';test_pin=pin(test);unit=[];classes21=None
        for compiler in [21,25]:
            home=out/f'unit-javac{compiler}';home.mkdir();classes=home/'classes';classes.mkdir();runner=home/'GenesisPartitionTestRunner.java';runner.write_text(RUNNER,encoding='utf8',newline='\n')
            api=ROOT/builds[compiler]['artifacts']['genesis-partition-api.jar']['path'];cp=';'.join(map(str,[api,*dependencies]))
            tools=Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if compiler==21 else 'Common Files/Oracle/Java/javapath')
            run([str(tools/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(test),str(runner)],f'unit-compile{compiler}')
            run([str(tools/'java.exe'),'-Xmx512m','-cp',str(classes)+';'+cp,'GenesisPartitionTestRunner'],f'unit-tests{compiler}');unit.append(dict(host='windows',compiler=compiler,tests=10))
            if compiler==21:classes21=classes
        api=ROOT/builds[21]['artifacts']['genesis-partition-api.jar']['path']
        run(['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx512m','-cp',':'.join(linux(f) for f in [classes21,api,*dependencies]),'GenesisPartitionTestRunner'],'unit-linux21');unit.append(dict(host='linux',compiler=21,tests=10))
        assert pin(canonical)==canonical_pin and pin(test)==test_pin and pin(Path(__file__))==producer
        assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and all(pin(Path(n))==v for n,v in {**protected,**dependency_pins}.items())
        state.update(status='PASS300_NATIVE_GENESIS_CASE_DISPOSITIONS_2048_R64_SUMS_AND30_API_TEST_EXECUTIONS',records=records,unit=unit,source_pins=source_pins,
            producer=producer,dependency_pins=dependency_pins,canonical_executable_unchanged=canonical_pin,
            complete_partitions=268,reproduced_original_failures=24,unsupported_nonfinite_refusals=8,scalar_sum_comparisons=2048,JUnit_executions=30,
            scope='Native finite/symmetric/unique-ID matrix partition component only, with exact original ordered sets/warnings, source refusals, flagged source-related unrelated pairs and R4.6.1 Windows long-double64 scalar sums. No R execution, software installation, GENESIS PCA/PC-Relate/model fitting, historical study reproduction or new tool integration credit.')
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:5000],flush=True);return 0 if state['status'].startswith('PASS300_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original source or strict API/canonical preservation checks')
    raise SystemExit(main())
