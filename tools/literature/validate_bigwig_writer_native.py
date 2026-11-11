# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-source native bigWig tests plus every-interval checks by original UCSC and pyBigWig readers."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/bigwig-native'
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin

ORIGINAL=r'''
import hashlib,json,os,subprocess,sys
from pathlib import Path
root,tools=map(Path,sys.argv[1:]);cases=json.loads((root/'native-controls/CONTROLS.json').read_bytes())['cases'];records=[]
def pin(path):return dict(bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest())
before={p.name:pin(p) for p in tools.iterdir() if p.is_file()};(root/'original-controls').mkdir(mode=0o700)
for spec in cases:
 name=spec['id'];source=root/'native-controls'/name;out=root/'original-controls'/name;out.mkdir(mode=0o700);runs=[]
 commands=[('convert',[str(tools/'bedGraphToBigWig'),'-itemsPerSlot='+str(spec['options']['itemsPerSection']),'-blockSize='+str(spec['options']['treeFanout']),*(['-unc'] if not spec['options']['compressed'] else []),str(source/'signal.bedGraph'),str(source/'reference.sizes'),str(out/'original.bw')]),('read-native',[str(tools/'bigWigToBedGraph'),str(source/'native.bw'),str(out/'native-decoded.bedGraph')]),('native-info',[str(tools/'bigWigInfo'),'-chroms','-zooms',str(source/'native.bw')])]
 for label,command in commands:
  with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
   try:r=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=30);row=dict(label=label,command=command,exit_code=r.returncode,timed_out=False)
   except subprocess.TimeoutExpired:row=dict(label=label,command=command,exit_code=None,timed_out=True)
  runs.append(row)
  if label!='convert':assert row['exit_code']==0 and not row['timed_out'],(name,label,row)
  elif row['exit_code']!=0:assert name in ['empty-declared-track','unsigned32-coordinates'],(name,row)
 (out/'processes.json').write_text(json.dumps(runs,indent=2)+'\n');records.append(dict(id=name,runs=runs))
assert {p.name:pin(p) for p in tools.iterdir() if p.is_file()}==before
(root/'ORIGINAL-CONTROLS.json').write_text(json.dumps(dict(status='completed-original-UCSC-native-file-read-and-converter-probes',tool_pins=before,cases=records),indent=2)+'\n')
print('Original UCSC read every native file:',len(records),flush=True)
'''

INDEPENDENT=r'''
import hashlib,json,math,struct,sys
from pathlib import Path
import pyBigWig
root=Path(sys.argv[1]);assert pyBigWig.__version__=='0.3.26';assert hashlib.sha256(Path(pyBigWig.__file__).read_bytes()).hexdigest()=='e8ffa2730b27c762870d27e3ed51b89d3b84d263099eac2cc7fca505767041df'
cases=json.loads((root/'native-controls/CONTROLS.json').read_bytes())['cases'];results=[]
def f32(value):return struct.unpack('<f',struct.pack('<f',float(value)))[0]
def fingerprint(rows):
 digest=hashlib.sha256()
 for chrom,start,end,value in rows:
  name=chrom.encode();digest.update(struct.pack('<I',len(name)));digest.update(name);digest.update(struct.pack('<QQf',start,end,value))
 return digest.hexdigest()
def summary(path):
 with path.open('rb') as stream:
  head=stream.read(64);offset=struct.unpack_from('<Q',head,44)[0];stream.seek(offset);raw=stream.read(40)
 values=struct.unpack('<Qdddd',raw);names=['covered_bases','minimum','maximum','sum','sum_squares']
 return dict(raw_hex=raw.hex(),fields={k:(repr(v) if isinstance(v,float) and not math.isfinite(v) else v) for k,v in zip(names,values)})
for case in cases:
 folder=root/'native-controls'/case['id'];rows=[]
 for line in (folder/'signal.bedGraph').read_text().splitlines():
  c,a,b,v=line.split('\t');rows.append((c,int(a),int(b),f32(v)))
 rows.sort(key=lambda r:(r[0].encode(),r[1],r[2]));expected=fingerprint(rows);dictionary=case['report']['dictionary']
 with pyBigWig.open(str(folder/'native.bw')) as bw:
  assert bw.isBigWig() and bw.chroms()==dictionary,(case['id'],'dictionary')
  actual=[];signed_coordinate_views=[]
  for chrom in sorted(dictionary,key=lambda c:c.encode()):
   for start,end,value in bw.intervals(chrom) or []:
    if start<0 or end<0:
     assert case['id']=='unsigned32-coordinates' and -2147483648<=start<=2147483647 and -2147483648<=end<=2147483647
     signed_coordinate_views.append(dict(chromosome=chrom,original_signed_start=start,original_signed_end=end,format_unsigned_start=start&0xffffffff,format_unsigned_end=end&0xffffffff));start,end=start&0xffffffff,end&0xffffffff
    actual.append((chrom,start,end,value))
  assert len(actual)==len(rows) and fingerprint(actual)==expected,(case['id'],'raw values')
  decoded=[]
  for line in (root/'original-controls'/case['id']/'native-decoded.bedGraph').read_text().splitlines():
   chrom,start,end,_=line.split('\t');decoded.append((chrom,int(start),int(end)))
  assert decoded==[(c,a,b) for c,a,b,_ in rows],(case['id'],'complete UCSC-decoded unsigned coordinates')
  count=sum(b-a for _,a,b,_ in rows);weighted=math.fsum((b-a)*float(v) for _,a,b,v in rows);squares=math.fsum((b-a)*float(v)*float(v) for _,a,b,v in rows);native=case['report']
  assert count==native['coveredBases']
  for key,value in [('sum',weighted),('sumSquares',squares)]:assert math.isclose(native[key],value,rel_tol=2e-15,abs_tol=1e-300),(case['id'],key,native[key],value)
  header=bw.header();assert header['nLevels']==0 and header['nBasesCovered']==count
  # libBigWig exposes some total-summary fields as Python ints; retain its original header literally.
  native_header=header
 original=root/'original-controls'/case['id']/'original.bw';processes=json.loads((original.parent/'processes.json').read_bytes());conversion=next(r for r in processes if r['label']=='convert');comparison=dict(original_conversion_exit=conversion['exit_code'],original_conversion_timed_out=conversion['timed_out'])
 if conversion['exit_code']==0:
  with pyBigWig.open(str(original)) as bw:
   original_rows=[]
   for chrom in sorted(bw.chroms(),key=lambda c:c.encode()):
    for start,end,value in bw.intervals(chrom) or []:original_rows.append((chrom,start,end,value))
   assert len(original_rows)==len(rows) and fingerprint(original_rows)==expected,(case['id'],'original converter raw values');comparison.update(original_raw_float32_exact=True,original_dictionary=bw.chroms(),original_stored_total_summary=summary(original))
   if any(isinstance(v,str) for v in comparison['original_stored_total_summary']['fields'].values()):comparison['original_header_unavailable']='Not invoked for this nonfinite original summary: a retained direct control observed pinned Cygwin pyBigWig0.3.26 header() segmentation fault on the float32-boundaries original. Complete raw float32 intervals are independently verified, exact raw IEEE summary bytes/nonfinite values retained, and the native double summary remains finite.'
   else:
    try:comparison['original_header']=bw.header()
    except (OverflowError,ValueError) as failure:comparison['original_header_unavailable']=str(failure)
   source_fields=comparison['original_stored_total_summary']['fields'];native_fields=summary(folder/'native.bw')['fields'];comparison['total_summary_differences']=[dict(field=k,native=v,original=source_fields[k]) for k,v in native_fields.items() if v!=source_fields[k]]
 results.append(dict(id=case['id'],complete_records=len(rows),exact_float32_fingerprint=expected,original_pyBigWig_signed_coordinate_views=signed_coordinate_views,complete_UCSC_unsigned_coordinates_exact=True,native_header=native_header,native_stored_total_summary=summary(folder/'native.bw'),native_summary=native,comparison=comparison))
(root/'INDEPENDENT-READERS.json').write_text(json.dumps(dict(status='passed-every-native-and-successful-original-interval-through-pinned-pyBigWig',version=pyBigWig.__version__,module=pyBigWig.__file__,module_sha256=hashlib.sha256(Path(pyBigWig.__file__).read_bytes()).hexdigest(),cases=results),indent=2,allow_nan=False)+'\n');print('Exact independent float32 records:',sum(r['complete_records'] for r in results),'in',len(results),'protocols',flush=True)
'''


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();jar,out=args.artifact.resolve(),args.out.resolve();assert jar.is_relative_to(BASE.resolve()) and out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir(parents=True)
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');parent_proof=json.loads((ROOT/'docs/literature/meme-tomtom-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert canonical==parent_proof['canonical_executable_unchanged']
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and set(old.namelist())<=set(new.namelist());additions=set(new.namelist())-set(old.namelist());assert len(additions)==9 and all(n.startswith('org/jlinalg/genetics/BigWigWriter') and n.endswith('.class') for n in additions);assert all(old.read(n)==new.read(n) for n in old.namelist());unchanged=len(old.namelist())
    names=['src/main/java/org/jlinalg/genetics/BigWigWriter.java','src/test/java/org/jlinalg/cli/BigWigWriterTest.java','src/test/java/org/jlinalg/cli/BigWigWriterControls.java','src/test/java/org/jlinalg/cli/BigWigSignalTest.java','src/test/java/org/jlinalg/genetics/BedGraphSignalsTest.java','tools/literature/validate_bigwig_writer_native.py','tools/literature/bigwig-development.init.gradle','tools/literature/intake_bigwig_reference.py'];names.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/test/resources/igv/bigwig').iterdir() if p.is_file());sources={n:pin(ROOT/n) for n in names};write(out/'SOURCE-PINS.json',sources)
    for name in sources:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    def command(name,argv,timeout=900):
        argv=list(map(str,argv))
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout)
        write(out/(name+'.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
    compiled=out/'source-compiled';compiled.mkdir();command('compile-exact-source',[JAVA21/'javac.exe','--release','17','-g','-encoding','UTF-8','-Xlint:all','-Werror','-cp',jar,'-d',compiled,ROOT/names[0]])
    with zipfile.ZipFile(jar) as z:
        classes={p.relative_to(compiled).as_posix():p for p in compiled.rglob('*.class')};assert set(classes)==additions
        for name,path in classes.items():assert path.read_bytes()==z.read(name)
    test_classes=out/'test-classes';test_classes.mkdir();libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;runner=out/'BigWigNativeChecks.java';runner.write_text('''import java.io.*;import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class BigWigNativeChecks {public static void main(String[] args)throws Exception {if(!org.jlinalg.genetics.BigWigWriter.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual writer JAR");var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.cli.BigWigWriterTest"),selectClass("org.jlinalg.cli.BigWigSignalTest"),selectClass("org.jlinalg.genetics.BedGraphSignalsTest")).build(),listener);var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));if(s.getTestsFoundCount()!=28||s.getTestsSucceededCount()!=28||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native bigWig checks");System.out.println("COMPLETE_NATIVE_BIGWIG_CHECKS 28 NO_SKIPS");}}
''',encoding='utf-8',newline='\n');command('compile-tests',[JAVA21/'javac.exe','--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',test_classes,runner,*[ROOT/n for n in names[1:5]]])
    cp=[test_classes,jar,ROOT/'src/test/resources',*libraries];platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def test(platform):
        name,java,convert,separator=platform;command(name,[*java,'-Xmx768m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(convert,cp)),'BigWigNativeChecks',convert(jar)]);assert 'COMPLETE_NATIVE_BIGWIG_CHECKS 28 NO_SKIPS' in (out/(name+'.stdout')).read_text();return dict(name=name,tests=28,passed=28,skipped=0)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(test,platforms))
    command('export-native-protocols',[JAVA21/'java.exe','-Xmx768m','-cp',os.pathsep.join(map(str,[test_classes,jar])),'org.jlinalg.cli.BigWigWriterControls',out/'native-controls',jar]);tools=BASE/'source-intake-1/bin';intake=json.loads((tools.parent/'COMPLETED.json').read_bytes());assert all(pin(tools/name)=={k:intake['files']['bin/'+name][k] for k in ['bytes','sha256']} for name in ['bedGraphToBigWig','bigWigToBedGraph','bigWigInfo']);original=out/'original-driver.py';original.write_text(ORIGINAL,encoding='utf-8',newline='\n');command('original-UCSC-controls',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(original),linux(out),linux(tools)])
    independent=out/'independent-reader.py';independent.write_text(INDEPENDENT,encoding='utf-8',newline='\n');profile=json.loads((ROOT/'build/omics-deeptools-runtime-v2/.jlinalg-deeptools.json').read_bytes());critical=['build/omics-deeptools-runtime-v2/launch.sh','build/omics-deeptools-cyg-science-layout-v2/pyBigWig.cpython-39-x86_64-cygwin.dll',str(Path(profile['python_executable']))];reader_pins={}
    for name in critical:
        path=(ROOT/name).resolve() if not Path(name).is_absolute() else Path(name);assert pin(path)['sha256']==profile['files'][str(path)];reader_pins[str(path)]=pin(path)
    command('independent-pyBigWig-readers',[*profile['python_command'],'-B','/cygdrive/'+independent.drive[0].lower()+independent.as_posix()[2:],'/cygdrive/'+out.drive[0].lower()+out.as_posix()[2:]])
    proof=json.loads((out/'INDEPENDENT-READERS.json').read_bytes());assert len(proof['cases'])==22 and sum(r['complete_records'] for r in proof['cases'])==79948 and all(pin(Path(n))==v for n,v in reader_pins.items());assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical and all(pin(ROOT/n)==value for n,value in sources.items())
    write(out/'COMPLETED.json',dict(status='passed-exact-source-native-bigWig-writer-three-JVM-and-independent-original-readers',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),source_pins=sources,exact_source_classes=9,new_classes=sorted(additions),unchanged_parent_entries=unchanged,parent_artifact=parent_proof['artifact'],canonical_executable_unchanged=canonical,runs=runs,original_reference_intake=dict(path=tools.parent.relative_to(ROOT).as_posix(),completion=pin(tools.parent/'COMPLETED.json')),independent_reader_pins=reader_pins,protocols=22,complete_native_intervals=79948,original_reader_results=proof,public_workflow_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope='Native complete raw bigWig format components; original UCSC reader accepts every produced file and pinned pyBigWig verifies all raw float32 values. Original converter probes retain failures and double-summary differences; no zoom approximation, original byte identity, public workflow, paper reconstruction or biological certificate is claimed.'))
    print(json.dumps(dict(status='passed',checks_per_JVM=28,protocols=22,complete_intervals=79948,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native/reference verification')
    main()
