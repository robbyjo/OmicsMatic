#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a complete six-image synthetic request for the original QuPath0.5.1 adapter."""
from pathlib import Path
import argparse,json
from make_fixtures import create
HERE=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();provider=a.provider.resolve()
 for protected in [HERE,provider]:
  if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Example output overlaps source/provider')
 out.mkdir(parents=True,exist_ok=False);settings=create(out/'fixture-inputs')
 outputs={'complete-summary.json':{'minimum_bytes':1}}
 for row in settings['jobs']:
  for suffix in ['.raw.qpdata','.qpdata','.measurements.tsv','.objects.geojson']:outputs[row['id']+suffix]={'minimum_bytes':0}
 request=dict(schema_version=1,version='0.5.1',mode='SCRIPT',script=str(HERE/'OriginalControl.groovy'),provider_directory=str(provider),
  inputs={p.name:str(p) for p in sorted((out/'fixture-inputs').iterdir())},required_classes=['qupath.imagej.detect.cells.PositiveCellDetection','qupath.lib.images.ImageData','com.google.gson.Gson'],
  expected_outputs=outputs,input_provenance='Explicit synthetic RGB images and H-DAB stain/ROI/parameter controls; no author images or settings recovered',
  analysis_population='All six synthetic jobs including the blank image, with72 original detections in the retained reference',
  expected_return='QUPATH051_COMPLETE_SYNTHETIC_CELL_POPULATION',execution_policy='ALLOW',trusted_code_policy='ALLOW',out=str(out/'analysis'))
 (out/'request.json').write_text(json.dumps(request,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n');print('Prepared synthetic request: '+str(out/'request.json'),flush=True)
if __name__=='__main__':main()
