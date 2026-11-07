# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check overlap ordering when query/feature population sizes change; preserve all source/native mismatches."""
import argparse
import csv
import io
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from setup_deseq2_reference import BASES,RSCRIPT,environment,pin,save
from validate_mashr_native import JAVA21
from validate_chipseeker_files import text_table


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--artifact-build',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    proof=json.loads((args.artifact_build/'COMPLETED.json').read_bytes());jar=ROOT/proof['artifact']['path'];assert pin(jar)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    plan=json.loads((ROOT/'build/literature-review/chipseeker-runtime/nearest-original-3/PLAN.json').read_bytes());cases=[]
    features=[dict(contig='x',start=a,end=b,strand='+') for a,b in [(200,400),(50,500),(100,1000),(500,700)]]
    for count in [1,3,4,5]:
        for start,end in [(300,310),(80,220)]:
            for overlap in ['TSS','all']:
                cases.append(dict(id=f'n{count}-p{start}-{overlap}',features=features,peaks=[dict(contig='x',start=start,end=end,strand='*') for i in range(count)],
                    options=dict(same_strand=False,ignore_overlap=False,ignore_upstream=False,ignore_downstream=False,overlap=overlap)))
    for start,end in [(300,310),(80,220)]:
        for overlap in ['TSS','all']:
            mixed_features=[*features,*[dict(f,contig='y') for f in features]]
            mixed_peaks=[dict(contig='x',start=start,end=end,strand='*'),*[dict(contig='y',start=start,end=end,strand='*') for i in range(5)]]
            cases.append(dict(id=f'mixed-p{start}-{overlap}',features=mixed_features,peaks=mixed_peaks,
                options=dict(same_strand=False,ignore_overlap=False,ignore_upstream=False,ignore_downstream=False,overlap=overlap)))
    plan['cases']=cases;save(out/'PLAN.json',plan)
    command=[str(RSCRIPT),'--vanilla',str(ROOT/'tools/literature/collect_chipseeker_nearest.R'),str(out/'PLAN.json'),str(out/'ORIGINAL.json'),os.pathsep.join(map(str,BASES))]
    r=subprocess.run(command,cwd=ROOT,env=environment(),capture_output=True,timeout=180);(out/'R.stdout').write_bytes(r.stdout);(out/'R.stderr').write_bytes(r.stderr);assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
    sources=json.loads((out/'ORIGINAL.json').read_bytes())['cases'];records=[]
    for case in cases:
        folder=out/case['id'];folder.mkdir();(folder/'peaks.tsv').write_text(text_table(case['peaks'],False),encoding='utf8',newline='\n');(folder/'features.tsv').write_text(text_table(case['features'],True),encoding='utf8',newline='\n')
        save(folder/'request.json',dict(schema_version=1,options={'input':'peaks.tsv','features':'features.tsv','reference-build':'declared-reference','coordinates':'one-based-inclusive','overlap':case['options']['overlap'].upper(),'out':'result'}))
        command=[str(JAVA21/'java.exe'),'-Xmx256m','-cp',str(jar),'org.jlinalg.cli.ChipSeekerNearestCli','--request',str(folder/'request.json')]
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(folder/'stdout.log').write_bytes(r.stdout);(folder/'stderr.log').write_bytes(r.stderr)
        assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
        actual=list(csv.DictReader(io.StringIO((folder/'result/source-retained.tsv').read_text(encoding='utf8')),delimiter='\t'))
        observed=dict(index_tokens=[row['source_feature_ordinal'] for row in actual],distance_tokens=[row['distance_to_tss'] for row in actual],retained_input_ordinals=[int(row['input_ordinal']) for row in actual])
        expected=[row for row in sources if row['case_id']==case['id']];assert len(expected)==4
        def vectors(output):
            return {k:v if isinstance(v,list) else [v] for k,v in output.items()} if output is not None else None
        differences=[dict(version=row['version'],source_error=row['source_error'],original=row['output'],native=observed) for row in expected if row['source_error'] is not None or vectors(row['output'])!=observed]
        records.append(dict(case_id=case['id'],query_count=len(case['peaks']),feature_count=len(case['features']),differences=differences))
    save(out/'RESULT.json',dict(artifact=proof['artifact'],records=records,source_script=pin(Path(__file__)),analysis_admitted=False,
        comparison_representation='Only jsonlite singleton-vector unboxing is wrapped back into a list for comparison; every original index/distance/ordinal remains unchanged in ORIGINAL.json.'))
    mismatches=sum(bool(r['differences']) for r in records)
    if mismatches:save(out/'FAILURE.json',dict(status='overlap-order-mismatch-native-component-not-admitted',mismatching_populations=mismatches));raise SystemExit(2)
    save(out/'COMPLETED.json',dict(status='passed-source-overlap-order-population-branch-controls',artifact=proof['artifact'],native_workflows=len(cases),original_calls=len(sources),source_script=pin(Path(__file__)),implementation_credit=False))
    print(f'Passed{len(cases)} native workflows /{len(sources)} original calls across query/feature population branches')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original overlap-order checks')
    main()
