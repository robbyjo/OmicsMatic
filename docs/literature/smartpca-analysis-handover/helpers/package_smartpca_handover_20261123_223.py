from pathlib import Path
import json,shutil,sys,zipfile
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-2';Q=T/'docs/literature/smartpca-analysis-handover'
sys.path.insert(0,str(T/'tools/literature'))
from mrpresso_catalog.model import META
helpers=['start_smartpca_analysis_20261123_214.py','build_smartpca_extensions_20261123_216.py',
 'prepare_smartpca_analysis_check_20261123_218.py','build_smartpca_analysis_20261123_218.py',
 'retain_smartpca_precision_mismatch_20261123_219.py','build_smartpca_analysis_20261123_219.py',
 'prepare_smartpca_workflow_files_20261123_220.py','handover_smartpca_build_20261123_221.py',
 'handover_smartpca_checks_20261123_222.py',Path(__file__).name]
(Q/'helpers').mkdir(exist_ok=True)
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
sources=[p.relative_to(T).as_posix() for p in (T/'src/main/java/org/jlinalg/cli').glob('SmartpcaWorkflow*.java')]
sources+=['src/main/java/org/jlinalg/genetics/'+n+'.java' for n in ('SmartpcaAnalysis','SmartpcaOutliers','SmartpcaShrinkage')]
sources+=['src/test/java/org/jlinalg/genetics/'+n+'.java' for n in ('SmartpcaAnalysisReferenceTest','SmartpcaExtensionsTest')]
sources+=['src/test/resources/org/jlinalg/genetics/smartpca-original-'+n+'-reference.zip' for n in ('analysis','outlier')]
sources+=['tools/literature/'+n for n in ('generate_smartpca_analysis_reference.py','generate_smartpca_outlier_reference.py','verify_smartpca_analysis_handover.py','smartpca_reference/outlier_harness.c')]
sources+=[(Q/'helpers'/name).relative_to(T).as_posix() for name in helpers]
source_pins={name:pin(T/name) for name in sorted(sources)}
normal=json.loads((B/'handover-normal-build-1/RESULT.json').read_bytes());assert normal['exit_code']==0
assert json.loads((B/'handover-checks-1/RESULT.json').read_bytes())['status']=='PASS_PACKAGED_NUMERICAL_COMPONENTS_AND_FROZEN_READERS'
inventory={}
with zipfile.ZipFile(Q/'evidence.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
 for path in sorted(B.rglob('*')):
  if not path.is_file():continue
  name=path.relative_to(B).as_posix()
  if name.startswith('handover-normal-build-1/project-build/') and not (name.startswith('handover-normal-build-1/project-build/test-results/test/') and path.suffix=='.xml'):continue
  assert pin(path)['bytes']<20_000_000,name
  archive.write(path,name);inventory[name]=pin(path)
 for name,wanted in source_pins.items():
  member='source-context/'+name;archive.write(T/name,member);inventory[member]=wanted
save(Q/'INVENTORY.json',inventory)
state=dict(status='SAVED_SOURCE_COMPARED_COMPONENTS_WITH_UNFINISHED_WORKFLOW',schema_version=1,
 source_code=source_pins,canonical_metadata={name:pin(T/name) for name in META},
 inventory=pin(Q/'INVENTORY.json'),evidence=pin(Q/'evidence.zip'),producer=normal['producer'],
 scoped_families=153,native=28,upstream=125,reviewed_uses=1691,pending_uses=1031,papers=250,
 original_direct_outlier_calls=23,corrected_original_program_calls=18,finite_original_program_comparisons=16,
 refused_undefined_original_witnesses=2,retained_invalid_precision_original_program_calls=18,
 workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)
save(Q/'STATUS.json',state)
roots=[]
for group in ('component-check-1','analysis-check-2','handover-checks-1'):
 for host in ('windows21','windows25','linux21'):
  root=B/group/host/'classes';rows=[]
  for path in sorted(root.rglob('*')):
   if not path.is_file():continue
   name=path.relative_to(B).as_posix();identity=pin(path);assert inventory[name]==identity
   rows.append(dict(path=path.relative_to(root).as_posix(),identity=identity,member=name))
  assert rows;roots.append(dict(root=str(root),files=rows))
save(Q/'CLEANUP-PLAN.json',dict(status='AFTER_BYTE_EXACT_PUBLICATION_ONLY',roots=roots,
 recovery_archive=pin(Q/'evidence.zip'),retained_failed_checks=True,scientific_inputs_outputs_and_runtimes_eligible=False))
print(json.dumps(dict(files=len(inventory),archive=pin(Q/'evidence.zip'),sources=len(sources),cleanup_roots=len(roots)),indent=2))
