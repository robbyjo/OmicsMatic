# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a self-contained Java17 native kinship API and verify a fresh public BED consumer."""
import argparse, csv, json, math, shutil, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT/'build/literature-review/plink-relatedness-runtime'


def linux(path):
    value = str(path.resolve()).replace('\\', '/')
    assert value[1:3] == ':/'
    return '/mnt/'+value[0].lower()+value[2:]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_NATIVE_KING_PUBLIC_API', new_catalog_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        sources = [ROOT/'src/main/java/org/jlinalg/genetics'/(name+'.java') for name in ['KingRobustKinship', 'PlinkBedDataset']]
        example = ROOT/'examples/native-king/KingBedExample.java'
        pins = {path.relative_to(ROOT).as_posix(): pin(path) for path in [*sources, example, Path(__file__)]}
        for name in pins:
            target = out/'source-snapshot'/name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT/name, target)
        tools = Path('C:/Program Files')/('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes, consumer = out/'classes', out/'public-consumer'
        classes.mkdir(); consumer.mkdir()
        command = [str(tools/'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-d', str(classes), *map(str, sources)]
        compiled = subprocess.run(command, capture_output=True, timeout=120)
        (out/'compile.stdout').write_bytes(compiled.stdout); (out/'compile.stderr').write_bytes(compiled.stderr)
        save(out/'compile.json', dict(command=command, exit_code=compiled.returncode))
        assert compiled.returncode == 0, compiled.stderr[-1800:]
        jar = out/'native-king-api.jar'
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            payload = {path.relative_to(classes).as_posix(): path.read_bytes() for path in classes.rglob('*.class')}
            payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\n\n'
            payload['META-INF/LICENSE.txt'] = (ROOT/'LICENSE').read_bytes()
            payload['META-INF/NOTICE.txt'] = b'Native between-family KING-robust arithmetic and unchanged existing PLINK BED reader. GPL-3.0-or-later. Original PLINK and KING executables are not bundled. This is an API, not a standalone KING/PLINK workflow or biological relationship certificate.\n'
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type, item.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                z.writestr(item, raw)
        command = [str(tools/'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-encoding', 'UTF-8',
            '-cp', str(jar), '-d', str(consumer), str(example)]
        compiled = subprocess.run(command, capture_output=True, timeout=120)
        (out/'consumer-compile.stdout').write_bytes(compiled.stdout); (out/'consumer-compile.stderr').write_bytes(compiled.stderr)
        save(out/'consumer-compile.json', dict(command=command, exit_code=compiled.returncode))
        assert compiled.returncode == 0, compiled.stderr[-1800:]
        reference = BASE/'reference-controls-3'
        prefix = reference/'inputs/variants-129/cohort'
        variants = out/'selected-variants.txt'
        variants.write_text(''.join('v'+str(i)+'\n' for i in range(129)), encoding='utf8')
        invocations = []
        for platform in ['windows', 'linux']:
            convert = linux if platform == 'linux' else lambda path: str(path.resolve())
            launcher = ['wsl.exe', '-d', 'JLinAlg-Omics-SAIGE-20261026', '--exec', '/usr/bin/java'] if platform == 'linux' else [str(tools/'java.exe')]
            target = out/(platform+'-public-result.tsv')
            command = [*launcher, '-cp', (':' if platform == 'linux' else ';').join(convert(path) for path in [consumer, jar]),
                'KingBedExample', convert(prefix), convert(variants), convert(target)]
            run = subprocess.run(command, capture_output=True, timeout=120)
            (out/(platform+'.stdout')).write_bytes(run.stdout); (out/(platform+'.stderr')).write_bytes(run.stderr)
            save(out/(platform+'.process.json'), dict(command=command, exit_code=run.returncode))
            assert run.returncode == 0, run.stderr[-1800:]
            with target.open(encoding='utf8', newline='') as stream:
                rows = list(csv.DictReader(stream, delimiter='\t'))
            assert len(rows) == 1
            row = rows[0]
            assert (row['FID1'], row['IID1'], row['FID2'], row['IID2']) == ('family', 's0', 'family', 's1')
            assert [int(row[n]) for n in ['NSNP', 'HETHET', 'IBS0', 'HET1_HOM2', 'HET2_HOM1']] == [128, 55, 0, 0, 0]
            assert float(row['KINSHIP']) == 0.5 and row['DISPOSITION'] == 'FINITE'
            assert b'NaN/-Infinity are unreportable' in run.stderr
            invocations.append(dict(platform=platform, standalone_public_consumer=True, original_counts_match=True))
        assert all(pin(ROOT/name) == value for name, value in pins.items()) and pin(canonical) == before
        state.update(status='PASS_STANDALONE_NATIVE_KING_API_AND_FRESH_PUBLIC_CONSUMERS', compiler=a.compiler,
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), source_pins=pins, invocations=invocations,
            canonical_executable_unchanged=before, source_dependency='Unchanged existing PlinkBedDataset.java bundled for the public example',
            scope='Native Java component and explicit first-pair/selected-autosome BED example. No whole-tool, standalone KING, PI_HAT, segment, sample-selection or historical paper claim.')
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:1800], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable public API/original-count/preservation checks')
    raise SystemExit(main())
