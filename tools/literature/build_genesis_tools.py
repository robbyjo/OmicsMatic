# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an isolated Java17 GENESIS development adapter; preserve the canonical released executable."""
import argparse
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE = ROOT / 'build/literature-review/genesis-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)
    state = dict(status='FAIL2_ISOLATED_GENESIS_ADAPTER_BUILD', new_catalog_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'; before = pin(canonical)
        sources = [ROOT / 'src/main/java/org/jlinalg/cli' / (n + '.java') for n in [
            'NextflowParameters', 'NextflowJson', 'NextflowFiles', 'SimpleJson', 'SnprelateRequest', 'SnprelateAudit',
            'SnprelatePcaAudit', 'GenesisRequest', 'GenesisInputs', 'GenesisAudit', 'GenesisToolsCli']]
        sources += [ROOT / 'src/main/java/org/jlinalg/genetics' / (n + '.java') for n in [
            'KingRobustKinship', 'SnprelateKingRobust', 'SnprelateVariantQc', 'GenesisPcairPartition']]
        resources = [ROOT / 'src/main/resources/org/jlinalg/omics/genesis' / n for n in ['run_genesis.R', 'prepare_genesis_runtime.py']]
        pins = {f.relative_to(ROOT).as_posix(): pin(f) for f in [*sources, *resources, Path(__file__)]}
        for name in pins:
            target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes = out / 'classes'; classes.mkdir()
        command = [str(tools / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-d', str(classes), *map(str, sources)]
        run = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
        (out / 'compile.stdout').write_bytes(run.stdout); (out / 'compile.stderr').write_bytes(run.stderr)
        save(out / 'compile.json', dict(command=command, exit_code=run.returncode)); assert run.returncode == 0, run.stderr[-5000:]
        payload = {f.relative_to(classes).as_posix(): f.read_bytes() for f in classes.rglob('*.class')}
        payload.update({f.relative_to(ROOT / 'src/main/resources').as_posix(): f.read_bytes() for f in resources})
        payload['META-INF/LICENSE'] = (ROOT / 'LICENSE').read_bytes()
        payload['META-INF/NOTICE'] = b'Project-owned typed GENESIS development adapter; scientific behavior attributed to GENESIS authors GPL3, SNPRelate authors GPL3 and R Core GPL2+. No upstream runtime bundled. Native audit contracts are explicitly bounded and do not independently refit all estimators. No catalog or whole-goal completion claim.\n'
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.GenesisToolsCli\n\n'
        jar = out / 'genesis-tools-development.jar'
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16
                archive.writestr(item, raw)
        assert pin(canonical) == before and all(pin(ROOT / name) == value for name, value in pins.items())
        state.update(status='PASS_ISOLATED_GENESIS_DEVELOPMENT_ADAPTER_BUILD_ONLY', compiler=a.compiler, source_pins=pins,
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), canonical_executable_unchanged=before)
    except Exception as error:
        state['error'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('error', '')[:5000], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source and canonical artifact preservation checks')
    raise SystemExit(main())
