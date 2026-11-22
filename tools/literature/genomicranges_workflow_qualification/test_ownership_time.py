"""Exact time ordering for the independent saved-capture audit;no OS processes."""
import unittest
from genomicranges_workflow_qualification.verify_checkpoint import instant_nanoseconds, verify_owned_manifest


def manifest(root, child):
    return dict(worker_pid=1, owned_processes=[dict(pid=1, start=root, same_identity_alive=False),
                                            dict(pid=2, start=child, same_identity_alive=False)])


class OwnershipTimeTests(unittest.TestCase):
    def test_fractional_child_after_whole_second_root_is_valid(self):
        verify_owned_manifest(manifest('2026-11-22T00:00:00Z', '2026-11-22T00:00:00.001Z'))

    def test_older_whole_second_child_before_fractional_root_is_refused(self):
        with self.assertRaises(ValueError):
            verify_owned_manifest(manifest('2026-11-22T00:00:00.001Z', '2026-11-22T00:00:00Z'))

    def test_nanoseconds_are_not_rounded_to_microseconds(self):
        a = '2026-11-22T00:00:00.000000001Z'
        b = '2026-11-22T00:00:00.000000002Z'
        self.assertEqual(1, instant_nanoseconds(b) - instant_nanoseconds(a))
        with self.assertRaises(ValueError):
            verify_owned_manifest(manifest(b, a))

    def test_second_boundary_orders_correctly(self):
        self.assertEqual(1, instant_nanoseconds('2026-11-22T00:00:01Z')
                         - instant_nanoseconds('2026-11-22T00:00:00.999999999Z'))

    def test_nonboolean_liveness_or_cleanup_error_is_refused(self):
        for mutate in [lambda j: j['owned_processes'][1].update(same_identity_alive='false'),
                       lambda j: j.update(cleanup_error='')]:
            data = manifest('2026-11-22T00:00:00Z', '2026-11-22T00:00:01Z')
            mutate(data)
            with self.assertRaises(ValueError):
                verify_owned_manifest(data)

    def test_missing_or_unsupported_identity_is_refused(self):
        for value in [None, '', '2026-11-22T00:00:00', '2026-11-22T00:00:00.1234567890Z']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                instant_nanoseconds(value)


if __name__ == '__main__':
    unittest.main()
