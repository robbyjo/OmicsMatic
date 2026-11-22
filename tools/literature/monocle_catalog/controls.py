"""Run model/transaction tests and eight explicit source false-admission controls."""
from pathlib import Path
import argparse,copy,io,json,sys,unittest
from monocle_catalog import primary as p

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();args.out.mkdir(parents=True,exist_ok=False)
    suite=unittest.defaultTestLoader.loadTestsFromNames(['monocle_catalog.test_model','monocle_catalog.test_transaction']);stream=io.StringIO();result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite);(args.out/'unittest.txt').write_text(stream.getvalue(),encoding='utf-8',newline='\n');p.require(result.wasSuccessful() and result.testsRun==41,'Model/transaction controls failed;read unittest.txt')
    source=p.frozen();cases={}
    mutations={
        'whole-paper':lambda row:row['roles'][0].update(whole_paper_reproduced=True),
        'author-execution':lambda row:row['roles'][0].update(author_pipeline_executed=True),
        'unreported-edition':lambda row:row['roles'][1].update(reported_edition=dict(literal='1.0.0')),
        'wrong-provider':lambda row:row['roles'][1].update(qualified_component_commands=['RUN']),
        'new-reviews':lambda row:row.update(new_review_records=9),
        'source-only-credit':lambda row:row.update(new_family_credit=1),
        'missing-paper':lambda row:row.update(papers_scanned=249),
        'changed-identity':lambda row:row['roles'][0].update(doi='10.0000/changed'),
    }
    for name,mutate in mutations.items():
        changed=copy.deepcopy(source);mutate(changed)
        try:p.validate(changed)
        except ValueError as error:cases[name]=dict(refused=True,message=str(error))
        else:raise ValueError('False source admission accepted: '+name)
    output=dict(status='PASS_MONOCLE_MODEL_TRANSACTION_AND_SOURCE_CONTROLS',tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),source_refusals=len(cases),source_controls=cases,scientific_models_run=0,canonical_metadata_modified=False);(args.out/'RESULT.json').write_bytes(p.encoded(output));print('PASS41 model/transaction tests and8 source false-admission refusals');return 0

if __name__=='__main__':raise SystemExit(main())
