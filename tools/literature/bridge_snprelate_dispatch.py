# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add exactly the SNPRelate route/help and preserve the preceding dispatcher through a byte-exact inverse."""
import argparse
import json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';path=ROOT/name;before=path.read_bytes()
    proof=json.loads((ROOT/'docs/literature/diamond-development/VALIDATION.json').read_bytes());assert pin(path)==proof['source_pins'][name]
    anchor=b'        if (arguments.length > 0 && arguments[0].equals("cellchat-tools"))\n'
    addition=b'        if (arguments.length > 0 && arguments[0].equals("snprelate-tools"))\n            return SnprelateToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    assert before.count(anchor)==1 and addition not in before;after=before.replace(anchor,addition+anchor)
    anchor=b'              java -jar omicsmatic-<version>.jar cellchat-tools --help\n';assert after.count(anchor)==1
    after=after.replace(anchor,b'              java -jar omicsmatic-<version>.jar snprelate-tools --help\n'+anchor)
    row=inverse(before,after);row['path']=name;assert len(row['edits'])==2 and reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'snprelate-dispatch-shared-source-compatibility.json';assert not descriptor.exists()
    (out/'JLinAlgCli.before.java').write_bytes(before);(out/'JLinAlgCli.after.java').write_bytes(after)
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snprelate',
        scope='One SNPRelate dispatch branch and one help line only. Every preceding route is unchanged; the exact previous dispatcher reconstructs. No scientific-source exception.',paths=[row]))
    path.write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    save(out/'COMPLETED.json',dict(status='PASS_TWO_SNPRELATE_DISPATCH_EDITS_AND_EXACT_PREDECESSOR_INVERSE',
        source_pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__),path,descriptor]},scientific_sources_changed=False,new_catalog_credit=False,whole_goal_complete=False))
    print('Added snprelate-tools route/help; exact preceding dispatcher preserved')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact dispatcher inverse checks')
    main()
