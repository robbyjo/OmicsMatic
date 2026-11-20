"""Preserve literal Scanpy paper roles and distinguish reusable components from author parity."""
import copy
from scanpy_coverage import build as frozen_crosswalk, encoded, require

NATIVE = {
    'preprocessing': dict(source='src/main/java/org/jlinalg/singlecell/ScanpyPreprocessing.java',
        profiles=['1.9.3', '1.13.0'], representation='explicit float64 dense/CSR',
        operations=['filter_cells', 'filter_genes', 'normalize_total', 'log1p', 'scale'],
        qualification='../scanpy-preprocessing-checks/QUALIFICATION.json'),
    'pearson-matrix': dict(source='src/main/java/org/jlinalg/singlecell/ScanpyPearsonResiduals.java',
        profiles=['1.10.4', '1.13.0'], representation='explicit float64 dense/CSR',
        operations=['analytic Pearson residual matrix; not HVG selection or SCT fitting'],
        qualification='../scanpy-pearson-checks/QUALIFICATION.json'),
    'dispersion-hvg': dict(source='src/main/java/org/jlinalg/singlecell/ScanpyHighlyVariableGenes.java',
        profiles=['1.9.3', '1.13.0'], representation='explicit float64 dense/CSR; optional explicit batches',
        operations=['seurat', 'cell_ranger'], qualification='../scanpy-hvg-checks/QUALIFICATION.json'),
}


def build(census):
    result = frozen_crosswalk(census)
    for role in result['roles']:
        pid = role['paper_id']
        require(all(role[k] is False for k in ['author_runtime_executed', 'exact_author_settings_established',
            'whole_paper_reproduced', 'full_reported_role_qualified', 'complete_author_workflow_qualified',
            'historical_package_parity']), 'Source review does not prove author runtime or historical parity')
        overlaps = []
        ops = [n.removeprefix('pp.') for n in role['current_1130_tested_function_counterparts']
               if n in ['pp.normalize_total', 'pp.log1p', 'pp.scale']]
        if ops:
            overlaps.append(dict(component='preprocessing', operations=ops,
                relationship='Related explicit matrix operations; current counterparts are not author-function attribution.',
                historical_author_profile_qualified=False))
        if pid == 'P062':
            overlaps.append(dict(component='pearson-matrix', operations=['analytic Pearson residual matrix'],
                relationship='Matrix transformation only; experimental Pearson HVGs and SysVI remain separate.',
                historical_author_profile_qualified=False))
        if pid == 'P021':
            overlaps.append(dict(component='dispersion-hvg', operations=['cell_ranger'],
                relationship='The reported flavor has a native counterpart; author 1.9.1/custom-wrapper precedence is not qualified.',
                historical_author_profile_qualified=False))
            role['remaining_author_roles'].append('Native dispersion HVGs qualify explicit 1.9.3/1.13.0 profiles, not this historical/custom wrapper.')
        if pid in ['P062', 'P093']:
            role['remaining_author_roles'].append('Native dispersion HVGs do not implement the reported Pearson/seurat_v3 HVG branch.')
        role.update(qualified_native_component_overlap=overlaps,
            native_components_are_explicit_not_automatic_fallback=True,
            review_disposition='existing-review-preserved' if pid == 'P061' else 'new-source-review',
            catalog_review_appended=(pid != 'P061'))
    result.update(status='PASS_SCANPY_22_LITERAL_SOURCE_ROLES_AND_EXPLICIT_QUALIFIED_SCOPES',
        available_native_components=copy.deepcopy(NATIVE), current_upstream_profile='Scanpy-1.13.0-CPU-v1',
        qualified_upstream_operations=15, development_allowlisted_operations=29,
        source_role_counts={label:sum(r['role'] == label for r in result['roles']) for label in sorted({r['role'] for r in result['roles']})},
        review_records_to_append=21, existing_review_records_to_preserve=1462,
        native_family_credit_added=0, upstream_family_credit_added=1,
        new_family_credit=1, full_author_workflows_reproduced=0)
    return result
