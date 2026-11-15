#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or verify one source-bound R Arrow admission and every preceding catalog gate."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from arrow_catalog.model import *
from arrow_catalog.bridge import load_before,metadata_view,predecessor_pins,prior_admission
from arrow_catalog.source import scan
from arrow_catalog.qualification import qualification
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta

ACTION=('Read ARROW1201_CATALOG_INTEGRATION.md and all original warnings. Use exact permitted Arrow, mapping and DoubletFinder providers and NEW writable paths. '
 'SKIP3 skips dependent analysis for missing/forbidden prerequisites. FAIL2 admits no complete result, including recording failure. '
 'Preserve primary unknowns, complete original RDS objects/conditions, literal IDs, NA/NaN/Inf, raw consoles, partials and old hashes. '
 'The invalidated same-version C++ build50 evidence remains retained and unqualified; use the recipe-unpatched build2 provider. '
 'The missing-column trace retains its actual caller context; whole-error and live-pointer identity are not claimed for that control. '
 'HICAT/BIGCAT/PyArrow stay separate missing interfaces; Cell Ranger/ARC remain excluded. '
 'This audit installs nothing and fits no models. The full 250-paper objective remains open.')

def compatibility(before,after):
    row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact preceding reviewed-use bytes cannot be recovered')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='arrow1201-scoped-Parquet-coverage',
        scope='One existing implementation coverage note only; all primary facts and 1179 other reviews unchanged. Only exact reviewed-uses metadata is reversible; scientific overlays forbidden.',paths=[row])

def load_catalog_registry(root=ROOT):
    from scrattchmapping_catalog.catalog import load_catalog_registry as previous
    rows=previous(metadata_view(root,load_before(root)))
    if len(rows)!=476:raise ValueError('All 476 preceding cumulative definitions required')
    current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']==TOOL)
    indices=[i for i,r in enumerate(rows) if r['id']==TOOL]
    if len(indices)!=1:raise ValueError('Exactly one existing R Arrow interface required')
    for key in ['current_coverage','existing_source','coverage_note']:rows[indices[0]][key]=current[key]
    return rows

def coverage_record(root,after):
    return dict(status='PRIMARY_R_ARROW_ROLES_WITH_SELECTED1201_PARQUET_COVERAGE',reviews=reviews(root),
        paper_role_coverage=next(r for r in json.loads(after[META[0]]) if r['id']==TOOL)['paper_role_coverage'],
        P059_reported_edition='12.0.1',reported_chunk_cells=50000,reported_chunk_genes=500,
        author_calls_schema_mmap_query_options_unreported=True,sparse_matrix_conversion_qualified=False,
        author_environment_inferred=False,live_execution_context_identity_claimed=False,
        complete_original_conditions_retained=True,missing_column_difference='trace only; actual caller context retained',
        selected_CPP_build='h32ed672_2_cpu',recipe_source_patches=[],
        prior_CPP_build50_invalidated=True,original_CPP_binary_independently_rebuilt=False,
        new_upstream_family_credit=1,native_family_credit_added=0,whole_goal_complete=False)

def apply(root,out,runtime,wsl_distro):
    if any((root/n).exists() for n in [EVIDENCE,PROOF,COVERAGE,COMPATIBILITY]):
        raise ValueError('Admission evidence exists; retain it and use verification')
    before={n:(root/n).read_bytes() for n in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):raise ValueError('Exact preceding metadata differs; preserve concurrent work')
    census=scan(root,json.loads(before[PAPERS]));after=transition(root,before);result=check_transition(root,before,after)
    inverse=compatibility(before,after);component=qualification(root,runtime,wsl_distro)
    evidence=root/EVIDENCE;evidence.mkdir(exist_ok=False)
    (evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in before.items():
            info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,raw)
    (evidence/'CENSUS.json').write_bytes(encoded(census))
    (evidence/'QUALIFICATION.json').write_bytes(encoded(component))
    (evidence/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode())
    (root/COMPATIBILITY).write_bytes(encoded(inverse))
    (root/COVERAGE).write_bytes(encoded(coverage_record(root,after)))
    source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE,CURRENT]
    evidence_names=sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file() and p.name!='catalog-before.zip')
    evidence_names += [COVERAGE,COMPATIBILITY,DATA+'/scrattchmapping-catalog-validation.json',
        'docs/literature/arrow1201-pristine-workflow-validation/QUALIFICATION.json',
        'docs/literature/arrow1201-pristine-workflow-validation/original-workflow-and-installation-evidence.zip',
        'docs/literature/arrow1201-workflow-validation/INVALIDATION.json']
    for name,raw in before.items():
        if (root/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite permitted: '+name)
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    proof=dict(status='PASS_ARROW1201_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION',
        before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},
        after_files={n:pin(root/n) for n in META},source_pins={n:pin(root/n) for n in source_names},
        evidence_pins={n:pin(root/n) for n in evidence_names},admitted_tool_ids=[TOOL],**result,
        scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False)
    (root/PROOF).write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False,runtime=None,prior_runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_ARROW1201_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION' or proof['new_scoped_integration_credit']!=1 or proof['native_family_credit_added']:
        raise ValueError('Unsupported or duplicate R Arrow family credit')
    for key in ['source_pins','evidence_pins','after_files']:
        for name,expected in proof[key].items():
            if pin(root/name)!=expected:raise ValueError('Qualified catalog source/evidence differs: '+name)
    before=load_before(root)
    if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:
        raise ValueError('Exact eight-input preceding catalog differs')
    after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
    if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact one-note historical inverse differs')
    census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
    if len(census)!=250 or rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Complete primary census differs')
    if json.loads((root/COVERAGE).read_bytes())!=coverage_record(root,after):raise ValueError('Primary role coverage or source-build claims differ')
    previous=prior_admission(root,before,rescan=rescan,runtime=prior_runtime,doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    component=qualification(root,runtime,wsl_distro);registry=load_catalog_registry(root)
    if len(registry)!=476:raise ValueError('Cumulative definition population changed')
    return dict(status=proof['status'],**result,registry_definitions=len(registry),primary_sources_rescanned=rescan,
        component=component,predecessor=previous['status'],scientific_sources_overridden=False,
        preceding_scientific_verifiers_changed=False,original_io_or_model_fitting_executed=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    parser.add_argument('--out',required=True,type=Path);parser.add_argument('--rescan-sources',action='store_true')
    parser.add_argument('--runtime',help='Exact permitted recipe-unpatched Arrow R provider')
    parser.add_argument('--prior-runtime',help='Exact permitted preceding mapping provider')
    parser.add_argument('--doubletfinder-runtime',help='Exact permitted DoubletFinder provider for cumulative gates')
    parser.add_argument('--wsl-distro',help='Explicit permitted WSL distribution on Windows')
    args=parser.parse_args();out,owned=args.out.resolve(),False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True
        result=apply(ROOT,out,args.runtime,args.wsl_distro) if args.apply else verify(rescan=args.rescan_sources,runtime=args.runtime,
            prior_runtime=args.prior_runtime,doubletfinder_runtime=args.doubletfinder_runtime,wsl_distro=args.wsl_distro)
        (out/('APPLIED.json' if args.apply else 'COMPLETED.json')).write_bytes(encoded(result))
        (out/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ARROW_CATALOG_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                (out/'FAILURE.json').write_bytes(encoded(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
                (out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode())
            except BaseException as recording:code=2;print('FAIL2_CATALOG_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
