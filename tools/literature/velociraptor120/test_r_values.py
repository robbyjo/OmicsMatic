# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check real original SCE captures, literal/missing IDs, NaN bits and complete-tree refusals."""
from pathlib import Path
import argparse,hashlib,json,shutil,struct,sys,unittest
from RValues import load_tree,compare_trees

PARSER=argparse.ArgumentParser(description=__doc__)
PARSER.add_argument('--evidence',type=Path,required=True)
PARSER.add_argument('--out',type=Path,required=True)

def chars(tree,identifier):
    node=tree['index']['nodes'][identifier-1];assert node['type']=='character'
    data=(tree['root']/node['payload']['path']).read_bytes();offset=0;values=[]
    for _ in range(node['length']):
        flag=data[offset];offset+=1
        if flag==0:values.append(None);continue
        assert flag==1
        size=struct.unpack_from('<i',data,offset)[0];offset+=4;encoding=data[offset:offset+size];offset+=size
        size=struct.unpack_from('<i',data,offset)[0];offset+=4;value=data[offset:offset+size];offset+=size
        values.append((encoding,value))
    assert offset==len(data)
    return values

class RValuesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.evidence=ARGS.evidence.resolve();cls.out=ARGS.out.resolve();cls.out.mkdir(parents=True,exist_ok=False)
        expected=json.loads((cls.evidence/'SOURCE-PIN.json').read_bytes())
        assert hashlib.sha256(Path(__file__).with_name('RValues.R').read_bytes()).hexdigest()==expected['sha256']
        assert json.loads((cls.evidence/'R-COMPLETED.json').read_bytes())['status']=='PASS_COMPLETE_ORIGINAL_R_VALUES_AND_REFUSAL_CONTROLS'
        cls.ordinary=load_tree(cls.evidence/'ordinary-values')
        root=cls.ordinary['index']['nodes'][cls.ordinary['index']['root']-1]
        names=[value[1].decode('utf-8') for value in chars(cls.ordinary,root['element_names'])]
        cls.fields=dict(zip(names,root['children']))
    def fixture(self,label):
        path=self.out/label;shutil.copytree(self.evidence/'ordinary-values',path);return path
    def node(self,name):return self.ordinary['index']['nodes'][self.fields[name]-1]
    def test_literal_NA_missing_and_greek_identifiers_stay_distinct(self):
        values=chars(self.ordinary,self.fields['ids'])
        self.assertEqual([b'NA',None,'β'.encode(),b'a\tb',b'a\nb'],[None if v is None else v[1] for v in values])
    def test_missing_nan_custom_nan_and_signed_zero_bits(self):
        node=self.node('numbers');data=(self.ordinary['root']/node['payload']['path']).read_bytes()
        bits=struct.unpack('<8Q',data)
        self.assertNotEqual(bits[0],bits[1]);self.assertEqual(bits[2],0x7ff0000000000000);self.assertEqual(bits[3],0xfff0000000000000)
        self.assertEqual(bits[4],0x8000000000000000);self.assertEqual(bits[5],0);self.assertEqual(bits[7],0x7ff8000000000001)
        self.assertEqual(struct.unpack_from('<d',data,48)[0],1/3)
    def test_integer_and_logical_missing_payloads(self):
        for name,expected in [('integer',(-2147483648,-1,0,2147483647)),('logical',(-2147483648,1,0))]:
            node=self.node(name);data=(self.ordinary['root']/node['payload']['path']).read_bytes()
            self.assertEqual(expected,struct.unpack('<'+'i'*len(expected),data))
    def test_named_call_tags_and_symbol_are_preserved(self):
        node=self.node('named.call');self.assertEqual('language',node['type'])
        self.assertEqual([b'',b'alpha',b'beta'],[value[1] for value in chars(self.ordinary,node['element_names'])])
        symbol=self.ordinary['index']['nodes'][node['children'][0]-1]
        self.assertEqual('symbol',symbol['type']);self.assertEqual(b'f',chars(self.ordinary,symbol['symbol_name'])[0][1])
    def test_custom_list_was_not_coerced_by_its_as_list_method(self):
        node=self.node('custom.list');self.assertEqual('list',node['type']);self.assertEqual(2,len(node['children']))
        self.assertEqual(b'NA',chars(self.ordinary,node['children'][0])[0][1])
    def test_complete_original_dynamical_and_stochastic_SCE_trees(self):
        for name in ['dynamical','stochastic']:
            tree=load_tree(self.evidence/(name+'-tree'))
            self.assertTrue(tree['index']['nodes'][tree['index']['root']-1]['S4'])
            result=compare_trees(tree['root'],tree['root'])
            self.assertGreater(result['nodes'],100);self.assertGreater(result['atomic_payload_bytes'],100000)
    def test_source_RDS_and_graph_unsupported_or_capacity_failures_refused(self):
        for name in ['refused-node-capacity','refused-byte-capacity','refused-environment']:
            self.assertTrue((self.evidence/name/'original.rds').is_file())
            with self.assertRaises(ValueError):load_tree(self.evidence/name)
    def test_any_missing_payload_refused(self):
        path=self.fixture('missing-payload');node=self.node('numbers');(path/node['payload']['path']).rename(path/'retained-original-payload.bin')
        with self.assertRaises(ValueError):load_tree(path)
    def test_extra_original_bytes_cannot_be_silently_omitted(self):
        path=self.fixture('extra-file');(path/'unindexed.bin').write_bytes(b'original-extra')
        with self.assertRaises(ValueError):load_tree(path)
    def test_duplicate_index_keys_refused(self):
        path=self.fixture('duplicate-key');raw=(path/'INDEX.json').read_bytes();self.assertEqual(b'{',raw[:1]);(path/'INDEX.json').write_bytes(b'{"schema":"duplicate",'+raw[1:])
        with self.assertRaisesRegex(ValueError,'Duplicate'):load_tree(path)
    def test_traversing_payload_path_refused_before_file_access(self):
        path=self.fixture('traversal');index=json.loads((path/'INDEX.json').read_bytes())
        next(node for node in index['nodes'] if node['payload'])['payload']['path']='../outside.bin'
        (path/'INDEX.json').write_text(json.dumps(index))
        with self.assertRaises(ValueError):load_tree(path)
    def test_finite_value_change_rejected_by_complete_comparison(self):
        path=self.fixture('changed-finite-value');node=self.node('numbers');payload=path/node['payload']['path'];data=bytearray(payload.read_bytes());struct.pack_into('<d',data,48,0.5);payload.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'atomic values'):compare_trees(self.evidence/'ordinary-values',path)
    def test_NaN_payload_difference_rejected_even_when_both_are_NaN(self):
        path=self.fixture('changed-NaN-payload');node=self.node('numbers');payload=path/node['payload']['path'];data=bytearray(payload.read_bytes());struct.pack_into('<Q',data,56,0x7ff8000000000002);payload.write_bytes(data)
        with self.assertRaisesRegex(ValueError,'atomic values'):compare_trees(self.evidence/'ordinary-values',path)

if __name__=='__main__':
    ARGS=PARSER.parse_args()
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(RValuesTests))
    if not result.wasSuccessful():raise SystemExit(2)
    report=dict(status='PASS_COMPLETE_R_VALUE_TREES_BIT_FIDELITY_AND_REFUSALS',tests=result.testsRun,new_tool_credit=0,whole_goal_complete=False,source_RDS_and_partial_unit_fixtures_retained=True)
    (ARGS.out/'COMPLETED.json').write_text(json.dumps(report,indent=2)+'\n')
