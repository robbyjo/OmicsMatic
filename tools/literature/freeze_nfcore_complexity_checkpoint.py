# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native Picard references, complete marked-BAM checks, retained failures and source image intake."""
import hashlib,json,shutil,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE

DEST=ROOT/'docs/literature/nfcore-complexity-development'

def main():
 assert not DEST.exists();prior_path=ROOT/'docs/literature/nfcore-deseq126-development/VALIDATION.json';prior=json.loads(prior_path.read_bytes())
 assert all(pin(ROOT/n)==v for n,v in prior['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in prior['catalog_unchanged'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical_executable_unchanged']
 controls=json.loads((BASE/'complexity-controls-2/COMPLETED.json').read_bytes());assert controls['status']=='PASS_COMPLETE_PICARD2231_COMPLEXITY_COMPONENT_CONTROLS_ONLY'
 assert len(controls['native_and_optical_replays'])==12 and len(controls['complete_captured_replays'])==4 and len(controls['negative_controls'])==15 and all(r['passed'] for r in controls['native_and_optical_replays']+controls['complete_captured_replays']+controls['negative_controls'])
 names=['src/main/java/org/jlinalg/sequence/PicardLibraryComplexity.java','src/main/java/org/jlinalg/cli/NfcoreChipseq121Complexity.java','src/main/java/org/jlinalg/cli/NfcoreComplexityAuditCli.java','docs/literature/NFCORE_COMPLEXITY.md']
 names+=['tools/literature/'+n for n in ['build_nfcore_complexity.py','validate_nfcore_complexity.py','freeze_nfcore_complexity_checkpoint.py','verify_nfcore_complexity_checkpoint.py','java/PicardComplexityOriginal.java','java/PicardComplexityControl.java','java/NfcoreComplexityControl.java']]
 for directory in ['src/main/resources/org/jlinalg/omics/picard2231','src/test/resources/org/jlinalg/sequence/picard2231']:names += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT/directory).iterdir()) if p.is_file()]
 source_pins={n:pin(ROOT/n) for n in names};files={'sources/'+n:ROOT/n for n in names};builds={};artifacts={};DEST.mkdir()
 for compiler in ['21','25']:
  folder=BASE/('complexity-build'+compiler+'-1');build=json.loads((folder/'COMPLETED.json').read_bytes());assert build['status']=='PASS_NATIVE_COMPLEXITY_BUILD_ONLY' and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
  for basename,prefix in [('picard-complexity-library.jar','picard-complexity'),('nfcore-complexity-development.jar','nfcore-complexity')]:
   original=folder/basename;assert pin(original)==build['artifacts'][basename]==controls['artifacts'][compiler][basename];name=prefix+'-javac'+compiler+'.jar';shutil.copyfile(original,DEST/name);artifacts[name]=pin(DEST/name)
  builds[compiler]=build
 folders=['picard2231-source-intake-1','complexity-intake-1','picard2231-original-jar-1','picard2231-original-reference-1','picard2231-original-reference-2','picard2231-markdup-controls-1','picard2231-markdup-controls-2','complexity-native-1','complexity-build21-1','complexity-build25-1','complexity-controls-1','complexity-controls-2','chipseq200-container-source-intake-1','chipseq200-container-source-intake-2','chipseq200-container-source-intake-3','chipseq200-images-1']
 for name in folders:
  folder=BASE/name;assert folder.exists()
  for p in folder.rglob('*'):
   if not p.is_file() or p.suffix in ['.jar','.class','.sif']:continue
   relative=p.relative_to(folder)
   if any(part.startswith('classes') or part.startswith('helpers') for part in relative.parts[:-1]):continue
   if name.startswith('complexity-controls') and relative.parts[0] in ['negative','small-fixtures','reference']:continue
   assert not p.is_symlink();files['evidence/'+name+'/'+relative.as_posix()]=p
 # Original full curves and metric files are small; large immutable original BAMs remain local.
 fixture=BASE/'complexity-complete-library-fixture-1'
 for p in fixture.iterdir():
  if p.is_file() and p.name not in ['marked.bam','genome.fa']:files['complete-first-library/'+p.name]=p
 for name in ['capture_picard2231_source1.py','prepare_complexity_intake1.py','extract_picard_jar1.py','generate_picard2231_reference1.py','generate_picard2231_reference2.py','generate_picard_markdup1.py','generate_picard_markdup2.py','prepare_markdup_fixture2.py','picard2231-markdup-valid-derivation.json','inventory_chipseq200_containers1.py','inventory_chipseq200_containers2.py','inventory_chipseq200_containers3.py','prepare_chipseq200_images1.py']:
  files['generation/'+name]=BASE/name
 identities={};archive=DEST/'controls.zip'
 with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,p in sorted(files.items()):
   raw=p.read_bytes();identities[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());item=zipfile.ZipInfo(name,(1980,1,1,0,0,0));item.compress_type=zipfile.ZIP_DEFLATED;item.external_attr=0o100644<<16;z.writestr(item,raw)
 images=json.loads((BASE/'chipseq200-images-1/COMPLETED.json').read_bytes());source_intake=json.loads((BASE/'chipseq200-container-source-intake-3/COMPLETED.json').read_bytes());assert images['status']=='PASS_CHIPSEQ200_SOURCE_DECLARED_SIF_PREPARATION_ONLY' and images['prepared_images']==33 and not images['scientific_analysis_executed']
 reference=json.loads((BASE/'picard2231-original-reference-2/COMPLETED.json').read_bytes());assert reference['cases']==20
 original=BASE/'picard2231-original-reference-2';assert (original/'windows21.tsv').read_text().splitlines()==(original/'original_image.tsv').read_text().splitlines()
 save(DEST/'VALIDATION.json',dict(status='PASS_NATIVE_PICARD2231_COMPLEXITY_COMPONENT_ONLY',source_pins=source_pins,artifacts=artifacts,archive=pin(archive),archive_files=identities,builds=builds,controls=controls,original_reference=reference,original_reference_fields_match_across_two_JVMs=True,physical_original_line_endings_retained=True,chipseq200_source_intake=source_intake,chipseq200_image_preparation=images,large_original_BAMs_not_redistributed=True,predecessor_proof=pin(prior_path),canonical_executable_unchanged=prior['canonical_executable_unchanged'],catalog_unchanged=prior['catalog_unchanged'],claims=prior['claims']))
 (DEST/'.gitattributes').write_bytes(b'* -text\n');(DEST/'README.md').write_text('# Native Picard complexity development checkpoint\n\n**No new catalog-tool credit; full nf-core and the 250-paper goal remain unfinished.** Read [the API, source missing-estimate warnings and required actions](../NFCORE_COMPLEXITY.md).\n\nThe small numerical JAR needs Java only and has no executable entrypoint. The larger executable audits complete captured source workflow objects without installing or running R, Picard or Preseq. Original source, license notices, numerical references, optical/flag controls and prior failures are retained in controls.zip. Large original BAMs, original Picard executable and private container images remain local. Image preparation for ChIP-seq 2.0.0 is prerequisite evidence only.\n\nRun `python tools/literature/verify_nfcore_complexity_checkpoint.py` from the repository root. Read WARNINGS.md after any audit; FAIL2 admits no component result. The released executable and live catalog are unchanged.\n',encoding='utf8',newline='\n')
 (DEST/'SHA256SUMS').write_text(''.join(pin(DEST/n)['sha256']+'  '+n+'\n' for n in [*artifacts,'controls.zip','VALIDATION.json']),encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in source_pins.items());print('Frozen native complexity:',len(files),'evidence files;12 native/optical replays;4 complete bundle replays;15 refusals;33 prepared images;0 tool credit.',flush=True)

if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete native scope and source identity checks')
 main()
