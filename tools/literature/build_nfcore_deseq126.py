# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a separate pinned original-postfit/native-consistency development executable."""
import argparse,json,shutil,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25

SOURCES=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['NfcoreDeseq126Audit','NfcoreDeseq126Runtime','NfcoreDeseq126AuditCli']]
RESOURCE='src/main/resources/org/jlinalg/omics/nfcore-deseq126'

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--compiler',choices=['21','25'],required=True);a=p.parse_args()
 out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');state=dict(status='FAIL2_ORIGINAL_POSTFIT_BUILD',nfcore_tool_credit=0)
 try:
  predecessors=[ROOT/f'docs/literature/nfcore-consensus-development/nfcore-consensus-javac{a.compiler}.jar',ROOT/f'docs/literature/nfcore-captured-development/nfcore-captured-javac{a.compiler}.jar',ROOT/f'docs/literature/source-numerics-development/source-numerics-javac{a.compiler}.jar']
  # Bind published predecessor files to their published complete validation receipts.
  for path in predecessors:
   proof=json.loads((path.parent/'VALIDATION.json').read_bytes());assert pin(path)==proof['artifacts'][path.name]
  sources=[ROOT/n for n in SOURCES];resources=sorted((ROOT/RESOURCE).iterdir());source_pins={path.relative_to(ROOT).as_posix():pin(path) for path in [*sources,*resources,Path(__file__).resolve()] if path.is_file()}
  for name in source_pins:
   target=out/'sources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/name,target)
  classes=out/'classes';classes.mkdir();java=JAVA if a.compiler=='21' else JAVA25
  command=[str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-sourcepath','','-cp',';'.join(map(str,reversed(predecessors))),'-d',str(classes),*map(str,sources)]
  r=subprocess.run(command,capture_output=True,timeout=120);(out/'javac.stdout').write_bytes(r.stdout);(out/'javac.stderr').write_bytes(r.stderr);save(out/'javac.process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr
  with zipfile.ZipFile(predecessors[0]) as z:old={n:z.read(n) for n in z.namelist()}
  payload=dict(old);added={}
  for artifact,families in [(predecessors[1],['org/jlinalg/cli/NfcoreProcess','org/jlinalg/workflow/LinuxProcessSnapshot']),(predecessors[2],['org/jlinalg/loess/RLowess','org/jlinalg/sequence/Deseq2IndependentFiltering'])]:
   with zipfile.ZipFile(artifact) as z:
    selected=[n for n in z.namelist() if any(n==f+'.class' or n.startswith(f+'$') and n.endswith('.class') for f in families)]
    assert all(f+'.class' in selected for f in families)
    for n in selected:assert n not in payload;payload[n]=z.read(n);added[n]=dict(origin=pin(artifact),sha256=__import__('hashlib').sha256(payload[n]).hexdigest())
    if artifact==predecessors[2]:
     for n in z.namelist():
      if n.startswith('META-INF/source-numerics/'):
       if n in payload:assert payload[n]==z.read(n)
       else:payload[n]=z.read(n)
  for path in classes.rglob('*.class'):
   name=path.relative_to(classes).as_posix();assert name not in payload;payload[name]=path.read_bytes()
  for path in resources:
   if path.is_file():name='org/jlinalg/omics/nfcore-deseq126/'+path.name;assert name not in payload;payload[name]=path.read_bytes()
  for path in sources:payload['META-INF/nfcore-deseq126/sources/'+path.relative_to(ROOT).as_posix()]=path.read_bytes()
  payload['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreDeseq126AuditCli\n\n'
  payload['META-INF/nfcore-deseq126/NOTICE']=b'Original R3.6.2 DESeq2 1.26.0 postfit inspection and native full-output consistency component only. No NB refit, independent rlog/vst recomputation, whole-pipeline or biological admission. Zero nf-core tool credit. All consensus predecessor members except manifest remain byte-identical. Added process and numeric class families are byte-identical to the matching published tested development artifacts. See NFCORE_DESEQ126.md and WARNINGS.md.\n'
  artifact=out/'nfcore-deseq126-development.jar'
  with zipfile.ZipFile(artifact,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
   for name,raw in sorted(payload.items()):
    item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
  with zipfile.ZipFile(artifact) as z:assert all(z.read(n)==raw for n,raw in old.items() if n!='META-INF/MANIFEST.MF')
  assert all(pin(ROOT/n)==v for n,v in source_pins.items())
  state.update(status='PASS_ORIGINAL_POSTFIT_BUILD_ONLY',compiler=a.compiler,artifact=pin(artifact),source_pins=source_pins,predecessors={p.relative_to(ROOT).as_posix():pin(p) for p in predecessors},inherited_added_class_pins=added,all_consensus_predecessor_members_except_manifest_unchanged=True)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state['canonical_executable_unchanged']=canonical;save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable predecessor and complete source checks')
 raise SystemExit(main())
