# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the complete declared adapter evidence, preserving earlier development and source failures."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--validation',required=True,type=Path);a=p.parse_args();base=ROOT/'build/literature-review/palantir-runtime';out=ROOT/'docs/literature/palantir-workflow-development';assert not out.exists();build=json.loads((a.build.resolve()/'COMPLETED.json').read_bytes());validation=json.loads((a.validation.resolve()/'COMPLETED.json').read_bytes());assert build['status']=='PASS_COMPILED_PALANTIR_COORDINATE_WORKFLOW_ONLY' and validation['status']=='PASS_TWO_ORIGINAL_EDITIONS_THREE_JVM_PALANTIR_COORDINATE_WORKFLOWS_ONLY' and validation['artifact']==build['artifact'];sources=set(build['source_pins'])|set(validation['source_pins']);sources.update('tools/literature/'+name for name in ['prepare_palantir_workflow_profile.py','update_palantir_workflow_draft_profile.py','build_palantir_workflow.py','validate_palantir_workflow.py','palantir_workflow_evidence.py','seal_palantir_workflow.py']);sources.add('docs/literature/PALANTIR_WORKFLOW_DEVELOPMENT.md');pins={n:pin(ROOT/n) for n in sorted(sources)};assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items()) and all(pin(ROOT/n)==v for n,v in validation['source_pins'].items());files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
 for directory in sorted(base.iterdir()):
  if not directory.name.startswith('workflow-') or not directory.is_dir():continue
  for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
   dirs[:]=[n for n in dirs if n not in {'dependencies','editions','wheels','archives','classes','__pycache__'} and not(Path(current)/n).is_symlink()]
   for name in names:
    path=Path(current)/name
    if path.suffix in {'.jar','.class','.pyc'} or path.is_symlink() or not path.is_file():continue
    files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
 sources.add('tools/literature/test_palantir_workflow_inputs.py');pins['tools/literature/test_palantir_workflow_inputs.py']=pin(ROOT/'tools/literature/test_palantir_workflow_inputs.py');files['current-source/tools/literature/test_palantir_workflow_inputs.py']=(ROOT/'tools/literature/test_palantir_workflow_inputs.py').read_bytes()
 controls=json.loads((base/'workflow-input-controls-1/COMPLETED.json').read_bytes());assert controls['status']=='PASS_PALANTIR_INPUT_EXPORT_PERMISSION_AND_TIMEOUT_CONTROLS_ONLY' and controls['artifact']==build['artifact'] and all(pin(ROOT/n)==v for n,v in controls['source_pins'].items())
 for name in ['initialize_workflow_draft.py','smoke_workflow_draft.py','audit_workflow_draft.py','finalize_workflow_draft.py','prepare_workflow_publisher.py','check_catalog_preservation.py']:
  path=base/name
  if path.exists():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
 parents={n:pin(ROOT/n) for n in ['docs/literature/palantir-multiscale-development/VALIDATION.json','docs/literature/palantir-multiscale-development/controls.zip','docs/literature/omics-2023-2026/cellphonedb-effective-catalog-validation.json','docs/literature/omics-2023-2026/cellphonedb-effective-catalog-support.zip']};held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-palantir-multiscale-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];out.mkdir();(out/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');archive=out/'controls.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000;save(out/'VALIDATION.json',dict(schema_version=1,status='passed-original-Palantir-coordinate-workflow-adapter-only',artifact=build['artifact'],parent_artifact=build['parent_artifact'],unchanged_parent_entries=build['unchanged_parent_entries'],build_directory=a.build.resolve().name,validation_directory=a.validation.resolve().name,source_pins=pins,parent_proofs=parents,archive=pin(archive),canonical_executable_unchanged=canonical,coordinate_workflow_adapter_admitted=True,independent_trajectory_refit=False,new_catalog_integration_credit=False,current_catalog_integrations=85,including_existing_adapters=88,missing_cataloged_tools=251,source_roles_applied=False,whole_paper_reproduced=False,whole_goal_complete=False,**packing));assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items())
 from palantir_workflow_evidence import verify
 verify();print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; current catalog application and goal remain open')
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable exact adapter evidence checks')
 main()
