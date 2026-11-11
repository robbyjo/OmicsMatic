# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wire the typed GENESIS command and retain an exact inverse of root dispatcher metadata only."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    name = 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'; source = ROOT / name; before = source.read_bytes()
    assert b'arguments[0].equals("genesis-tools")' not in before
    anchor = b'        if (arguments.length > 0 && arguments[0].equals("snprelate-tools"))\n'
    assert before.count(anchor) == 1
    after = before.replace(anchor, b'        if (arguments.length > 0 && arguments[0].equals("genesis-tools"))\n            return GenesisWorkflowToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n' + anchor)
    anchor = b'              java -jar omicsmatic-<version>.jar snprelate-tools --help\n'
    assert after.count(anchor) == 1; after = after.replace(anchor, b'              java -jar omicsmatic-<version>.jar genesis-tools --help\n' + anchor)
    row = inverse(before, after); row['path'] = name; assert reverse_delta(after, row) == before and len(row['edits']) == 2
    data = ROOT / 'docs/literature/omics-2023-2026'; target = data / 'genesis-dispatch-shared-source-compatibility.json'; assert not target.exists()
    save(target, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='genesis',
                     scope='One typed root route and one help line. No scientific source or execution policy change.', paths=[row]))
    (out / 'dispatcher.before.java').write_bytes(before); (out / 'dispatcher.after.java').write_bytes(after)
    assert source.read_bytes() == before; source.write_bytes(after); verify_source(ROOT, data, name, row['before_sha256'])
    save(out / 'COMPLETED.json', dict(status='PASS_EXACT_GENESIS_ROOT_DISPATCH_METADATA_INVERSE', dispatcher=pin(source), descriptor=pin(target), producer=pin(Path(__file__)), new_catalog_credit=False, whole_goal_complete=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact dispatcher-only source checks')
    main()
