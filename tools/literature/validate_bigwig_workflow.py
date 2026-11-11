# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-source public native bigWig workflows, original-reference byte linkage and portable main verification."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/bigwig-native'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin

PERMISSIONS=r'''
import json,os,subprocess,sys
from pathlib import Path
root,jar=Path(sys.argv[1]),Path(sys.argv[2]);assert not root.exists();root.mkdir(mode=0o755);root.chmod(0o755)
(root/'out').mkdir();(root/'out').chmod(0o777);(root/'no-write').mkdir();(root/'no-write').chmod(0o555)
for name in ['readable.bdg','forbidden.bdg']:(root/name).write_text('chr\t0\t1\t0.5\n');(root/name).chmod(0o644)
(root/'forbidden.bdg').chmod(0);(root/'reference.sizes').write_text('chr\t10\n');(root/'reference.sizes').chmod(0o644)
results=[]
for label,signal,out in [('forbidden-input',root/'forbidden.bdg',root/'out/read-skip'),('forbidden-output',root/'readable.bdg',root/'no-write/output-skip')]:
 command=['/usr/bin/java','-Xmx512m','-XX:ActiveProcessorCount=2','-jar',str(jar),'bigwig-tools','--input',str(signal),'--sizes',str(root/'reference.sizes'),'--genome-build','synthetic-reference','--units','synthetic scores','--float32','exact','--dictionary','used','--compression','zlib','--out',str(out)]
 with (root/(label+'.stdout')).open('xb') as stdout,(root/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,user=65534,group=65534,extra_groups=(),timeout=60)
 assert r.returncode==3 and 'SKIP3' in (root/(label+'.stderr')).read_text() and not (out/'COMPLETED.json').exists(),(label,r.returncode)
 if label=='forbidden-input':assert 'SKIP3' in (out/'WARNINGS.md').read_text() and json.loads((out/'FAILURE.json').read_bytes())['status']=='SKIP3_CONVERSION_SKIPPED'
 else:assert not out.exists()
 results.append(dict(id=label,uid=65534,gid=65534,exit_code=r.returncode,command=command,overall_completion_absent=True))
(root/'COMPLETED.json').write_text(json.dumps(dict(status='PASSED_ACTUAL_NONPRIVILEGED_NATIVE_PERMISSION_SKIPS',cases=results),indent=2)+'\n');print('Two real nonprivileged permission skips passed',flush=True)
'''


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');folder=ROOT/'docs/literature/bigwig-native-development';prior=json.loads((folder/'VALIDATION.json').read_bytes());parent=ROOT/prior['artifact']['path'];assert pin(parent)=={k:prior['artifact'][k] for k in ['bytes','sha256']} and canonical==prior['canonical_executable_unchanged'];assert pin(folder/'controls.zip')=={k:prior['archive'][k] for k in ['bytes','sha256']}
    prefixes=['org/jlinalg/cli/'+n for n in ['BigWigRequest','BigWigFiles','BigWigText','BigWigWorkflow','BigWigToolsCli']]
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist())) and set(old.namelist())<=set(current.namelist());added=set(current.namelist())-set(old.namelist());changed={n for n in old.namelist() if old.read(n)!=current.read(n)};assert changed=={'org/jlinalg/cli/JLinAlgCli.class'} and len(added)==15 and all(n.endswith('.class') and any(n.startswith(p) for p in prefixes) for n in added);unchanged=len(old.namelist())-len(changed)
    main=['src/main/java/org/jlinalg/genetics/BigWigWriter.java']+['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['BigWigRequest','BigWigFiles','BigWigText','BigWigWorkflow','BigWigToolsCli','JLinAlgCli']];tests=['src/test/java/org/jlinalg/cli/'+n+'.java' for n in ['BigWigToolsCliTest','BigWigWriterTest','BigWigSignalTest','BigWigWorkflowControls']]+['src/test/java/org/jlinalg/genetics/BedGraphSignalsTest.java'];names=main+tests+['tools/literature/validate_bigwig_workflow.py','tools/literature/bigwig-development.init.gradle','tools/literature/bigwig_dispatch_bridge.py'];names.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'examples/bigwig').rglob('*') if p.is_file());names.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/test/resources/igv/bigwig').iterdir() if p.is_file());sources={n:pin(ROOT/n) for n in names};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    def command(name,argv,timeout=900,expected_exit=0):
        argv=list(map(str,argv));started=time.monotonic()
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        row=dict(command=argv,exit_code=r.returncode,elapsed_seconds=time.monotonic()-started);write(out/(name+'.json'),row);assert r.returncode==expected_exit,(name,r.returncode,expected_exit);return row
    # The scientific writer is unchanged. Only the two exact public route/help additions are reversible.
    root=(ROOT/main[-1]).read_bytes();route=b'            if (arguments.length > 0 && arguments[0].equals("bigwig-tools"))\n                return BigWigToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n';help=b'              java -jar omicsmatic-<version>.jar bigwig-tools --help\n';assert root.count(route)==root.count(help)==1;inverse=root.replace(route,b'',1).replace(help,b'',1);assert inverse==(BASE/'workflow-development-1/before-JLinAlgCli.java').read_bytes();baseline=out/'baseline-source';baseline.mkdir();(baseline/'JLinAlgCli.java').write_bytes(inverse);baseline_classes=out/'baseline-classes';baseline_classes.mkdir();command('compile-parent-dispatcher',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent,'-d',baseline_classes,baseline/'JLinAlgCli.java'])
    with zipfile.ZipFile(parent) as z:
        for p in baseline_classes.rglob('*.class'):assert p.read_bytes()==z.read(p.relative_to(baseline_classes).as_posix())
    compiled=out/'source-compiled';compiled.mkdir();command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,*[ROOT/n for n in main]])
    with zipfile.ZipFile(jar) as z:
        classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert len(classes)==25
        for name,path in classes.items():assert path.read_bytes()==z.read(name),'Exact public source differs: '+name
    test_classes=out/'test-classes';test_classes.mkdir();libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;runner=out/'BigWigPublicChecks.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class BigWigPublicChecks {public static void main(String[] args)throws Exception {for(String name:new String[]{"org.jlinalg.cli.JLinAlgCli","org.jlinalg.cli.BigWigToolsCli","org.jlinalg.cli.BigWigText","org.jlinalg.genetics.BigWigWriter"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual public JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.BigWigToolsCliTest"),selectClass("org.jlinalg.cli.BigWigWriterTest"),selectClass("org.jlinalg.cli.BigWigSignalTest"),selectClass("org.jlinalg.genetics.BedGraphSignalsTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=74||s.getTestsSucceededCount()!=74||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete public native bigWig checks");System.out.println("COMPLETE_PUBLIC_BIGWIG_CHECKS74 NO_SKIPS");}}
''',encoding='utf-8',newline='\n');command('compile-tests',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*[ROOT/n for n in tests]])
    prototypes=BASE/'writer-development-1/matrix-5/native-controls';prototype_nodes={}
    for p in prototypes.rglob('*'):
        if p.is_file():
            relative=p.relative_to(prototypes).as_posix();assert pin(p)==prior['archive_files']['native-output-controls/'+relative];prototype_nodes[relative]=pin(p)
    write(out/'PROTOTYPE-PINS.json',prototype_nodes);cp=[test_classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def produce(platform):
        name,java,convert,separator=platform;test=command(name,[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'BigWigPublicChecks',convert(jar)]);assert 'COMPLETE_PUBLIC_BIGWIG_CHECKS74 NO_SKIPS' in (out/(name+'.stdout')).read_text();target=out/('produced-'+name);receipt=command(name+'-public-workflows',[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,[test_classes,jar])),'org.jlinalg.cli.BigWigWorkflowControls','produce',convert(target),convert(prototypes),convert(jar)],timeout=1800);assert 'COMPLETE_PUBLIC_NATIVE_BIGWIG_WORKFLOWS44 INTERVALS159896' in (out/(name+'-public-workflows.stdout')).read_text();print(name+':74 checks and44 complete public workflows passed',flush=True);return dict(name=name,tests=74,passed=74,skipped=0,tests_process=test,public_workflows_process=receipt,public_workflows=44,complete_intervals=159896)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(produce,platforms))
    portable=[]
    for producer,_,_,_ in platforms:
        for consumer,java,convert,separator in platforms:
            row=command(producer+'-portable-on-'+consumer,[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,[test_classes,jar])),'org.jlinalg.cli.BigWigWorkflowControls','verify',convert(out/('produced-'+producer)),convert(prototypes),convert(jar)],timeout=1800);assert 'COMPLETE_PORTABLE_NATIVE_BIGWIG_WORKFLOWS 44' in (out/(producer+'-portable-on-'+consumer+'.stdout')).read_text();portable.append(dict(producer=producer,consumer=consumer,complete_bundles=44,process=row))
    examples=[];(out/'examples').mkdir()
    for name,java,convert,_ in platforms:
        target=out/'examples'/name;target.mkdir()
        for label,precision,dictionary,compression,bases in [('exact','exact','used','zlib',8),('rounding','round','all','none',5)]:
            bundle=target/label;row=command(name+'-example-'+label,[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-jar',convert(jar),'bigwig-tools','--input',convert(ROOT/'examples/bigwig/inputs'/(label+'.bedGraph')),'--sizes',convert(ROOT/'examples/bigwig/inputs/reference.sizes'),'--genome-build','synthetic-reference','--units','arbitrary supplied scores','--float32',precision,'--dictionary',dictionary,'--compression',compression,'--out',convert(bundle)]);verified=command(name+'-example-verify-'+label,[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-jar',convert(jar),'bigwig-tools','--verify',convert(bundle)]);audit=json.loads((bundle/'OUTPUT-AUDIT.json').read_bytes());assert audit['complete_raw_intervals']==3 and audit['covered_bases']==bases;examples.append(dict(platform=name,example=label,fit=row,verify=verified,intervals=3,covered_bases=bases))
    adversarial=[];adversarial_root=out/'adversarial-main';adversarial_root.mkdir()
    for name in ['track','browser']:
        target=adversarial_root/name;target.mkdir();(target/'input.bedGraph').write_text(name+'\t0\t1\t1\textra\n',encoding='utf-8');(target/'reference.sizes').write_text('chr\t10\n',encoding='utf-8');receipt=command('actual-main-malformed-'+name,[JAVA21/'java.exe','-Xmx512m','-jar',jar,'bigwig-tools','--input',target/'input.bedGraph','--sizes',target/'reference.sizes','--genome-build','synthetic-reference','--units','synthetic scores','--float32','exact','--dictionary','all','--compression','zlib','--out',target/'result'],expected_exit=2);assert not (target/'result/COMPLETED.json').exists() and not (target/'result/track.bw').exists() and (target/'result/FAILURE.json').is_file();adversarial.append(dict(row_name=name,process=receipt,overall_completion_absent=True,malformed_coordinate_row_rejected=True))
    native='/var/tmp/omicsmatic-bigwig-permissions-'+uuid.uuid4().hex;write(out/'PERMISSION-OWNER.json',dict(linux_root=native));driver=out/'permission-controls.py';driver.write_text(PERMISSIONS,encoding='utf-8',newline='\n');command('actual-permission-skips',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(driver),native,linux(jar)])
    exporter="""from pathlib import Path
import json,shutil,sys
sys.path.insert(0,sys.argv[1]);from collect_meme_original_controls import census
source,dest=Path(sys.argv[2]),Path(sys.argv[3]);assert source.is_dir() and not dest.exists();before=census(source);shutil.copytree(source,dest);after=census(dest);assert set(before)==set(after)
for name,value in before.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in after[name].items() if k!='mode'}
assert census(source)==before
(dest.parent/'PERMISSION-INVENTORY.json').write_text(json.dumps(before,indent=2)+'\\n')
""";command('capture-permission-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',exporter,linux(ROOT/'tools/literature'),native,linux(out/'permission-controls-captured')]);permissions=json.loads((out/'permission-controls-captured/COMPLETED.json').read_bytes());assert len(permissions['cases'])==2
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(prototypes/n)==v for n,v in prototype_nodes.items());write(out/'COMPLETED.json',dict(status='passed-exact-source-public-native-bigWig-workflows-and-portable-bundles',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,exact_source_classes=25,new_classes=sorted(added),changed_classes=sorted(changed),unchanged_parent_entries=unchanged,parent_artifact=prior['artifact'],canonical_executable_unchanged=canonical,platform_validation=runs,portable_validation=portable,actual_main_examples=examples,actual_malformed_metadata_rejections=adversarial,actual_permission_skips=permissions,packaged_public_workflows=132,complete_packaged_intervals=479688,portable_complete_bundle_verifications=396,actual_main_example_fits=6,actual_main_example_verifications=6,original_reference_linkage=dict(native_checkpoint=pin(folder/'VALIDATION.json'),native_archive=pin(folder/'controls.zip'),protocols_per_format=22,complete_independently_read_intervals_per_format=79948,public_track_bytes_exact_originally_read_native_reference=True,new_UCSC_or_pyBigWig_runs_claimed=False),public_workflow_available=True,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope='Complete public native Java plain/gzip conversion on three JVMs, exact literal decimal precision/reference ledgers, buffered bounded ledger I/O, requested file capacities, precommit guard/finalization, exact unchanged native writer and byte linkage for all22 independently read reference protocols in both input formats, nine producer/consumer portable combinations, six actual executable-main example fits/verifies, two malformed coordinate-bearing metadata rejections and two actual lower-privilege permission skips. Catalog/source-role/browser closure is separate.'))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public/native/reference checks')
    main()
