# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Trusted complete original example; retains NaN padding and reports project review conditions."""
import json,logging,sys
sys.path.insert(0,str(input_directory/'driver'))
import OriginalControl
options=json.loads((input_directory/'options.json').read_bytes());expected=json.loads((input_directory/'expected-example.json').read_bytes())
if options['version']!=selected_original_version or original_scvelo.__version__!=selected_original_version:raise ValueError('Literal requested original edition differs; no substitution')
previous_argv=sys.argv[:]
try:
 sys.argv=[str(input_directory/'driver/OriginalControl.py'),'--input',str(input_directory/'kinetics.h5ad'),'--out',str(result_directory/'workflow'),'--version',options['version'],'--mode',options['mode']]
 code=OriginalControl.main()
finally:sys.argv=previous_argv
if code!=0:raise ValueError('Original complete example failed; preserve every raw object, stage, condition and partial file')
graph=json.loads((result_directory/'workflow/complete-stages/COMPLETE-STAGES.json').read_bytes())
if graph['snapshot_roots']!=expected['stage_roots'] or sum(r['kind']=='array' for r in graph['records'])!=expected['array_count'] or graph['opaque_values']!=expected['opaque_values']:raise ValueError('Complete original stage/array population differs')
if graph['nonfinite_arrays']!=expected['expected_nonfinite_array_snapshots']:raise ValueError('Original nonfinite values differ from the full fixed example; no repair or removal admitted')
review=dict(scope='Project example retention review, distinct from original upstream conditions',nonfinite_array_snapshots=graph['nonfinite_arrays'],nonfinite_values_repaired=False,original_loss_padding_preserved=True,convergence_established=False,paper_reproduced=False)
(diagnostic_directory/'example-retention-review.json').write_text(json.dumps(review,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
if graph['nonfinite_arrays']:
 logging.getLogger('omicsmatic.scvelo.example').warning('Project retention review: %s original nonfinite array snapshots retained, including padded loss histories. Inspect all original states; no convergence or biological-validity claim.',len(graph['nonfinite_arrays']))
analysis_complete='COMPLETE_ORIGINAL_SCVELO_EXAMPLE'
