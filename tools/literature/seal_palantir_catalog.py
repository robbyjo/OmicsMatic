# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal applied source roles, exact catalog deltas, unchanged numerical gates and current browser evidence."""
import json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
from render_palantir_catalog import refresh_manifest

def main():
 base=ROOT/'build/literature-review/palantir-runtime';data=ROOT/'docs/literature/omics-2023-2026';proof_path=data/'palantir-catalog-validation.json';archive=data/'palantir-catalog-support.zip';assert not proof_path.exists() and not archive.exists();roles=json.loads((data/'palantir-role-coverage.json').read_bytes());assert roles['applied'];gates=json.loads((base/'catalog-gates-2/COMPLETED.json').read_bytes());assert gates['status']=='passed-twenty-three-frozen-and-additive-Palantir-gates' and len(gates['checks'])==23;ui=json.loads((base/'catalog-ui-1/COMPLETED.json').read_bytes());assert ui['status']=='passed-local-general-and-palantir-catalog-browser-checks';(data/'palantir-source-audit.json').write_bytes((base/'catalog-source-audit-3/COMPLETED.json').read_bytes());sources=set(gates['source_pins'])|set(ui['source_pins']);sources.update('tools/literature/'+n for n in ['palantir-named-software-registry.json','palantir_catalog.py','curate_palantir_uses.py','prepare_palantir_catalog.py','render_palantir_catalog.py','bootstrap_palantir_catalog.py','audit_palantir_catalog.py','prepare_palantir_catalog_ui.py','check_palantir_review_ui.cjs','validate_palantir_catalog_ui.py','update_palantir_catalog_readme.py','verify_palantir_catalog.py','seal_palantir_catalog.py','trajectory-spatial-use-reviews.json','catalog_source_integrity.py','collect_omics_review.py','collect_htseq_omics.py','curate_shapeit_uses.py','reviewed-uses.json']);sources.add('docs/literature/PALANTIR_CATALOG.md');sources={n for n in sources if not n.startswith('site/')};pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
 for directory in sorted(base.iterdir()):
  if not directory.is_dir() or not directory.name.startswith('catalog-'):continue
  for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
   dirs[:]=[n for n in dirs if n not in {'__pycache__'} and not(Path(current)/n).is_symlink()]
   for name in names:
    path=Path(current)/name
    if path.is_symlink() or not path.is_file():continue
    files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
 for name in ['correct_catalog_audit_labels.py','correct_catalog_ui_draft.py']:
  path=base/name
  if path.exists():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
 for name in ['site/literature-review.html','site/literature-warnings.html']:files['current-output/'+name]=(ROOT/name).read_bytes()
 before=base/'catalog-prepare-1/before/docs/literature/omics-2023-2026'
 for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']:files['catalog-before/'+name]=(before/name).read_bytes()
 for name in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','catalog.md','implementation-plan.md','palantir-role-coverage.json','palantir-prior-papers.json.gz','palantir-prior-reviews.json.gz','palantir-render-ui-shared-source-compatibility.json','palantir-validation-note-shared-source-compatibility.json','palantir-reviews-shared-source-compatibility.json','palantir-readme-evolution.json','palantir-source-audit.json']:files['catalog-snapshot/'+name]=(data/name).read_bytes()
 old=json.loads((data/'cellphonedb-effective-catalog-validation.json').read_bytes());prior=unpack(data/'cellphonedb-effective-catalog-support.zip',old)
 for name,raw in prior.items():
  if name=='primary-attribution.json' or name.startswith('primary-'):files['prior-licensed-primary/'+name]=raw
 parents={n:pin(ROOT/n) for n in ['docs/literature/palantir-workflow-development/VALIDATION.json','docs/literature/palantir-workflow-development/controls.zip','docs/literature/omics-2023-2026/cellphonedb-effective-catalog-validation.json','docs/literature/omics-2023-2026/cellphonedb-effective-catalog-support.zip','docs/literature/omics-2023-2026/cellphonedb-catalog-validation.json','docs/literature/omics-2023-2026/cellphonedb-catalog-support.zip']};held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-palantir-workflow-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');packing=pack(archive,files);assert archive.stat().st_size<95000000;save(proof_path,dict(schema_version=1,status='passed-Palantir-coordinate-adapter-and-eight-primary-role-catalog-integration',source_pins=pins,parent_proofs=parents,archive=pin(archive),canonical_executable_unchanged=canonical,new_scoped_integrations=86,including_existing_adapters=89,named_tools=400,observations=2465,reviewed_roles=988,supporting_pointers=340,missing=255,scientific_verifiers_changed=False,scientific_source_exception_added=False,independent_trajectory_refit=False,whole_paper_reproduced=False,whole_goal_complete=False,**packing));refresh_manifest(data,(data/'SHA256SUMS').read_bytes());assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items());from verify_palantir_catalog import verify
 verify();print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; goal remains open')
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable primary/catalog evidence gates')
 main()
