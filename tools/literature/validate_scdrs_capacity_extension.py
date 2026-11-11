# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify natural sparse-matrix capacity declarations and replay prior workflows after the one-bound extension."""
import argparse
import copy
import json
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--build21', required=True, type=Path)
    p.add_argument('--build25', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_SCDRS_MATRIX_CAPACITY_EXTENSION', new_catalog_credit=False,
        large_biological_atlas_analyzed=False, logical_matrix_allocated_by_declaration_probe=False)

    def run(label, args, expected=0):
        result = subprocess.run(args, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (out / (label + '.stdout')).write_bytes(result.stdout)
        (out / (label + '.stderr')).write_bytes(result.stderr)
        save(out / (label + '.process.json'), dict(command=args, exit_code=result.returncode))
        assert result.returncode == expected, (label, result.returncode, result.stderr[-2000:])

    try:
        artifacts = {}
        changes = {}
        for tag, before_dir, after_dir in [('21', BASE / 'workflow-build21-5', a.build21), ('25', BASE / 'workflow-build25-4', a.build25)]:
            old = json.loads((before_dir / 'COMPLETED.json').read_bytes())
            new = json.loads((after_dir / 'COMPLETED.json').read_bytes())
            for record in [old, new]: assert record['status'] == 'PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY'
            old_jar, new_jar = ROOT / old['artifact']['path'], ROOT / new['artifact']['path']
            assert pin(old_jar) == {k: old['artifact'][k] for k in ['bytes', 'sha256']}
            assert pin(new_jar) == {k: new['artifact'][k] for k in ['bytes', 'sha256']}
            for name, wanted in new['source_pins'].items(): assert pin(ROOT / name) == wanted
            source_name = 'src/main/java/org/jlinalg/cli/ScdrsRequest.java'
            before = (before_dir / 'source-snapshot' / source_name).read_bytes()
            after = (after_dir / 'source-snapshot' / source_name).read_bytes()
            old_line = b'integer(l.get("matrix_elements"),1,10_000_000_000L)'
            new_line = b'integer(l.get("matrix_elements"),1,200_000_000_000L)'
            assert before.count(old_line) == 1 and before.replace(old_line, new_line) == after
            with zipfile.ZipFile(old_jar) as z: old_entries = {n: z.read(n) for n in z.namelist()}
            with zipfile.ZipFile(new_jar) as z: new_entries = {n: z.read(n) for n in z.namelist()}
            assert set(old_entries) == set(new_entries)
            different = [n for n in old_entries if old_entries[n] != new_entries[n]]
            assert different == ['org/jlinalg/cli/ScdrsRequest.class']
            artifacts[tag] = new_jar
            changes[tag] = dict(previous=pin(old_jar), current=pin(new_jar), changed_entries=different,
                every_other_class_and_resource_byte_identical=True, source_change_one_explicit_upper_bound_only=True)
        probe = ROOT / 'src/test/java/org/jlinalg/cli/ScdrsCapacityReferenceProbe.java'
        (out / 'probe-at-attempt.java').write_bytes(probe.read_bytes())
        jdk = Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')
        classes = out / 'classes'
        classes.mkdir()
        run('compile', [str(jdk / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-sourcepath', '',
            '-encoding', 'UTF-8', '-cp', str(artifacts['21']), '-d', str(classes), str(probe)])
        template = json.loads((BASE / 'typed-workflow-smoke-1/request.json').read_bytes())
        trials = [(1, True), (10_000_000_000, True), (30_000_000_000, True),
                  (200_000_000_000, True), (200_000_000_001, False), (0, False)]
        results = []
        for platform, java, sep, convert, tag in [
            ('windows21', [str(jdk / 'java.exe')], ';', str, '21'),
            ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'], ';', str, '25'),
            ('linux21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java'], ':', linux, '21')]:
            for number, valid in trials:
                request = copy.deepcopy(template)
                request['limits'].update(cells=1_000_000, genes=200000, matrix_elements=number)
                recipe = out / (platform + '-capacity-' + str(number) + '.json')
                save(recipe, request)
                output = out / (platform + '-capacity-' + str(number) + '.txt')
                run(output.stem, java + ['-cp', sep.join(convert(p) for p in [classes, artifacts[tag]]),
                    'org.jlinalg.cli.ScdrsCapacityReferenceProbe', convert(recipe), convert(output)], 0 if valid else 1)
                assert (output.read_text() == str(number) + '\n') if valid else not output.exists()
                results.append(dict(platform=platform, declared_matrix_elements=number, accepted=valid))
        replays = 0
        for parent in ['workflow-validation-1', 'workflow-extra-validation-1']:
            receipt = json.loads((BASE / parent / 'COMPLETED.json').read_bytes())
            assert receipt['status'] == 'PASS_TYPED_SCDRS_WORKFLOWS_MATCH_ORIGINAL_CLI_DATA_AND_THREE_SAVED_JVMS'
            for case in receipt['cases']:
                bundle = BASE / parent / case['case'] / 'bundle'
                for platform, command in [
                    ('windows21', [str(jdk / 'java.exe'), '-jar', str(artifacts['21']), '--verify', str(bundle)]),
                    ('windows25', ['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', '-jar', str(artifacts['25']), '--verify', str(bundle)]),
                    ('linux21', ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--', '/usr/bin/java', '-jar', linux(artifacts['21']), '--verify', linux(bundle)])]:
                    run('saved-' + parent + '-' + case['case'] + '-' + platform, command)
                    replays += 1
        assert replays == 60
        state.update(status='PASS_ONE_BOUND_SCDRS_CAPACITY_EXTENSION_18_DECLARATIONS_AND60_SAVED_REPLAYS',
            compiler_changes=changes, declaration_controls=results, saved_replays=replays,
            maximum_cells=1_000_000, maximum_genes=200000, natural_maximum_logical_matrix_elements=200_000_000_000,
            input_output_nonzero_working_and_wall_capacities_not_relaxed=True, original_scientific_functions_unchanged=True)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable exact source/binary/capacity/replay checks')
    raise SystemExit(main())
