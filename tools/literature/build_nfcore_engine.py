# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the separate nf-core primary-config foundation; no catalog, live-root or canonical JAR changes."""
import argparse
import json
import shutil
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

BASE = ROOT / 'build/literature-review/nfcore-runtime'


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    state = dict(status='FAIL2_NFCORE_ENGINE_FOUNDATION_BUILD', new_catalog_credit=False, full_adapter_validated=False)
    try:
        assert before['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
        home = ROOT / 'docs/literature/scdrs-workflow-development'
        predecessor = home / 'scdrs-root-javac21.jar'
        assert pin(predecessor) == json.loads((home / 'VALIDATION.json').read_bytes())['artifacts'][predecessor.name]
        engine_proof = json.loads((ROOT / 'docs/literature/nextflow-typed-workflow/VALIDATION.json').read_bytes())
        dependency_home = ROOT / 'docs/literature/nfcore-engine-development'
        engine = dependency_home / 'nextflow-engine-dependency.jar'
        dependency = json.loads((dependency_home / 'nextflow-engine-dependency.json').read_bytes())
        assert pin(engine) == dependency['artifact'] == dict(bytes=204483,
            sha256='8071d13f809fec93d57d3c9e4804c1499dae885fa06a162291f99c1a1049ea4e')
        assert dependency['original_candidate'] == engine_proof['artifact']
        sources = [*sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('NfcoreEngine*.java')),
            ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreInputBindings.java']
        resources = sorted(p for p in (ROOT / 'src/main/resources/org/jlinalg/omics/nfcore').iterdir() if p.is_file())
        tracked = [*sources, *resources, Path(__file__)]
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in tracked}
        for name in pins:
            target = out / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes = out / 'classes'
        classes.mkdir()
        command = [str(tools / 'javac.exe'), '--release', '17', '-sourcepath', '', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
            '-cp', ';'.join(map(str, [engine, predecessor])), '-d', str(classes), *map(str, sources)]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (out / 'javac.stdout').write_bytes(result.stdout)
        (out / 'javac.stderr').write_bytes(result.stderr)
        save(out / 'javac.process.json', dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, result.stderr[-4000:]
        with zipfile.ZipFile(predecessor) as z: old = {n: z.read(n) for n in z.namelist()}
        payload = dict(old)
        inherited = {}
        with zipfile.ZipFile(engine) as z:
            for name in z.namelist():
                if name.endswith('/') or not name.startswith(('org/jlinalg/cli/Nextflow', 'org/jlinalg/workflow/Nextflow', 'org/jlinalg/cli/nextflow-setup/')): continue
                raw = z.read(name)
                if name in payload: assert payload[name] == raw, ('Conflicting existing helper; never replace predecessor bytes', name)
                payload[name] = raw
                inherited[name] = dict(bytes=len(raw), sha256=__import__('hashlib').sha256(raw).hexdigest())
        assert inherited == dependency['inherited_entries'] and len(inherited) == 67
        payload.update({p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')})
        for path in resources:
            if path.name != '.gitattributes': payload['org/jlinalg/omics/nfcore/' + path.name] = path.read_bytes()
        payload['META-INF/nfcore/NOTICE'] = b'Separate nf-core config-binding engine foundation only. No tool-level catalog admission, whole-package or paper-reproduction claim. Existing Nextflow scientific sources/classes remain unchanged. Original pipeline sources are locked separately with their MIT notices.\n'
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreEngineToolsCli\n\n'
        artifact = out / 'nfcore-engine-development.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                item.compress_type = zipfile.ZIP_DEFLATED
                item.external_attr = 0o100644 << 16
                z.writestr(item, raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(n) == raw for n, raw in old.items() if n != 'META-INF/MANIFEST.MF')
        assert all(pin(ROOT / n) == wanted for n, wanted in pins.items())
        for name in ['NextflowWorkflow.java', 'NextflowBundle.java', 'NextflowToolsCli.java']:
            path = 'src/main/java/org/jlinalg/cli/' + name
            assert pin(ROOT / path) == engine_proof['source_pins'][path]
        state.update(status='PASS_NFCORE_PRIMARY_CONFIG_ENGINE_FOUNDATION_BUILD_ONLY', compiler=a.compiler,
            source_pins=pins, artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)),
            predecessor=pin(predecessor), every_predecessor_scientific_class_and_resource_byte_identical=True,
            exact_nextflow_candidate=engine_proof['artifact'], exact_helper_dependency=pin(engine),
            exact_inherited_nextflow_entries=inherited, existing_nextflow_sources_modified=False)
    except Exception as error:
        import traceback
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable original scientific-source, inherited-byte or canonical preservation checks')
    raise SystemExit(main())
