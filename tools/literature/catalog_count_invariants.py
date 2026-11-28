"""Cross-check reported catalog counts against complete tool/use/review populations."""
from collections import Counter
from nfcore_review_source_audit import require

def validate(papers,inventory,summary,ledger,registry):
    statuses=Counter(tool['implementation_status']for tool in inventory)
    require(dict(statuses)==summary['implementation_status_counts'],'Implementation-status counts differ from the complete inventory')
    native=statuses['native-subset-validated'];upstream=statuses['upstream-adapter-validated'];scoped=native+upstream
    counts=summary['integration_family_counts']
    require(counts['native']==native and counts['upstream']==upstream and counts['scoped']==scoped,'Native/upstream/scoped aggregate counts disagree with the complete inventory')
    require(counts['including_existing_adapters']==scoped+statuses['existing-adapter'],'Existing-adapter total disagrees with the inventory')
    require(counts['validated_alias_labels_excluded_from_extra_credit']==statuses['validated-family-alias'],'Alias labels earned extra family credit')
    require(summary['papers']==len(papers)and summary['distinct_named_tools']==len(inventory),'Paper/named-label totals differ')
    ids=[tool['id']for tool in inventory];require(len(ids)==len(set(ids)),'Repeated named tool label')
    observations=[(p['pmcid'],t['tool_id'])for p in papers for t in p['tools']]
    reviews=[(r['pmcid'],r['tool_id'])for r in ledger['reviews']]
    require(len(observations)==len(set(observations))and len(reviews)==len(set(reviews)),'Repeated observation or review credit')
    require(set(reviews).issubset(observations),'Review lacks a paper-tool observation')
    require(summary['paper_tool_observations']==len(observations)and summary['reviewed_tool_uses']==len(reviews)and summary['pending_tool_use_reviews']==len(observations)-len(reviews),'Use/review/pending arithmetic differs')
    require(len(registry)==len({tool['id']for tool in registry}),'Repeated provider definition')
    return dict(papers=len(papers),named_labels=len(inventory),provider_definitions=len(registry),observations=len(observations),reviewed_uses=len(reviews),pending_uses=len(observations)-len(reviews),scoped_families=scoped,native=native,upstream=upstream,missing_labels=statuses['missing'],whole_goal_complete=False)
