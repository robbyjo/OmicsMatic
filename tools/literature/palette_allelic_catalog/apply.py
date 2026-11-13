#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Append two individually reviewed families, admitting only the qualified native palette subset."""
from pathlib import Path
import argparse, json, sys, traceback, zipfile
TOOLS=Path(__file__).resolve().parents[1];sys.path.insert(0,str(TOOLS))
from palette_allelic_catalog.model import *
from palette_allelic_catalog.source import scan,validate
from palette_allelic_catalog.qualification import qualification
from palette_allelic_catalog.verify import ACTION
from imagej_catalog.verify import verify as previous
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();owned=False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):raise ValueError('Use NEW monocle-runtime output')
        out.mkdir(parents=True,exist_ok=False);owned=True;archive=ROOT/EVIDENCE/'catalog-before.zip'
        if any(path.exists() for path in [archive,ROOT/PROOF,ROOT/COMPATIBILITY,ROOT/COVERAGE]):raise ValueError('Amendment already exists; preserve all evidence')
        prior=previous(ROOT);before={n:(ROOT/n).read_bytes() for n in META};papers=json.loads(before[PAPERS])
        census=scan(ROOT,papers);validate(census,papers,reviews(ROOT),ROOT);components=qualification(ROOT)
        evidence=ROOT/EVIDENCE;evidence.mkdir(exist_ok=False)
        (evidence/'.gitattributes').write_text('* -text whitespace=cr-at-eol\n',encoding='ascii',newline='\n')
        (evidence/'CENSUS.json').write_bytes(encoded(census));(evidence/'curation-intake-before.json').write_bytes((ROOT/CURATION).read_bytes())
        after=transition(ROOT,before);result=check_transition(ROOT,before,after)
        with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as saved:
            for name,raw in before.items():
                info=zipfile.ZipInfo(name,(2026,11,13,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;saved.writestr(info,raw)
        audit=dict(status='PASS_ALL250_PRIMARY_SOURCES_AND_COMPLETE_COMPONENT_EVIDENCE_BEFORE_MUTATION',
            source=dict(papers=250,paper_family_pairs=2,named_primary_paragraphs=27),components=components,
            previous={k:prior[k] for k in ['papers','named_tools','observations','reviewed_uses','new_scoped_integrations','missing']},
            scientific_sources_changed=False,original_analysis_executed=False,whole_goal_complete=False)
        (evidence/'SOURCE_AUDIT.json').write_bytes(encoded(audit))
        wes,allelic=entries(ROOT)
        (ROOT/COVERAGE).write_bytes(encoded(dict(status='PASS_NATIVE_PALETTE_ROLES_AND_VERSION_SEPARATE_ALLELIC_CANDIDATE',
            source_reviews=reviews(ROOT),wesanderson_paper_roles=wes['paper_role_coverage'],wesanderson_uncovered_roles=wes['uncovered_roles'],
            allelicseries_paper_roles=allelic['paper_role_coverage'],allelicseries_uncovered_roles=allelic['uncovered_roles'],
            new_scoped_integration_credit=1,allelicseries_credit=0,whole_paper_reproduced=False,whole_tool_qualified=False,whole_goal_complete=False)))
        delta=inverse(before[REVIEWS],after[REVIEWS])
        if reverse_delta(after[REVIEWS],delta)!=before[REVIEWS]:raise ValueError('Exact two-review inverse differs')
        (ROOT/COMPATIBILITY).write_bytes(encoded(dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',
            tool_id='palette-and-allelic-reviewed-roles',scope='Exactly two appended individual reviews; all1130 previous reviews unchanged. No scientific-source overlay.',paths=[delta])))
        for name,raw in before.items():
            if (ROOT/name).read_bytes()!=raw:raise ValueError('Concurrent catalog edit: '+name)
        (out/'PREPARED.json').write_bytes(encoded(dict(before_files={n:rawpin(v) for n,v in before.items()},after_files={n:rawpin(v) for n,v in after.items()},before_archive=pin(archive))))
        sources=['tools/literature/palette_allelic_catalog/'+n for n in ['model.py','source.py','bridge.py','qualification.py','verify.py','apply.py','test_catalog.py','check_ui.cjs','.gitattributes']]
        sources += [REGISTRY,CURATION,GUIDE,'tools/literature/catalog_source_integrity.py','tools/literature/run_mrmega_catalog_checks.py','tools/literature/curate_shapeit_uses.py','tools/literature/collect_omics_review.py']
        names=[EVIDENCE+'/'+n for n in ['CENSUS.json','SOURCE_AUDIT.json','curation-intake-before.json','.gitattributes']]+[COVERAGE,COMPATIBILITY,DATA+'/imagej-catalog-validation.json']
        names += ['docs/literature/'+folder+'/'+n for folder,files in [
            ('wesanderson037-workflow-development',['VALIDATION.json','ARTIFACTS.json','SHA256SUMS','PAPER_USE.json','CONTROLS_LAYOUT.json']),
            ('allelicseries0112-development-checkpoint',['VALIDATION.json','CONTROLS_LAYOUT.json','SHA256SUMS']),
            ('allelicseries0112-batch-development',['VALIDATION.json','CONTROLS_LAYOUT.json','SHA256SUMS'])] for n in files]
        names += ['tools/literature/allelicseries0112/paper-use-intake.json']
        # Check every required admission input before changing the eight live paths.
        source_pins={n:pin(ROOT/n) for n in sources};evidence_pins={n:pin(ROOT/n) for n in names}
        for name,raw in after.items():(ROOT/name).write_bytes(raw)
        proof=dict(status='PASS_NATIVE_PALETTE_ADMISSION_AND_VERSION_SEPARATE_ALLELIC_CANDIDATE',
            before_archive=pin(archive),before_files={n:rawpin(v) for n,v in before.items()},after_files={n:pin(ROOT/n) for n in META},
            source_pins=source_pins,evidence_pins=evidence_pins,**result,admitted_tool_ids=['wesanderson'],candidate_only_tool_ids=['allelicseries'],
            added_software_names=2,scientific_sources_changed=False,scientific_verifiers_changed=False,previous_goal_turn_classification='progress')
        (ROOT/PROOF).write_bytes(encoded(proof));(out/'APPLIED.json').write_bytes(encoded(proof));print(proof['status'],flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_CATALOG_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_PALETTE_ALLELIC_CATALOG_AMENDMENT',cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION)
        if owned:(out/'FAILURE.json').write_bytes(encoded(result));(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
