#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete/check the exact six-review metadata inverse required by unchanged older gates.

The original admission and its source/evidence hashes remain unchanged. This
additive record reconstructs the entire preceding review file byte for byte;
it cannot substitute or overlay scientific code, results or verifier inputs.
"""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from cell2location_catalog.model import ROOT,META,REVIEWS,DATA,PROOF,encoded,pin,check_transition
from cell2location_catalog.bridge import load_before
from catalog_source_integrity import reverse_delta,verify_source
from wire_signac_catalog import delta
DESCRIPTOR=DATA+'/cell2location-reviews-shared-source-compatibility.json'

def expected(root=ROOT):
 before=load_before(root);after={n:(root/n).read_bytes() for n in META};check_transition(root,before,after)
 row=delta(REVIEWS,before[REVIEWS],after[REVIEWS])
 if row['path']!=REVIEWS or reverse_delta(after[REVIEWS],row)!=before[REVIEWS]:raise ValueError('Exact preceding review population was not recovered')
 return dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='cell2location-individual-source-roles',scope='Exactly six appended individual cell2location reviews; all1156 previous reviews unchanged. Only reviewed-uses.json metadata is reversible. Scientific sources/results/verifiers remain live and unchanged.',paths=[row])

def check(root=ROOT):
 wanted=expected(root);raw=(root/DESCRIPTOR).read_bytes()
 if raw!=encoded(wanted):raise ValueError('Exact six-review inverse differs')
 verify_source(root,root/DATA,REVIEWS,wanted['paths'][0]['before_sha256'])
 return dict(status='PASS_EXACT_SIX_REVIEW_INVERSE_UNCHANGED_PREDECESSOR_AND_SCIENTIFIC_GATES',descriptor=pin(root/DESCRIPTOR),admission=pin(root/PROOF),before_sha256=wanted['paths'][0]['before_sha256'],after_sha256=wanted['paths'][0]['after_sha256'],prior_reviews_unchanged=1156,appended_reviews=6,scientific_sources_overridden=False,previous_saved_proofs_changed=False)

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--write',action='store_true',help='Create the missing additive descriptor; never replace an existing file.');a=parser.parse_args();out=a.out.resolve();owned=False
 try:
  if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime output directory')
  out.mkdir(parents=True,exist_ok=False);owned=True
  original=(ROOT/PROOF).read_bytes()
  if a.write:
   raw=encoded(expected())
   with (ROOT/DESCRIPTOR).open('xb') as stream:stream.write(raw)
  result=check();assert (ROOT/PROOF).read_bytes()==original
  result['source_pins']={name:pin(ROOT/name) for name in ['tools/literature/cell2location_catalog/finalize.py','tools/literature/wire_signac_catalog.py','tools/literature/catalog_source_integrity.py']}
  (out/'COMPLETED.json').write_bytes(encoded(result));print(result['status'],flush=True);return 0
 except BaseException as error:
  if owned:(out/'FAILURE.json').write_bytes(encoded(dict(status='FAIL2_METADATA_FINALIZATION_INCOMPLETE',cause=repr(error),traceback=traceback.format_exc(),required_action='Preserve this attempt and every original proof. Resolve the exact review-only inverse; never substitute scientific inputs or rewrite saved source hashes. Retry with NEW output paths.')))
  print(traceback.format_exc(),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
