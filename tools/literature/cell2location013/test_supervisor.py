#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the monitor's temporary-directory race without changing scientific files."""
from pathlib import Path
import argparse,importlib.util,json,os,tempfile
from unittest.mock import patch

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 file=Path(__file__).with_name('run.py');spec=importlib.util.spec_from_file_location('cell2location013_original_monitor',file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 cases=[]
 with tempfile.TemporaryDirectory(prefix='cell2location013-') as value:
  root=Path(value);(root/'child').mkdir();(root/'surviving.txt').write_bytes(b'exact persistent input')
  scan=os.scandir
  def disappear(folder):
   if Path(folder)==root/'child':(root/'child').rmdir()
   return scan(folder)
  with patch.object(m.os,'scandir',disappear):actual=m.tree(root,100,100000,100000,True)
  assert set(actual)=={'surviving.txt'} and actual['surviving.txt']==m.pin(root/'surviving.txt');cases.append('disappearing-temporary-child-preserves-all-persistent-files')
  missing=root/'absent-root'
  try:m.tree(missing,100,100000,100000);raise AssertionError('Missing root accepted')
  except FileNotFoundError:cases.append('missing-owned-root-refused')
  (root/'oversize.bin').write_bytes(b'123456')
  try:m.tree(root,100,100000,5);raise AssertionError('Complete byte capacity bypassed')
  except ValueError:cases.append('persistent-file-capacity-still-refused')
  if os.name!='nt':
   (root/'forbidden-link').symlink_to(root/'surviving.txt')
   try:m.tree(root,100,100000,100000);raise AssertionError('Link accepted')
   except ValueError:cases.append('symlinks-still-refused')
 result=dict(status='PASS_CELL2LOCATION013_MONITOR_RACE_PERSISTENT_FILE_IDENTITY_AND_REFUSALS',controls=len(cases),cases=cases,new_tool_credit=0,whole_goal_complete=False)
 (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True)
if __name__=='__main__':main()
