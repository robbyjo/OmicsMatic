# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-source public Tomtom command, original report contracts and complete native/copy verification."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');prior_path=ROOT/'docs/literature/meme-native-runtime-development/VALIDATION.json';prior=json.loads(prior_path.read_bytes());parent=ROOT/prior['artifact']['path'];assert pin(parent)=={k:prior['artifact'][k] for k in ['bytes','sha256']} and canonical==prior['canonical_executable_unchanged']
    sequence=['MemeXml','MemeMotifInputs','TomtomResults'];cli=['TomtomRecipe','TomtomInputs','MemeRuntimeFiles','MemeBuildEvidence','MemePerl','MemeNativeHost','TomtomRuntime','TomtomCapturedInputs','TomtomHtml','TomtomProcessReceipt','TomtomOutput','TomtomBundle','TomtomRuntimeBundle','TomtomWorkflow','TomtomToolsCli','TomtomSetupExport','JLinAlgCli']
    main_sources=[ROOT/'src/main/java/org/jlinalg/sequence'/(n+'.java') for n in sequence]+[ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in cli]
    test_names=['MemeXmlTest','MemeMotifInputsTest','TomtomResultsTest','TomtomTextResultsTest','TomtomPreparedInputsTest','TomtomPreparedRunControls','MemeBuildEvidenceTest','MemePerlTest','TomtomRuntimeTest','TomtomRuntimeControls','TomtomCapturedReportsTest','TomtomToolsCliTest','TomtomWorkflowBundleTest'];tests=[ROOT/'src/test/java/org/jlinalg/cli'/(n+'.java') for n in test_names]
    allowed_prefixes=['org/jlinalg/cli/'+n for n in ['TomtomCapturedInputs','TomtomHtml','TomtomProcessReceipt','TomtomOutput','TomtomBundle','TomtomRuntimeBundle','TomtomWorkflow','TomtomToolsCli','TomtomSetupExport']]
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist())) and set(old.namelist())<=set(current.namelist())
        additions=set(current.namelist())-set(old.namelist());resources={'org/jlinalg/omics/meme-runtime-setup/setup_tomtom.py','org/jlinalg/omics/meme-runtime-setup/build_meme_linux_reference.py'};assert resources<=additions and len(additions)==16 and all(n in resources or n.endswith('.class') and any(n.startswith(p) for p in allowed_prefixes) for n in additions)
        changed={n for n in old.namelist() if old.read(n)!=current.read(n)};assert all(n.startswith('org/jlinalg/cli/JLinAlgCli') or n=='org/jlinalg/cli/TomtomInputs.class' or n.startswith('org/jlinalg/cli/TomtomRuntime') for n in changed),sorted(changed)
        unchanged=len(old.namelist())-len(changed)
    paths=[*main_sources,*tests,Path(__file__),ROOT/'tools/literature/meme-development.init.gradle',ROOT/'tools/literature/freeze_tomtom_report_fixtures.py',ROOT/'tools/literature/freeze_tomtom_workflow_fixtures.py']
    paths.extend(p for p in (ROOT/'examples/tomtom').rglob('*') if p.is_file())
    for folder in [ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup',ROOT/'src/test/resources/org/jlinalg/sequence/meme-suite',ROOT/'src/test/resources/org/jlinalg/sequence/tomtom-contracts',ROOT/'src/test/resources/org/jlinalg/sequence/tomtom-inputs',ROOT/'src/test/resources/org/jlinalg/cli/meme-build-evidence',ROOT/'src/test/resources/org/jlinalg/cli/tomtom-reports',ROOT/'src/test/resources/org/jlinalg/cli/tomtom-workflows']:paths.extend(p for p in folder.iterdir() if p.is_file())
    paths.extend([ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-lock.json',ROOT/'src/main/resources/org/jlinalg/omics/.gitattributes',ROOT/'src/main/resources/META-INF/NOTICE-meme-runtime.txt'])
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in paths};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        dest=out/'source-snapshot'/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/name).read_bytes())
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    def command(name,argv,timeout=900,require_zero=True):
        argv=list(map(str,argv))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        result=dict(command=argv,exit_code=r.returncode);write(out/(name+'.json'),result)
        if require_zero:assert r.returncode==0,(name,r.returncode)
        return result
    # Independently prove the exact inverse of the two dispatcher insertions.
    current=(ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_text()
    route='            if (arguments.length > 0 && arguments[0].equals("tomtom-tools"))\n                return TomtomToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n';help_line='              java -jar omicsmatic-<version>.jar tomtom-tools --help\n'
    assert current.count(route)==current.count(help_line)==1;inverse=current.replace(route,'',1).replace(help_line,'',1);baseline=out/'baseline-source';baseline.mkdir();(baseline/'JLinAlgCli.java').write_text(inverse,encoding='utf-8',newline='\n')
    with zipfile.ZipFile(prior_path.parent/'controls.zip') as z:
        for name in ['TomtomInputs.java','TomtomRuntime.java']:(baseline/name).write_bytes(z.read('source/src/main/java/org/jlinalg/cli/'+name))
    old_classes=out/'baseline-classes';old_classes.mkdir();command('compile-frozen-owner-baselines',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',parent,'-d',old_classes,*sorted(baseline.glob('*.java'))])
    with zipfile.ZipFile(parent) as z:
        for p in old_classes.rglob('*.class'):assert p.read_bytes()==z.read(p.relative_to(old_classes).as_posix()),'Frozen owner baseline differs: '+str(p)
    compiled=out/'source-compiled';compiled.mkdir();command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,*main_sources])
    classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert len(classes)==64
    with zipfile.ZipFile(jar) as z:
        for n,p in classes.items():assert p.read_bytes()==z.read(n),'Exact-source public workflow class differs: '+n
    test_classes=out/'test-classes';test_classes.mkdir();runner=out/'TomtomWorkflowChecks.java'
    selections=','.join('selectClass("org.jlinalg.cli.'+name+'")' for name in test_names if not name.endswith('Controls'))
    runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class TomtomWorkflowChecks {public static void main(String[] args)throws Exception {
 for(String name:new String[]{"org.jlinalg.cli.JLinAlgCli","org.jlinalg.cli.TomtomToolsCli","org.jlinalg.cli.TomtomWorkflow","org.jlinalg.cli.TomtomCapturedInputs","org.jlinalg.cli.TomtomHtml","org.jlinalg.cli.TomtomRuntime"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR: "+name);
 var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors('''+selections+''').build(),listener);var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out));summary.printFailuresTo(new PrintWriter(System.out));if(summary.getTestsFoundCount()!=286||summary.getTestsSucceededCount()!=286||summary.getTestsSkippedCount()!=0||summary.getTestsAbortedCount()!=0||summary.getTestsFailedCount()!=0||summary.getContainersFailedCount()!=0)throw new AssertionError("Incomplete public workflow/component checks");System.out.println("COMPLETE_TOMTOM_WORKFLOW_CHECKS 286 NO_SKIPS");}}
''',encoding='utf-8',newline='\n')
    command('compile-packaged-tests',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*tests])
    cp=[test_classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def run(platform):
        name,java,convert,sep=platform;receipt=command(name,[*java,'-Xmx768m','-XX:ActiveProcessorCount=2','-cp',sep.join(map(convert,cp)),'TomtomWorkflowChecks',convert(jar)]);assert 'COMPLETE_TOMTOM_WORKFLOW_CHECKS 286 NO_SKIPS' in (out/(name+'.stdout')).read_text();print(name+':286 actual-JAR checks passed without skips',flush=True);return dict(name=name,tests=286,passed=286,skipped=0,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(run,platforms))
    build=BASE/'linux-build-3';home=json.loads((build/'COMPLETED.json').read_bytes())['prefix'];native='/var/tmp/omicsmatic-tomtom-workflow-'+uuid.uuid4().hex;write(out/'NATIVE-OWNER.json',dict(linux_root=native,home=home,build_evidence=linux(build),status='preparing-public-main-controls'))
    initialize='''from pathlib import Path
import json,sys
root=Path(sys.argv[1]);assert not root.exists();root.mkdir(mode=0o700)
for name in ['data','requests','outputs']:(root/name).mkdir(mode=0o700)
motifs=root/'data/original motifs α with spaces.meme';motifs.write_bytes(Path(sys.argv[2]).read_bytes())
for label,mode,distance in [('text','TEXT','kullback'),('full','FULL_REPORT','ed')]:
 request=dict(schema_version=1,profile='linux-x86_64-tomtom-5.5.9',query=str(motifs),targets=[str(motifs)],distance=distance,reverse_complement=True,pseudocount=.1,minimum_overlap=1,internal=False,complete_scores=True,threshold=dict(kind='QVALUE',value=1),background=dict(source='QUERY',path=None),output_mode=mode,limits=dict(input_bytes=8388608,decoded_characters=8388608,matrix_values=1000000,table_bytes=8388608,wall_seconds=240,cpu_seconds=240,address_space_bytes=2147483648,file_bytes=67108864,workspace_bytes=536870912,files=10000,processes=32,threads=256,sampled_rss_bytes=1073741824,poll_millis=50))
 (root/'requests'/(label+'.json')).write_text(json.dumps(request,indent=2)+'\\n')
print('Prepared two complete public native-main requests at',root)
'''
    command('initialize-public-native-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',initialize,native,linux(ROOT/'src/main/resources/org/jlinalg/omics/meme-runtime-setup/health-motifs.meme')])
    public=[];failure=None
    for label in ['text','full']:
        result=command('linux-public-'+label,['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx1024m','-XX:ActiveProcessorCount=2','-jar',linux(jar),'tomtom-tools','--recipe',native+'/requests/'+label+'.json','--home',home,'--build-evidence',linux(build),'--out',native+'/outputs/'+label],timeout=3600,require_zero=False);public.append(dict(label=label,process=result))
        if result['exit_code']!=0:failure=label;break
        print('linux-public-'+label+':actual main completed',flush=True)
    exporter='''from pathlib import Path
import json,shutil,sys
sys.path.insert(0,sys.argv[1]);from collect_meme_original_controls import census
source,dest=Path(sys.argv[2]),Path(sys.argv[3]);assert source.is_dir() and not dest.exists();before=census(source);shutil.copytree(source,dest);after=census(dest);assert set(before)==set(after)
for name,value in before.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in after[name].items() if k!='mode'}
assert census(source)==before
(dest.parent/'NATIVE-INVENTORY.json').write_text(json.dumps(before,indent=2)+'\\n');print('Complete public native capture:',len(before),'nodes')
'''
    command('capture-public-native-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',exporter,linux(ROOT/'tools/literature'),native,linux(out/'captured')]);assert failure is None,('public main failed',failure)
    verifies=[]
    for label in ['text','full']:
        for name,java,convert,sep in platforms:
            receipt=command(name+'-verify-'+label,[*java,'-Xmx1024m','-XX:ActiveProcessorCount=2','-jar',convert(jar),'tomtom-tools','--verify',convert(out/'captured/outputs'/label)]);verifies.append(dict(name=name,label=label,process=receipt))
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/name)==value for name,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-public-Tomtom-workflows-and-portable-main-verification',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,runs=runs,exact_source_bytecode_classes=64,added_classes=sorted(additions),changed_classes=sorted(changed),unchanged_prior_archive_entries=unchanged,original_parent=prior['artifact'],canonical_executable_unchanged=canonical,native_root=native,public_main_runs=public,portable_actual_main_verifications=verifies,
        public_execution_adapter_available=True,complete_public_workflows=2,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        scope='Complete typed current5.5.9 original text/full public workflows, exact original HTML scientific display payloads, unchanged captured inputs, fresh runtime health, full preparation/process accounting and complete portable output/pair/source/runtime verification. Copied verification never creates a ticket. Pinned setup export and semantic mutation tests included; actual setup/example evidence and catalog closure are separately required.'))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public main/source/portable evidence checks')
    main()
