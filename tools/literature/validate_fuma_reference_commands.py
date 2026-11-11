"""Exercise the documented public reference commands against an isolated executable."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
from validate_mashr_native import ROOT, JAVA21, pin

PAYLOAD = ['variants.tsv', 'samples.tsv', 'sample-selection.tsv', 'genotypes.bin', 'reference.tsv', 'variant-audit.tsv', 'ld.tsv', 'ld-audit.tsv']


def write(p, j):
    p.write_text(json.dumps(j, indent=2) + '\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__:
        raise RuntimeError('Never disable evidence assertions')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', required=True, type=Path)
    parser.add_argument('--classes', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    a = parser.parse_args()
    jar, classes, out = a.artifact.resolve(), a.classes.resolve(), a.out.resolve()
    base = (ROOT / 'build/literature-review/fuma-runtime').resolve()
    assert jar.is_relative_to(base) and classes.is_relative_to(base) and out.is_relative_to(base) and not out.exists()
    out.mkdir()
    try:
        artifact = pin(jar)
        canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
        sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__), ROOT / 'docs/literature/FUMA_REFERENCE_WORKFLOW.md', *sorted((ROOT / 'examples/fuma-reference').glob('*'))]}
        assert (classes / 'CreateInputs.class').read_bytes()[6:8] == b'\x00='
        calls = []

        def command(label, argv):
            with (out / (label + '.stdout')).open('xb') as stdout, (out / (label + '.stderr')).open('xb') as stderr:
                result = subprocess.run(list(map(str, argv)), cwd=ROOT, stdout=stdout, stderr=stderr, stdin=subprocess.DEVNULL, timeout=60, check=False)
            record = dict(label=label, argv=list(map(str, argv)), exit_code=result.returncode)
            write(out / (label + '.json'), record)
            assert result.returncode == 0, label + ': inspect retained complete logs'
            calls.append(record)
            print(label + ': exit0', flush=True)

        work = out / 'work'
        command('create-inputs', [JAVA21 / 'java.exe', '-Xmx128m', '-cp', classes, 'CreateInputs', work])
        shutil.copyfile(ROOT / 'examples/fuma-reference/workflow.json', work / 'workflow.json')
        java = [JAVA21 / 'java.exe', '-Xmx128m', '-jar', jar, 'fuma-tools']
        workflow = work / 'workflow-1'
        command('workflow', [*java, '--method', 'workflow', '--workflow-options-file', work / 'workflow.json', '--out', workflow])
        captured = workflow / 'reference-preparation'
        command('replay', [*java, '--method', 'replay-reference', '--bundle', captured, '--out', work / 'replay-1'])
        command('loci', [*java, '--locus-options-file', ROOT / 'examples/fuma-reference/locus-options.json', '--gwas', work / 'input/gwas.tsv', '--reference-bundle', work / 'replay-1', '--expected-reference-assembly', 'synthetic-GRCh37', '--out', work / 'loci-1'])
        command('standalone-prepare', [*java, '--method', 'prepare-reference', '--bfile', work / 'input/panel', '--variants', work / 'input/variants.tsv', '--samples', work / 'input/samples.tsv', '--sample-policy', 'founders', '--assembly', 'synthetic-GRCh37', '--population', 'declared-synthetic', '--release', 'example-v1', '--minimum-maf', '0', '--ld-floor', '.05', '--window-bp', '1000', '--window-index-gap', '1', '--out', work / 'standalone-1'])
        portable = work / 'portable-capture'
        portable.mkdir()
        for name in [*PAYLOAD, 'reference-bundle.json']:
            shutil.copyfile(captured / name, portable / name)
        command('portable-replay', [*java, '--method', 'replay-reference', '--bundle', portable, '--out', work / 'portable-replay-1'])
        for other in [work / 'replay-1', work / 'standalone-1', work / 'portable-replay-1']:
            for name in PAYLOAD:
                assert (captured / name).read_bytes() == (other / name).read_bytes(), (other, name)
        bundle = json.loads((captured / 'reference-bundle.json').read_text(encoding='utf-8'))
        assert bundle['accounting'] == dict(retained_variants=3, assessed_pairs=2, stored_pairs=2, below_floor_pairs=0, word_work=88, peak_cached_rows=2)
        assert len((captured / 'variant-audit.tsv').read_text(encoding='utf-8').splitlines()) == 7
        assert len((captured / 'samples.tsv').read_text(encoding='utf-8').splitlines()) == 5
        assert len((work / 'loci-1/independent.tsv').read_text(encoding='utf-8').splitlines()) == 3
        assert (work / 'loci-1/loci.tsv').read_bytes() == (workflow / 'loci/loci.tsv').read_bytes()
        for folder in [captured, work / 'replay-1', work / 'standalone-1', work / 'portable-replay-1', work / 'loci-1', workflow / 'loci']:
            assert 'completed-with-review-warnings' in (folder / 'analysis-status.json').read_text(encoding='utf-8')
            assert 'Required action' in (folder / 'WARNINGS.md').read_text(encoding='utf-8')
        assert pin(jar) == artifact and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        assert all(pin(ROOT / name) == value for name, value in sources.items())
        write(out / 'COMPLETED.json', dict(status='passed-packaged-reference-commands-and-portable-replay', calls=calls, source_pins=sources, artifact=dict(path=jar.relative_to(ROOT).as_posix(), **artifact), canonical_executable_unchanged=canonical, payload_comparisons=24, synthetic_example_only=True, files={p.relative_to(out).as_posix(): pin(p) for p in sorted(out.rglob('*')) if p.is_file()}))
        return 0
    except Exception as failure:
        write(out / 'FAILURE.json', dict(status='FAIL2_REFERENCE_COMMANDS', error=str(failure)))
        raise


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as failure:
        print('FAIL2_REFERENCE_COMMANDS: ' + str(failure) + '\nRequired action: retain failed output, inspect the named command/comparison and rerun to new paths.', file=sys.stderr)
        sys.exit(2)
