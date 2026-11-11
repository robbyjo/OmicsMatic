# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal new complete-execution evidence without updating any historical scientific proof."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from gseapy_execution_evidence import STATUS,BASE,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);candidate=out/'candidate';candidate.mkdir();base=ROOT/BASE
    required=['execution-build-4','execution-cli-extended-2','execution-extended-replay-2','execution-contracts-2','execution-input-rejections-1','execution-corruptions-2','execution-failures-1','execution-example-2']
    for n in required:assert json.loads((base/n/'COMPLETED.json').read_bytes())['status'].startswith('passed-'),n
    built=json.loads((base/'execution-build-4/COMPLETED.json').read_bytes());sources=set(built['source_pins']);sources.update(['docs/literature/GSEAPY_EXECUTION.md','tools/literature/create_gseapy_example.py','tools/literature/create_gseapy_execution_fixtures.py','tools/literature/create_gseapy_extended_fixtures.py','tools/literature/create_gseapy_failure_fixtures.py','tools/literature/prepare_gseapy_execution_sources.py','tools/literature/validate_gseapy_execution.py','tools/literature/validate_gseapy_execution_replay.py','tools/literature/validate_gseapy_extended_replay.py','tools/literature/validate_gseapy_extended_execution.py','tools/literature/validate_gseapy_execution_contracts.py','tools/literature/validate_gseapy_execution_corruptions.py','tools/literature/validate_gseapy_failures.py','tools/literature/validate_gseapy_input_rejections.py','tools/literature/validate_gseapy_execution_example.py','tools/literature/gseapy_execution_evidence.py','tools/literature/seal_gseapy_execution.py','tools/literature/java/org/jlinalg/cli/GseapyExecutionReplay.java','tools/literature/java/org/jlinalg/cli/GseapyExecutionSingleAudit.java']);pins={n:pin(ROOT/n) for n in sorted(sources)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in sorted(sources)}
    folders=sorted(p for p in base.iterdir() if p.is_dir() and p.name.startswith('execution-') and not p.name.startswith('execution-seal-'))
    for folder in folders:
        for file in sorted(folder.rglob('*')):
            if file.is_file() and file.suffix not in ['.jar','.class'] and '__pycache__' not in file.parts:files[file.relative_to(ROOT).as_posix()]=file.read_bytes()
    totals=dict(cases=0,numeric_states=0,identity_states=0,tsv_states=0,native_walks=0,native_null_families=0)
    for edition in ['1.0.4','1.1.2','1.1.3','1.1.8']:
        audit=json.loads((base/'execution-cli-extended-2'/edition/'native-audit.json').read_bytes());totals['cases']+=len(audit['cases'])
        for key in ['numeric_states','identity_states','tsv_states']:totals[key]+=audit[key]
        for c in audit['cases']:
            for key in ['native_walks','native_null_families']:totals[key]+=c.get(key,0)
    archive=candidate/'controls.zip';record=pack(archive,files);parents={n:pin(ROOT/n) for n in ['docs/literature/gseapy-workflow-foundation/VALIDATION.json','docs/literature/gseapy-workflow-foundation/controls.zip','docs/literature/gseapy-statistics-development/VALIDATION.json','docs/literature/gseapy-statistics-development/controls.zip']};proof=dict(status=STATUS,scope='Declared original-class local Enrichr/prerank/GSEA/ssGSEA/GSVA workflows only, four exact editions, complete native observed-equation/report audits and atomic requested-batch admission. Upstream preprocessing, RNG and GSVA fitting remain unchanged.',artifact=built['artifact'],parent_artifact=built['parent_artifact'],canonical_executable_unchanged=built['canonical_executable_unchanged'],source_pins=pins,parent_proofs=parents,positive_totals=totals,archive=pin(archive),**record,full_gseapy_adapter_admitted=True,analysis_command_available=True,implementation_credit=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,scientific_source_exception_added=False,independent_permutation_generation=False,independent_gsva_refit=False,retained_failed_attempts=['RES list decoding rejection','incorrect legacy permuted-ssGSEA ES audit expectation','Linux replay driver180-second timeout','diagnostic verifier filename collision restored to sealed original'],native_null_domain='Finite normalized sort inputs only; unsupported/nonfinite source families fail whole-batch admission. No independent permutation calibration.',supervision_scope='Actual owned-worker cleanup, wall time and sampled output limits, not hard OS CPU/memory containment.',new_catalog_credit=False)
    save(candidate/'VALIDATION.json',proof);verify(False,candidate);assert all(pin(ROOT/n)==v for n,v in pins.items());destination=ROOT/'docs/literature/gseapy-execution-development';assert not destination.exists();destination.mkdir()
    for n in ['VALIDATION.json','controls.zip']:(destination/n).write_bytes((candidate/n).read_bytes())
    verify();save(out/'COMPLETED.json',dict(status='passed-new-GSEApy-execution-proof-sealed-and-verified',validation=pin(destination/'VALIDATION.json'),archive=pin(destination/'controls.zip'),logical_files=len(files),whole_goal_complete=False));print('Sealed',len(files),'logical evidence files;',pin(archive)['bytes'],'archive bytes')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable seal gates')
    main()
