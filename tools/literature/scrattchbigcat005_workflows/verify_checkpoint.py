#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check all frozen payloads and recompute complete saved-result comparisons."""
from pathlib import Path
import argparse,subprocess,sys,traceback
sys.dont_write_bytecode=True
from Runtime import ROOT,HERE,ACTION,Unavailable,pin,load,save,sources,check,check_output
from Evidence import extract
from Verification import verification_environment
from Claims import check_report

DATA=ROOT/'docs/literature/scrattchbigcat005-workflow-validation'

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--runtime2023',type=Path,required=True);p.add_argument('--runtime2022',type=Path,required=True)
    p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();out=args.out.absolute();owned=False
    try:
        ready={year:load(path.resolve()/'READY.json') for year,path in [('2023',args.runtime2023),('2022',args.runtime2022)]}
        protected=[HERE,DATA]
        for r in ready.values():protected += [r['provider'],r['workspace'],r['r_prefix'],r['library'],*r['source_paths'].values()]
        out=check_output(out,protected)
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use a NEW monocle-runtime verification directory')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if sys.platform!='linux':raise Unavailable('Complete saved BIGCAT checks require permitted Linux/WSL')
        proof=load(DATA/'QUALIFICATION.json')
        if proof['status']!='PASS_ORIGINAL_BIGCAT_REPORTED_WORKFLOWS_WITH_DISCLOSED_R_OBJECT_LIMITS':raise ValueError('Unsupported qualification')
        if (proof['original_controls'],proof['complete_returns'],proof['original_failures_without_result'])!=(25,19,6):raise ValueError('Incomplete original population')
        if proof['adapter_sources']!=sources():raise ValueError('Qualified adapter code changed; qualify new evidence without refreshing historical hashes')
        for name,identity in proof['module_sources'].items():
            if pin(HERE/name)!=identity:raise ValueError('Qualified helper source changed: '+name)
        for name,identity in proof['native_source_files'].items():
            if pin(ROOT/name)!=identity:raise ValueError('Prior native component changed: '+name)
        for year,profile in [('2023','prepublication-2023'),('2022','pre-regression-2022')]:
            if ready[year]['profile']!=profile:raise ValueError('Explicit source profiles cannot be interchanged')
            check(ready[year])
        layout=extract(DATA/'original-workflow-and-installation-evidence.zip',out/'frozen',proof)
        for name in ['home','tmp']:(out/name).mkdir()
        reports={};keys={}
        for name,spec in proof['cases'].items():
            year='2022' if spec['profile']=='pre-regression-2022' else '2023';r=ready[year]
            target=out/'comparisons'/name;target.parent.mkdir(exist_ok=True)
            frozen=out/'frozen';case=frozen/spec['case'];request=frozen/spec['request'];inputs=frozen/spec['inputs']
            command=[str(Path(r['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'CompareWorkflowReferences.R'),
              str(case),str(inputs),str(request),str(target)]
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.run(command,cwd=out,env=verification_environment(r,out),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            save(out/(name+'-PROCESS.json'),dict(command=command,exit_code=child.returncode))
            if child.returncode:raise ValueError('Saved comparison failed: '+name+'; inspect complete logs')
            report=load(target/'COMPARISON.json')
            reports[name]=check_report(report,spec['operation'],spec['returned'],spec['comparison_keys'])
            if not spec['returned'] and (case/'adapter/COMPLETED.json').exists():raise ValueError('Original failure has a false completion marker')
            print('SAVED_ORIGINAL_BIGCAT_CHECKED',name,flush=True)
        r=ready['2023'];target=out/'nonzero-partition-inspection';target.mkdir()
        command=[str(Path(r['r_prefix'])/'bin/Rscript'),'--vanilla',str(HERE/'InspectParquetBinControl.R'),str(target),
          str(out/'frozen/controls/mapping-nonzero-four-partitions'),str(out/'frozen/inputs/nonzero-bins')]
        with (out/'partition.stdout').open('xb') as stdout,(out/'partition.stderr').open('xb') as stderr:
            child=subprocess.run(command,cwd=out,env=verification_environment(r,out),stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
        if child.returncode:raise ValueError('Frozen Parquet reconstruction/distance inspection failed')
        inspection=load(target/'INSPECTION.json')
        if inspection['status']!='PASS_FOUR_NONZERO_ORIGINAL_PARTITIONS_AND_INDEPENDENT_MAPPING':raise ValueError('Incomplete saved partition check')
        for r in ready.values():check(r)
        if sources()!=proof['adapter_sources']:raise ValueError('Adapter changed during saved verification')
        result=dict(status='PASS_COMPLETE_FROZEN_BIGCAT_OBJECT_ASSET_AND_RENDER_REPLAY',
          original_controls=25,complete_returns=19,original_failures_without_result=6,logical_evidence_files=len(layout['files']),
          unique_frozen_payloads=len(layout['blobs']),all_frozen_members_checked=True,
          compared_fields=sum(r['compared_fields'] for r in reports.values()),cases=reports,
          original_condition_fields_deleted_or_coerced=False,live_R_object_identity_claimed=False,
          saved_Parquet_assets_reparsed=True,derived_saved_plot_renders_recomputed=True,
          original_Bigcat_analysis_or_model_fitting_executed=False,new_family_credit=0,whole_goal_complete=False)
        (out/'WARNINGS.md').write_text('# Complete saved BIGCAT records checked\n\n'
          'Full R objects and differences remain intact. Temporary Annoy paths, cyclic plot environments and FBM '
          'class-definition identities do not establish cross-process object identity. Scientific arrays, '
          'file-backed payloads, complete plot data and paired derived renders were checked independently. '
          'The six original failures remain failures without admitted results.\n\n'+ACTION+'\n',encoding='utf-8')
        save(out/'COMPLETED.json',result)
        print(result['status'],flush=True);return 0
    except BaseException as error:
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_BIGCAT_CHECKPOINT_UNAVAILABLE' if code==3 else 'FAIL2_BIGCAT_CHECKPOINT_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+label+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:print('FAIL2_CHECKPOINT_RECORDING_UNAVAILABLE',repr(recording),flush=True);code=2
        return code

if __name__=='__main__':raise SystemExit(main())
