# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Adversarial checks against complete actual reference controls, including coherent changed file hashes and invalid admission states."""
import argparse,copy,csv,io,json,sys,unittest
from pathlib import Path
from signac_scan_evidence import byte_pin
from cellphonedb_preparation_evidence import RetainedPath
from validate_cellphonedb_preparation import source_tables,verify_ledgers


class ReferenceAudit(unittest.TestCase):
    files={};request={};ledgers={}
    @staticmethod
    def obj(files,name):return json.loads(files[name])
    @staticmethod
    def put(files,name,value):files[name]=(json.dumps(value,indent=2)+'\n').encode()
    def view(self,files):return RetainedPath(files,'original')
    def update_case(self,files,record):
        self.put(files,'original/'+record['id']+'/CASE-COMPLETED.json',record);result=self.obj(files,'original/original-results.json');result['cases']=[record if row['id']==record['id'] else row for row in result['cases']];self.put(files,'original/original-results.json',result)
    def test_01_complete_baseline(self):
        request,expected,checks=source_tables(self.view(self.files));self.assertEqual(15,checks['prepared_cases']);self.assertEqual(15,len(expected['cases']));self.assertEqual(104,verify_ledgers(request,self.ledgers)['cast_changes'])
    def test_02_coherent_file_hash_change_still_rejects_wrong_P_value(self):
        files=dict(self.files);name='statistical-original100-permutations';record=self.obj(files,'original/'+name+'/CASE-COMPLETED.json');table=record['returned_tables']['pvalues'];path='original/'+name+'/original-output/'+table['file'];rows=list(csv.reader(io.StringIO(files[path].decode('utf8')),delimiter='\t'));column=rows[0].index('A|A');rows[1][column]='0.875' if rows[1][column]!='0.875' else '0.625';buffer=io.StringIO(newline='');csv.writer(buffer,delimiter='\t',lineterminator='\n').writerows(rows);files[path]=buffer.getvalue().encode();table['pin']=byte_pin(files[path]);record['files'][table['file']]=table['pin'];self.update_case(files,record)
        with self.assertRaises(AssertionError):source_tables(self.view(files))
    def test_03_original_class_sentinel_is_not_an_empty_frame(self):
        files=dict(self.files);name='original/statistical-original100-permutations/original-returned.json';returned=self.obj(files,name);returned['CellSign_active_interactions']={'$dataframe':dict(values=[],columns=[],dtypes=[])};self.put(files,name,returned)
        with self.assertRaises(AssertionError):source_tables(self.view(files))
    def test_04_compensated_mean_cannot_be_replaced_with_sequential_mean(self):
        files=dict(self.files);name='original/simple-duplicate-raw-gene-rows-compensated-mean/preparation.json';preparation=self.obj(files,name);position=preparation['mapped_features'].index(102);preparation['mapped_expression'][position]=[5592405.5]*len(preparation['cells']);self.put(files,name,preparation)
        with self.assertRaises(AssertionError):source_tables(self.view(files))
    def test_05_original_cell_order_cannot_be_reversed(self):
        files=dict(self.files);name='original/simple-Unicode-cell-axis-order/preparation.json';preparation=self.obj(files,name);preparation['cells'].reverse();self.put(files,name,preparation)
        with self.assertRaises(AssertionError):source_tables(self.view(files))
    def test_06_float_cast_ledger_cannot_omit_a_changed_cell(self):
        ledgers=copy.deepcopy(self.ledgers);ledgers['simple-fractional-float32-casting-ledger']['casting_ledger'].pop()
        with self.assertRaises(AssertionError):verify_ledgers(self.request,ledgers)
    def test_07_reference_execution_does_not_admit_a_public_result(self):
        files=dict(self.files);record=self.obj(files,'original/simple-raw-gene-mapping-and-duplicate-interaction-annotations/CASE-COMPLETED.json');record['scientific_result_admitted']=True;self.update_case(files,record)
        with self.assertRaises(AssertionError):source_tables(self.view(files))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact scientific comparison tests')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--reference',required=True,type=Path);parser.add_argument('--validation',required=True,type=Path);args=parser.parse_args()
    for path in args.reference.rglob('*'):
        relative=path.relative_to(args.reference)
        if relative.parts[0].startswith('private-'):continue
        if path.is_file():ReferenceAudit.files['original/'+relative.as_posix()]=path.read_bytes()
    ReferenceAudit.request=json.loads((args.validation/'cases.json').read_bytes());ReferenceAudit.ledgers=json.loads((args.validation/'windows21/ledgers.json').read_bytes());unittest.main(argv=[sys.argv[0]])
