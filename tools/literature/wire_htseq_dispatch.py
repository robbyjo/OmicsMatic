# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the closed HTSeq request route/help with an exact reversible source delta."""
from setup_deseq2_reference import ROOT,save
from wire_signac_catalog import delta
from catalog_source_integrity import verify_source


def main():
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';path=ROOT/name;before=path.read_bytes()
    marker=b'            ProjectConfiguration.Resolved config = ProjectConfiguration.resolve(arguments,\n'
    help_marker=b'              java -jar omicsmatic-<version>.jar signac-tools --help\n'
    route=b'            if (arguments.length > 0 && arguments[0].equals("htseq-tools"))\n                return HtseqToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    assert before.count(marker)==before.count(help_marker)==1 and route not in before
    after=before.replace(marker,route+marker).replace(help_marker,help_marker+b'              java -jar omicsmatic-<version>.jar htseq-tools --help\n')
    row=delta(name,before,after);record=ROOT/'docs/literature/omics-2023-2026/htseq-dispatch-shared-source-compatibility.json'
    out=ROOT/'build/literature-review/htseq-runtime/dispatch-source-intake-1';assert not record.exists() and not out.exists();out.mkdir()
    (out/'JLinAlgCli.before.java').write_bytes(before)
    save(record,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='htseq',
        scope='Only the fixed htseq-tools request route and its root help line. Exact inverse preserves every prior dispatcher byte; no scientific waiver or catalog credit.',paths=[row]))
    assert path.read_bytes()==before;path.write_bytes(after);verify_source(ROOT,record.parent,name,row['before_sha256'])
    print('Wired closed HTSeq root route/help; retained exact dispatcher inverse')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact dispatch preservation checks')
    main()
