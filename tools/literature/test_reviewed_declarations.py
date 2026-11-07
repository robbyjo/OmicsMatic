"""Reviewed declarations must not broaden ordinary Methods extraction."""
import copy,gzip,hashlib,json,unittest,xml.etree.ElementTree as ET
from pathlib import Path
import collect_omics_review as base
import collect_reviewed_omics as checked
ROOT=Path(__file__).resolve().parents[2]

class ReviewedDeclarationsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.registry=json.loads((ROOT/'tools/literature/tool-registry.json').read_bytes());cls.rows=checked.declarations();cls.sources={}
        for row in cls.rows:
            raw=gzip.decompress((ROOT/'build/literature-review/xml'/(row['pmcid']+'.xml.gz')).read_bytes());assert hashlib.sha256(raw).hexdigest()==row['xml_sha256'];cls.sources[row['paper_id']]=base.paragraphs(ET.fromstring(raw))
    def test_exact_checked_review_adds_only_named_DESeq2(self):
        for row in self.rows:
            ps=self.sources[row['paper_id']];prefs={'deseq2':row['paragraph_sha256']};old=base.tool_matches(ps,self.registry,prefs);new=checked.tool_matches(ps,self.registry,prefs)
            self.assertEqual(old,[t for t in new if t['tool_id']!='deseq2']);ds=[t for t in new if t['tool_id']=='deseq2'];self.assertEqual(len(ds),1);self.assertEqual(ds[0]['evidence_paragraph_index'],row['paragraph_index']);self.assertEqual(ds[0]['analysis_role'],row['analysis_role'])
    def test_unreviewed_and_wrong_hash_declarations_are_not_admitted(self):
        for row in self.rows:
            for preferred in [None,{'deseq2':'0'*64}]:
                self.assertEqual(checked.tool_matches(self.sources[row['paper_id']],self.registry,preferred),base.tool_matches(self.sources[row['paper_id']],self.registry,preferred))
    def test_changed_section_body_and_index_are_rejected(self):
        for row in self.rows:
            for key,value in [('section','Associated Data'),('text','DESeq2 was used.'),('paragraph_index',999999)]:
                ps=copy.deepcopy(self.sources[row['paper_id']]);next(p for p in ps if p['paragraph_index']==row['paragraph_index'])[key]=value
                with self.assertRaises(ValueError):checked.tool_matches(ps,self.registry,{'deseq2':row['paragraph_sha256']})
    def test_frozen_collector_globals_are_unchanged(self):
        self.assertIs(base.process.__globals__['tool_matches'],base.tool_matches);self.assertIsNot(checked.process.__globals__,base.process.__globals__);self.assertIs(checked.process.__globals__['tool_matches'],checked.tool_matches)

if __name__=='__main__':unittest.main()
