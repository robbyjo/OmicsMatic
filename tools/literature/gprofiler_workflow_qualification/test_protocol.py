"""Real captured populations plus independently altered protocol/result controls."""
import copy
import json
import unittest
from gprofiler_workflow_qualification.verify_checkpoint import packet, audit_bundle


class ProtocolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.files = packet()
        cls.base = 'publication/live-packaged-3/highlight/run'

    def changed(self, filename, mutate, message, combined=False):
        files = dict(self.files)
        base = 'publication/live-packaged-3/combined-fdr/run' if combined else self.base
        key = base + '/' + filename
        value = json.loads(files[key])
        mutate(value)
        files[key] = json.dumps(value).encode()
        if filename == 'result.json':
            files[base + '/wire/002-response.body'] = files[key]
            files[base + '/results.jsonl'] = b''.join((json.dumps(v) + '\n').encode() for v in value['result'])
        with self.assertRaisesRegex(ValueError, message):
            audit_bundle(files, base)

    def test_actual_public_protocols_cover_all_modes(self):
        checks = json.loads(self.files['publication-audit/RESULT.json'])['checks']
        reports = [audit_bundle(self.files, 'publication/' + row['root']) for row in checks if row['exit_code'] == 0]
        self.assertEqual(21, len(reports))
        self.assertEqual({'RUN', 'REPLAY'}, {r['mode'] for r in reports})
        self.assertEqual({'metadata', 'enrichment'}, {r['operation'] for r in reports})

    def test_changed_source_request(self):
        self.changed('source-request.json', lambda j: j['payload'].update(query=['OTHER']), 'Portable source')

    def test_replaced_service_origin(self):
        self.changed('wire/001-exchange.json', lambda j: j.update(url='https://other.invalid/api/util/data_versions'), 'service route')

    def test_changed_wire_resource_limit(self):
        self.changed('wire/001-exchange.json', lambda j: j.update(timeout_nanos=1), 'wire contract')

    def test_changed_recorded_provenance(self):
        self.changed('execution.json', lambda j: j.update(network_executed=False), 'execution/origin')

    def test_impossible_probability_even_with_aligned_lossless_exports(self):
        self.changed('result.json', lambda j: j['result'][0].update(p_value=2), 'probability')

    def test_impossible_intersection_even_with_aligned_lossless_exports(self):
        self.changed('result.json', lambda j: j['result'][0].update(intersection_size=1000000000), 'population count')

    def test_inconsistent_precision(self):
        self.changed('result.json', lambda j: j['result'][0].update(precision=0), 'ratio inconsistent')

    def test_one_sided_combined_sentinel_is_refused(self):
        self.changed('result.json', lambda j: j['result'][0]['query_sizes'].__setitem__(1, 0), 'absence sentinel', combined=True)

    def test_missing_requested_go_driver_flag(self):
        self.changed('result.json', lambda j: j['result'][0].pop('highlighted'), 'GO drivers missing')

    def test_changed_derived_mapping(self):
        self.changed('gene-mapping.json', lambda j: j.update(failed=['silently_changed']), 'gene mapping')

    def test_changed_derived_counts(self):
        self.changed('summary.json', lambda j: j.update(reported_terms=0), 'result counts')


if __name__ == '__main__':
    unittest.main()
