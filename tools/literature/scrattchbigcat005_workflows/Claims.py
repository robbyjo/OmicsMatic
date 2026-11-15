# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Admit compared scientific payloads without claiming live R-object identity."""

def opaque_labels(operation):
    if operation=='plot_constellation':
        return {'complete_original_return','complete_plot/member/constellation'}
    if operation=='constellation_from_embedding':
        return {'complete_original_graph','complete_original_plot','complete_index_slot/path',
          'complete_plot/member/constellation','original_call/graph/value','original_call/plot/value',
          'original_plot_call/member/constellation'}
    if operation=='impute_cross_big':return {'complete_original_return'}
    return set()

def check_report(report,operation,returned,expected_keys=None):
    checks=report['checks']
    actual={name for name,row in checks.items() if 'all_equal' in row}
    if report['comparison_count']!=len(actual):raise ValueError('Comparison population disagrees with complete report')
    if expected_keys is not None and actual!=set(expected_keys):raise ValueError('Qualified comparison field population changed')
    required={'original_returned','complete_error','complete_R_conditions'}
    if returned:required.add('complete_RNG')
    if operation in ['plot_constellation','constellation_from_embedding'] and returned:
        required.update({'complete_plot/member_names','complete_plot/member/edges.df','complete_plot/member/nodes.df',
          'complete_plot/layer_data/constellation','complete_plot/panel_parameters/constellation',
          'complete_plot/complete_derived_PNG_SHA256/constellation'})
    if operation=='constellation_from_embedding' and returned:
        required.update({'graph_array/knn.index','graph_array/knn.distance','complete_cluster_graph',
          'complete_outlier_ids','complete_selected_edges','assembled/complete_original_graph',
          'complete_copied_Annoy_payload_SHA256','original_plot_call/member/edges.df',
          'original_plot_call/member/nodes.df','original_plot_call/complete_derived_PNG_SHA256/constellation'})
    if operation=='impute_cross_big' and returned:
        required.update({'complete_return_member_names','complete_FBM_class_definition',
          'complete_FBM_metadata','complete_FBM_payload_SHA256'})
    if not required<=actual:raise ValueError('Required complete scientific checks missing: '+str(sorted(required-actual)))
    differences={name for name in actual if checks[name]['all_equal'] is not True}
    unexpected=differences-opaque_labels(operation)
    if unexpected:raise ValueError('Scientific comparison failed: '+str(sorted(unexpected)))
    if operation=='impute_cross_big' and 'complete_original_return' in differences:
        message=checks['complete_original_return']['complete_all_equal_return']
        if message!='Component “fbm”: Class definitions are not identical':
            raise ValueError('Unreviewed complete FBM return difference: '+str(message))
    return dict(compared_fields=len(actual),retained_opaque_differences=sorted(differences),
      scientific_fields_agree=True,live_R_object_identity_claimed=False)
