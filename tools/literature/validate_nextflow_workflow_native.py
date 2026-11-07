# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the packaged Nextflow capture/session primitives and retained admission controls on three JVMs."""
import argparse,concurrent.futures,hashlib,json,os,re,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin
from catalog_source_integrity import digest,reverse_delta

def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')
def main():
    if not __debug__:raise RuntimeError('Never disable evidence checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/nextflow-runtime';jar=args.artifact.resolve();out=args.out.resolve();assert jar.is_relative_to(base.resolve()) and out.is_relative_to(base.resolve()) and not out.exists();out.mkdir()
    names=sorted('org.jlinalg.cli.'+p.stem for p in (ROOT/'src/test/java/org/jlinalg/cli').glob('Nextflow*Test.java'))+['org.jlinalg.workflow.NextflowTraceAuditTest','org.jlinalg.workflow.NextflowCompletionAuditTest']
    assert len(names)==15,names
    source_paths=sorted((ROOT/'src/main/java/org/jlinalg/cli').glob('Nextflow*.java'))+[ROOT/('src/test/java/'+n.replace('.','/')+'.java') for n in names]+list((ROOT/'src/main/java/org/jlinalg/workflow').glob('Nextflow*.java'))+[Path(__file__),ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java',ROOT/'tools/literature/nextflow_dispatch_bridge.py',ROOT/'docs/literature/omics-2023-2026/nextflow-dispatch-shared-source-compatibility.json',ROOT/'src/test/resources/org/jlinalg/cli/nextflow-workflow/initial-controls.zip',ROOT/'src/test/resources/org/jlinalg/cli/nextflow-workflow/provenance.json']
    sources={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths};artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    parent=base/'completion-development-1/package-1/omicsmatic-0.3.7-nextflow-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        legacy=[n for n in old.namelist() if n.endswith('.class') and not n.startswith(('org/jlinalg/cli/Nextflow','org/jlinalg/workflow/Nextflow'))];changed=[n for n in legacy if current.read(n)!=old.read(n)]
        assert changed==['org/jlinalg/cli/JLinAlgCli.class'],('Unexpected legacy class changes',changed);unchanged=[n for n in legacy if n not in changed]
    bridge=json.loads((ROOT/'docs/literature/omics-2023-2026/nextflow-dispatch-shared-source-compatibility.json').read_bytes());assert bridge['status']=='passed-dispatch-catalog-only-source-deltas' and len(bridge['paths'])==1
    row=bridge['paths'][0];assert row['path']=='src/main/java/org/jlinalg/cli/JLinAlgCli.java';new=(ROOT/row['path']).read_bytes();original=reverse_delta(new,row);assert digest(original)==row['before_sha256']
    chunks=[b''.join(new.splitlines(keepends=True)[edit['after_start_line']:edit['after_end_line']]) for edit in row['edits']];assert set(chunks)=={b'            if (arguments.length > 0 && arguments[0].equals("nextflow-tools"))\n                return NextflowToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n',b'              java -jar omicsmatic-<version>.jar nextflow-tools --help\n'}
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9
    runner=out/'WorkflowChecks.java';runner.write_text('''import java.io.File;
import java.io.PrintWriter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class WorkflowChecks {
 public static void main(String[] args)throws Exception {
  System.out.println(System.getProperty("os.name")+" Java "+System.getProperty("java.version"));
  for(String name:new String[]{"org.jlinalg.cli.NextflowCapture","org.jlinalg.cli.NextflowProcess","org.jlinalg.cli.NextflowBundle"})if(!Class.forName(name).getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(SELECTORS).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));int aborted=System.getProperty("os.name").equals("Linux")?0:4;
  if(s.getTestsFoundCount()!=166||s.getTestsSucceededCount()!=166-aborted||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=aborted||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native workflow checks");
 }
}
'''.replace('SELECTORS',','.join('selectClass("'+n+'")' for n in names)),encoding='utf-8',newline='\n')
    def command(label,argv):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:result=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=900)
        record=dict(command=list(map(str,argv)),exit_code=result.returncode);write(out/(label+'.json'),record)
        if result.returncode:raise RuntimeError(label+' failed: inspect retained stdout/stderr')
        return record
    classes=out/'classes';classes.mkdir();command('compile',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,*[ROOT/('src/test/java/'+n.replace('.','/')+'.java') for n in names]])
    paths=[classes,jar,ROOT/'src/test/resources',*libraries];runtime_base=base/'managed-setup-development-1';properties=['-Djlinalg.nextflow.home.'+v+'='+linux(runtime_base/('home-'+s)) for v,s in [('21.04.1','21-v2'),('22.10.6','22'),('24.04.4','24')]]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep,4),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep,4),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':',0)]
    def check(spec):
        name,java,path,separator,aborted=spec;record=command(name,[*java,*properties,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'WorkflowChecks',path(jar)])
        log=(out/(name+'.stdout')).read_text(encoding='utf-8',errors='replace')
        for count,label in [(166,'tests found'),(166-aborted,'tests successful'),(aborted,'tests aborted'),(0,'tests failed')]:assert re.search(r'\[\s*'+str(count)+' '+label+r'\s*\]',log),(name,label)
        print(name+': '+str(166-aborted)+' passed; '+str(aborted)+' Linux-only filesystem aborts',flush=True);return dict(name=name,tests=166,passed=166-aborted,aborted=aborted,process=record)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,platforms))
    assert artifact==pin(jar) and canonical==pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') and all(pin(ROOT/p)==value for p,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-packaged-Nextflow-capture-session-and-admission-checks',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),runs=runs,source_pins=sources,unrelated_packaged_classes_identical=len(unchanged),legacy_packaged_classes_changed=changed,dispatch_source_inverse_verified=True,canonical_executable_unchanged=canonical,scope='Native component/session/capture checks, six real standalone user-workflow/cache attempts, sixteen corruption controls, and retained exact-runtime, trace, completion, and real guarded engine controls. Windows explicitly checks unavailable execution branches; four Linux filesystem tests abort there. Typed user-workflow protocol coverage is recorded separately.',catalog_promoted=False))
if __name__=='__main__':
    try:main()
    except Exception as failure:print('FAIL2_NEXTFLOW_NATIVE_WORKFLOW_MATRIX: '+str(failure)+'\nRequired action: retain this attempt, inspect the named compile/test/source mismatch and rerun only after correction to NEW output paths.',file=sys.stderr);sys.exit(2)
