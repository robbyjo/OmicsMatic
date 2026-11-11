# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check preserved parent evidence, catalog checksums and website structure."""
import argparse
import hashlib
import json
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    catalog = ROOT / 'docs/literature/omics-2023-2026'
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    catalog_pins = {n: pin(catalog / n) for n in ['SHA256SUMS', 'summary.json', 'papers.json', 'tool-inventory.json']}
    commands = [
        ('native-parent', [os.sys.executable, '-X', 'utf8', 'tools/literature/verify_seurat_marker_summary.py']),
        ('deseq2-parent', [os.sys.executable, '-X', 'utf8', 'tools/literature/verify_deseq2_catalog.py']),
        ('website', ['cmd.exe', '/d', '/c', 'gradlew.bat', '--offline', '--no-daemon',
                     '--no-configuration-cache', 'checkWebsite'])]
    try:
        results = []
        env = dict(os.environ, JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot')
        for name, argv in commands:
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                result = subprocess.run(argv, cwd=ROOT, env=env, stdin=subprocess.DEVNULL,
                                        stdout=stdout, stderr=stderr, timeout=300)
            save(out / (name + '.process.json'), dict(command=argv, exit_code=result.returncode))
            assert result.returncode == 0, name + ' failed; inspect retained logs'
            results.append(dict(gate=name, exit_code=0))
        entries = (catalog / 'SHA256SUMS').read_text(encoding='utf8').splitlines()
        for line in entries:
            wanted, name = line.split('  ', 1)
            path = catalog / name
            assert path.resolve().is_relative_to(catalog.resolve()) and path.is_file()
            assert hashlib.sha256(path.read_bytes()).hexdigest() == wanted, name
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        assert all(pin(catalog / n) == value for n, value in catalog_pins.items())
        save(out / 'COMPLETED.json', dict(status='passed-Seurat-parent-catalog-and-website-gates',
                                         gates=results, catalog_checksum_entries=len(entries),
                                         canonical_executable_preserved=canonical, catalog_preserved=catalog_pins,
                                         full_project_test_suite_run=False,
                                         source_pins={Path(__file__).relative_to(ROOT).as_posix(): pin(Path(__file__))}))
        print('Native/DESeq2 parent evidence, catalog checksums and website gate passed')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_PARENT_AND_WEBSITE_GATES', error=str(error)))
        raise


if __name__ == '__main__':
    main()
