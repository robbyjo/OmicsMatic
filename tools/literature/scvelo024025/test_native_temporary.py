#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Test real Linux IPC, combined capacities and complete native-temporary retention beside mounted outputs."""
from pathlib import Path
import argparse,importlib.util,json,socket,sys,tempfile
sys.dont_write_bytecode=True
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False)
 file=Path(__file__).with_name('run.py');spec=importlib.util.spec_from_file_location('scvelo_native_temporary_monitor',file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 native=Path(tempfile.mkdtemp(prefix='omicsmatic-ipc-control-',dir='/tmp')).resolve();manager=native/'pymp-abcdefgh';manager.mkdir();node=manager/'listener-12345678';sock=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM);cases=[]
 try:
  sock.bind(str(node));sock.listen(1);cases.append('real-native-Linux-manager-socket')
  try:m.tree(native,100,100000,100000);raise AssertionError('Default socket refusal bypassed')
  except ValueError:cases.append('default-special-node-refusal')
  files=m.tree(native,100,100000,100000,True,allow_owned_ipc=True,ipc_prefix='');assert files['pymp-abcdefgh/listener-12345678']['kind']=='owned-temporary-manager-socket';cases.append('only-declared-manager-socket-metadata-accepted')
  unexpected=native/'unexpected.sock';other=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
  try:
   other.bind(str(unexpected))
   try:m.tree(native,100,100000,100000,True,allow_owned_ipc=True,ipc_prefix='');raise AssertionError('Arbitrary native socket admitted')
   except ValueError:cases.append('unrecognized-native-socket-refused')
  finally:other.close();unexpected.unlink()
  workspace=out/'workspace';workspace.mkdir();(workspace/'ordinary.txt').write_bytes(b'1234567');(native/'temporary.bin').write_bytes(b'abcdefg')
  limits=dict(maximum_files=4,maximum_workspace_bytes=14,maximum_file_bytes=7)
  actual=m.workspace(workspace,limits,native,True);assert actual['statistics']==dict(entries=4,bytes=14);cases.append('both-roots-share-exact-entry-and-byte-population')
  for label,key,value in [('combined-bytes','maximum_workspace_bytes',13),('combined-entries','maximum_files',3),('individual-file','maximum_file_bytes',6)]:
   limited=dict(limits);limited[key]=value
   try:m.workspace(workspace,limited,native,True);raise AssertionError(label+' capacity bypassed')
   except ValueError:cases.append(label+'-capacity-refused')
  retained=m.retain_native_temporary(native,workspace)
  assert (workspace/'native-temporary/temporary.bin').read_bytes()==(native/'temporary.bin').read_bytes()==b'abcdefg'
  assert retained['files']['pymp-abcdefgh/listener-12345678']['kind']=='socket-metadata-only' and not (workspace/'native-temporary/pymp-abcdefgh/listener-12345678').exists();cases.append('all-regular-bytes-and-native-IPC-identity-retained-without-following-special-nodes')
  assert native.is_dir() and (native/'temporary.bin').is_file();cases.append('original-native-root-and-bytes-remain-retained')
  result=dict(status='PASS_NATIVE_LINUX_IPC_COMBINED_CAPACITIES_AND_COMPLETE_RETAINED_TEMPORARY_BYTES',controls=len(cases),cases=cases,native_root=str(native),retained_inventory=retained,original_native_root_retained=True,scientific_analysis_executed=False,new_tool_credit=0)
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True)
 finally:
  sock.close()
  if node.exists():node.unlink()
if __name__=='__main__':main()
