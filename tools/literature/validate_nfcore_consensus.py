# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete native consensus calculations to original GNU sort/BEDTools/Python/awk, including the real full peak population."""
import argparse, hashlib, json, shlex, shutil, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux, wsl


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--complete-attempt', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); attempt = args.complete_attempt.resolve()
    assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    sources = [ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Consensus.java',
        ROOT / 'tools/literature/java/NfcoreConsensusControl.java', Path(__file__)]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    state = dict(status='FAIL2_NATIVE_CONSENSUS_CONTROLS', new_tool_credit=0, scientific_result_admitted=False)
    try:
        for name in source_pins:
            target = out / 'sources' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
        proof_dir = ROOT / 'docs/literature/nfcore-captured-development'
        captured = proof_dir / 'nfcore-captured-javac21.jar'
        proof = json.loads((proof_dir / 'VALIDATION.json').read_bytes()); assert pin(captured) == proof['artifacts'][captured.name]

        def run(label, command, expected=0, timeout=120):
            command = list(map(str, command))
            with (out / (label + '.stdout')).open('xb') as so, (out / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=timeout)
            save(out / (label + '.process.json'), dict(command=command, exit_code=result.returncode, expected_exit=expected))
            assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-2500:])

        run('verify-complete-original-attempt', [JAVA / 'java.exe', '-Xmx512m', '-jar', captured, '--verify', attempt], timeout=180)
        capture = json.loads((attempt / 'capture.json').read_bytes()); outputs = json.loads((attempt / 'outputs.json').read_bytes())['members']
        lock = json.loads((ROOT / 'src/main/resources/org/jlinalg/omics/nfcore/nfcore-source-lock.json').read_bytes())['pipelines']['chipseq-1.2.1']
        original = out / 'original'; original.mkdir()
        for name in ['bin/macs2_merged_expand.py', 'LICENSE']:
            item = capture['roots'][0]['items']['workflow/' + name]; identity = item['original']['pin']
            assert identity == lock['files'][name]
            obj = attempt / 'objects' / identity['sha256']; assert pin(obj) == identity
            shutil.copyfile(obj, original / Path(name).name)
        apptainer = '/var/tmp/omicsmatic-nfcore-apptainer-1/bin/apptainer'
        image = '/var/tmp/omicsmatic-nfcore-images-1/nfcore-chipseq-1.2.1.sif'
        def runtime_pin():
            return json.loads(wsl('/usr/bin/python3', '-c', 'import hashlib,json,sys;from pathlib import Path;p=Path(sys.argv[1]);h=hashlib.file_digest(p.open("rb"),"sha256");print(json.dumps(dict(bytes=p.stat().st_size,sha256=h.hexdigest())))', image, timeout=120))
        expected_image = dict(bytes=1131143168, sha256='50bd3f475b82608dd305138dff30b4789973d930994a5f710a7e5dd8acbb1e38')
        assert runtime_pin() == expected_image
        locale = wsl(apptainer, 'exec', '--userns', '--no-home', '--pid', image, '/usr/bin/locale', timeout=60)
        (out / 'original-image-locale.stdout').write_bytes(locale); assert b'LC_ALL=C.UTF-8' in locale
        editions = wsl(apptainer, 'exec', '--userns', '--no-home', '--pid', image, '/bin/bash', '-c', 'bedtools --version; python --version; sort --version | head -1', timeout=60)
        (out / 'original-editions.stdout').write_bytes(editions); assert b'v2.29.2' in editions and b'Python 3.7.6' in editions
        classes = {}
        for compiler, java in [('21', JAVA), ('25', JAVA25)]:
            directory = out / ('classes' + compiler); directory.mkdir(); classes[compiler] = directory
            run('javac' + compiler, [java / 'javac.exe', '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-d', directory, *sources[:2]])
            for label, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                run('portable-' + compiler + '-' + label, [vm, '-cp', directory, 'org.jlinalg.workflow.NfcoreConsensusControl', '--portable'])
            run('portable-' + compiler + '-linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(directory), 'org.jlinalg.workflow.NfcoreConsensusControl', '--portable'])

        def row(sample, start, end, number=1, chrom='chr1', fold='2.0', p='3.0', q='4.0', summit=None):
            return '\t'.join(map(str, [chrom, start, end, sample + '_peak_' + str(number), 100, '.', fold, p, q, *([] if summit is None else [summit])])) + '\n'

        cases = [
            ('bookended-transitive', 'BROAD', 1, [('A_R1', row('A_R1', 0, 10) + row('A_R1', 20, 30, 2)), ('A_R2', row('A_R2', 10, 20)), ('B_R1', row('B_R1', 80, 90))], 0),
            ('filter-after-merge-bridge', 'BROAD', 2, [('A_R1', row('A_R1', 10, 20)), ('A_R2', row('A_R2', 30, 40)), ('B_R1', row('B_R1', 20, 30))], 0),
            ('duplicates-are-not-replicates', 'BROAD', 2, [('A_R1', row('A_R1', 10, 20) * 2), ('B_R1', row('B_R1', 50, 60)), ('B_R2', row('B_R2', 55, 65))], 0),
            ('full-line-tie-break-and-count-ties', 'BROAD', 1, [('A_R1', row('A_R1', 10, 30, 2, fold='2e-4') + row('A_R1', 10, 20, 10, fold='-0.0')), ('B_R1', row('B_R1', 100, 110)), ('C_R1', row('C_R1', 200, 210))], 0),
            ('unicode-and-contig-byte-order', 'BROAD', 1, [('α_R1', row('α_R1', 1, 5, chrom='chr2')), ('𐀀_R1', row('𐀀_R1', 1, 5, chrom='chr10')), ('\ue000_R1', row('\ue000_R1', 1, 5, chrom='chr1'))], 0),
            ('narrow-raw-relative-summits', 'NARROW', 1, [('A_R1', row('A_R1', 0, 20, summit=-1)), ('A_R2', row('A_R2', 10, 30, summit=0))], 0),
            ('empty-complete-peak-population', 'BROAD', 1, [('A_R1', ''), ('A_R2', '')], 0),
            ('no-group-passes-threshold', 'BROAD', 3, [('A_R1', row('A_R1', 10, 20)), ('A_R2', row('A_R2', 10, 20))], 0),
            ('source-unknown-sample-graph', 'BROAD', 1, [('A_R1', row('UNKNOWN_R1', 10, 20))], 2),
            ('source-nonfinite-value-retained', 'BROAD', 1, [('A_R1', row('A_R1', 10, 20, fold='nan'))], 2),
            ('source-zero-length-geometry-retained', 'BROAD', 1, [('A_R1', row('A_R1', 10, 10))], 2)]
        real_prefix = 'bwa/mergedLibrary/macs/broadPeak/'
        real_samples = ['SPT5_T0_R1', 'SPT5_T0_R2', 'SPT5_T15_R1', 'SPT5_T15_R2']
        real_tables = []
        for sample in real_samples:
            identity = outputs[real_prefix + sample + '_peaks.broadPeak']['pin']; obj = attempt / 'objects' / identity['sha256']; assert pin(obj) == identity
            real_tables.append((sample, obj.read_text(encoding='utf8')))
        cases.append(('complete-original-3357-peaks', 'BROAD', 1, real_tables, 0))
        results = []
        for name, kind, minimum, tables, expected in cases:
            case = out / name; case.mkdir(); names = []
            for sample, text in tables:
                filename = sample + '_peaks.' + ('broadPeak' if kind == 'BROAD' else 'narrowPeak'); (case / filename).write_text(text, encoding='utf8', newline='\n'); names.append((sample, filename))
            (case / 'samples.tsv').write_text(''.join(s + '\t' + f + '\n' for s, f in names), encoding='utf8', newline='\n')
            columns = ','.join(map(str, range(2, 10 if kind == 'BROAD' else 11))); collapse = ','.join(['collapse'] * (8 if kind == 'BROAD' else 9))
            command = 'set -euo pipefail\nexport LC_ALL=C.UTF-8\ncd ' + shlex.quote(linux(case)) + '\n'
            command += 'sort -T . -k1,1 -k2,2n ' + ' '.join(shlex.quote(f) for _, f in names) + ' | mergeBed -c ' + columns + ' -o ' + collapse + ' > original.merged.txt\n'
            command += 'python ' + shlex.quote(linux(original / 'macs2_merged_expand.py')) + ' original.merged.txt ' + shlex.quote(','.join(s for s, _ in names)) + ' original.boolean.txt --min_replicates ' + str(minimum) + (' --is_narrow_peak' if kind == 'NARROW' else '') + '\n'
            command += "awk -v FS='\t' -v OFS='\t' 'FNR > 1 {print $1,$2,$3,$4,\"0\",\"+\"}' original.boolean.txt > original.bed\n"
            command += "printf 'GeneID\\tChr\\tStart\\tEnd\\tStrand\\n' > original.saf\nawk -v FS='\t' -v OFS='\t' 'FNR > 1 {print $4,$1,$2,$3,\"+\"}' original.boolean.txt >> original.saf\n"
            script = case / 'original-control.sh'; script.write_text(command, encoding='utf8', newline='\n')
            run(name + '-original', ['wsl.exe', '-d', DISTRO, '--exec', apptainer, 'exec', '--userns', '--no-home', '--pid', '-B', linux(ROOT) + ':' + linux(ROOT), image, '/bin/bash', linux(script)])
            checks = []
            for compiler in ['21', '25']:
                for label, vm in [('windows21', JAVA / 'java.exe'), ('windows25', JAVA25 / 'java.exe')]:
                    run(name + '-' + compiler + '-' + label, [vm, '-cp', classes[compiler], 'org.jlinalg.workflow.NfcoreConsensusControl', case, kind, minimum], expected)
                    checks.append(dict(compiler=compiler, platform=label, expected_exit=expected, passed=True))
                run(name + '-' + compiler + '-linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-cp', linux(classes[compiler]), 'org.jlinalg.workflow.NfcoreConsensusControl', linux(case), kind, minimum], expected)
                checks.append(dict(compiler=compiler, platform='linux21', expected_exit=expected, passed=True))
            if name == 'complete-original-3357-peaks':
                prefix = real_prefix + 'consensus/SPT5/SPT5.consensus_peaks'
                for short, suffix in [('original.boolean.txt', '.boolean.txt'), ('original.bed', '.bed'), ('original.saf', '.saf'), ('original.boolean.intersect.txt', '.boolean.intersect.txt')]:
                    identity = outputs[prefix + suffix]['pin']; assert pin(case / short) == identity, ('Original replay and captured pipeline differ', short)
            results.append(dict(name=name, kind=kind, minimum_replicates=minimum, original_exit=0, checks=checks, complete_input_rows=sum(t.count('\n') for _, t in tables)))
            print('PASS', name, flush=True)
        assert runtime_pin() == expected_image and all(pin(ROOT / n) == v for n, v in source_pins.items())
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        state.update(status='PASS_COMPLETE_NATIVE_CONSENSUS_AGAINST_ORIGINAL_TOOLS_ONLY', source_pins=source_pins,
            cases=results, original_image=expected_image, original_script=pin(original / 'macs2_merged_expand.py'),
            native_classes={c: {p.relative_to(d).as_posix(): pin(p) for p in d.rglob('*.class') if not p.name.startswith('NfcoreConsensusControl')} for c, d in classes.items()},
            canonical_executable_unchanged=canonical, source_SAF_coordinate_convention_preserved=True,
            original_full_peak_population_replayed_byte_exactly=True, full_nfcore_adapter=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
        (out / 'WARNINGS.md').write_text('# FAIL2 NO CONSENSUS RESULT\n\n' + repr(error) + '\n\nRequired action: preserve all original inputs, tools and failed attempts. Inspect the named source, ordering, replicate, identity, coordinate or numeric discrepancy. Never repair source coordinates/IDs or shorten inputs to force a pass. Use a new control path after correction.\n', encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original-tool, complete-population or native-output checks')
    raise SystemExit(main())
