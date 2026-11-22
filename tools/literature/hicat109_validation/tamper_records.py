#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Accept unchanged records and reject paired fabricated records after inventory resealing."""
from pathlib import Path
import argparse,shutil,subprocess,sys,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'tools/literature/scrattchhicat109'))
from Runtime import ACTION,check,check_output,environment,inventory,load,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--runtime',type=Path,required=True);p.add_argument('--replay',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    args=p.parse_args();ready=load(args.runtime/'READY.json');out=args.out.absolute();owned=False
    try:
        out=check_output(out,[HERE,args.replay,args.runtime,ready['workspace']]);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nOnly NEW copied negative fixtures are deliberately modified. No qualified archive, runtime, scientific implementation or original record is changed.\n')
        check(ready)
        if load(args.replay/'COMPLETED.json')['original_controls']!=26:raise ValueError('Complete fresh original replay required')
        before=inventory(args.replay/'fresh');cases=args.replay/'records/controls/cases'
        rscript=Path(ready['r_prefix'])/'bin/Rscript';results=[]
        scenarios=[('unchanged-dense','dense-default',None),('unchanged-warning','partial-match-warning',None),
          ('score-real','dense-default','score-real'),('score-artificial','dense-default','score-artificial'),
          ('names','literal-identifiers','names'),('rng','dense-default','rng'),
          ('warning','partial-match-warning','warning'),('failure','k1','failure')]
        for name,case,kind in scenarios:
            folder=out/name;folder.mkdir()
            for item in ['home','tmp','cases']:(folder/item).mkdir()
            shutil.copytree(cases/case,folder/'cases'/case)
            def run(label,command,expected):
                with (folder/(label+'.stdout')).open('xb') as so,(folder/(label+'.stderr')).open('xb') as se:
                    child=subprocess.run(list(map(str,command)),cwd=folder,env=environment(ready,folder),stdout=so,stderr=se,stdin=subprocess.DEVNULL,timeout=300)
                save(folder/(label+'-PROCESS.json'),dict(command=list(map(str,command)),exit_code=child.returncode,expected=expected))
                if child.returncode!=expected:raise ValueError('Unexpected semantic control '+name+' '+label)
            if kind:run('deliberate-corruption',[rscript,'--vanilla',HERE/'MakeTamper.R',folder/'cases'/case,kind],0)
            # Reseal the complete logical scientific-record inventory after
            # deliberately changing both stored upstream and worker records.
            changed=inventory(folder/'cases');save(folder/'RESEALED-MANIFEST.json',changed)
            assert load(folder/'RESEALED-MANIFEST.json')==inventory(folder/'cases')
            run('fresh-original-comparison',[rscript,'--vanilla',HERE/'CompareReplay.R',ready['library'],folder/'cases',args.replay/'fresh',folder,case],2 if kind else 0)
            report=load(folder/'REPLAY-COMPARISONS.json')
            if kind and all(report['cases'][case].values()):raise ValueError('Fabricated science accepted '+name)
            results.append(dict(name=name,accepted=kind is None,complete_inventory_resealed=True,comparison=report))
            print('SEMANTIC_GUARD',name,'REJECTED' if kind else 'ACCEPTED',flush=True)
        if before!=inventory(args.replay/'fresh'):raise ValueError('Fresh original reference was modified')
        check(ready)
        save(out/'COMPLETED.json',dict(status='PASS_TWO_UNCHANGED_ACCEPTED_SIX_PAIRED_RESEALED_SCIENTIFIC_CORRUPTIONS_REJECTED',
          results=results,original_reference_unchanged=True,whole_original_values_conditions_rng_compared=True,
          archived_ZIP_envelope_resealed_claimed=False,scope='Shared full-object semantic comparer used by replay; complete logical inventory resealed in each NEW copy.',
          whole_tool_qualified=False,new_family_credit=0,whole_goal_complete=False))
        return 0
    except BaseException as error:
        print('FAIL2_HICAT109_SEMANTIC_GUARDS',repr(error),ACTION,flush=True)
        if owned:save(out/'FAILURE.json',dict(cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION))
        return 2

if __name__=='__main__':raise SystemExit(main())
