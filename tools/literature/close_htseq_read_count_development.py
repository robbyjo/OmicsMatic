# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal native prepared-read counts and five real original counters without whole-tool credit."""
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from validate_htseq_read_count_native import BASE,JAR


def main():
    folder=ROOT/'docs/literature/htseq-read-count-development';assert not folder.exists()
    native=json.loads((BASE/'read-count-native-matrix-1/COMPLETED.json').read_bytes())
    example=json.loads((BASE/'read-count-example-1/COMPLETED.json').read_bytes())
    binding=json.loads((BASE/'count-reference-binding-2/COMPLETED.json').read_bytes())
    browser=json.loads((BASE/'role-ui-2/COMPLETED.json').read_bytes())
    general_ui=json.loads((BASE/'role-ui-2/general/ui-validation.json').read_bytes())
    role_ui=json.loads((BASE/'role-ui-2/roles/htseq-ui-verification.json').read_bytes())
    roles=json.loads((ROOT/'docs/literature/omics-2023-2026/htseq-role-coverage.json').read_bytes())
    artifact=pin(JAR);canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    assert artifact==native['artifact']==example['artifact'] and canonical==native['canonical_executable_preserved']
    assert native['tests_per_JVM']==2315 and len(native['JVMs'])==example['JVMs']==3
    assert native['all_existing_parent_entries_preserved'] and roles['applied'] and roles['total_reviews']==898
    assert len(binding['profiles'])==5 and browser['status'].startswith('passed-local-')
    sources=[ROOT/'src/main/java/org/jlinalg/sequence/HtseqReadCounter.java',
        ROOT/'src/test/java/org/jlinalg/cli/HtseqReadCounterTest.java',ROOT/'docs/literature/HTSEQ_READ_COUNT_DEVELOPMENT.md']
    for name in ['src/main/resources/org/jlinalg/omics/htseq-read-count',
        'src/test/resources/org/jlinalg/sequence/htseq-read-count','examples/htseq-read-count']:
        sources += [p for p in (ROOT/name).rglob('*') if p.is_file()]
    for name in ['collect_htseq_count_controls.py','collect_htseq_fraction_controls.py','collect_htseq_historical_counts.py',
        'setup_htseq_historical_reference.py','prepare_htseq_gcc_setup.py','setup_htseq_historical_gcc_reference.py',
        'bind_htseq_reference_profiles.py','prepare_htseq_read_count_fixture.py','htseq-read-count-development.init.gradle',
        'validate_htseq_read_count_native.py','validate_htseq_read_count_example.py','collect_htseq_omics.py',
        'curate_htseq_uses.py','wire_htseq_role_catalog.py','check_htseq_roles_ui.cjs','validate_htseq_role_ui.py',
        'close_htseq_read_count_development.py','verify_htseq_read_count_development.py']:
        sources.append(ROOT/'tools/literature'/name)
    data=ROOT/'docs/literature/omics-2023-2026'
    for name in ['htseq-role-coverage.json','htseq-use-shared-source-compatibility.json','htseq-role-ui-shared-source-compatibility.json']:
        sources.append(data/name)
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    evidence={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    for name in ['count-original-1','count-fraction-original-1','historical-py27-counts-1','historical-py27-counts-2',
        'historical-py39-counts-1','read-count-native-matrix-1','read-count-example-1','count-reference-binding-1','count-reference-binding-2','role-ui-1','role-ui-2']:
        for path in (BASE/name).rglob('*'):
            if path.is_file():evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    # Keep setup logs, process metadata, publisher metadata and per-profile
    # outcomes, without copying mutable installed homes, wheels or build trees.
    for name in ['historical-py27-setup-1','historical-py39-setup-1','historical-py39-setup-2']:
        setup=BASE/name
        for path in setup.iterdir():
            if path.is_file():evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        for path in setup.glob('*/*.json'):
            evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    evidence['historical-gcc-source-derivation.json']=(BASE/'historical-gcc-source-derivation.json').read_bytes()
    for name in ['role-curation-dry-1','role-curation-applied-1']:
        for path in (BASE/name).iterdir():
            if path.is_file() and not path.name.startswith('PRIVATE-'):evidence[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for profile in binding['profiles']:
        for row in profile['scientific_source_bindings']:
            source=ROOT/row['original_path'];body=source.read_bytes()
            assert pin(source)=={k:row[k] for k in ['bytes','sha256']}
            evidence['original-scientific-sources/'+profile['version']+'/'+row['installed_path']]=body
    evidence['read-count-gradle-tests-1.log']=(BASE/'read-count-gradle-tests-1.log').read_bytes()
    website=(BASE/'read-count-website-1.log').read_bytes()
    assert 'BUILD SUCCESSFUL' in website.decode('utf8')
    evidence['read-count-website-1.log']=website
    for test in ['HtseqReadCounterTest','HtseqFeatureAssignmentTest']:
        evidence['normal-Gradle-'+test+'.xml']=(ROOT/'build/test-results/test'/('TEST-org.jlinalg.cli.'+test+'.xml')).read_bytes()
    entries=json.loads((BASE/'read-count-native-matrix-1/parent-entries.json').read_bytes())
    with zipfile.ZipFile(JAR) as archive:
        for name in entries['added']:
            if name.endswith('/'):assert archive.getinfo(name).is_dir() and archive.read(name)==b''
            else:evidence['candidate-owned-entries/'+name]=archive.read(name)
    summary=json.loads((data/'summary.json').read_bytes())
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],summary['implementation_status_counts']['missing'])==(250,387,2446,257)
    for name in ['summary.json','papers.json','tool-inventory.json','SHA256SUMS']:
        evidence['catalog-snapshot/'+name]=(data/name).read_bytes()
    evidence['catalog-snapshot/reviewed-uses.json']=(ROOT/'tools/literature/reviewed-uses.json').read_bytes()
    catalog={line.split('  ',1)[1]:line.split('  ',1)[0] for line in (data/'SHA256SUMS').read_text().splitlines()}
    assert len(catalog)==425 and all(pin(data/name)['sha256']==digest for name,digest in catalog.items())
    parent=ROOT/'docs/literature/htseq-interval-development'
    parent_record=json.loads((parent/'VALIDATION.json').read_bytes());assert pin(parent/'controls.zip')==parent_record['archive']
    folder.mkdir()
    (folder/'WARNINGS.md').write_text('''# HTSEQ PREPARED-READ COUNTING: WARNINGS AND REQUIRED ACTION

Use the separate htseq-read-count development JAR and examples/htseq-read-count with Java17+. The canonical/released executable does not expose this API. The counter requires explicitly prepared read units and original annotation identities. It does not parse SAM/GTF, pair reads, implement random assignment, expose an HTSeq root command or certify a full paper pipeline. HTSeq remains missing in the catalog. Eight reviewed roles are documented;71new scoped integrations/74including existing adapters/257missing remain unchanged.

Native source profile is HTSeq2.1.2 only. Preserve unaligned/flag/NH/MAPQ filter order, the missing-first-NH short circuit, absent versus present unaligned mates, source strand transforms, whole-unit unknown-chromosome handling and overlapping summary categories. FRACTION divides by assigned-feature count, not NH. Source floating-point increments are retained exactly. Minimum21 implements strict MAPQ>20. No automatic genome aliases, identifier repair, annotation conversion, filtering relaxation, FPKM or inference are supplied.

Required action after invalid input, coordinate, strand/layout, exceeded logical bounds or cancellation: retain original inputs and the exception, correct the stated cause and retry. No partial Counts result is returned. Callers must retain failed attempts; this library API creates no status/run directory. Empty inputs and zero gene counts are not evidence of biological absence. Logical budgets are not resident-memory guarantees.

Actual original counters ran185valid SAM/GTF calls across0.5.3/0.6.0/0.11.1/0.13.5/2.1.2:171successful calls and14retained source failures. These reuse tiny synthetic populations/options, not185independent datasets.0.5.3 default minimum MAPQ is0, its summary names are unprefixed and unknown chromosomes warn/skip without incrementing no_feature. Later releases differ.0.5.3/0.6.0 reject tested header-only SAM and =/X CIGAR. All five retain missing-ID/stranded-annotation errors. Do not rewrite unsupported inputs or interpret failures as negative findings. Native current-source comparison covers40count tables,198gene values and200summary values from627original parsed/paired units; current-source random controls are excluded from native replay.2315tests pass without skips per JVM on Windows21/25 and Linux21; the example passes on each. Existing parent entries and canonical executable are preserved.

Retained failures include59Python2 Unicode-filename bridge calls, excluded from the185valid reference calls. Corrected UTF8 byte-string transport alters no HTSeq code. Initial Python3.9 historical builds failed because the SDK selected absent Clang. The derived installer changes only compiler selection to installed GCC/G++. The first source binder stopped at the2.1.2 wheel's omitted obsolete count_old.py; recovery accepts that exact source-only omission and refuses all other added/missing scientific Python modules. All installed scientific Python modules match their release originals. Four completed, hash-checked runtime inventories are reused explicitly; the fifth is freshly captured. Bytecode is included. This is recorded private-home provenance, not a fresh rescan of reused homes, whole historical environment equivalence, signature, host/DLL authentication or execution authentication. Shared Python packages are unchanged.

Missing/forbidden runtime policy for future upstream-dependent analyses: emit SKIP3 and skip the affected analysis. Where environment permissions permit, provision the exact declared profile and retry into NEW paths. If installation is forbidden, retain the skip and its recovery instructions. Source/runtime integrity, invalid input, process and capacity errors must produce no admitted partial analysis. This native prepared-read API needs Java only. Original reference installation is separate from native execution; no runtime is bundled in this evidence archive.

P099 describes custom binary peak-by-cell read presence, with unstated edition/barcode filters/script. It is not the standard gene counter. P121 requires strict MAPQ>20/proper pairing but does not resolve read/fragment units. P113 RNA states0.11.1; EU does not separately state an edition. P195 states0.13, not an exact patch. Do not infer missing paper settings. Cell Ranger remains excluded. Read ../HTSEQ_READ_COUNT_DEVELOPMENT.md for the checked API and full boundaries. No full project suite, biological certification, whole-paper reproduction, release, push, deployment or whole-goal completion is claimed.
''',encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',evidence)
    proof=dict(schema_version=1,status='passed-native-HTSeq-prepared-read-counting-and-five-original-references',
        artifact=dict(path=JAR.relative_to(ROOT).as_posix(),**artifact),source_pins=pins,archive=pin(folder/'controls.zip'),
        warnings_document=pin(folder/'WARNINGS.md'),**layout,parent_interval_validation=pin(parent/'VALIDATION.json'),parent_interval_archive=parent_record['archive'],
        parent_artifact=entries['parent'],unchanged_existing_entries=entries['unchanged_existing_entries'],all_parent_entries_preserved=True,
        canonical_executable_preserved=canonical,tests_per_JVM=2315,new_counting_tests_per_JVM=91,JVMs=3,checked_example_JVMs=3,
        original_count_tables_compared=40,original_gene_values_compared=198,original_summary_values_compared=200,original_prepared_units=627,
        original_reference_versions=['0.5.3','0.6.0','0.11.1','0.13.5','2.1.2'],valid_original_calls=185,original_successes=171,original_source_failures=14,
        excluded_transport_invalid_calls=59,reviewed_primary_roles=8,prior_reviews_preserved=890,reviewed_uses=898,paper_tool_observations=2446,
        general_browser_checks=len(general_ui['checks']),role_browser_checks=len(role_ui['checks']),catalog_checksums=catalog,catalog_counts=dict(new_scoped_integrations=71,including_existing=74,missing=257),
        original_SAM_GTF_count_executed=True,independent_native_SAM_GTF_parser=False,independent_native_pairing=False,native_random_assignment=False,
        HTSeq_root_dispatch_available=False,Cell_Ranger_excluded=True,historical_whole_packages=False,execution_authentication=False,
        biological_validity_certified=False,whole_paper_reproduced=False,full_project_test_suite_run=False,catalog_implementation_credit=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/name)==value for name,value in pins.items())
    save(folder/'VALIDATION.json',proof)
    (folder/'SHA256SUMS').write_text(''.join(pin(folder/name)['sha256']+'  '+name+'\n' for name in ['VALIDATION.json','controls.zip','WARNINGS.md']),encoding='ascii',newline='\n')
    print(json.dumps(dict(status=proof['status'],archive=proof['archive'],logical_files=proof['logical_files'],source_pins=len(pins))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native checkpoint checks')
    main()
