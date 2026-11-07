# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native GC, checked examples and unchanged original background intake controls."""
import json
from pathlib import Path
from validate_mashr_native import pin
from signac_scan_evidence import pack
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime';TARGET=ROOT/'docs/literature/chromvar-gc-development'
def read(p):return json.loads(p.read_bytes())
def main():
    assert not TARGET.exists(),'Preserve existing evidence'
    parent_path=ROOT/'docs/literature/chromvar-deviations-development/VALIDATION.json';parent=read(parent_path);assert all(pin(ROOT/n)==v for n,v in parent['source_pins'].items())
    assert pin(parent_path.parent/'controls.zip')==parent['archive']
    native=read(BASE/'background-gc-controls-1/COMPLETED.json');examples=read(BASE/'background-gc-example-2/COMPLETED.json');sampler=read(BASE/'background-original-controls-1/COMPLETED.json')
    assert native['parent_artifact']==parent['artifact'] and native['artifact']==examples['artifact']
    assert len(native['platform_validation'])==3 and all(p['tests']==p['passed']==49 and p['skipped']==0 for p in native['platform_validation'])
    assert (native['sequence_cases'],native['source_profiles'],native['indexed_reference_ranges'])==(22,2,21)
    assert len(examples['runs'])==3 and examples['documented_example_with_NEW_paths_executed'] and examples['existing_output_refusals']==3
    assert (sampler['case_populations'],sampler['source_and_public_protocols'],sampler['successful_populations'],sampler['retained_source_failures'])==(15,45,9,6) and sampler['all_source_public_backgrounds_and_RNG_states_identical']
    sources={}
    for record in [native,examples,sampler]:
        for n,v in record['source_pins'].items():
            assert pin(ROOT/n)==v and (n not in sources or sources[n]==v);sources[n]=v
    for p in (ROOT/'src/test/resources/org/jlinalg/sequence/chromvar-gc').iterdir():
        if p.is_file():assert pin(p)==native['fixture_files'][p.name];sources[p.relative_to(ROOT).as_posix()]=pin(p)
    for n in ['tools/literature/freeze_chromvar_gc.py','tools/literature/verify_chromvar_gc.py']:sources[n]=pin(ROOT/n)
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');assert canonical==native['canonical_executable_unchanged_during_this_validation']==parent['canonical_executable_at_freeze']
    for a in [native['artifact'],parent['artifact']]:assert pin(ROOT/a['path'])=={k:a[k] for k in ['bytes','sha256']}
    catalog=ROOT/'docs/literature/omics-2023-2026';checksums={}
    for line in (catalog/'SHA256SUMS').read_text().splitlines():
        digest,name=line.split('  ',1);assert pin(catalog/name)['sha256']==digest;checksums[name]=pin(catalog/name)
    assert len(checksums)==402;summary=read(catalog/'summary.json');assert summary['papers']==250 and summary['distinct_named_tools']==387
    counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(16,52,259)
    files={}
    def add(p,name=None):
        assert not p.is_symlink();name=name or p.relative_to(ROOT).as_posix();raw=p.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for n in sources:add(ROOT/n,'current-source/'+n)
    retained=[]
    for folder in sorted(BASE.glob('background-*')):
        if folder.is_dir():
            retained.append(folder.name)
            for p in sorted(folder.rglob('*')):
                if p.is_file() and p.suffix not in {'.jar','.class','.dll','.so'} and 'javadoc' not in p.relative_to(folder).parts:add(p)
    assert {'background-gc-controls-1','background-gc-example-1','background-gc-example-2','background-original-controls-1'}.issubset(retained)
    for folder in [ROOT/'build/chromvar-gc-example-NEW',ROOT/'build/chromvar-gc-example-2-NEW']:
        for p in folder.iterdir():
            if p.is_file():add(p)
    add(parent_path,'parent-deviations-VALIDATION.json');add(parent_path.parent/'WARNINGS.md','parent-deviations-WARNINGS.md')
    files['unchanged-catalog.json']=(json.dumps(checksums,indent=2)+'\n').encode()
    warnings='''# WARNINGS / REQUIRED ACTIONS — CHROMVAR GC DEVELOPMENT

UNDEFINED_GC — Original bias is (C+G)/(A+C+G+T). Ambiguity, gaps and masks are excluded. All-ambiguous/empty sequences retain NaN. Inspect all values and reference/range identities before dependent background sampling; do not silently replace undefined values or filter rows. Signac GC percent/100 is a different denominator when ambiguity is present.

REFERENCE_FAILURE — Missing contigs, duplicate peak identities, out-of-range intervals, invalid residues/references, mismatched indexes and capacity failures admit no result. Preserve the failure, correct the named cause and retry into NEW paths. The API does not write source reference/index files. Coordinate conversion and assembly provenance remain the caller's responsibility.

WINDOWS_JAVA25_COMMAND_LINE — The retained first example failed before entering the native API because this environment's Java25 rejected a Unicode output-path argument. The documented ASCII-path example passed on Java21/25; the fresh Linux example also passed with a Unicode path. Use ASCII helper arguments here or a separately tested caller reading UTF-8 configuration. Biological identifiers must not be renamed as a workaround.

UPSTREAM_BACKGROUND — Source intake controls ran the two unchanged R samplers and current public Matrix interface on recorded current dependencies. Fifteen populations produced nine successful populations and six retained source failures, with exact seeded background matrices and 626-integer before/after RNG states matching across interfaces. Constant bias/intensity, zero pooled counts, undefined bias and one/two-peak rank failures are not repaired. Matching source replay is not independent Java RNG validation, DLL source authentication or a finished end-user background adapter.

SKIP_DEPENDENT_ANALYSIS — Native GC and prepared deviation scoring require Java. A separately needed original sampler requires its exact R/packages. If installation is forbidden or the runtime is unavailable, skip that dependent analysis and name the requirements/action needed to resume; do not admit a substitute result. No installation was attempted here.

SCOPE_AND_PUBLICATION — This is local native API development and synthetic example validation. Historical whole packages, BSgenome container methods, the complete Signac workflow, biological accuracy, root dispatch, catalog credit, release, push and deployment are not certified. Scientific tests bind to the pinned development JAR. The shared canonical JAR is preserved and is a separate build. Hashes establish integrity, not authenticity. Catalog counts remain 68 new integrations, 71 including existing tools, and 259 missing.
'''
    TARGET.mkdir();(TARGET/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');(TARGET/'.gitattributes').write_text('*.json -text\n*.zip -text\n*.md -text\n',encoding='utf8',newline='\n')
    archive=TARGET/'controls.zip';layout=pack(archive,files)
    result=dict(schema_version=1,status='passed-native-chromVAR-GC-and-original-background-intake-development',source_pins=sources,artifact=native['artifact'],parent_artifact=parent['artifact'],parent_validation=pin(parent_path),parent_archive=parent['archive'],archive=pin(archive),**layout,platform_validation=native['platform_validation'],sequence_cases=22,source_profiles=2,indexed_reference_ranges=21,checked_example_runs=3,existing_output_refusals=3,retained_Java25_command_line_failure=True,background_case_populations=15,background_source_and_public_protocols=45,background_successful_populations=9,background_retained_source_failure_populations=6,exact_backgrounds_and_RNG_states_match=True,RNG_state_integers=626,retained_directories=retained,canonical_executable_preserved=canonical,parent_source_pins_unchanged=437,catalog_checksum_count=402,new_integrations=68,including_existing=71,missing=259,background_sampler_intake_only=True,end_user_background_adapter_available=False,independent_native_RNG_replay=False,installed_DLL_source_authentication=False,historical_whole_packages_executed=False,BSgenome_container_methods_executed=False,full_Signac_workflow_available=False,root_dispatch_available=False,catalog_implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,warnings_document=pin(TARGET/'WARNINGS.md'))
    (TARGET/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n');assert all(pin(ROOT/n)==v for n,v in sources.items());print(json.dumps(dict(status=result['status'],archive=result['archive'],physical_members=layout['archive_members'],logical_files=layout['logical_files'],source_pins=len(sources))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native GC milestone checks')
    main()
