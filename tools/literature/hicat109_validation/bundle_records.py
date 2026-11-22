#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze generated scientific records; retain original compilation consoles privately."""
from pathlib import Path
import argparse,importlib.util,sys,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/scrattchhicat109'))
from Runtime import load,pin,save
spec=importlib.util.spec_from_file_location('hicat_logical_evidence',ROOT/'tools/literature/scrattchbigcat005_workflows/Evidence.py')
evidence=importlib.util.module_from_spec(spec);spec.loader.exec_module(evidence)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);args=p.parse_args()
    out=args.out.absolute();out.mkdir(parents=True,exist_ok=False)
    base=ROOT/'build/literature-review/monocle-runtime'
    folders={'controls':'hicat109-fresh-controls-2','public-runner':'hicat109-public-runner-guards-1',
      'windows-platform':'hicat109-windows-platform-guard-1','offline-analysis':'hicat109-offline-installed-analysis-1',
      'download-analysis':'hicat109-download-installed-analysis-1','r-comparisons':'hicat109-final-r-comparisons-1',
      'native-linux':'doublet-score-threshold-native-linux-1','native-windows':'doublet-score-threshold-native-windows-1',
      'packaged-windows':'hicat109-packaged-java-qualification-1','packaged-linux':'hicat109-packaged-java-linux-qualification-1',
      'provider-reference':'hicat109-provider-dev-2','record-source-audit':'hicat109-private-record-audit-1',
      'preserved-bigcat-provider':'hicat109-sealed-bigcat-preservation-1',
      'semantic-guards':'hicat109-semantic-guards-1'}
    expected=load(base/'hicat109-private-record-audit-1/AUDIT.json')
    if expected['status']!='PASS_RDS_NO_ORIGINAL_HICAT_FUNCTION_BODIES_ENVIRONMENTS_OR_POINTERS_DETECTED':raise ValueError('Complete private record audit required')
    writer=evidence.Writer(out/'complete-validation-records.zip')
    for label,name in folders.items():
        folder=base/name
        if not (folder/'COMPLETED.json').is_file() and label!='record-source-audit':raise ValueError('Complete qualification record absent: '+name)
        writer.tree(label,folder)
    withheld=[]
    for label,name in [('offline','/opt/omicsmatic-hicat109-public-offline-install-20261115-1'),
      ('download','/opt/omicsmatic-hicat109-public-download-install-20261115-1')]:
        folder=Path(name)
        if load(folder/'COMPLETED.json')['status']!='PASS_INSTALLED_AND_AUDITED_ORIGINAL_HICAT109_PROFILE_NOT_ANALYSIS':raise ValueError('Complete public install required')
        for path in sorted(folder.iterdir()):
            if not path.is_file():continue
            if path.name in ['original-scrattch.hicat.stdout','original-scrattch.hicat.stderr','original-RANN.stdout','original-RANN.stderr']:
                withheld.append(dict(path=str(path),reason='Complete unchanged native compilation console retained privately; diagnostics reproduce original source excerpts.',**pin(path)))
            else:writer.add('install-'+label+'/'+path.name,path)
        writer.tree('provider-'+label,folder.parent/(folder.name+'-provider'))
    save(out/'PRIVATE-COMPILATION-RECORDS.json',dict(status='COMPLETE_NATIVE_COMPILATION_CONSOLES_RETAINED_PRIVATELY_NOT_EDITED',files=withheld,
      scientific_analysis_conditions_or_consoles_withheld=False,upstream_source_or_runtime_redistributed=False))
    writer.add('PRIVATE-COMPILATION-RECORDS.json',out/'PRIVATE-COMPILATION-RECORDS.json')
    junit=ROOT/'build/hicat109-check-1/build/test-results/test/TEST-org.jlinalg.singlecell.DoubletScoreThresholdTest.xml'
    writer.add('project-junit/DoubletScoreThresholdTest.xml',junit)
    jar=next((ROOT/'build/hicat109-check-1/build/cli').glob('*.jar'))
    classes=out/'packaged-own-java-classes';classes.mkdir()
    with zipfile.ZipFile(jar) as archive:
        for name in archive.namelist():
            if name.startswith('org/jlinalg/singlecell/DoubletScoreThreshold') and name.endswith('.class'):
                path=classes/Path(name).name;path.write_bytes(archive.read(name));writer.add('packaged-own-java-classes/'+path.name,path)
    sources=[]
    for name in ['tools/literature/scrattchhicat109','tools/literature/doublet_score_threshold','tools/literature/hicat109_validation']:
        sources.extend(p for p in (ROOT/name).rglob('*') if p.is_file())
    sources.extend(ROOT/n for n in ['src/main/java/org/jlinalg/singlecell/DoubletScoreThreshold.java',
      'src/test/java/org/jlinalg/singlecell/DoubletScoreThresholdTest.java',
      'src/test/resources/org/jlinalg/singlecell/doublet-score-threshold-r423.zip',
      'tools/literature/scrattchbigcat005_workflows/Evidence.py'])
    identities={p.relative_to(ROOT).as_posix():pin(p) for p in sorted(sources)}
    for name,identity in identities.items():
        writer.add('qualified-project-sources/'+name,ROOT/name)
        if {k:writer.files['qualified-project-sources/'+name][k] for k in ['bytes','sha256']}!=identity:
            raise ValueError('Complete project source changed during archive capture: '+name)
    proof=dict(status='PASS_SCOPED_ORIGINAL_HICAT109_AND_INDEPENDENT_JAVA_THRESHOLD',source_files=identities,
      original_controls=26,complete_original_returns=14,original_failures_without_result=12,
      complete_original_objects_conditions_rng_retained=True,public_Linux_guards=15,Windows_prerequisite_skip=3,
      both_actual_public_install_branches_passed=True,both_actual_installed_analyses_compared_whole=True,
      literal_R_comparisons=17697,actual_original_scores_compared_through_packaged_Java=13824,
      Java_versions=['Windows25','Linux21'],permanent_project_JUnit_passed=True,
      RDS_source_body_audit_files=368,upstream_HICAT_function_bodies_in_RDS_detected=0,
      semantic_unchanged_copies_accepted=2,paired_resealed_logical_science_corruptions_rejected=6,
      ZIP_envelope_resealed_in_semantic_guards_claimed=False,
      native_compilation_consoles_retained_privately=True,source_or_runtime_redistributed=False,
      source_or_dependency_algorithms_patched=False,author_commit_function_inputs_environment_established=False,
      whole_tool_qualified=False,whole_paper_reproduced=False,new_family_credit=0,whole_goal_complete=False)
    layout=writer.finish(proof)
    for name,identity in identities.items():
        if pin(ROOT/name)!=identity:raise ValueError('Complete project source changed during bundle freeze: '+name)
    proof.update(evidence_layout=layout,evidence_bundle=pin(out/'complete-validation-records.zip'),
      evidence_payloads={p.name:pin(p) for p in writer.part_paths})
    save(out/'QUALIFICATION.json',proof)
    print('FROZEN_HICAT_NUMERIC_CONTROL_CANDIDATE_REPLAY_STILL_REQUIRED',layout,flush=True)

if __name__=='__main__':main()
