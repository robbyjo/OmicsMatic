# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze real MACS2 reference calls without admitting a Java/user workflow."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack


def read(p):
    return json.loads(p.read_bytes())


def main():
    base = ROOT / 'build/literature-review/signac-runtime'
    folder = ROOT / 'docs/literature/signac-peak-runtime-development'
    assert not folder.exists()
    setup = read(base / 'peaks-reference-setup-5/COMPLETED.json')
    assert setup['status'] == 'installed-three-original-MACS2-private-reference-runtimes-not-analysis'
    assert not setup['upstream_source_modified'] and len(setup['runtimes']) == 3
    assert all(pin(ROOT / n) == v for n, v in setup['source_pins'].items())
    references = [read(base / ('peaks-macs-original-' + str(i) + '/COMPLETED.json')) for i in [1,2,3]]
    versions = ['2.2.7.1','2.2.9','2.2.9.1']
    guards = {}
    for version, record in zip(versions, references):
        assert record['actual_calls'] == 9 and record['input_records'] == 6600
        assert record['valid_empty_peak_results'] == record['expected_empty_input_failures'] == 1
        assert all(pin(ROOT / n) == v for n, v in record['source_pins'].items())
        runtime = record['runtimes'][0]
        assert runtime['version'] == version
        home = ROOT / runtime['runtime']['home']
        for n, wanted in runtime['package_unchanged'].items():
            p = home / 'lib/python3.9/site-packages/MACS2' / n
            assert pin(p) == wanted
            if p.suffix in ['.py','.pxd']:
                original = base / ('peaks-source-intake-1/' + version + '/MACS2') / n
                assert p.read_bytes() == original.read_bytes()
        installed = (home / 'bin/macs2').read_bytes()
        source = (base / ('peaks-source-intake-1/' + version + '/bin/macs2')).read_bytes()
        assert installed.splitlines(keepends=True)[1:] == source.splitlines(keepends=True)[1:]
        cases = runtime['cases']
        assert len(cases) == 9 and sum(c['exit_code'] == 0 for c in cases) == 8
        assert [c['peak_rows'] for c in cases] == [3,3,3,3,3,2,2,0,None]
        for c in cases:
            directory = base / ('peaks-macs-original-' + str(versions.index(version)+1)) / version / c['name']
            assert {p.name for p in directory.iterdir() if p.is_file()} == set(c['files'])
            assert all(pin(directory/n) == v for n,v in c['files'].items())
        directory = base / ('peaks-macs-original-' + str(versions.index(version)+1)) / version
        assert (directory/'pooled-BED/fixture_peaks.narrowPeak').read_bytes() != (directory/'pooled-BEDPE/fixture_peaks.narrowPeak').read_bytes()
        guards[version] = dict(home=runtime['runtime']['home'], source_python_and_headers_unchanged=True,
                               installed_script_interpreter_directive_only=True,
                               installed_script=pin(home/'bin/macs2'), package_files=runtime['package_unchanged'])
    names = [ROOT / 'docs/literature/SIGNAC_PEAK_RUNTIME_DEVELOPMENT.md'] + [ROOT/'tools/literature'/n for n in [
        'intake_macs2_reference.py','setup_macs2_reference.py','collect_macs2_peak_reference.py',
        'close_signac_peak_runtime.py','verify_signac_peak_runtime.py']]
    sources = {p.relative_to(ROOT).as_posix():pin(p) for p in names}
    files = {'current-source/'+n:(ROOT/n).read_bytes() for n in sources}
    for name in ['peaks-macs-original-1','peaks-macs-original-2','peaks-macs-original-3',
                 'peaks-preparation-website-1']:
        for p in (base/name).rglob('*'):
            if p.is_file():files[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    for number in [1,2,3,4,5]:
        directory=base/('peaks-reference-setup-'+str(number))
        # Retain every top-level receipt/log/script; private homes/downloads stay local.
        for p in directory.iterdir():
            if p.is_file():files[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    intake=base/'peaks-source-intake-1'
    for p in intake.iterdir():
        if p.is_file() and not p.name.startswith(('cpython-','python-release')):
            files[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    native=read(ROOT/'docs/literature/signac-peak-preparation-development/VALIDATION.json')
    assert canonical==native['canonical_executable_preserved']
    folder.mkdir();(folder/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n')
    warnings='''# REFERENCE ONLY: COMPLETE JAVA CALLPEAKS WORKFLOW UNFINISHED

Three original MACS2 editions built unchanged and27 synthetic original calls were retained.24 calls succeed, including3 valid empty peak outputs;3 empty-input calls fail as expected. These are reference calculations, not an admitted Java/user workflow or biological result. Keep valid empty outputs distinct from failed analyses.

SKIP3 means skip dependent peak analysis when exact permitted requirements are unavailable/forbidden. FAIL2 admits no result for source/input/integrity/process/capacity/finalization failure. Preserve attempts/logs and retry into NEW paths after correcting the named cause. No generic threshold peak substitution or automatic edition replacement is allowed. The first four setup attempts are historical failures/skips; successful attempt5 supersedes them without deletion.

BED and BEDPE are different source models. Five-column BED uses forward-strand tag starts plus declared extension/shift; BEDPE uses full fragment intervals. Read support does not multiply records. Original MACS duplicate handling remains separate. Typed settings, source-compatible group reduction, complete captures/verification and public workflow integration are still required.

Runtime dependencies and compiler choices are explicit, not full historical-paper or host/DLL authentication. Python's unused terminal database was explicitly omitted during private extraction; the complete original archive is retained. No upstream MACS source was patched. Developer setup scripts are not a general portable user installer. Read SIGNAC_PEAK_RUNTIME_DEVELOPMENT.md and the complete receipts.

No root route/catalog credit, whole Signac/whole-paper or biological validation, full project test suite, release, push, deployment or whole-goal completion is claimed. Signac remains missing, totals70new scoped integrations/73including existing adapters/258missing. Cell Ranger stays excluded.
'''
    (folder/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n')
    layout=pack(folder/'controls.zip',files)
    result=dict(schema_version=1,status='passed-three-original-MACS2-reference-runtimes-and-27-calls-not-Java-workflow',
        source_pins=sources,archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,
        exact_MACS2_editions=versions,runtime_guards=guards,actual_original_calls=27,successful_original_outputs=24,
        valid_empty_peak_outputs=3,expected_empty_input_failures=3,unchanged_source_archives={r['version']:r['source'] for r in setup['runtimes']},
        private_toolchain=dict(Python='3.9.20',numpy='1.24.4',Cython='0.29.37',pip='21.3.1',setuptools='57.5.0',wheel='0.37.1',GCC='13.3.0'),
        canonical_executable_preserved=canonical,prior_setup_attempts_retained=4,successful_setup_attempt=5,
        native_preparation_parent=pin(ROOT/'docs/literature/signac-peak-preparation-development/VALIDATION.json'),
        catalog_counts=dict(papers=250,tools=387,observations=2444,new_scoped_integrations=70,including_existing=73,missing=258),
        source_python_and_headers_unchanged=True,MACS_scientific_or_build_source_patched=False,
        actual_Java_CallPeaks_analysis_interface=False,complete_group_reduction_validated=False,
        user_analysis_admitted=False,catalog_implementation_credit=False,root_dispatch_added=False,
        historical_whole_environment_reproduced=False,full_host_or_DLL_authentication=False,
        biological_accuracy_certified=False,full_paper_reproduction=False,full_project_test_suite_run=False,
        released=False,pushed=False,deployed=False,whole_goal_complete=False)
    assert all(pin(ROOT/n)==v for n,v in sources.items());save(folder/'VALIDATION.json',result)
    print(json.dumps(dict(status=result['status'],archive=result['archive'],logical_files=layout['logical_files'])))


if __name__=='__main__':main()
