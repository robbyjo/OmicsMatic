# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check Hornet component source context and optional retained private evidence."""
from pathlib import Path
import argparse, hashlib, json, zipfile

def pin(path):
 digest=hashlib.sha256();size=0
 with Path(path).open('rb')as stream:
  for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
 return dict(bytes=size,sha256=digest.hexdigest())

def verify(repo,evidence=None):
 folder=repo/'docs/literature/hornet-alleles-validation'
 q=json.loads((folder/'QUALIFICATION.json').read_bytes())
 assert q['status']=='PASS_NATIVE_HORNET_ALLELE_COMPONENT_ONLY'
 assert q['new_family_credit']==0 and not q['mapping_workflow_qualified'] and not q['whole_goal_complete']
 assert q['native_tests_per_os']==341 and q['original_cases']==334
 fixture=repo/'src/test/resources/org/jlinalg/sequence/hornet-alleles/oracle.json'
 oracle=json.loads(fixture.read_bytes());assert len(oracle['cases'])==334
 assert len([x for x in oracle['cases']if 'source_error'in x])==2
 assert len(oracle['reverse_complements'])==3
 assert oracle['source']['revision']=='e642ac0ee9ed6625ce415305b9ced4d686403c79'
 assert pin(folder/'source-context.zip')==q['source_archive']
 with zipfile.ZipFile(folder/'source-context.zip')as archive:
  assert set(archive.namelist())==set(q['sources'])and len(archive.namelist())==len(q['sources'])
  for name,expected in q['sources'].items():
   raw=archive.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected
   assert pin(repo/name)==expected,('Current source differs; restore archived matching source for historical verification',name)
 assert pin(repo/'docs/literature/omics-2023-2026/summary.json')==q['unchanged_catalog_summary']
 assert pin(repo/'docs/literature/rfmix-catalog-admission/REGISTRY.json')==q['unchanged_effective_registry']
 if evidence is not None:
  proof=json.loads((evidence/'QUALIFICATION-PROOF.json').read_bytes())
  assert proof['public_qualification']==pin(folder/'QUALIFICATION.json')
  for name,expected in proof['retained_files'].items():assert pin(evidence/name)==expected,('Changed private evidence',name)
  assert pin(evidence/q['producer_relative'])==q['producer']
  for host in ['Windows','Linux']:
   report=json.loads((evidence/('packaged-'+host+'-1/COMPLETED.json')).read_bytes())
   assert report['producer']==q['producer']and report['counts']==dict(captures=9,replays=9,negative_controls=9)
   assert len(report['records'])==27 and all(x['exit_code']==x['expected']for x in report['records'])
  assert pin(evidence/'Hornet-public-oracle-replay-Linux-1/oracle.json')==pin(fixture)
 return dict(status='PASS_HORNET_ALLELE_COMPONENT_SOURCE_AND_EVIDENCE',original_cases=334,native_tests_per_os=341,private_evidence_checked=evidence is not None,new_family_credit=0,whole_goal_complete=False)

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2])
 parser.add_argument('--evidence',type=Path)
 args=parser.parse_args()
 print(json.dumps(verify(args.repo.resolve(),args.evidence.resolve()if args.evidence else None),indent=2))
