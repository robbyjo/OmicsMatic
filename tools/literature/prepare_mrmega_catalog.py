# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record exact dispatcher/renderer deltas; retain all scientific source bytes."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import ALLOWED_PATHS,reverse_delta


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--apply',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';summary=json.loads((data/'summary.json').read_bytes());counts=summary['implementation_status_counts']
    assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,74,250)
    assert len(json.loads((ROOT/'tools/literature/reviewed-uses.json').read_bytes())['reviews'])==1009
    assert ALLOWED_PATHS==frozenset({'.gitattributes','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/render_review.py','tools/literature/reviewed-uses.json','tools/literature/repeat_catalog.py','tools/literature/check_review_ui.cjs','tools/literature/check_repeat_review_ui.cjs'})
    names=['tools/literature/render_review.py','src/main/java/org/jlinalg/cli/JLinAlgCli.java','tools/literature/reviewed-uses.json','tools/literature/check_review_ui.cjs',
        'site/literature-review.html','site/literature-warnings.html',*['docs/literature/omics-2023-2026/'+n for n in ['papers.json','tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS','catalog.md','implementation-plan.md']]]
    before={n:(ROOT/n).read_bytes() for n in names}
    for n,raw in before.items():target=out/'before'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    renderer='tools/literature/render_review.py';text=before[renderer].decode();assert text.count('from sniffles_catalog import')==9
    text=text.replace('from sniffles_catalog import','from mrmega_catalog import')
    needle='from pathlib import Path\n';assert text.count(needle)==1;text=text.replace(needle,needle+'from mrmega_catalog import mrmega_metadata, MRMEGA_WARNING, MRMEGA_ACTIONS\n')
    needle='        elif tool_id == "sniffles":';assert text.count(needle)==1;text=text.replace(needle,'        elif tool_id == "mrmega":\n            tool.update(mrmega_metadata(ROOT,DATA))\n'+needle)
    needle='if tool_id not in {"rsubread",';assert text.count(needle)==1;text=text.replace(needle,'if tool_id not in {"mrmega", "rsubread",')
    for needle,replacement in [('write_text(SNIFFLES_WARNING +','write_text(MRMEGA_WARNING + SNIFFLES_WARNING +'),('warning_body = SNIFFLES_ACTIONS +','warning_body = MRMEGA_ACTIONS + SNIFFLES_ACTIONS +')]:assert text.count(needle)==1;text=text.replace(needle,replacement)
    candidate={renderer:text.encode()};compile(text,renderer,'exec')
    main='src/main/java/org/jlinalg/cli/JLinAlgCli.java';text=before[main].decode()
    needle='        if (arguments.length > 0 && arguments[0].equals("magma-tools"))';assert text.count(needle)==1
    text=text.replace(needle,'        if (arguments.length > 0 && arguments[0].equals("mrmega-tools"))\n            return MrMegaWorkflowCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n'+needle)
    needle='              java -jar omicsmatic-<version>.jar magma-tools --help';assert text.count(needle)==1;text=text.replace(needle,'              java -jar omicsmatic-<version>.jar mrmega-tools --help\n'+needle);candidate[main]=text.encode()
    rows=[]
    for n,raw in candidate.items():
        row=inverse(before[n],raw);row['path']=n;assert reverse_delta(raw,row)==before[n];rows.append(row)
        target=out/'candidate'/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    descriptor=dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='mrmega',scope='Add one native MR-MEGA command, one metadata provider and prominent warnings; preserve scientific methods and seven-path allowlist.',paths=rows)
    save(out/'mrmega-shared-source-compatibility.json',descriptor)
    if a.apply:
        from mrmega_workflow_evidence import verify
        proof,_=verify(False);assert proof['standalone_native_file_workflow_available'] and all((ROOT/n).read_bytes()==raw for n,raw in before.items())
        path=data/'mrmega-shared-source-compatibility.json';assert not path.exists();path.write_bytes((out/path.name).read_bytes())
        for n,raw in candidate.items():(ROOT/n).write_bytes(raw)
    save(out/'COMPLETED.json',dict(status='passed-prepared-MRMEGA-dispatch-only-deltas',applied=a.apply,input_pins={n:pin(out/'before'/n) for n in before},
        candidate_pins={n:pin(out/'candidate'/n) for n in candidate},allowlist_unchanged=True,scientific_sources_changed=False,whole_goal_complete=False))
    print('Prepared exact renderer/root dispatcher deltas; applied='+str(a.apply))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source-delta checks')
    main()
