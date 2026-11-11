# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze source-bound complete postfit controls, prior failures and separate development executables."""
import hashlib,json,shutil,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE

DEST=ROOT/'docs/literature/nfcore-deseq126-development'

def main():
 assert not DEST.exists()
 prior_path=ROOT/'docs/literature/source-numerics-development/VALIDATION.json';prior=json.loads(prior_path.read_bytes())
 assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in prior['catalog_unchanged'].items())
 assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical_executable_unchanged']
 native=json.loads((BASE/'deseq126-controls-2/COMPLETED.json').read_bytes());runtime=json.loads((BASE/'deseq126-runtime-controls-1/COMPLETED.json').read_bytes())
 assert native['status']=='PASS_COMPLETE_ORIGINAL_POSTFIT_NATIVE_COMPONENT_CONTROLS_ONLY' and len(native['positive_controls'])==18 and len(native['negative_controls'])==19
 assert runtime['status']=='PASS_ORIGINAL_POSTFIT_PACKAGED_RUNTIME_CONTROLS_ONLY' and len(runtime['checks'])==9
 assert all(r['passed'] for r in native['positive_controls']+native['negative_controls']+runtime['checks'])
 names=['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['NfcoreDeseq126Audit','NfcoreDeseq126Runtime','NfcoreDeseq126AuditCli']]
 names+=['tools/literature/'+n for n in ['build_nfcore_deseq126.py','validate_nfcore_deseq126.py','validate_nfcore_deseq126_runtime.py','freeze_nfcore_deseq126_checkpoint.py','verify_nfcore_deseq126_checkpoint.py','java/NfcoreDeseq126Control.java']]
 names+=['docs/literature/NFCORE_DESEQ126.md']
 for directory in ['src/main/resources/org/jlinalg/omics/nfcore-deseq126','src/test/resources/org/jlinalg/cli/nfcore-deseq126']:names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/directory).iterdir()) if p.is_file()]
 source_pins={n:pin(ROOT/n) for n in names};files={'sources/'+n:ROOT/n for n in names};builds={};artifacts={};DEST.mkdir()
 for compiler in ['21','25']:
  folder=BASE/('deseq126-build'+compiler+'-3');build=json.loads((folder/'COMPLETED.json').read_bytes());artifact=folder/'nfcore-deseq126-development.jar'
  assert build['status']=='PASS_ORIGINAL_POSTFIT_BUILD_ONLY' and pin(artifact)==build['artifact']==native['artifacts'][compiler]
  assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
  tested=BASE/('deseq126-build'+compiler+'-2/nfcore-deseq126-development.jar');assert pin(tested)==runtime['artifacts'][compiler]
  # Final packaging only adds corresponding source/license notices; every runtime-tested member is identical.
  with zipfile.ZipFile(tested) as old,zipfile.ZipFile(artifact) as final:assert all(old.read(n)==final.read(n) for n in old.namelist())
  name='nfcore-deseq126-javac'+compiler+'.jar';shutil.copyfile(artifact,DEST/name);artifacts[name]=pin(DEST/name);builds[compiler]=build
 folders=['deseq126-worker-controls-1','deseq126-worker-controls-2','deseq126-worker-controls-3','deseq126-synthetic-original-1','deseq126-replacement-probe-1','deseq126-native-controls-1','deseq126-native-controls-3','deseq126-native-controls-4','deseq126-controls-1','deseq126-controls-2','deseq126-runtime-controls-1','deseq126-packaged-1']
 folders += ['deseq126-build'+c+'-'+i for c in ['21','25'] for i in ['1','2','3']]
 for name in folders:
  folder=BASE/name;assert folder.exists()
  for p in folder.rglob('*'):
   if not p.is_file() or p.suffix in ['.jar','.class','.tar','.pdf']:continue
   relative=p.relative_to(folder)
   if any(part.startswith('classes') or part.startswith('helper') for part in relative.parts[:-1]):continue
   if name.startswith(('deseq126-native-controls','deseq126-controls')) and len(relative.parts)>1:continue
   assert not p.is_symlink();files['evidence/'+name+'/'+relative.as_posix()]=p
 for name in ['run_deseq126_synthetic1.py','run_deseq126_worker3.py','probe_deseq126_replacement1.py','prepare_deseq126_reference1.py']:files['generation/'+name]=BASE/name
 identities={};archive=DEST/'controls.zip'
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,p in sorted(files.items()):
   raw=p.read_bytes();identities[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
 proof=dict(status='PASS_ORIGINAL_DESEQ126_POSTFIT_COMPONENT_ONLY',source_pins=source_pins,artifacts=artifacts,archive=pin(archive),archive_files=identities,builds=builds,native=native,runtime=runtime,runtime_tested_members_byte_identical=True,final_packaging_additions='Corresponding Java and inherited numerical source/license notices only.',retained_failures=['Original replacement worker initially refused the source all-zero NA replacement flag; the flag is now preserved explicitly.','Native audit initially refused source scientific integer notation 1e+08; exact integral decimal/scientific values are now accepted without rounding.'],predecessor_proof=pin(prior_path),canonical_executable_unchanged=prior['canonical_executable_unchanged'],catalog_unchanged=prior['catalog_unchanged'],claims=prior['claims'])
 save(DEST/'VALIDATION.json',proof);(DEST/'.gitattributes').write_bytes(b'* -text\n')
 (DEST/'README.md').write_text('# Original DESeq2 postfit development component\n\n**Zero additional tool credit; the full nf-core adapter and 250-paper goal remain unfinished.** Read [the guide, warnings and required actions](../NFCORE_DESEQ126.md).\n\nThe JARs retain their tested predecessor classes and add source-bound original postfit execution and complete native differential-output checks. Linux execution requires the separate pinned private Apptainer/image. Native reference replays require Java only. Missing/forbidden prerequisites produce SKIP3; integrity, capacity or scientific-contract failures produce FAIL2.\n\nRun `python tools/literature/verify_nfcore_deseq126_checkpoint.py` from the repository root. `controls.zip` retains original exports, passing controls, earlier failures and source/license provenance. No whole-pipeline, model-refit, independent rlog/vst, biological or paper admission is granted. The released executable is unchanged.\n',encoding='utf8',newline='\n')
 (DEST/'SHA256SUMS').write_text(''.join(pin(DEST/n)['sha256']+'  '+n+'\n' for n in [*artifacts,'controls.zip','VALIDATION.json']),encoding='utf8',newline='\n')
 assert all(pin(ROOT/n)==v for n,v in source_pins.items());print('Frozen original postfit component:',len(files),'evidence files;18 native replays;19 refusals;9 runtime controls;0 tool credit.',flush=True)

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete original postfit scope and inherited-byte checks')
 main()
