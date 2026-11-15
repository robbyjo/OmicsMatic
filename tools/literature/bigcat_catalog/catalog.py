#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or verify BIGCAT coverage and every preceding source-bound catalog gate."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from bigcat_catalog.model import *
from bigcat_catalog.bridge import load_before,metadata_view,predecessor_pins,prior_admission
from bigcat_catalog.source import scan
from bigcat_catalog.qualification import qualification
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
from bigcat_catalog.dispatcher import COMPATIBILITY as HELP_COMPATIBILITY,declaration,reviewed_root

ACTION=('Read BIGCAT005_CATALOG_INTEGRATION.md and all original warnings. Use both explicit permitted BIGCAT profiles and the exact Arrow, mapping and DoubletFinder providers with NEW writable paths. '
 'SKIP3 skips dependent analysis for missing/forbidden prerequisites; FAIL2 admits no complete result, including recording failure. '
 'Preserve original IDs, NA/NaN/Inf, complete objects/conditions/RNG, raw consoles, partials, prior evidence and hashes. '
 'The 2023 harmonization error remains; the 2022 profile needs explicit sets and lacks impute_cross_big. '
 'P058 edition, author commits and unreported caller settings remain unknown. Live R-object identity and paper reproduction are not claimed. '
 'HICAT/PyArrow remain separate missing interfaces; Cell Ranger/ARC remain excluded. This audit installs nothing and fits no models. The full 250-paper goal remains open.')

def compatibility(before,after):
    row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact preceding reviewed-use bytes cannot be recovered')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='bigcat005-scoped-workflow-coverage',
      scope='Two existing BIGCAT implementation coverage notes only; all primary facts and 1178 other reviews unchanged. Only exact reviewed-use metadata is reversible; scientific overlays forbidden.',paths=[row])

def load_catalog_registry(root=ROOT):
    from arrow_catalog.catalog import load_catalog_registry as previous
    rows=previous(metadata_view(root,load_before(root)))
    if len(rows)!=476:raise ValueError('All 476 preceding cumulative definitions required')
    current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']==TOOL)
    indices=[i for i,r in enumerate(rows) if r['id']==TOOL]
    if len(indices)!=1:raise ValueError('Exactly one existing BIGCAT definition required')
    for key in ['current_coverage','existing_source','coverage_note']:rows[indices[0]][key]=current[key]
    return rows

def coverage_record(root,after):
    return dict(status='PRIMARY_BIGCAT_ROLES_WITH_EXPLICIT_TWO_PROFILE005_WORKFLOWS',reviews=reviews(root),
      paper_role_coverage=next(r for r in json.loads(after[META[0]]) if r['id']==TOOL)['paper_role_coverage'],
      P058_reported_edition=None,P059_reported_edition='0.0.5',author_commits_established=False,
      source_profiles=['prepublication-2023','pre-regression-2022'],single_profile_success_for_all_roles_claimed=False,
      reported_function_roles=8,compound_workflows=2,complete_original_controls=25,complete_returns=19,
      original_failures_without_result=6,compared_fields=3198,complete_original_conditions_retained=True,
      original_condition_fields_deleted_or_coerced=False,live_R_object_identity_claimed=False,
      all_training_cells_used_for_centroids=True,SPN_selection_after_integration=True,
      reciprocal_taxonomy_correspondence_reproduced=False,manual_biological_review_reproduced=False,
      source_or_dependency_algorithms_patched=False,native_component_already_implemented=True,
      new_upstream_family_credit=1,native_family_credit_added=0,whole_tool_qualified=False,
      whole_paper_reproduced=False,whole_goal_complete=False)

def apply(root,out,runtime2023,runtime2022,arrow_runtime,mapping_runtime,doubletfinder_runtime,wsl_distro):
    if any((root/n).exists() for n in [EVIDENCE,PROOF,COVERAGE,COMPATIBILITY]):
        raise ValueError('Admission evidence exists; retain it and use verification')
    before={n:(root/n).read_bytes() for n in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):raise ValueError('Exact preceding metadata differs; preserve concurrent work')
    help_document=encoded(declaration(root));help_path=root/HELP_COMPATIBILITY
    if help_path.exists():
        if help_path.read_bytes()!=help_document:raise ValueError('Existing help-only relationship differs; preserve it')
    else:help_path.write_bytes(help_document)
    reviewed_root(root)
    census=scan(root,json.loads(before[PAPERS]));after=transition(root,before);result=check_transition(root,before,after)
    inverse=compatibility(before,after)
    previous=prior_admission(root,before,rescan=True,arrow_runtime=arrow_runtime,mapping_runtime=mapping_runtime,
      doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    component=qualification(root,runtime2023,runtime2022,wsl_distro)
    evidence=root/EVIDENCE;evidence.mkdir(exist_ok=False)
    (evidence/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    with zipfile.ZipFile(evidence/'catalog-before.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in before.items():
            info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            archive.writestr(info,raw)
    for name,value in [('CENSUS.json',census),('QUALIFICATION.json',component),('PREDECESSOR.json',previous)]:
        (evidence/name).write_bytes(encoded(value))
    (evidence/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode())
    (root/COMPATIBILITY).write_bytes(encoded(inverse));(root/COVERAGE).write_bytes(encoded(coverage_record(root,after)))
    source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE,CURRENT]
    evidence_names=sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file() and p.name!='catalog-before.zip')
    evidence_names += [COVERAGE,COMPATIBILITY,HELP_COMPATIBILITY,DATA+'/arrow-catalog-validation.json']
    evidence_names += sorted(p.relative_to(root).as_posix() for p in (root/'docs/literature/scrattchbigcat005-workflow-validation').iterdir() if p.is_file())
    evidence_names += ['docs/literature/scrattchbigcat005-native-development/VALIDATION.json']
    for name,raw in before.items():
        if (root/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite permitted: '+name)
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    proof=dict(status='PASS_BIGCAT005_SCOPED_WORKFLOWS_AND_PRIMARY_CATALOG_ADMISSION',
      before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},
      after_files={n:pin(root/n) for n in META},source_pins={n:pin(root/n) for n in source_names},
      evidence_pins={n:pin(root/n) for n in evidence_names},admitted_tool_ids=[TOOL],**result,
      scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False)
    (root/PROOF).write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False,runtime2023=None,runtime2022=None,arrow_runtime=None,mapping_runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_BIGCAT005_SCOPED_WORKFLOWS_AND_PRIMARY_CATALOG_ADMISSION' or proof['new_scoped_integration_credit']!=1 or proof['native_family_credit_added']:
        raise ValueError('Unsupported or duplicate BIGCAT family credit')
    for key in ['source_pins','evidence_pins','after_files']:
        for name,identity in proof[key].items():
            if pin(root/name)!=identity:raise ValueError('Qualified catalog source/evidence differs: '+name)
    before=load_before(root)
    if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:
        raise ValueError('Exact eight-input preceding Arrow catalog differs')
    after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
    if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact two-note historical inverse differs')
    census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
    if len(census)!=250 or rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Complete primary census differs')
    if json.loads((root/COVERAGE).read_bytes())!=coverage_record(root,after):raise ValueError('Primary source roles or coverage limits differ')
    previous=prior_admission(root,before,rescan=rescan,arrow_runtime=arrow_runtime,mapping_runtime=mapping_runtime,
      doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    component=qualification(root,runtime2023,runtime2022,wsl_distro);registry=load_catalog_registry(root)
    if len(registry)!=476:raise ValueError('Cumulative definition population changed')
    return dict(status=proof['status'],**result,registry_definitions=len(registry),primary_sources_rescanned=rescan,
      component=component,predecessor=previous['status'],scientific_sources_overridden=False,
      preceding_scientific_verifiers_changed=False,help_only_dispatcher_review=previous['help_only_dispatcher_review'],
      original_analysis_or_model_fitting_executed=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    parser.add_argument('--out',required=True,type=Path);parser.add_argument('--rescan-sources',action='store_true')
    for name,helptext in [('runtime2023','Exact permitted 2023 BIGCAT provider'),('runtime2022','Exact permitted 2022 BIGCAT provider'),
      ('arrow-runtime','Exact permitted recipe-unpatched Arrow provider'),('mapping-runtime','Exact permitted mapping provider'),
      ('doubletfinder-runtime','Exact permitted DoubletFinder provider')]:parser.add_argument('--'+name,help=helptext)
    parser.add_argument('--wsl-distro',help='Explicit permitted WSL distribution on Windows')
    args=parser.parse_args();out,owned=args.out.resolve(),False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True
        providers=dict(runtime2023=args.runtime2023,runtime2022=args.runtime2022,arrow_runtime=args.arrow_runtime,
          mapping_runtime=args.mapping_runtime,doubletfinder_runtime=args.doubletfinder_runtime,wsl_distro=args.wsl_distro)
        result=apply(ROOT,out,**providers) if args.apply else verify(rescan=args.rescan_sources,**providers)
        (out/('APPLIED.json' if args.apply else 'COMPLETED.json')).write_bytes(encoded(result))
        (out/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_BIGCAT_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_BIGCAT_CATALOG_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                (out/'FAILURE.json').write_bytes(encoded(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
                (out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode())
            except BaseException as recording:code=2;print('FAIL2_CATALOG_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
