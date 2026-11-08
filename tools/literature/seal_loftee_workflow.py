# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the complete scoped original LOFTEE execution workflow and its retained failures; catalog admission is separate."""
import json,os,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin,unpack

ATTEMPTS=dict(build='workflow-build-5',native='workflow-native-validation-7',default='workflow-default-discovery-1',compressed='workflow-expanded-probe-6',forked='workflow-expanded-probe-7',late='workflow-expanded-probe-8',fast='workflow-failures-4',supervision='workflow-failures-5',example='workflow-example-validation-1',comparator='workflow-comparator-validation-1',preparation='workflow-preparation-failures-1',refinement='workflow-profile-refinement-1')
HELPERS=['build_loftee_workflow','capture_loftee_workflow_runtime','prepare_loftee_workflow_profile','refine_loftee_workflow_profile','prepare_loftee_install_lock','setup_loftee_workflow_runtime','install_loftee_workflow_runtime','prepare_loftee_workflow_controls','pack_loftee_workflow_inputs','probe_loftee_workflow','probe_loftee_workflow_expanded','probe_loftee_workflow_failures','probe_loftee_preparation_failures','compare_loftee_original','test_compare_loftee_original','recheck_loftee_workflow_original','validate_loftee_comparator','validate_loftee_workflow_native','validate_loftee_default_discovery','prepare_loftee_workflow_example','validate_loftee_workflow_example','seal_loftee_workflow','loftee_workflow_evidence']

def main():
    base=ROOT/'build/literature-review/loftee-runtime';target=ROOT/'docs/literature/loftee-workflow-development';assert target.is_dir() and set(p.name for p in target.iterdir())=={'INSTALL-LOCK.json'};reports={k:json.loads((base/v/'COMPLETED.json').read_bytes()) for k,v in ATTEMPTS.items()};build=reports['build'];artifact=build['artifact'];jar=ROOT/artifact['path'];assert pin(jar)=={k:artifact[k] for k in ['bytes','sha256']}
    for key in ['native','compressed','forked','late','fast','supervision','example']:
        report=reports[key];assert report['artifact']==artifact and report['status'].startswith('passed-'),key
    for key in ['comparator','preparation','refinement','default']:assert reports[key]['status'].startswith('passed-')
    assert [r['tests'] for r in reports['native']['JVMs']]==[39,39,45] and all(r['exit_code']==r['skipped']==0 for r in reports['native']['JVMs']);assert len(reports['compressed']['comparisons'])==8 and len(reports['forked']['comparisons'])==6 and len(reports['example']['comparisons'])==2;assert len(reports['fast']['protocols'])==16 and len(reports['supervision']['protocols'])==2 and reports['late']['earlier_success_retained_but_unadmitted'];assert len(reports['preparation']['protocols'])==3 and all(r['tests']==8 and r['exit_code']==r['skipped']==0 for r in reports['comparator']['platforms'])
    restoration=ROOT/'build/lft-restore-2';restore=json.loads((restoration/'COMPLETED.json').read_bytes());assert restore['status']=='PASS_EXACT_LOCKED_ENVIRONMENT_AND_COMPACT_RUNTIME_PREPARED' and restore['installed_packages']==224 and restore['existing_locked_environment_used_read_only'];reports['restoration']=restore
    for report in reports.values():
        for name,value in report.get('source_pins',{}).items():assert pin(ROOT/name)==value,name
    (target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n')
    names={('tools/literature/'+n+'.py') for n in HELPERS};names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/java/org/jlinalg/cli').glob('LofteeWorkflow*.java'));names.update(['src/main/java/org/jlinalg/cli/LofteeRuntimePrefix.java','src/main/java/org/jlinalg/cli/LofteeRuntimeTree.java','src/test/java/org/jlinalg/cli/LofteeWorkflowTest.java','src/test/java/org/jlinalg/cli/LofteeCompressedInputsTest.java','src/test/java/org/jlinalg/cli/LofteeRuntimeTreeTest.java','src/test/resources/org/jlinalg/genetics/loftee-original/controls-v1.zip','src/test/resources/org/jlinalg/genetics/loftee-original/workflow-inputs-v1.zip','docs/literature/LOFTEE_WORKFLOWS.md','docs/literature/loftee-workflow-development/INSTALL-LOCK.json','docs/literature/loftee-workflow-development/.gitattributes']);names.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'src/main/resources/org/jlinalg/omics/loftee-workflow').iterdir());source_pins={n:pin(ROOT/n) for n in sorted(names)};files={'current-source/'+n:(ROOT/n).read_bytes() for n in names};omissions={}
    for folder in sorted(base.iterdir()):
        if not folder.is_dir() or not (folder.name.startswith('workflow-') or folder.name.startswith('catalog-role-dry-run-')):continue
        for path in folder.rglob('*'):
            if not path.is_file() or path.is_symlink() or '__pycache__' in path.parts or path.suffix=='.pyc':continue
            name=path.relative_to(ROOT).as_posix()
            if path.suffix=='.jar':omissions[name]=dict(**pin(path),reason='Full development JAR retained locally; exact parent/candidate entries and candidate-owned bytes are public below.')
            elif folder.name=='workflow-proof-before-platform-guards-1' and path.name=='controls.zip':omissions[name]=dict(**pin(path),reason='Superseded sealed proof retained privately. Prior validation receipt/current-source bytes and all final scientific inputs/outputs remain public; only six explicit Linux test applicability annotations changed, with independently tested default Windows discovery.')
            else:files[name]=path.read_bytes()
    external={n:v for n,v in reports['native']['source_pins'].items() if n.startswith('build/')};assert len(external)==9 and all(n.endswith('.jar') for n in external);files['EXTERNAL-TEST-LIBRARIES.json']=(json.dumps(external,indent=2)+'\n').encode()
    previous_root=base/'workflow-proof-before-platform-guards-1';previous=json.loads((previous_root/'VALIDATION.json').read_bytes());assert previous['artifact']==artifact and previous['archive']==pin(previous_root/'controls.zip');previous_files=unpack(previous_root/'controls.zip',previous);evolution=json.loads((previous_root/'TEST-PLATFORM-EVOLUTION.json').read_bytes());before_test=previous_files['current-source/'+evolution['path']];after_test=(ROOT/evolution['path']).read_bytes();assert byte_pin(before_test)==evolution['before'] and byte_pin(after_test)==evolution['after'] and after_test.replace(evolution['annotation_line'].encode('ascii'),b'')==before_test
    private_trees={}
    for folder_name in ['lft-restore-1','lft-restore-2','lft-prefix-probe-1']:
        folder=ROOT/'build'/folder_name;assert folder.is_dir()
        for directory,subdirs,filenames in os.walk(folder,followlinks=False):
            current=Path(directory);relative=current.relative_to(folder).parts
            excluded=[]
            for name in subdirs:
                if name in ['archives','mamba-root','full-environment','bioperl-source','environment','bioperl'] or name.startswith('environmentx') or name=='__pycache__':excluded.append(name)
            for name in excluded:private_trees[(current/name).relative_to(ROOT).as_posix()]='Private dependency/package tree; complete archive lock and receipts, exported file identities, installer logs, source-profile derivation and module identities are retained. No final synthetic variant inputs/results are omitted.'
            subdirs[:]=[n for n in subdirs if n not in excluded]
            for filename in filenames:
                path=current/filename
                if path.is_symlink() or path.suffix=='.pyc':continue
                name=path.relative_to(ROOT).as_posix()
                if filename=='micromamba':omissions[name]=dict(**pin(path),reason='Private exact installer ELF; locked original archive identity and version check retained.')
                else:files[name]=path.read_bytes()
        archive_root=folder/'archives'
        if archive_root.exists():
            for path in archive_root.glob('*.receipt.json'):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    files['EXPLICIT-OMISSIONS.json']=(json.dumps(dict(files=omissions,private_dependency_trees=private_trees,final_synthetic_scientific_inputs_outputs_logs_complete=True,no_variant_input_or_output_dropped_for_runtime_refinement=True),indent=2)+'\n').encode()
    parent=ROOT/build['parent_artifact']['path']
    with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:
        old={n:byte_pin(before.read(n)) for n in before.namelist()};new={n:byte_pin(after.read(n)) for n in after.namelist()};assert all(new[n]==v for n,v in old.items()) and set(new)-set(old)==set(build['added_entries'])
        for name in build['added_entries']:files['candidate-owned-entry/'+name]=after.read(name)
    for label,entries in [('parent',old),('candidate',new)]:files['artifact-'+label+'-entries.json']=(json.dumps(entries,indent=2,sort_keys=True)+'\n').encode()
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==build['canonical_executable_unchanged'];parents={n:pin(ROOT/n) for n in ['docs/literature/loftee-audit-development/VALIDATION.json','docs/literature/loftee-audit-development/controls.zip']};counts=json.loads(previous_files['catalog-baseline/summary.json'])['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,63,251)
    for name in ['summary.json','tool-inventory.json']:files['catalog-baseline/'+name]=previous_files['catalog-baseline/'+name]
    (target/'.gitattributes').write_text('* -text\n',encoding='utf8',newline='\n');layout=pack(target/'controls.zip',files);assert (target/'controls.zip').stat().st_size<100000000
    warning=target/'WARNINGS.md';warning.write_text('''# LOFTEE warnings and required actions

**REQUIRE EXIT0 AND result_admitted=true.** Use LofteeWorkflowCli and LOFTEE_WORKFLOWS.md. Read every original log, warning, process cleanup receipt and native variant/annotation ledger. Original VEP exit0 can accompany a plugin error; that batch is refused. Original calls, flags, empty non-target confidence, sample/genotype tokens and raw LoF_info order remain unchanged.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Prepare the exact permitted Linux x86_64 runtime/resources with the locked private installer/exporter, or keep the analysis skipped. Missing, changed, forbidden or unsupported dependencies are not substituted. VEP cache execution is unvalidated and explicitly skipped before deserialization. Preparation without authorization or on Windows stops before downloads. Read the named preparation WARNINGS.md, status and logs; correct the specific cause and retry NEW short paths where permitted. Analysis installs nothing.

**FAIL2: NO ATOMIC-BATCH RESULT ADMITTED.** Earlier successful cases stay unadmitted after a later failure. Preserve originals, errors, partials and owned-process cleanup receipts. Fix the named reference/index/input/source/capacity/timeout/permission cause and retry NEW paths. Never repair identifiers/alleles, omit variants/transcripts, infer masks, manufacture HC/LC, remove flags or replace NA/NaN/Inf. SQLite conservation requires threads1. Complete-CDS database checking is outside this closed local profile.

**SOURCE-SPECIFIC LIMITS.** Both exact original LOFTEE source editions ran with complete synthetic controls, compressed references/genotypes, ancestral and conservation flags, extended splice settings and forked workers. Extended LoF_info feature order varies through original Perl hash iteration; independent checks retain literal token multiplicities with no numeric tolerance and compare every other field/order exactly. Synthetic controls do not certify biological transcript completeness, human resource/release provenance, HC calibration, paper binaries, MANE/canonical masks or downstream burden tests. The existing short-intron control does not validate SMALL_INTRON/GC_TO_GT_DONOR filtering.

**RETAINED FAILURES AND RUNTIME REFINEMENT.** Initial comparison path mistakes, Windows filesystem refusal, the incorrect BGZF EOF constant, strict unordered-feature comparison failures and a validation invalidated by later source changes remain retained.224 exact packages installed, but the first fresh export rejected one old Perl5.22 ViennaRNA module. Both original LOFTEE sources and actual Perl5.32.1 include/module checks prove all eight Perl5.22 ViennaRNA files unreachable in this closed profile. Only those eight files were excluded; all remaining4840 entries retain their original SHA256 checks, and all224 package pins are unchanged. The first failed installation/export and equal-prefix diagnostic remain failed. A separate recovery inspected the installed locked environment read-only and prepared the exact refined compact runtime. This is staged installation/recovery validation, not a relabeled successful first installation.

Default Windows all-class discovery also passed:39 checks pass and six positive Linux-only verifier checks are explicitly skipped; the Windows filesystem-identity refusal check still runs. All45 checks pass on Linux. The prior sealed proof and exact annotation-only test evolution are retained, with the original unchanged main JAR and all model comparisons reused.

Private package/environment trees and full development JARs have explicit omission receipts. Complete final synthetic scientific inputs, outputs and logs are retained publicly. The canonical executable is unchanged. Catalog application, whole-paper reproduction and completion of the250-paper implementation goal are separate.
''',encoding='utf8',newline='\n')
    final={k:(base/v).relative_to(ROOT).as_posix() for k,v in ATTEMPTS.items()};final['restoration']=restoration.relative_to(ROOT).as_posix();save(target/'VALIDATION.json',dict(schema_version=1,status='passed-scoped-typed-original-LOFTEE-workflow',source_pins=source_pins,parent_proofs=parents,artifact=artifact,parent_artifact=build['parent_artifact'],canonical_executable_unchanged=canonical,archive=pin(target/'controls.zip'),warnings=pin(warning),final_attempts=final,unchanged_parent_entries=build['unchanged_parent_entries'],added_entries=build['added_entries'],complete_original_case_comparisons=16,native_checks_by_JVM=[39,39,45],comparator_contract_checks_per_platform=8,positive_runtime_batches=3,expected_no_result_analysis_protocols=19,expected_no_result_preparation_protocols=3,locked_packages=224,runtime_closure_entries=4840,excluded_unreachable_legacy_files=8,installation_validation='original224-package-install-then-separate-read-only-recovery-with-refined-exact-closure',adapter_admitted=True,implementation_credit=True,admitted_named_tools=['loftee'],cache_execution_validated=False,default_Windows_discovery_checks=dict(discovered=45,passed=39,Linux_only_skipped=6,JVMs=2),test_platform_evolution_receipt=pin(previous_root/'TEST-PLATFORM-EVOLUTION.json'),prior_sealed_proof=pin(previous_root/'VALIDATION.json'),catalog_baseline_is_historical=True,catalog_changed=False,whole_paper_reproduced=False,whole_goal_complete=False,**layout));(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['controls.zip','WARNINGS.md','VALIDATION.json','INSTALL-LOCK.json']),encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in source_pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;print('Sealed scoped LOFTEE adapter:',len(files),'complete logical evidence files;16 original case comparisons; catalog admission remains separate')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete workflow checks')
    main()
