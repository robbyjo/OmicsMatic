# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an additive native CPU arithmetic component, preserving the entire validated development parent."""
import argparse,json,subprocess,sys,traceback,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_SIMPLEPPT_ARITHMETIC_BUILD',implementation_credit=False,whole_goal_complete=False)
 try:
  parent_proof=json.loads((ROOT/'docs/literature/palantir-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/parent_proof['artifact']['path'];assert pin(parent)=={k:parent_proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');intake=ROOT/'build/literature-review/scfates-runtime/source-intake-1';source=json.loads((intake/'SOURCE.json').read_bytes());review=ROOT/'build/literature-review/scfates-runtime/source-review-1/COMPLETED.json';assert json.loads(review.read_bytes())['status']=='PASS_SCFATES_SIMPLEPPT_SCIENTIFIC_SOURCES_AND_GENERATED_VERSION_REVIEW_ONLY';resources=ROOT/'src/main/resources/org/jlinalg/omics/simpleppt-core';members={'.gitattributes':b'* -text\n'}
  for name,package in source['packages'].items():
   for member,want in package['wheel_members'].items():path=intake/name/'wheel-source'/member;assert pin(path)==want;members['original/'+name+'/'+member]=path.read_bytes()
   for name2 in ['LICENSE','README.md','pyproject.toml','setup.py']:
    if name2 in package['git_source_members']:path=intake/name/'git-source'/name2;assert pin(path)=={k:package['git_source_members'][name2][k] for k in ['bytes','sha256']};members['repository/'+name+'/'+name2]=path.read_bytes()
  members['SOURCE.json']=(json.dumps(dict(schema_version=1,original_source=source,source_review=json.loads(review.read_bytes()),scope='Native CPU assignment, literal convergence, conditional linear-system/RHS and diagnostic score arithmetic only. No MST, RNG, full tree fitting, roots or scFates pseudotime workflow.',implementation_credit=False),indent=2)+'\n').encode();members['NOTICE.txt']=b'Original SimplePPT1.1.7 and scFates1.2.5 source and BSD-3-Clause license conditions are retained unchanged under original/ and repository/. Upstream: https://github.com/LouisFaure/simpleppt and https://github.com/LouisFaure/scFates . New Java arithmetic is GPL-3.0-or-later and follows literal CPU equations, including exp(-distance/sigma), zero-mass NaN-to-zero normalization, signed-coordinate-sum convergence, weighted conditional equations and original nonfinite score terms. This is not a complete Java tree fitter, scFates workflow or paper reproduction. No installation or random draw occurs in the native component. The generated SimplePPT wheel/tag version-only difference is retained and explicitly reviewed; neither original is rewritten.\n'
  for name,raw in members.items():
   path=resources/name;path.parent.mkdir(parents=True,exist_ok=True)
   if path.exists():assert path.read_bytes()==raw,'Existing source/profile member protected'
   else:path.write_bytes(raw)
  sources=[ROOT/'src/main/java/org/jlinalg/embedding/SimplePptMath.java',ROOT/'tools/literature/java/org/jlinalg/cli/SimplePptMathMatrix.java'];files=[Path(__file__).resolve(),*sources,*[p for p in resources.rglob('*') if p.is_file()]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
  for path in files:target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
  classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode(errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-simpleppt-math-development.jar'
  with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
   assert set(added).isdisjoint(before.namelist())
   for info in before.infolist():after.writestr(info,before.read(info.filename))
   for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
  with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
  assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_NATIVE_SIMPLEPPT_CPU_ARITHMETIC_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=parent_proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':raise SystemExit(main())
