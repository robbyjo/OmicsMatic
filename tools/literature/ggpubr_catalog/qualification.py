# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute unchanged frozen evidence gates with only their exact eight historical catalog inputs."""
from pathlib import Path
import importlib.util,json,os,zipfile
from ggpubr_catalog.model import pin,rawpin,META
from ggpubr_catalog.bridge import metadata_view
WORKFLOW='docs/literature/ggpubr-workflow-development'
def qualification(root):
 data=root/WORKFLOW;proof=json.loads((data/'VALIDATION.json').read_bytes());cumulative=json.loads((data/'CUMULATIVE_VERIFICATION.json').read_bytes())
 if cumulative['base_validation']!=pin(data/'VALIDATION.json') or cumulative['source_files']!=30:raise ValueError('Qualified scientific/auxiliary source baseline differs')
 sources=cumulative['current_scientific_and_auxiliary_source_pins']
 if len(sources)!=30 or any(sources.get(n)!=wanted for n,wanted in proof['sources'].items()):raise ValueError('Incomplete current source closure')
 for n,wanted in sources.items():
  if pin(root/n)!=wanted:raise ValueError('Qualified original-workflow scientific source differs: '+n)
 with zipfile.ZipFile(data/'catalog-before.zip') as z:
  if len(z.namelist())!=8 or set(z.namelist())!=set(META):raise ValueError('Only exact eight predecessor metadata inputs may be replayed')
  before={n:z.read(n) for n in META}
 if {n:rawpin(v) for n,v in before.items()}!=proof['main_catalog_files']:raise ValueError('Frozen workflow catalog inputs differ')
 file=Path(os.fspath(root/'tools/literature/ggpubr_workflow/verify.py')).resolve();spec=importlib.util.spec_from_file_location('retained_complete_original_ggpubr_workflow',file);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
 module.ROOT=metadata_view(root,before)
 result=module.evidence(False)
 if (result['complete_archive_files'],result['complete_cases_per_edition'],result['complete_plots_per_edition'],result['complete_tables_per_edition'],result['complete_result_files_per_edition'])!=(11513,79,54,25,294):raise ValueError('Incomplete original scientific control population')
 if (result['complete_table_cells_per_edition'],result['complete_layer_cells_per_edition'],result['complete_portable_file_comparisons_per_edition'])!=(416,171084,134):raise ValueError('Incomplete original table/layer/portable-file population')
 file=Path(os.fspath(root/'tools/literature/ggpubr_workflow/verify_boundaries.py')).resolve();spec=importlib.util.spec_from_file_location('retained_ggpubr_frozen_verifier_boundaries',file);aux=importlib.util.module_from_spec(spec);spec.loader.exec_module(aux)
 # This gate reads only frozen evidence, never mutable catalog metadata. Its
 # source and every input remain live; no scientific code is overlaid.
 extra=aux.evidence();check=json.loads((data/'VERIFIER_BOUNDARIES_CHECK.json').read_bytes())
 if extra['refusal_controls']!=4 or extra['baseline_controls']!=1 or extra['additional_archive_members']!=59 or len(extra['reconstructed_negative_archives'])!=2:raise ValueError('Incomplete frozen-verifier refusal/negative-input population')
 if any(extra[k]!=check[k] for k in extra) or extra['auxiliary_source']!=cumulative['auxiliary']['auxiliary_source']:raise ValueError('Auxiliary source or exact negative-input qualification differs')
 return dict(original_workflow=result,frozen_verifier_controls=extra,preserved_catalog_inputs=8,scientific_sources_overridden=False,
  scientific_verifiers_changed=False,new_original_analysis_executed=False,new_tool_credit=0)
