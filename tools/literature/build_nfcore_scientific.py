# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package native ChIP-seq component auditing with exact existing Java reader dependencies; no released JAR changes."""
import argparse,hashlib,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
 canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);state=dict(status='FAIL2_NFCORE_NATIVE_SCIENTIFIC_BUILD',new_catalog_credit=False,full_nfcore_adapter=False)
 try:
  assert before==dict(bytes=164898617,sha256='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2')
  home=ROOT/'docs/literature/nfcore-engine-development';proof=json.loads((home/'VALIDATION.json').read_bytes());predecessor=home/'nfcore-engine-javac21.jar';assert pin(predecessor)==proof['artifacts'][predecessor.name]
  sources=[*(ROOT/'src/main/java/org/jlinalg/cli'/n for n in ['NfcoreScientificFiles.java','NfcoreChipseq121BamAudit.java','NfcoreChipseq121AuditCli.java']),*(ROOT/'src/main/java/org/jlinalg/workflow'/n for n in ['NfcoreChipseq121Mappings.java','NfcoreChipseq121Frip.java'])]
  source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [*sources,Path(__file__)]}
  for n in source_pins:
   target=out/'source-snapshot'/n;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/n,target)
  classes=out/'classes';classes.mkdir();tools=JAVA if a.compiler=='21' else JAVA25
  command=[str(tools/'javac.exe'),'--release','17','-sourcepath','','-encoding','UTF-8','-Xlint:all','-Werror','-cp',str(predecessor)+';'+str(canonical),'-d',str(classes),*map(str,sources)]
  r=subprocess.run(command,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(predecessor) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old);external={};existing_native={}
  native_families=['org/jlinalg/sequence/FastaIndexes','org/jlinalg/sequence/FastqIO','org/jlinalg/sequence/GtfAnnotations','org/jlinalg/genetics/GenomicIntervals']
  with zipfile.ZipFile(canonical) as z:
   for name in z.namelist():
    if name.endswith('/'):continue
    if name.startswith('org/jlinalg/'):
     if not any(name==prefix+'.class' or name.startswith(prefix+'$') and name.endswith('.class') for prefix in native_families):continue
     raw=z.read(name)
     if name in payload:assert payload[name]==raw,('Existing native dependency collision',name)
     payload[name]=raw;existing_native[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());continue
    if name.endswith('.class') or name.startswith('htsjdk/') or name.startswith('native/'):
     raw=z.read(name)
     if name in payload:assert payload[name]==raw,('External predecessor collision',name)
     payload[name]=raw;external[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    elif name.startswith('META-INF/') and any(k in name.lower() for k in ['license','notice','copyright']):
     payload['META-INF/nfcore-scientific-canonical-notices/'+name[9:]]=z.read(name)
  assert all(prefix+'.class' in existing_native for prefix in native_families)
  payload.update({p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')})
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreChipseq121AuditCli\n\n'
  payload['META-INF/nfcore-scientific/NOTICE']=b'Native source-specific ChIP-seq1.2.1 component auditing only. Exact original mappings, captured FASTQ/reference/GTF and BAM/read-group/FRiP agreement. No nf-core tool credit, whole-pipeline scientific admission or biological accuracy claim. External Java reader classes/resources are byte-identical to the existing canonical distribution; original notices are retained separately.\n'
  artifact=out/'nfcore-scientific-development.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):
    item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==b for n,b in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in source_pins.items())
  state.update(status='PASS_NFCORE_NATIVE_COMPONENT_BUILD_ONLY',compiler=a.compiler,source_pins=source_pins,artifact=dict(path=artifact.relative_to(ROOT).as_posix(),**pin(artifact)),predecessor=pin(predecessor),canonical_dependency=before,external_java_members=external,existing_native_java_members=existing_native,predecessor_scientific_classes_and_resources_unchanged=True,upstream_analysis_executed=False)
 except Exception as e:state.update(cause=repr(e),traceback=traceback.format_exc())
 assert pin(canonical)==before;state['canonical_executable_unchanged']=before;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable canonical, inherited-byte or external dependency checks')
 raise SystemExit(main())
