#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Wrap the new warning panel on narrow screens; replay only exact historical presentation metadata."""
from pathlib import Path
import argparse,json,sys,traceback,zipfile
sys.dont_write_bytecode=True;sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from velociraptor_catalog.model import ROOT,DATA,PROOF,GUIDE,encoded,pin,rawpin
from run_mrmega_catalog_checks import ArchivedCatalogView
EVIDENCE='docs/literature/velociraptor-catalog-ui-revision2'
RECORD=EVIDENCE+'/VALIDATION.json'
SELF='tools/literature/velociraptor_catalog/ui_revision2.py'
NEW_GUIDE='docs/literature/VELOCIRAPTOR_CATALOG_LAYOUT_REVISION2.md'
PATHS=('site/literature-review.html','site/literature-warnings.html',DATA+'/README.md',GUIDE)
STYLE='\n<style id="velociraptor-warning-wrap">#velociraptor-required-actions { overflow-wrap: anywhere; min-width: 0; }</style>\n'
README_BANNER='Current presentation verification: [velociraptor warning layout revision2](../VELOCIRAPTOR_CATALOG_LAYOUT_REVISION2.md). It preserves all scientific evidence, counts and source readings. Use `python tools/literature/velociraptor_catalog/ui_revision2.py --verify --out build/literature-review/monocle-runtime/NEW-wrapper-layout-check --rescan-sources` in the matching qualified checkout.\n\n'
GUIDE_BANNER='Current verification: [warning layout revision2](VELOCIRAPTOR_CATALOG_LAYOUT_REVISION2.md). Scientific commands and counts below are unchanged. The catalog-only verification command below describes the historical presentation checkpoint; use the current verification link for the complete current catalog.\n\n'
ACTION='This update changes only warning wrapping and documentation routing. Preserve all previous metadata, scientific sources, verifiers, results and hashes. No family/paper/review credit is added. Missing or mismatched exact runtime/evidence prerequisites skip dependent analysis or fail verification; retain the named error and retry NEW paths. A concurrent development CLI build must not replace the historically qualified artifact silently. Use the unchanged qualified CLI artifact in a separate NEW validation checkout, or independently requalify the changed artifact; do not overwrite another active workspace build. The original R native stack diagnostic and the full250-paper goal remain unresolved/open.'

def transition(before):
 if set(before)!=set(PATHS):raise ValueError('Only exact two HTML pages and two documentation metadata files may change')
 after={}
 for name,raw in before.items():
  text=raw.decode('utf-8')
  if name.endswith('.html'):
   if text.count('</head>')!=1 or 'id="velociraptor-warning-wrap"' in text:raise ValueError('Expected unchanged prior HTML head')
   after[name]=text.replace('</head>',STYLE+'</head>',1).encode('utf-8')
  else:after[name]=(README_BANNER if name.endswith('/README.md') else GUIDE_BANNER).encode('utf-8')+raw
 return after

def historical_view(root,before):
 if set(before)!=set(PATHS):raise ValueError('Scientific source or result overlays forbidden; only four presentation metadata paths allowed')
 return ArchivedCatalogView(root,before)

def apply(out):
 if any((ROOT/n).exists() for n in [EVIDENCE]):raise ValueError('Keep previous layout evidence; choose a new revision instead of overwriting it')
 old=json.loads((ROOT/PROOF).read_bytes());before={name:(ROOT/name).read_bytes() for name in PATHS}
 for name,raw in before.items():
  wanted=old['source_pins'][name] if name==GUIDE else old['after_files'][name]
  if rawpin(raw)!=wanted:raise ValueError('Concurrent metadata change; preserve it: '+name)
 after=transition(before);evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False);(evidence/'.gitattributes').write_bytes(b'* -text\n')
 with zipfile.ZipFile(evidence/'before-presentation.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
  for name,raw in before.items():
   info=zipfile.ZipInfo(name,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
 for name,raw in before.items():
  if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent metadata change; no overwrite permitted: '+name)
 for name,raw in after.items():(ROOT/name).write_bytes(raw)
 proof=dict(status='APPLIED_WARNING_LAYOUT_REVISION2_PENDING_COMPLETE_VERIFICATION',before_archive=pin(evidence/'before-presentation.zip'),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in PATHS},source_pins={n:pin(ROOT/n) for n in [SELF,NEW_GUIDE,'tools/literature/velociraptor_catalog/check_ui.cjs']},preserved_phase1_proof=pin(ROOT/PROOF),unchanged_shared_stylesheet=pin(ROOT/'site/assets/styles.css'),changed_paths=list(PATHS),scientific_sources_overridden=False,scientific_verifiers_changed=False,new_scoped_integration_credit=0,added_papers=0,added_observations=0,added_reviews=0,whole_goal_complete=False)
 (ROOT/RECORD).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));return proof

def verify(root=ROOT,rescan=False):
 proof=json.loads((root/RECORD).read_bytes())
 if proof['changed_paths']!=list(PATHS) or proof['new_scoped_integration_credit'] or proof['whole_goal_complete']:raise ValueError('Unexpected presentation-only scope')
 for name,wanted in {**proof['after_files'],**proof['source_pins']}.items():
  if pin(root/name)!=wanted:raise ValueError('Current layout source or metadata differs: '+name)
 if pin(root/PROOF)!=proof['preserved_phase1_proof'] or pin(root/'site/assets/styles.css')!=proof['unchanged_shared_stylesheet']:raise ValueError('Old admission proof or protected shared stylesheet changed')
 evidence=root/EVIDENCE
 if pin(evidence/'before-presentation.zip')!=proof['before_archive']:raise ValueError('Historical exact presentation metadata changed')
 with zipfile.ZipFile(evidence/'before-presentation.zip') as archive:
  if len(archive.namelist())!=4 or set(archive.namelist())!=set(PATHS):raise ValueError('Only exact four historical presentation paths may be replayed')
  before={name:archive.read(name) for name in PATHS}
 if {n:rawpin(v) for n,v in before.items()}!=proof['before_files'] or any((root/n).read_bytes()!=raw for n,raw in transition(before).items()):raise ValueError('Layout differs from the exact scoped wrap/banner update')
 from velociraptor_catalog.verify import verify as previous
 result=previous(historical_view(root,before),rescan=rescan)
 if tuple(result[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing'])!=(250,433,2672,1168,112,262):raise ValueError('Scientific source role, counter or admission changed')
 return dict(status='PASS_WARNING_LAYOUT_REVISION2_WITH_ALL_CURRENT_SCIENTIFIC_GATES',papers=250,named_tools=433,observations=2672,reviewed_uses=1168,new_scoped_integrations=112,missing=262,source_catalog=result,documentation_HTML_metadata_replayed_only=list(PATHS),shared_stylesheet_unchanged=True,scientific_sources_overridden=False,scientific_verifiers_changed=False,new_scoped_integration_credit=0,whole_goal_complete=False)

def load_catalog_registry(root=ROOT):
 proof=json.loads((root/RECORD).read_bytes())
 with zipfile.ZipFile(root/EVIDENCE/'before-presentation.zip') as archive:before={name:archive.read(name) for name in PATHS}
 if {n:rawpin(v) for n,v in before.items()}!=proof['before_files']:raise ValueError('Exact historical presentation input differs')
 from velociraptor_catalog.verify import load_catalog_registry as previous
 return previous(historical_view(root,before))

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);mode=parser.add_mutually_exclusive_group(required=True);mode.add_argument('--apply',action='store_true');mode.add_argument('--verify',action='store_true');parser.add_argument('--out',required=True,type=Path);parser.add_argument('--rescan-sources',action='store_true');args=parser.parse_args();out=args.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output')
  out.mkdir(parents=True,exist_ok=False);owned=True;result=apply(out) if args.apply else verify(rescan=args.rescan_sources)
  if args.verify:(out/'COMPLETED.json').write_bytes(encoded(result))
  (out/'WARNINGS.md').write_bytes((ACTION+'\n').encode());print(result['status'],flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_LAYOUT_VERIFICATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_LAYOUT_REVISION_UNADMITTED'
  if owned:(out/'FAILURE.json').write_bytes(encoded(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)))
  print(status,repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
