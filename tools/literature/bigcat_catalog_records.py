#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check the complete saved catalog validation records; repeat no scientific fit."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/scrattchbigcat005_workflows'))
from Evidence import extract
from Runtime import pin,save

DATA=ROOT/'docs/literature/bigcat-catalog-validation'
ACTION=('Use a NEW direct child of build/literature-review/monocle-runtime. '
 'SKIP3 skips dependent verification for unavailable/forbidden requirements. '
 'FAIL2 admits no result; retain partial files and correct the named cause into NEW paths. '
 'This checks record integrity and qualified source bytes, not a new scientific computation. '
 'Use bigcat_catalog/catalog.py in the documented isolated worktree for cumulative semantic verification. '
 'The full 250-paper objective remains open.')

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();owned=False
    out=args.out.absolute()
    try:
        if out!=out.resolve() or out.parent!=ROOT/'build/literature-review/monocle-runtime':
            raise ValueError('Use a canonical NEW direct child of monocle-runtime, separate from providers and source trees')
        out.parent.mkdir(parents=True,exist_ok=True)
        out.mkdir(exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf-8')
        proof=json.loads((DATA/'VALIDATION.json').read_bytes())
        if proof['status']!='PASS_ISOLATED_BIGCAT_CATALOG_ALL_PREDECESSORS_PRIMARY_COUNTS_AND_BROWSER':raise ValueError('Unsupported validation checkpoint')
        for name,identity in proof['source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Qualified source/evidence changed: '+name)
        index=extract(DATA/'complete-validation-records.zip',out/'records',proof)
        for name,identity in proof['catalog_after_files'].items():
            if pin(out/'records/catalog-after'/name)!=identity:raise ValueError('Archived complete catalog snapshot differs: '+name)
        result=dict(status='PASS_BIGCAT_CATALOG_VALIDATION_RECORD_INTEGRITY_NOT_NEW_SCIENTIFIC_COMPUTATION',
          logical_files=len(index['files']),unique_payloads=len(index['blobs']),
          qualified_source_files=len(proof['source_files']),all_archive_members_checked=True,
          metadata_snapshot_is_current_catalog_claimed=False,scientific_fits_repeated=False,whole_goal_complete=False)
        save(out/'COMPLETED.json',result)
        print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_BIGCAT_CATALOG_RECORDS_UNAVAILABLE' if code==3 else 'FAIL2_BIGCAT_CATALOG_RECORDS_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                if (out/'COMPLETED.json').exists():
                    (out/'COMPLETED.json').rename(out/'COMPLETED-UNADMITTED.json')
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text(label+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:print('FAIL2_RECORDS_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
