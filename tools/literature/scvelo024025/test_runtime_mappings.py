#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Keep the observed POSIX semaphore classification narrow; deleted code never becomes an exception."""
from pathlib import Path
import argparse,json,sys
sys.dont_write_bytecode=True
from runtime_audit import semaphore_data_mapping
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False)
 exact='7fcd37415000-7fcd37416000 rw-s 00000000 00:41 3 /dev/shm/sem.aJUX1b (deleted)';accepted=semaphore_data_mapping(exact,4096)
 assert accepted and accepted['classification']=='unlinked-nonexecutable-shared-POSIX-semaphore-data'
 changes=[('executable',exact.replace('rw-s','rwxp')),('private',exact.replace('rw-s','rw-p')),('readonly',exact.replace('rw-s','r--s')),('library-path',exact.replace('/dev/shm/sem.aJUX1b','/usr/lib/anything.so')),('wrong-basename',exact.replace('sem.aJUX1b','not-a-semaphore')),('long-name',exact.replace('sem.aJUX1b','sem.aJUX1bQ')),('offset',exact.replace('00000000','00001000')),('two-pages',exact.replace('7fcd37416000','7fcd37417000')),('no-inode',exact.replace('00:41 3','00:41 0')),('unaligned',exact.replace('7fcd37415000-7fcd37416000','7fcd37415001-7fcd37416001')),('live-file',exact.replace(' (deleted)',''))]
 for name,line in changes:assert semaphore_data_mapping(line,4096) is None,name
 result=dict(status='PASS_OBSERVED_NONEXECUTABLE_SEMAPHORE_DATA_AND_ELEVEN_UNRECOGNIZED_OR_CODE_MAPPING_REFUSALS',accepted_controls=1,refusal_controls=len(changes),cases=[n for n,_ in changes],whole_runtime_or_analysis_qualified=False,new_tool_credit=0)
 (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True)
if __name__=='__main__':main()
