# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add only the cisTarget route/help and an exact inverse to the prior dispatcher."""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    name='src/main/java/org/jlinalg/cli/JLinAlgCli.java';path=ROOT/name;before=path.read_bytes();after=before
    anchor=b'        if (arguments.length > 0 && arguments[0].equals("mrmega-tools"))\n'
    addition=b'        if (arguments.length > 0 && arguments[0].equals("cistarget-tools"))\n            return CisTargetWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'
    assert after.count(anchor)==1 and addition not in after;after=after.replace(anchor,addition+anchor)
    anchor=b'              java -jar omicsmatic-<version>.jar mrmega-tools --help\n';assert after.count(anchor)==1
    after=after.replace(anchor,b'              java -jar omicsmatic-<version>.jar cistarget-tools --help\n'+anchor)
    row=inverse(before,after);row['path']=name;assert len(row['edits'])==2 and reverse_delta(after,row)==before
    data=ROOT/'docs/literature/omics-2023-2026';descriptor=data/'cistarget-dispatch-shared-source-compatibility.json';assert not descriptor.exists()
    (out/'JLinAlgCli.before.java').write_bytes(before);(out/'JLinAlgCli.after.java').write_bytes(after)
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cistarget',scope='Exactly one source-preserving early dispatch branch and one help line. Complete previous dispatcher reconstructs; no scientific verifier or source exception.',paths=[row]))
    path.write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    save(out/'COMPLETED.json',dict(status='PASS_TWO_CISTARGET_DISPATCH_ONLY_EDITS_WITH_EXACT_PREDECESSOR_RECONSTRUCTION',source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),path,descriptor]},scientific_sources_changed=False,whole_goal_complete=False));print('Added cistarget-tools route/help; exact prior dispatcher reconstructs')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact dispatcher preservation checks')
    main()
