# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exact historical dispatcher view for an independently reviewed two-line route.

No scientific source, primary evidence, existing dispatcher route, or saved hash
is waived. The live dispatcher must match one of the two exact reviewed states.
"""
from pathlib import Path
import hashlib,json,sys
TOOLS=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(TOOLS))
from catalog_source_integrity import reverse_delta,verify_source
from run_mrmega_catalog_checks import ArchivedCatalogView

PATH='src/main/java/org/jlinalg/cli/JLinAlgCli.java'
BEFORE='bcc8bee3c2335d3971db0e88189c1d1879f4d3d6ac0eb65c205e6fcb8b54831f'
AFTER='cb14367c079da29ce25c4416f37dc9c5f06b690033eda1a34bfa3349864ae47a'
DESCRIPTOR='docs/literature/omics-2023-2026/gseapy-native-dispatch-shared-source-compatibility.json'
INSERTION=(b'            if (arguments.length > 0 && arguments[0].equals("gseapy-native"))\n'
 b'                return GseapyNativeCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def reviewed_root(root):
 document=json.loads((root/DESCRIPTOR).read_bytes())
 if document.get('schema_version')!=1 or document.get('status')!='passed-dispatch-catalog-only-source-deltas' or len(document.get('paths',[]))!=1:raise ValueError('Unreviewed native dispatcher transition')
 row=document['paths'][0]
 if (row['path'],row['before_sha256'],row['after_sha256'],row['before_bytes'],row['after_bytes'])!=(PATH,BEFORE,AFTER,49816,50011) or len(row['edits'])!=1:raise ValueError('Dispatcher transition differs from the exact two reviewed states')
 raw=(root/PATH).read_bytes();signature=pin(raw)
 if signature['sha256']==AFTER:
  edit=row['edits'][0];lines=raw.splitlines(keepends=True);chunk=b''.join(lines[edit['after_start_line']:edit['after_end_line']])
  if chunk!=INSERTION or edit['before_bytes']!=0:raise ValueError('Transition is not the reviewed gseapy-native insertion')
  restored=reverse_delta(raw,row)
 elif signature['sha256']==BEFORE:restored=raw
 else:raise ValueError('Live dispatcher is outside both exact reviewed states; review its changes before auditing')
 if pin(restored)!=dict(bytes=49816,sha256=BEFORE):raise ValueError('Exact reviewed scDRS dispatcher did not reconstruct')
 verify_source(root,root/'docs/literature/omics-2023-2026',PATH,BEFORE)
 return ArchivedCatalogView(root,{PATH:restored}),dict(path=PATH,current=signature,reviewed_predecessor=pin(restored),descriptor=pin((root/DESCRIPTOR).read_bytes()),scientific_sources_overridden=False,primary_sources_overridden=False)
