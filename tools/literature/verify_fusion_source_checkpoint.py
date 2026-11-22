"""Check the source-only FUSION checkpoint; never admit a provider or edit the catalog."""
from pathlib import Path
import argparse
import json
import sys
from fusion_catalog.primary import ROOT, audit, pin, require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, help='Optional exact private XML cache for fresh all-250 inspection')
    args = parser.parse_args()
    try:
        folder = ROOT / 'docs/literature/fusion-source-development'
        status = json.loads((folder / 'STATUS.json').read_bytes())
        raw = (folder / 'CENSUS.json').read_bytes()
        require(pin(raw) == status['census'], 'Changed source census')
        for name, identity in status['sources'].items():
            require(pin((ROOT / name).read_bytes()) == identity, 'Changed source-audit program: ' + name)
        census = json.loads(raw)
        require(census['status'] == 'PASS_FULL250_FUSION_SOURCE_CANDIDATE_AND_HOMONYM_AUDIT_ONLY' and
                census['papers_checked'] == len(census['identities']) == 250 and len(census['candidates']) == 10 and
                len(census['actual_software_analysis_or_comparator_papers']) == 6,
                'Changed audit scope')
        require(census['canonical_false_positive_to_review'] == 'P212' and
                census['canonical_missing_baseline_to_review'] == 'P108' and
                census['original_upstream_execution_in_P108_unconfirmed'], 'Lost homonym/uncertain-execution distinction')
        require(census['new_family_credit'] == census['canonical_reviews_added'] == 0 and
                census['canonical_catalog_modified'] is False and census['whole_goal_complete'] is False,
                'Source-only audit cannot grant credit')
        if args.cache is not None:
            require(audit(args.cache) == census, 'Fresh all-250 primary census differs')
        print(json.dumps(dict(status='PASS_FUSION_SOURCE_ONLY_CHECKPOINT', fresh_primary_XML_checked=250 if args.cache else 0,
                              candidate_papers=10, software_analysis_or_comparator_papers=6,
                              additional_method_baseline_execution_unconfirmed=True, new_family_credit=0,
                              canonical_reviews_added=0, whole_goal_complete=False)))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        print(('SKIP3: restore exact permitted source prerequisite: ' if code == 3 else 'FAIL2: ') + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
