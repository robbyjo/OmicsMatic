# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build an additive Java statistics component with complete original MIT source/notice retention."""
import argparse,ast,hashlib,json,subprocess,sys,traceback,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_GSEAPY_STATISTICS_BUILD',implementation_credit=False,whole_goal_complete=False)
    try:
        proof=json.loads((ROOT/'docs/literature/scfates-workflow-development/VALIDATION.json').read_bytes());parent=ROOT/proof['artifact']['path'];assert pin(parent)=={k:proof['artifact'][k] for k in ['bytes','sha256']};canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar');intake=ROOT/'build/literature-review/gseapy-runtime/source-intake-1';source=json.loads((intake/'SOURCE.json').read_bytes());assert source['status']=='passed-four-paper-reported-official-GSEApy-source-wheels';resources=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-statistics';members={'.gitattributes':b'* -text whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,cr-at-eol\n'}
        asts={}
        for edition,profile in source['versions'].items():
            for name,want in profile['members'].items():
                if not(name.endswith('.py') or name.endswith('/LICENSE') or name.endswith('/METADATA')):continue
                path=intake/edition/'wheel-source'/name;assert pin(path)==want;members['original/'+edition+'/'+name]=path.read_bytes()
            path=intake/edition/'wheel-source/gseapy/stats.py';functions={}
            for f in ast.parse(path.read_text()).body:
                if isinstance(f,ast.FunctionDef):functions[f.name]=hashlib.sha256(ast.dump(f,include_attributes=False).encode()).hexdigest()
            asts[edition]=functions
        assert asts['1.1.2']==asts['1.1.3']==asts['1.1.8'] and asts['1.0.4']['calc_pvalues']!=asts['1.1.2']['calc_pvalues']
        members['SOURCE-PROFILE.json']=(json.dumps(dict(source=source,original_stats_function_asts=asts,scope='Native literal-identifier local over-representation stats and correction only. No high-level case conversion, remote Enrichr, ranked GSEA, GSVA, ssGSEA or whole-tool/paper credit.'),indent=2)+'\n').encode()
        members['NOTICE.txt']=b'Original GSEApy1.0.4/1.1.2/1.1.3/1.1.8 Python sources, metadata and MIT licenses are retained unchanged under original/. Source wheels come from official PyPI records. Upstream: https://github.com/zqfang/GSEApy . New Java code is GPL-3.0-or-later. Changes: explicit typed background/edition API, native hypergeometric tails and version-bound odds, complete original correction family, deterministic code-point-ordered hit membership instead of Python set iteration. No identifier case conversion, library download, remote-service response, high-level GSEApy adapter or full paper reproduction is claimed. All original sources are retained without patching.\n'
        for name,raw in members.items():
            path=resources/name;path.parent.mkdir(parents=True,exist_ok=True)
            if path.exists():assert path.read_bytes()==raw,'Existing original resource protected'
            else:path.write_bytes(raw)
        sources=[ROOT/'src/main/java/org/jlinalg/enrichment/GseapyStatistics.java',ROOT/'tools/literature/java/org/jlinalg/cli/GseapyStatisticsMatrix.java'];files=[Path(__file__).resolve(),*sources,*sorted(p for p in resources.rglob('*') if p.is_file())];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
        for path in files:target=out/'source-snapshot'/path.relative_to(ROOT);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(path.read_bytes())
        classes=out/'classes';classes.mkdir();command=[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(parent),'-d',str(classes),*map(str,sources)];run=subprocess.run(command,capture_output=True,timeout=120);(out/'compile.stdout').write_bytes(run.stdout);(out/'compile.stderr').write_bytes(run.stderr);save(out/'compile.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,run.stderr.decode('utf8',errors='replace')
        added={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')};added.update({p.relative_to(ROOT/'src/main/resources').as_posix():p.read_bytes() for p in resources.rglob('*') if p.is_file()});jar=out/'omicsmatic-0.3.7-gseapy-statistics-development.jar'
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar,'x') as after:
            assert set(added).isdisjoint(before.namelist())
            for info in before.infolist():after.writestr(info,before.read(info.filename))
            for name,raw in sorted(added.items()):after.writestr(name,raw,compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(parent) as before,zipfile.ZipFile(jar) as after:assert after.testzip() is None and all(before.read(n)==after.read(n) for n in before.namelist());unchanged=len(before.namelist())
        assert all(pin(ROOT/n)==v for n,v in pins.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==canonical;state.update(status='passed-compiled-native-GSEApy-stats-component-only',artifact=dict(path=jar.relative_to(ROOT).as_posix(),**pin(jar)),source_pins=pins,parent_artifact=proof['artifact'],unchanged_parent_entries=unchanged,added_entries=sorted(added),canonical_executable_unchanged=canonical)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
