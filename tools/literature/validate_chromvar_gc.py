# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original GC expressions and compare the native API on actual indexed references and three JVMs."""
import argparse,concurrent.futures,json,os,subprocess,zipfile,shutil
from pathlib import Path
from validate_mashr_native import JAVA21,LIBS,linux,pin
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-chromVAR-GC-controls-in-progress'))
    try:
        parent=json.loads((ROOT/'docs/literature/chromvar-deviations-development/VALIDATION.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items())
        jar=ROOT/parent['artifact']['path'];assert pin(jar)=={k:parent['artifact'][k] for k in ['bytes','sha256']}
        canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
        source_names=['src/main/java/org/jlinalg/sequence/ChromVarGcBias.java','src/test/java/org/jlinalg/cli/ChromVarGcBiasTest.java','tools/literature/collect_chromvar_gc.R','tools/literature/validate_chromvar_gc.py']
        dep=BASE/'motif-dependency-source-2'
        source_names += [(dep/f'chromVAR-{v}/source/chromVAR/R/background_peaks.R').relative_to(ROOT).as_posix() for v in ['1.16.0','1.34.1']]
        sources={n:pin(ROOT/n) for n in source_names};save(out/'SOURCE-PINS.json',sources)
        for n in sources:
            dest=out/'source-snapshot'/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes((ROOT/n).read_bytes())
        def command(name,argv,cwd=ROOT,env=None):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=180,check=False)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode))
            assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1200:])
        fixture=out/'fixture';fixture.mkdir()
        sequences=['','A','C','G','T','ACGT','NNNN','-+','RYSWKMBDHVN','acgt','ggccaaTT','ANGC','NCGN','ryswkmbdhvn','GNC','NATN','cNg','ACGTN-+RYSWKMBDHVN','A'*4097+'GCN','GC'*32769+'N','+CG-','nGgCaTn']
        cases=[dict(id=f'sequence-{i}',sequence=s) for i,s in enumerate(sequences)];ranges=[];reference=''
        for case in cases:
            if case['sequence']:
                ranges.append(dict(id=case['id'],chromosome='chr1',start=len(reference)+1,end=len(reference)+len(case['sequence'])))
                reference+=case['sequence']+'NNNN'
        fasta=fixture/'reference.fa';fasta.write_text('>chr1\n'+'\n'.join(reference[i:i+80] for i in range(0,len(reference),80))+'\n',encoding='ascii',newline='\n')
        fai=fixture/'reference.fa.fai';fai.write_text(f'chr1\t{len(reference)}\t6\t80\t81\n',encoding='ascii',newline='\n');save(fixture/'cases.json',dict(cases=cases,ranges=ranges))
        protocol=out/'protocol.txt';protocol.write_text('\n'.join(map(str,[BASE/'motif-reference-setup-1/R-lib',BASE/'reference-setup-1/R-lib',ROOT/'build/omics-sctransform-r',fixture/'cases.json',fasta,dep]))+'\n',encoding='utf8',newline='\n')
        env={k:v for k,v in os.environ.items() if not k.startswith('LC_') and k not in {'LANG','LANGUAGE','R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS'}};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
        command('original-R',['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',ROOT/source_names[2],protocol],cwd=fixture,env=env)
        original=json.loads((fixture/'original.json').read_bytes());assert len(original['profiles'])==2 and all(len(p['direct'])==len(cases) and len(p['ranges'])==len(ranges) for p in original['profiles'].values())
        classes=out/'classes';classes.mkdir();command('compile-native',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',classes,ROOT/source_names[0]])
        command('javadoc',[JAVA21/'javadoc.exe','-quiet','-Xdoclint:none','-Werror','-encoding','UTF-8','-classpath',jar,'-d',out/'javadoc',ROOT/source_names[0]])
        additions={p.relative_to(classes).as_posix():p for p in classes.rglob('*.class')};candidate=out/'omicsmatic-0.3.7-chromvar-gc-development.jar'
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate,'x') as after:
            assert not set(additions).intersection(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,path in sorted(additions.items()):
                info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;after.writestr(info,path.read_bytes())
        with zipfile.ZipFile(jar) as before,zipfile.ZipFile(candidate) as after:assert after.testzip() is None and len(after.namelist())==len(set(after.namelist())) and set(after.namelist())-set(before.namelist())==set(additions) and all(before.read(n)==after.read(n) for n in before.namelist())
        libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;tests=2*len(cases)+5
        runner=out/'ChromVarGcChecks.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public final class ChromVarGcChecks{public static void main(String[] args)throws Exception{if(!org.jlinalg.sequence.ChromVarGcBias.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong GC candidate");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.ChromVarGcBiasTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));long expected=Long.parseLong(args[1]);if(s.getTestsFoundCount()!=expected||s.getTestsSucceededCount()!=expected||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete GC controls");System.out.println("COMPLETE_NATIVE_CHROMVAR_GC "+expected+" NO_SKIPS");}}
''',encoding='utf8',newline='\n')
        testclasses=out/'test-classes';testclasses.mkdir();command('compile-tests',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[candidate,*libraries])),'-d',testclasses,runner,ROOT/source_names[1]])
        cp=[testclasses,candidate,*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
        def test(spec):
            name,java,convert,separator=spec;command(name,[*java,'-Xmx512m','-XX:ActiveProcessorCount=2','-Dchromvar.gc.fixture='+convert(fixture),'-cp',separator.join(map(convert,cp)),'ChromVarGcChecks',convert(candidate),tests]);return dict(platform=name,tests=tests,passed=tests,skipped=0)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in parent['source_pins'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
        checked=ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-gc';assert not checked.exists();checked.mkdir()
        for p in fixture.iterdir():
            if p.is_file():shutil.copyfile(p,checked/p.name)
        (checked/'.gitattributes').write_text('*.json -text\n*.fa -text\n*.fai -text\n',encoding='utf8',newline='\n')
        save(checked/'CAPTURE.json',dict(schema_version=1,source_pins=sources,files={p.name:pin(p) for p in fixture.iterdir() if p.is_file()},method='Exact unchanged addGCBias sequence formula from two editions, Biostrings DNAStringSet and real Rsamtools indexed FASTA queries; whole historical packages/BSgenome not executed.',R_dependencies=original['versions']))
        result=dict(status='passed-native-chromVAR-GC-original-formula-and-indexed-reference-controls',source_pins=sources,artifact=dict(path=candidate.relative_to(ROOT).as_posix(),**pin(candidate)),parent_artifact=parent['artifact'],added_classes=len(additions),platform_validation=runs,sequence_cases=len(cases),source_profiles=2,indexed_reference_ranges=len(ranges),fixture_files={p.name:pin(p) for p in checked.iterdir() if p.is_file()},canonical_executable_unchanged_during_this_validation=canonical,background_sampling_performed=False,historical_whole_packages_executed=False,full_Signac_workflow_available=False,catalog_implementation_credit=False)
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],platform_validation=runs,artifact=result['artifact'])),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_CHROMVAR_GC_CONTROLS',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native GC controls')
    main()
