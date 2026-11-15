#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Apply or verify HICAT coverage and every preceding source-bound catalog gate."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from hicat109_catalog.model import *
from hicat109_catalog.bridge import load_before,metadata_view,predecessor_pins,prior_admission
from hicat109_catalog.source import scan
from hicat109_catalog.qualification import qualification
from wire_signac_catalog import delta
from catalog_source_integrity import reverse_delta
from bigcat_catalog.dispatcher import COMPATIBILITY as HELP_COMPATIBILITY,declaration,reviewed_root

ACTION=('Read HICAT109_CATALOG_INTEGRATION.md and every original warning. Use exact private HICAT and both BIGCAT profiles plus the declared Arrow, mapping and DoubletFinder providers. '
 'SKIP3 skips dependent analysis for missing/forbidden requirements; FAIL2 admits no complete result, including recording failure. '
 'Preserve original values/IDs/NA/NaN/Inf, complete conditions/RNG, raw consoles, partials and prior evidence/hashes. '
 'The actual author HICAT commit/function/gene panel/options/environment remain unknown; 62 housekeeping genes concern separate QC. '
 'Upstream source/runtime and complete native compilation consoles remain private under unresolved original license labels. '
 'No native HICAT scoring port, source repair, fallback, whole-tool validity or paper reproduction is claimed. '
 'This audit installs nothing; its HICAT qualification performs fresh synthetic original scoring calls. The full 250-paper goal remains open.')

def compatibility(before,after):
    row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
    if reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact preceding reviewed-use bytes cannot be recovered')
    return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='hicat109-scoped-workflow-coverage',
      scope='One existing HICAT implementation coverage note only; all primary facts and 1179 other reviews unchanged. Only exact reviewed-use metadata is reversible; scientific overlays forbidden.',paths=[row])

def load_catalog_registry(root=ROOT):
    from bigcat_catalog.catalog import load_catalog_registry as previous
    rows=previous(metadata_view(root,load_before(root)))
    if len(rows)!=476:raise ValueError('All 476 preceding cumulative definitions required')
    current=next(r for r in json.loads((root/META[0]).read_bytes()) if r['id']==TOOL)
    indices=[i for i,r in enumerate(rows) if r['id']==TOOL]
    if len(indices)!=1:raise ValueError('Exactly one existing HICAT definition required')
    for key in ['current_coverage','existing_source','coverage_note']:rows[indices[0]][key]=current[key]
    return rows

def coverage_record(root,after):
    return dict(status='PRIMARY_HICAT109_MODIFIED_DOUBLET_SCORE_WITH_DISTINCT_PREDECESSOR_CONTEXT',reviews=reviews(root),
      paper_role_coverage=next(r for r in json.loads(after[META[0]]) if r['id']==TOOL)['paper_role_coverage'],
      reported_edition='1.0.9',author_commit_exact_function_gene_panel_options_environment_established=False,
      selected_source_profile='dev-zy-prepublication-2022',selected_original_function='doubletFinder',
      selected_original_commit='24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4',primary_method_anchors=[85],
      historical_predecessor_context_anchors=[87,88,91],housekeeping_62_gene_QC_is_separate=True,
      original_controls=26,complete_returns=14,original_failures_without_result=12,
      complete_original_objects_conditions_rng_retained=True,source_or_dependency_algorithms_patched=False,
      native_component_only_supplied_score_predicate=True,native_HICAT_scoring_port=False,
      literal_R_comparisons=17697,actual_original_scores_compared=13824,
      upstream_source_or_runtime_redistributed=False,original_license_labels_resolved=False,
      new_upstream_family_credit=1,native_family_credit_added=0,whole_tool_qualified=False,
      whole_paper_reproduced=False,whole_goal_complete=False)

def apply(root,out,runtime,runtime2023,runtime2022,arrow_runtime,mapping_runtime,doubletfinder_runtime,wsl_distro):
    if any((root/n).exists() for n in [EVIDENCE,PROOF,COVERAGE,COMPATIBILITY]):
        raise ValueError('Admission evidence exists; retain it and use verification')
    before={n:(root/n).read_bytes() for n in META}
    if {n:rawpin(v) for n,v in before.items()}!=predecessor_pins(root):raise ValueError('Exact preceding metadata differs; preserve concurrent work')
    reviewed_root(root)
    census=scan(root,json.loads(before[PAPERS]));after=transition(root,before);result=check_transition(root,before,after)
    inverse=compatibility(before,after)
    previous=prior_admission(root,before,rescan=True,runtime2023=runtime2023,runtime2022=runtime2022,arrow_runtime=arrow_runtime,mapping_runtime=mapping_runtime,
      doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    component=qualification(root,runtime,wsl_distro)
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
    source_names=sorted(p.relative_to(root).as_posix() for p in (root/MODULE).iterdir() if p.is_file())+[GUIDE]
    evidence_names=sorted(p.relative_to(root).as_posix() for p in evidence.iterdir() if p.is_file() and p.name!='catalog-before.zip')
    evidence_names += [COVERAGE,COMPATIBILITY,HELP_COMPATIBILITY,DATA+'/bigcat-catalog-validation.json']
    evidence_names += sorted(p.relative_to(root).as_posix() for p in (root/'docs/literature/scrattchhicat109-workflow-validation').iterdir() if p.is_file())
    for name,raw in before.items():
        if (root/name).read_bytes()!=raw:raise ValueError('Concurrent metadata edit; no overwrite permitted: '+name)
    (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},**result)))
    for name,raw in after.items():(root/name).write_bytes(raw)
    proof=dict(status='PASS_HICAT109_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION',
      before_archive=pin(evidence/'catalog-before.zip'),before_files={n:rawpin(v) for n,v in before.items()},
      after_files={n:pin(root/n) for n in META},source_pins={n:pin(root/n) for n in source_names},
      evidence_pins={n:pin(root/n) for n in evidence_names},admitted_tool_ids=[TOOL],**result,
      scientific_sources_overridden=False,preceding_scientific_verifiers_changed=False)
    (root/PROOF).write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False,runtime=None,runtime2023=None,runtime2022=None,arrow_runtime=None,mapping_runtime=None,doubletfinder_runtime=None,wsl_distro=None):
    proof=json.loads((root/PROOF).read_bytes())
    if proof['status']!='PASS_HICAT109_SCOPED_WORKFLOW_AND_PRIMARY_CATALOG_ADMISSION' or proof['new_scoped_integration_credit']!=1 or proof['native_family_credit_added']:
        raise ValueError('Unsupported or duplicate HICAT family credit')
    for key in ['source_pins','evidence_pins','after_files']:
        for name,identity in proof[key].items():
            if pin(root/name)!=identity:raise ValueError('Qualified catalog source/evidence differs: '+name)
    before=load_before(root)
    if pin(root/EVIDENCE/'catalog-before.zip')!=proof['before_archive'] or {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:
        raise ValueError('Exact eight-input preceding BIGCAT catalog differs')
    after={n:(root/n).read_bytes() for n in META};result=check_transition(root,before,after)
    if json.loads((root/COMPATIBILITY).read_bytes())!=compatibility(before,after):raise ValueError('Exact one-note historical inverse differs')
    census=json.loads((root/EVIDENCE/'CENSUS.json').read_bytes())
    if len(census)!=250 or rescan and scan(root,json.loads(before[PAPERS]))!=census:raise ValueError('Complete primary census differs')
    if json.loads((root/COVERAGE).read_bytes())!=coverage_record(root,after):raise ValueError('Primary source roles or coverage limits differ')
    previous=prior_admission(root,before,rescan=rescan,runtime2023=runtime2023,runtime2022=runtime2022,arrow_runtime=arrow_runtime,mapping_runtime=mapping_runtime,
      doubletfinder_runtime=doubletfinder_runtime,wsl_distro=wsl_distro)
    component=qualification(root,runtime,wsl_distro);registry=load_catalog_registry(root)
    if len(registry)!=476:raise ValueError('Cumulative definition population changed')
    return dict(status=proof['status'],**result,registry_definitions=len(registry),primary_sources_rescanned=rescan,
      component=component,predecessor=previous['status'],scientific_sources_overridden=False,
      preceding_scientific_verifiers_changed=False,help_only_dispatcher_review=previous['help_only_dispatcher_review'],
      fresh_original_HICAT_synthetic_scoring_controls_executed=True,paper_analysis_reproduced=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true')
    parser.add_argument('--out',required=True,type=Path);parser.add_argument('--rescan-sources',action='store_true')
    for name,helptext in [('hicat-runtime','Exact permitted private HICAT 1.0.9 provider'),('runtime2023','Exact permitted 2023 BIGCAT provider'),('runtime2022','Exact permitted 2022 BIGCAT provider'),
      ('arrow-runtime','Exact permitted recipe-unpatched Arrow provider'),('mapping-runtime','Exact permitted mapping provider'),
      ('doubletfinder-runtime','Exact permitted DoubletFinder provider')]:parser.add_argument('--'+name,help=helptext)
    parser.add_argument('--wsl-distro',help='Explicit permitted WSL distribution on Windows')
    args=parser.parse_args();out,owned=args.out.resolve(),False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True
        providers=dict(runtime=args.hicat_runtime,runtime2023=args.runtime2023,runtime2022=args.runtime2022,arrow_runtime=args.arrow_runtime,
          mapping_runtime=args.mapping_runtime,doubletfinder_runtime=args.doubletfinder_runtime,wsl_distro=args.wsl_distro)
        result=apply(ROOT,out,**providers) if args.apply else verify(rescan=args.rescan_sources,**providers)
        (out/('APPLIED.json' if args.apply else 'COMPLETED.json')).write_bytes(encoded(result))
        (out/'WARNINGS.md').write_bytes((WARNING+'\n'+ACTION+'\n').encode());print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_HICAT109_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_HICAT109_CATALOG_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                (out/'FAILURE.json').write_bytes(encoded(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
                (out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode())
            except BaseException as recording:code=2;print('FAIL2_CATALOG_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
