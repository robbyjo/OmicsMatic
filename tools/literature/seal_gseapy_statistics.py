# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal an additive native component without crediting unfinished high-level GSEApy workflows."""
import argparse,json,os,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from gseapy_statistics_evidence import STATUS,verify

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();base=ROOT/'build/literature-review/gseapy-runtime';final=ROOT/'docs/literature/gseapy-statistics-development';assert not out.exists() and out.is_relative_to(base) and not final.exists();out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir()
    built=json.loads((base/'statistics-build-3/COMPLETED.json').read_bytes());native=json.loads((base/'statistics-native-3/COMPLETED.json').read_bytes());example=json.loads((base/'statistics-example-1/COMPLETED.json').read_bytes());assert len(native['runs'])==3 and len(example['runs'])==3
    sources=set(built['source_pins'])|set(example['source_pins']);sources.update('tools/literature/'+n for n in ['intake_gseapy_sources.py','draft_gseapy_uses.py','gseapy_statistics_reference.py','validate_gseapy_statistics.py','gseapy_statistics_evidence.py','seal_gseapy_statistics.py']);pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    for directory in sorted(base.iterdir()):
        if directory==out or not directory.is_dir() or not directory.name.startswith(('source-intake-','statistics-','primary-role-')):continue
        for current,dirs,names in os.walk(directory,topdown=True,followlinks=False):
            dirs[:]=[n for n in dirs if n not in {'classes','__pycache__'} and not(Path(current)/n).is_symlink()]
            for name in names:
                path=Path(current)/name
                if not path.is_file() or path.is_symlink() or path.suffix in {'.jar','.class','.pyc'}:continue
                files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    parents=['docs/literature/scfates-workflow-development/VALIDATION.json','docs/literature/omics-2023-2026/scfates-catalog-validation.json','docs/literature/omics-2023-2026/scfates-catalog-support.zip']
    parents.extend(p.relative_to(ROOT).as_posix() for p in (ROOT/'docs/literature/scfates-workflow-development').glob('*.zip'));parentpins={n:pin(ROOT/n) for n in sorted(parents)}
    for name in ['docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/summary.json','docs/literature/omics-2023-2026/tool-inventory.json','tools/literature/reviewed-uses.json']:files['unchanged-catalog/'+name]=(ROOT/name).read_bytes()
    held=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-08-scfates-catalog-1/selection.json').read_bytes())['preserved'];assert len(held)==18 and all(pin(ROOT/n)==v for n,v in held.items());files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');archive=candidate/'controls.zip';packing=pack(archive,files);assert archive.stat().st_size<95000000
    save(candidate/'VALIDATION.json',dict(schema_version=1,status=STATUS,artifact=built['artifact'],parent_artifact=built['parent_artifact'],unchanged_parent_entries=built['unchanged_parent_entries'],source_pins=pins,parent_proofs=parentpins,archive=pin(archive),canonical_executable_unchanged=canonical,implementation_credit=False,full_gseapy_adapter_admitted=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,**packing));verify(folder=candidate)
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in held.items());final.mkdir()
    for name in ['VALIDATION.json','controls.zip']:
        with (final/name).open('xb') as stream:stream.write((candidate/name).read_bytes())
    verify();save(out/'COMPLETED.json',dict(status=STATUS,candidate_verified_before_publication=True,logical_files=len(files),archive=pin(archive),unrelated_files_preserved=18,implementation_credit=False,whole_goal_complete=False));print('Sealed',len(files),'logical files;',archive.stat().st_size,'bytes; no catalog credit; goal remains open')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence gates')
    main()
