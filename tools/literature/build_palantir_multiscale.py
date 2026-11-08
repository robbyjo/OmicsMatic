# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an additive native coordinate component, preserving every parent JAR entry and original license/source."""
import argparse,ast,hashlib,json,subprocess,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_NATIVE_PALANTIR_MULTISCALE_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/cellphonedb-hash-seed-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');intake=ROOT/'build/literature-review/palantir-runtime/source-intake-3';source=json.loads((intake/'SOURCE.json').read_bytes());assert source['status']=='PASS_EXACT_OFFICIAL_PALANTIR_SOURCE_ONLY';resources=ROOT/'src/main/resources/org/jlinalg/omics/palantir-multiscale';members={'.gitattributes':b'* -text whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,cr-at-eol\n'};bodies=[]
        for edition,profile in source['versions'].items():
            for name,want in profile['wheel_members'].items():path=intake/edition/'wheel-source'/name;assert pin(path)==want;members['original/'+edition+'/'+name]=path.read_bytes()
            for name in ['LICENSE','README.md','pyproject.toml']:path=intake/edition/'git-source'/name;want=profile['git_source_members'][name];assert pin(path)=={k:want[k] for k in ['bytes','sha256']};members['repository/'+edition+'/'+name]=path.read_bytes()
            module=members['original/'+edition+'/palantir/utils.py'].decode('utf8');function=next(n for n in ast.parse(module).body if isinstance(n,ast.FunctionDef) and n.name=='determine_multiscale_space');body=function.body[1:] if isinstance(function.body[0],ast.Expr) and isinstance(function.body[0].value,ast.Constant) and isinstance(function.body[0].value.value,str) else function.body;bodies.append(ast.dump(ast.Module(body=body,type_ignores=[]),include_attributes=False))
        assert len(bodies)==2 and bodies[0]==bodies[1],'Original scientific function bodies differ; no shared-source behavior claim';profile=dict(schema_version=1,source=source,shared_multiscale_body_ast_sha256=hashlib.sha256(bodies[0].encode()).hexdigest(),scope='Native float64 ordered eigenpair multiscale coordinates, explicit bounds and tie-order-invariant automatic selection only. No arbitrary Series labels, float32 eigenvalue ufunc parity, diffusion fitting, pseudotime/fate/entropy or whole-tool/paper credit.',implementation_credit=False,whole_goal_complete=False);members['SOURCE-PROFILE.json']=(json.dumps(profile,indent=2)+'\n').encode();members['NOTICE.txt']=b'Palantir original1.4.4 and1.4.6 code and MIT notices are retained unchanged under original/ and repository/. Upstream: https://github.com/dpeerlab/Palantir . New native Java port is GPL-3.0-or-later. Changes: typed bounded float64 coordinate API, complete output/nonfinite dispositions and explicit deferral for automatic gap ties whose bound depends on NumPy sort order. No scientific function is patched in the original reference runtime. No full Palantir or paper reproduction is admitted. Source versions remain explicit; neither is inferred to be the unreported paper version.\n'
        for name,raw in members.items():
            path=resources/name;path.parent.mkdir(parents=True,exist_ok=True)
            if path.exists():assert path.read_bytes()==raw,'Existing resource protected'
            else:path.write_bytes(raw)
        sources=[ROOT/'src/main/java/org/jlinalg/embedding/PalantirMultiscale.java',ROOT/'tools/literature/java/org/jlinalg/cli/PalantirMultiscaleMatrix.java'];files=[Path(__file__).resolve(),*sources,*sorted(p for p in resources.rglob('*') if p.is_file())];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for p in files:target=out/'source-snapshot'/p.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace');added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-palantir-multiscale-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='PASS_COMPILED_NATIVE_PALANTIR_MULTISCALE_COMPONENT_ONLY',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
