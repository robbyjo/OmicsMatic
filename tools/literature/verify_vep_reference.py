"""Verify every file of the entire original VEP105 human bundle; optionally write its NEW exact profile."""
import argparse,json,os,sys
from pathlib import Path
from verify_vep_workflows import pin
ROOT=Path(__file__).resolve().parents[2]
ACTION='Use complete original Ensembl105 human GRCh38 cache and primary-assembly FASTA bytes, with the exact source-generated genome.fa.fai. Keep all194 sequences and all cache files. Correct missing/forbidden prerequisites where permitted or retain SKIP3. Different/partial/extra/changed files mean FAIL2: preserve evidence, correct the named cause and retry NEW preparation; do not relabel versions, repin different data, reduce chromosomes or overwrite an existing PROFILE.json.'
def verify(bundle,prepare):
 supplied=bundle.absolute();root=bundle.resolve()
 if not root.is_dir():raise FileNotFoundError('Complete original reference directory unavailable: '+str(root))
 if root!=supplied or root.is_symlink()or getattr(root,'is_junction',lambda:False)():raise RuntimeError('Require a canonical unlinked reference directory')
 source=ROOT/'src/main/resources/org/jlinalg/omics/vep105/REFERENCE.json';raw=source.read_bytes();profile=json.loads(raw);expected=profile['files'];actual={}
 if prepare and(root/'PROFILE.json').exists():raise RuntimeError('Preserve existing PROFILE.json; use verification or NEW preparation')
 for directory,dirs,files in os.walk(root,followlinks=False):
  for name in dirs+files:
   p=Path(directory)/name
   if p.is_symlink()or getattr(p,'is_junction',lambda:False)()or p.resolve()!=root/p.relative_to(root):raise RuntimeError('Reference alias found: '+str(p))
  for name in files:
   p=Path(directory)/name;relative=p.relative_to(root).as_posix()
   if relative=='PROFILE.json':continue
   if relative not in expected:raise RuntimeError('Extra reference file: '+relative)
   if not p.is_file()or pin(p)!=expected[relative]:raise RuntimeError('Original reference bytes differ: '+relative)
   actual[relative]=expected[relative]
 if actual!=expected:raise RuntimeError('Missing original reference files: '+str(sorted(set(expected)-set(actual))[:10]))
 if prepare:
  with(root/'PROFILE.json').open('xb')as target:target.write(raw)
 if(root/'PROFILE.json').read_bytes()!=raw:raise RuntimeError('Exact complete-reference PROFILE.json differs')
 return dict(status='PASS_ENTIRE_ORIGINAL_VEP105_HUMAN_REFERENCE_BYTES',profile=profile['profile'],files=len(expected),reference_bytes=sum(x['bytes']for x in expected.values()),sequences=profile['fasta_sequences'],bases=profile['fasta_bases'],profile_written=prepare,variant_analysis_executed=False,authentication=False)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--bundle',required=True,type=Path);p.add_argument('--prepare-profile',action='store_true');p.add_argument('--allow-prepare',action='store_true');a=p.parse_args()
 if a.prepare_profile and not a.allow_prepare:print('SKIP3_DEPENDENT_ANALYSIS_SKIPPED: --prepare-profile requires --allow-prepare. '+ACTION,file=sys.stderr);return 3
 try:print(json.dumps(verify(a.bundle,a.prepare_profile),indent=2));return 0
 except (FileNotFoundError,PermissionError)as error:print('SKIP3_DEPENDENT_ANALYSIS_SKIPPED: '+str(error)+'\nREQUIRED ACTION: '+ACTION,file=sys.stderr);return 3
 except Exception as error:print('FAIL2_NO_COMPLETE_RESULT: '+str(error)+'\nREQUIRED ACTION: '+ACTION,file=sys.stderr);return 2
if __name__=='__main__':raise SystemExit(main())
