#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the native composite API, original-function controls and paper audit."""
import json, sys, zipfile
from datetime import datetime, timezone
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from mashr_original_runtime import census, pin, write
ROOT=Path(__file__).resolve().parents[2]
RUN=ROOT/'build/literature-review/mashr-runtime/composite-development-1'
DEST=ROOT/'docs/literature/mashr-composite'
SOURCES=[
 'src/main/java/org/jlinalg/shrinkage/MashCompositeScores.java',
 'src/test/java/org/jlinalg/shrinkage/MashCompositeScoresTest.java',
 'src/test/java/org/jlinalg/shrinkage/MashCompositeOriginalTest.java',
 'src/test/resources/org/jlinalg/shrinkage/mashr-composite/.gitattributes',
 'src/test/resources/org/jlinalg/shrinkage/mashr-composite/original-controls.zip',
 'tools/literature/mashr-composite-controls.R',
 'tools/literature/collect_mashr_composite_controls.py',
 'tools/literature/validate_mashr_composite.py',
 'tools/literature/audit_mashr_roles.py',
 'tools/literature/finalize_mashr_composite.py',
 'tools/literature/validate_mashr_composite_checkpoint.py',
 'docs/literature/MASHR_COMPOSITE.md',
 'docs/literature/mashr-composite/role-audit.json',
 'docs/literature/mashr-composite/.gitattributes',
 'examples/mashr-composite/Example.java',
 'examples/mashr-composite/README.md']
CLAIMS=dict(native_composite_library_available=True,explicit_independent_marginal_proxy_assumption=True,
           source_score_separate_from_stable_log=True,unmodified_original_function_executed=True,
           all_eight_paper_method_roles_reviewed=True,full_dreamlet_package_validated=False,
           paper_binary_edition_identified=False,joint_posterior_probability_computed=False,
           public_composite_command_available=False,study_route_available=False,catalog_promoted=False,
           whole_paper_reproduced=False,biological_accuracy_certified=False,released_or_deployed=False)


def main():
    assert __debug__, 'Do not disable evidence assertions'
    assert DEST.is_dir() and not (DEST/'controls.zip').exists() and not (DEST/'VALIDATION.json').exists()
    matrix=json.loads((RUN/'matrix-1/COMPLETED.json').read_text())
    original=json.loads((RUN/'original-controls-2/COMPLETED.json').read_text())
    assert matrix['status']=='passed-three-JVM-native-composite-proxy-matrix' and matrix['tests_per_JVM']==440 and len(matrix['runs'])==3
    assert original['status']=='passed-unmodified-original-dreamlet-composite-controls' and original['cases']==30 and original['process']['exit_code']==0
    assert all(pin(ROOT/n)==v for record in [matrix,original] for n,v in record['source_pins'].items())
    prior_path=ROOT/'docs/literature/mashr-summary/VALIDATION.json'; prior=json.loads(prior_path.read_text())
    protected={**prior['protected_prior_source_pins'],**prior['source_pins']}
    assert all(pin(ROOT/n)==v for n,v in protected.items())
    artifact=ROOT/matrix['artifact']['path']; assert pin(artifact)=={k:matrix['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_executable_unchanged']==prior['canonical_executable_unchanged']
    catalog=pin(ROOT/'docs/literature/omics-2023-2026/summary.json'); assert catalog==prior['catalog_summary_unchanged']
    audit=json.loads((DEST/'role-audit.json').read_text()); assert audit['role_count']==8 and audit['claims']['method_formula_resolved'] and not audit['claims']['catalog_promoted']
    example=(RUN/'example-run-1.log').read_text();assert 'threshold >=0.05: true, false, false' in example and 'SOURCE_ROUNDING_TO_ZERO' in example
    assert (RUN/'example-compile-1.log').read_bytes()==b'' and (RUN/'example-classes-1/Example.class').is_file()
    bound={n:pin(ROOT/n) for n in SOURCES};items={};graphs={}
    def tree(path,prefix):
        graph=census(path,1_073_741_824,100000);graphs[path]=graph
        for n,v in graph.items(): assert prefix+'/'+n not in items;items[prefix+'/'+n]=(path/n,v)
        return graph
    for name,record in [('matrix-1',matrix),('original-controls-2',original)]:
        graph=tree(RUN/name,'evidence/'+name);assert {n:v for n,v in graph.items() if n!='COMPLETED.json'}==record['files']
    for name in ['original-controls-1','source-at-original-controls-1']:tree(RUN/name,'history/'+name)
    tree(RUN/'example-classes-1','evidence/example-classes')
    # Include licensed original utility and intake metadata; link/hash full author
    # analysis scripts whose redistribution license was not established.
    intake=RUN/'source-intake-1'
    for name in ['Artistic-2.0.txt','dreamlet-receipt.json','license-receipt.json','dreamlet/DESCRIPTION','dreamlet/R/compositePosteriorTest.R','commit.json','tree.json','RECEIPT.json']:
        path=intake/name;items['intake/'+name]=(path,pin(path))
    for name in ['commit.json','tree.json']:
        path=RUN/'P024-source-intake-1'/name;items['intake/P024/'+name]=(path,pin(path))
    for path in RUN.glob('*.log'):items['logs/'+path.name]=(path,pin(path))
    for n,v in bound.items():items['source/'+n]=(ROOT/n,v)
    with zipfile.ZipFile(DEST/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for n,(path,v) in sorted(items.items()):
            raw=path.read_bytes(); assert pin(path)==v
            entry=zipfile.ZipInfo(n,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    assert all(census(p,1_073_741_824,100000)==g for p,g in graphs.items()) and all(pin(ROOT/n)==v for n,v in bound.items()) and all(pin(ROOT/n)==v for n,v in protected.items())
    dependencies={**prior['baseline_dependencies']}
    for name in ['VALIDATION.json','controls.zip']:
        path='docs/literature/mashr-summary/'+name;dependencies[path]=pin(ROOT/path)
    proof=dict(schema_version=1,status='passed-native-composite-proxy-library-development-checkpoint',
        recorded_utc=datetime.now(timezone.utc).isoformat(),
        scope='Native ALL/AT_LEAST_ONE included marginal proxies and excluded complements, explicit independence/missingness, source-rounded scores and stable-log diagnostics; eight paper method roles reviewed without whole-paper claims.',
        artifact=matrix['artifact'],archive=dict(path='controls.zip',**pin(DEST/'controls.zip'),entries=len(items)),
        tests_per_JVM=440,new_tests_per_JVM=44,new_analytic_tests=14,original_R_cases=30,
        heap_mib=128,platforms=[r['name'] for r in matrix['runs']],source_pins=bound,protected_prior_source_pins=protected,
        original_reference=dict(commit=original['original_source_commit'],version=original['original_source_version'],license='Artistic-2.0',full_package_validated=False,paper_binary_edition_identified=False),
        numeric_tolerances=dict(source_score='1e-15 + 2e-15 * abs(reference)',stable_log='1e-12 + 2e-14 * abs(reference)',decisions='exact for explicitly requested >=0.05, >0.5 and >0.8 controls; no inferred paper boundary at0.8'),
        protected_packaged_classes_unchanged=matrix['protected_packaged_classes_unchanged'],owned_classes=matrix['owned_classes'],
        baseline_dependencies=dependencies,canonical_executable_unchanged=matrix['canonical_executable_unchanged'],catalog_summary_unchanged=catalog,
        retained_failed_fixture=dict(path='history/original-controls-1',cause='Fixture assigned a nonempty column-name vector to a zero-column matrix before the original method call. The corrected fixture preserves zero columns. This is not evidence of an original implementation defect.'),
        repository_example_compiled_and_run=True,archive_files={n:v for n,(_,v) in sorted(items.items())},claims=CLAIMS,
        required_action='Use the documented native Java API with this identified artifact. Declare exact selectors, scales, independence and missingness; inspect numeric loss. The public summary formal_composite placeholder remains SKIP3. Supply unresolved paper inputs and settings explicitly. Invalid input, integrity, capacity and cancellation fail without a returned prefix. Retain prior archives for baseline closure.')
    write(DEST/'VALIDATION.json',proof)
    print(json.dumps(dict(status=proof['status'],tests_per_JVM=440,original_R_cases=30,source_files=len(bound),protected_source_files=len(protected),archive=proof['archive'])))


if __name__=='__main__':
    try:main()
    except Exception as failure:
        print('FAIL2_COMPOSITE_CHECKPOINT_FREEZE: '+str(failure)+'\nRequired action: retain the failed attempt and inspect the named evidence mismatch; never overwrite an existing archive.',file=sys.stderr);sys.exit(2)
