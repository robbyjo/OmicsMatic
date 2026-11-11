# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire the typed MEME command with an exact route/help inverse; preserve all prior source checks."""
import hashlib
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, save
from wire_signac_catalog import delta
from catalog_source_integrity import verify_source


def main():
    relative = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'; path = ROOT / relative; before = path.read_bytes()
    marker = b'            ProjectConfiguration.Resolved config = ProjectConfiguration.resolve(arguments,\n'
    help_marker = b'              java -jar omicsmatic-<version>.jar htseq-tools --help\n'
    route = b'            if (arguments.length > 0 && arguments[0].equals("meme-tools"))\n                return MemeToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    assert before.count(marker) == before.count(help_marker) == 1 and route not in before
    after = before.replace(marker, route + marker).replace(help_marker, help_marker + b'              java -jar omicsmatic-<version>.jar meme-tools --help\n')
    row = delta(relative, before, after)
    data = ROOT / 'docs/literature/omics-2023-2026'; record = data / 'meme-discovery-dispatch-shared-source-compatibility.json'
    out = ROOT / 'build/literature-review/meme-runtime/dispatch-source-intake-1'; assert not record.exists() and not out.exists(); out.mkdir()
    (out / 'JLinAlgCli.before.java').write_bytes(before); (out / 'SHA256SUMS.before').write_bytes((data / 'SHA256SUMS').read_bytes())
    save(record, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='meme',
        scope='Only the fixed meme-tools route and one root help line. Every previous source byte reconstructs exactly. Scientific code, literature roles, release status and catalog implementation credit are unchanged.', paths=[row]))
    assert path.read_bytes() == before; path.write_bytes(after); verify_source(ROOT, data, relative, row['before_sha256'])
    names = [line.split('  ', 1)[1] for line in (data / 'SHA256SUMS').read_text(encoding='utf8').splitlines()]
    actual = {p.name for p in data.iterdir() if p.is_file() and p.name != 'SHA256SUMS'}
    assert len(names) == len(set(names)) and set(names) <= actual
    names += sorted(actual - set(names))
    (data / 'SHA256SUMS').write_text(''.join(hashlib.sha256((data / name).read_bytes()).hexdigest() + '  ' + name + '\n' for name in names), encoding='utf8', newline='\n')
    save(out / 'COMPLETED.json', dict(status='passed-exact-MEME-route-help-source-inverse', before=row['before_sha256'], after=row['after_sha256'], catalog_checksums=len(names), implementation_credit=False))
    print('Added typed MEME root route/help with exact previous-source reconstruction; no catalog credit')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable exact dispatcher/source preservation')
    main()
