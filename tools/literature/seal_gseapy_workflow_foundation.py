# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal current original-runtime/native foundations while preserving failed and incomplete-work dispositions."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from gseapy_workflow_foundation_evidence import STATUS,verify

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/gseapy-runtime';final=ROOT/'docs/literature/gseapy-workflow-foundation';assert not out.exists() and out.is_relative_to(base) and not final.exists();out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();built=json.loads((base/'workflow-build-7/COMPLETED.json').read_bytes());assert built['status']=='passed-compiled-GSEApy-runtime-transport-and-export-foundation-only'
    sources=set(built['source_pins']);sources.add('docs/literature/GSEAPY_WORKFLOW_DEVELOPMENT.md');sources.update('tools/literature/'+n for n in ['discover_gseapy_workflows.py','intake_gseapy_backend_sources.py','prepare_gseapy_runtime_sources.py','prepare_gseapy_worker_sources.py','prepare_gseapy_java_runtime_sources.py','create_gseapy_workflow_fixtures.py','run_gseapy_original_controls.py','validate_gseapy_ranked.py','validate_gseapy_setup_contracts.py','gseapy_workflow_foundation_evidence.py','seal_gseapy_workflow_foundation.py']);pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    prefixes=('source-intake-','backend-source-','namespace-discovery-','runtime-sources-','worker-sources-','java-runtime-sources-','workflow-')
    for directory in sorted(base.iterdir()):
        if not directory.is_dir() or directory==out or not directory.name.startswith(prefixes):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n not in {'__pycache__','classes','private-cache','private-temp','private-config','private-home'} and not(Path(current)/n).is_symlink()]
            for name in names:
                path=Path(current)/name
                if not path.is_file() or path.is_symlink() or path.suffix in {'.jar','.class','.pyc'}:continue
                files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    parents=['docs/literature/gseapy-statistics-development/VALIDATION.json','docs/literature/gseapy-statistics-development/controls.zip'];parentpins={n:pin(ROOT/n) for n in parents};held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-gseapy-statistics-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode();canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');archive=candidate/'controls.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    save(candidate/'VALIDATION.json',dict(schema_version=1,status=STATUS,artifact=built['artifact'],parent_artifact=built['parent_artifact'],unchanged_parent_entries=built['unchanged_parent_entries'],source_pins=pins,parent_proofs=parentpins,archive=pin(archive),canonical_executable_unchanged=canonical,excluded_non_scientific_caches=['private-cache','private-temp','private-config','private-home','__pycache__','classes'],implementation_credit=False,full_gseapy_adapter_admitted=False,analysis_command_available=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,scientific_source_exception_added=False,**packing));verify(folder=candidate);assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items());final.mkdir()
    for name in ['VALIDATION.json','controls.zip']:
        with (final/name).open('xb') as stream:stream.write((candidate/name).read_bytes())
    verify();save(out/'COMPLETED.json',dict(status=STATUS,logical_files=len(files),archive=pin(archive),candidate_verified_before_publication=True,unrelated_files_preserved=18,full_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False));print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; no full-adapter or catalog credit')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable foundation evidence gates')
    main()
