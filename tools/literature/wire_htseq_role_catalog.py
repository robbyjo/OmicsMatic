# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Update only the recovered HTSeq observation assertion, retaining an exact shared-source inverse."""
import json
from pathlib import Path
from wire_signac_catalog import delta

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'


def main():
    roles=json.loads((DATA/'htseq-role-coverage.json').read_bytes())
    assert roles['applied'] and roles['total_observations']==2446 and roles['total_reviews']==898
    path=ROOT/'tools/literature/check_review_ui.cjs';before=path.read_bytes()
    assert before.count(b'/2445 tool observations/')==1
    after=before.replace(b'/2445 tool observations/',b'/2446 tool observations/')
    record=DATA/'htseq-role-ui-shared-source-compatibility.json';assert not record.exists()
    summary_path=DATA/'summary.json';summary=json.loads(summary_path.read_bytes())
    assert summary['paper_tool_observations']==2445
    record.write_text(json.dumps(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='htseq',
        scope='One recovered P005 HT-seq observation changes the browser overview assertion from2445 to2446. Exact inverse; no scientific waiver or tool credit.',
        paths=[delta('tools/literature/check_review_ui.cjs',before,after)]),indent=2)+'\n',encoding='utf8',newline='\n')
    assert path.read_bytes()==before
    path.write_bytes(after)
    # Existing metadata providers validate this derived field before rendering.
    # The renderer regenerates the complete summary after this sole bootstrap.
    summary['paper_tool_observations']=2446
    summary_path.write_text(json.dumps(summary,indent=2)+'\n',encoding='utf8',newline='\n')
    print('Wired HTSeq role-only observation count; no implementation credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source-delta checks')
    main()
