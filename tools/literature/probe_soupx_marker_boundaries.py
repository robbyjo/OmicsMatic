# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain actual original/native cutoff behavior, including floating-point selection differences."""
import argparse,csv,io,json,os,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory
from validate_mashr_native import JAVA21


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/soupx-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};script=ROOT/'tools/literature/probe_soupx_marker_boundaries.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script]};libraries=[];profiles={}
    for version,attempt in [('1.6.2',1),('1.3.0',2)]:
        reference=base/f'reference-{version}-{attempt}';setup=json.loads((reference/'COMPLETED.json').read_bytes());assert setup['status']=='installed-source-bound-original-SoupX-reference-only' and setup['preexisting_libraries_unchanged'];chain=[reference/'R-lib',*map(Path,json.loads((reference/'STARTED.json').read_bytes())['libraries'])];profiles[version]=chain;libraries.extend(chain)
    libraries=list(dict.fromkeys(libraries));before=inventory(libraries);save(out/'LIBRARIES-BEFORE.json',before);reports=[]
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    for version,chain in profiles.items():
        folder=out/version;folder.mkdir();source=base/('source-intake-1/1.6.2' if version=='1.6.2' else 'source-recovery-1/1.3.0');receipt=json.loads((source/'SOURCE.json').read_bytes());assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(folder),str(source/'source'),version,*map(str,chain)];r=subprocess.run(command,cwd=ROOT,env=dict(os.environ,LC_ALL='C',LANG='C',LANGUAGE='en',R_LIBS=';'.join(map(str,chain)),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1'),stdin=subprocess.DEVNULL,capture_output=True,timeout=60);(folder/'stdout').write_bytes(r.stdout);(folder/'stderr').write_bytes(r.stderr);save(folder/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0;reference=json.loads((folder/'reference.json').read_bytes());assert reference['source_bodies_and_formals_checked_before_and_after']
        for i,call in enumerate(reference['calls']):
            trial=folder/f'native-{i+1}';trial.mkdir();(trial/'genes.tsv').write_text('gene_id\nb\na\n',encoding='utf8',newline='\n');(trial/'cells.tsv').write_text('cell_id\tcluster\nc1\tNA\nc2\tA\n',encoding='utf8',newline='\n');(trial/'counts.tsv').write_text('gene_id\tcell_id\tvalue\nb\tc1\t1\na\tc2\t1\n',encoding='utf8',newline='\n');save(trial/'request.json',dict(schema_version=1,mode='MARKERS',genes='genes.tsv',cells='cells.tsv',counts='counts.tsv',out='result',expression_units='synthetic counts',expression_cutoff=0,top_n='ALL',fdr=float(call['fdr'])));command=[str(JAVA21/'java.exe'),'-cp',str(jar),'org.jlinalg.cli.TfidfMarkersCli','--request',str(trial/'request.json')];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=30);(trial/'stdout').write_bytes(r.stdout);(trial/'stderr').write_bytes(r.stderr);save(trial/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0
            result=trial/'result';state=json.loads((result/'analysis-status.json').read_bytes());assert state['result_admitted'];provenance=json.loads((result/'provenance.json').read_bytes());assert provenance['near_cutoff_rows']>0 and 'CUTOFF REVIEW REQUIRED:' in (result/'WARNINGS.md').read_text(encoding='utf8');rows=list(csv.DictReader(io.StringIO((result/'results/markers.tsv').read_text(encoding='utf8')),delimiter='\t'));actual=[(r['gene_id'],r['cluster']) for r in rows];expected=[(r['gene'],r['cluster']) for r in call['rows']];complete=list(csv.DictReader(io.StringIO((result/'results/statistics.tsv').read_text(encoding='utf8')),delimiter='\t'));r_q={(r['gene'],r['cluster']):float(r['qval']) for r in call['all_statistics']};native_q={(r['gene_id'],r['cluster']):float(r['bh_q_value']) for r in complete};assert all(abs(native_q[k]-r_q[k])<3e-12 for k in native_q)
            reports.append(dict(version=version,trial=i+1,fdr=call['fdr'],original_selected=expected,native_selected=actual,selection_differs=actual!=expected,native_complete_q=[dict(gene=k[0],cluster=k[1],q=v,original_q=r_q[k]) for k,v in native_q.items()],exact_rational_BH_q=1,near_cutoff_warning_retained=True))
        assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items())
    after=inventory(libraries);save(out/'LIBRARIES-AFTER.json',after);assert before==after and all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='retained-original-native-and-exact-mathematical-cutoff-comparison',source_pins=pins,artifact=build['artifact'],protocols=reports,preexisting_libraries_unchanged=True,preexisting_files=sum(map(len,before.values())),implementation_credit=False,whole_goal_complete=False));print(json.dumps(dict(protocols=len(reports),selection_differences=sum(r['selection_differs'] for r in reports))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/cutoff checks')
    main()
