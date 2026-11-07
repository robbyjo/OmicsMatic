# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check actual-JAR native decoding against every complete original replicate on three JVMs."""
import argparse
import concurrent.futures
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,LIBS,linux,pin


def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    if not __debug__:raise RuntimeError('Never disable exact population checks')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--artifact',type=Path,required=True);p.add_argument('--matrix',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    jar=a.artifact.resolve();matrix=a.matrix.resolve();out=a.out.resolve();base=(ROOT/'build/literature-review/salmon-runtime').resolve();assert all(v.is_relative_to(base) for v in (jar,matrix,out)) and not out.exists();out.mkdir()
    reference=json.loads((matrix/'COMPLETED.json').read_bytes());assert reference['status']=='passed-25-original-Salmon-mapping-alignment-bias-and-inferential-protocols'
    assert reference['original_quantifications']==25 and reference['complete_quant_rows']==1000 and reference['complete_bootstrap_values']==31600
    artifact=pin(jar);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    parent=ROOT/'build/literature-review/nextflow-runtime/typed-workflow-development-1/package-5/omicsmatic-0.3.7-nextflow-development.jar'
    with zipfile.ZipFile(parent) as old,zipfile.ZipFile(jar) as current:
        legacy_class_names=[n for n in old.namelist() if n.endswith('.class')];assert all(current.read(n)==old.read(n) for n in legacy_class_names)
        added=[n for n in current.namelist() if n.endswith('.class') and n not in legacy_class_names];assert len(added)==4 and all(n.startswith('org/jlinalg/sequence/SalmonInferentialReplicates') for n in added)
    protocols=[];files={};total=0
    for family in reference['runs']:
        for row in family['quantifications']:
            if not row['bootstraps']:continue
            relative=family['version']+'/'+row['mode'];folder=matrix/relative
            meta=json.loads((folder/'aux_info/meta_info.json').read_bytes());assert meta['num_bootstraps']==row['bootstraps']
            payload=gzip.decompress((folder/'aux_info/bootstrap/bootstraps.gz').read_bytes());assert len(payload)==row['bootstraps']*40*8
            names=gzip.decompress((folder/'aux_info/bootstrap/names.tsv.gz').read_bytes()).decode().rstrip('\n').split('\t')
            assert names==[line.split('\t')[0] for line in (folder/'quant.sf').read_text().splitlines()[1:]] and len(names)==40
            protocols.append(dict(path=relative,replicates=row['bootstraps'],values=len(payload)//8,sha256=hashlib.sha256(payload).hexdigest()));total+=len(payload)//8
            for name in ['quant.sf','aux_info/meta_info.json','aux_info/bootstrap/names.tsv.gz','aux_info/bootstrap/bootstraps.gz']:files[relative+'/'+name]=pin(folder/name)
    assert len(protocols)==20 and total==31600
    sources={name:pin(ROOT/name) for name in ['src/main/java/org/jlinalg/sequence/SalmonInferentialReplicates.java','src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java','src/main/resources/META-INF/NOTICE-salmon.txt','tools/literature/salmon-development.init.gradle',Path(__file__).relative_to(ROOT).as_posix()]}
    runner=out/'SalmonChecks.java';runner.write_text('''import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.jlinalg.sequence.SalmonInferentialReplicates;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class SalmonChecks {
 public static void main(String[] args)throws Exception {
  if(!SalmonInferentialReplicates.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(new File(args[0]).toURI()))throw new AssertionError("Wrong actual JAR");
  var listener=new SummaryGeneratingListener();LauncherFactory.create().execute(LauncherDiscoveryRequestBuilder.request().selectors(selectClass("org.jlinalg.sequence.SalmonInferentialReplicatesTest")).build(),listener);
  var s=listener.getSummary();s.printTo(new PrintWriter(System.out));s.printFailuresTo(new PrintWriter(System.out));
  if(s.getTestsFoundCount()!=27||s.getTestsSucceededCount()!=27||s.getTestsSkippedCount()!=0||s.getTestsAbortedCount()!=0||s.getTestsFailedCount()!=0||s.getContainersFailedCount()!=0)throw new AssertionError("Incomplete native checks");
  long total=0;
  for(String[] spec:new String[][]{SPECS}) {
   Path folder=Path.of(args[1]).resolve(spec[0]);var rows=Files.readAllLines(folder.resolve("quant.sf"));var names=rows.subList(1,rows.size()).stream().map(line->line.split("\\t",-1)[0]).toList();
   MessageDigest digest=MessageDigest.getInstance("SHA-256");ByteBuffer value=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
   var summary=SalmonInferentialReplicates.read(Files.newInputStream(folder.resolve("aux_info/bootstrap/names.tsv.gz")),Files.newInputStream(folder.resolve("aux_info/bootstrap/bootstraps.gz")),names,Integer.parseInt(spec[1]),Long.parseLong(spec[2]),(replicate,index,name,count)->{value.putLong(0,Double.doubleToRawLongBits(count));digest.update(value.array());},()->{});
   if(summary.values()!=Long.parseLong(spec[2])||!summary.targets().equals(names)||!HexFormat.of().formatHex(digest.digest()).equals(spec[3]))throw new AssertionError("Original binary population differs: "+spec[0]);
   total+=summary.values();System.out.println("ORIGINAL_EXACT "+spec[0]+" "+summary.values()+" "+spec[3]);
  }
  if(total!=31600)throw new AssertionError("Incomplete original population");System.out.println("ALL_ORIGINAL_VALUES_EXACT "+total);
 }
}
'''.replace('SPECS',','.join('{'+','.join(json.dumps(str(row[k])) for k in ['path','replicates','values','sha256'])+'}' for row in protocols)),encoding='utf-8',newline='\n')
    libraries=sorted(LIBS.glob('*.jar'));assert len(libraries)==9;classes=out/'classes';classes.mkdir()
    def command(label,argv):
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:r=subprocess.run(list(map(str,argv)),cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        receipt=dict(command=list(map(str,argv)),exit_code=r.returncode);write(out/(label+'.json'),receipt);assert r.returncode==0,(label,r.returncode);return receipt
    command('compile',[JAVA21/'javac.exe','--release','17','-Xlint:all','-Werror','-cp',os.pathsep.join(map(str,[jar,*libraries])),'-d',classes,runner,ROOT/'src/test/java/org/jlinalg/sequence/SalmonInferentialReplicatesTest.java'])
    paths=[classes,jar,*libraries]
    platforms=[('windows-java21',[JAVA21/'java.exe'],str,os.pathsep),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str,os.pathsep),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux,':')]
    def check(spec):
        name,java,path,separator=spec;receipt=command(name,[*java,'-Xmx128m','-XX:ActiveProcessorCount=2','-cp',separator.join(map(path,paths)),'SalmonChecks',path(jar),path(matrix)]);log=(out/(name+'.stdout')).read_text()
        assert 'ALL_ORIGINAL_VALUES_EXACT 31600' in log and log.count('ORIGINAL_EXACT ')==20
        print(name+':27 tests and31600 original binary64 values pass without skips',flush=True);return dict(name=name,tests=27,passed=27,original_protocols=20,original_values=31600,process=receipt)
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:runs=list(pool.map(check,platforms))
    assert pin(jar)==artifact and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical
    assert all(pin(ROOT/name)==v for name,v in sources.items()) and all(pin(matrix/name)==v for name,v in files.items())
    write(out/'COMPLETED.json',dict(status='passed-three-JVM-native-Salmon-replicate-decoder-and-complete-original-values',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**artifact),runs=runs,source_pins=sources,reference_files=files,reference_protocols=protocols,unchanged_parent_classes=len(legacy_class_names),new_classes=added,canonical_executable_unchanged=canonical,public_quantification_adapter_available=False,Salmon_catalog_status='missing',whole_paper_reproduced=False))


if __name__=='__main__':main()
