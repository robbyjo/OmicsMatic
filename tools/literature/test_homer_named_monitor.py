"""Failure-focused tests for named setup process-memory monitoring."""
import unittest
from unittest.mock import patch
import install_homer_named as named


class FakeStatus:
    def __init__(self,samples,exists=True,stat=None):self.samples=iter(samples);self.present=exists;self.stat=stat;self.selected='status'
    def __truediv__(self,name):self.selected=name;return self
    def read_text(self):
        if self.selected=='stat':return self.stat or ('1 (fixture) R '+ ' '.join(['0']*21))
        value=next(self.samples)
        if isinstance(value,Exception):raise value
        return value
    def exists(self):return self.present
    name='fixture-task'


class MonitorTests(unittest.TestCase):
    def test_running_exiting_task_with_no_address_space_is_handled_narrowly(self):
        fields=['0']*22;fields[0]='R';fields[6]='4194308';stat='1 (fixture with ) names) '+' '.join(fields)
        self.assertEqual(0,named.resident(FakeStatus(['State:\tR (running)\n'],stat=stat)))
        fields[20]='4096';self.assertFalse(named.exiting_without_address_space('1 (fixture) '+' '.join(fields)))
        fields[20]='0';fields[6]='4194304';self.assertFalse(named.exiting_without_address_space('1 (fixture) '+' '.join(fields)))
    def test_exit_after_last_memoryless_read_is_not_an_unavailable_dependency(self):
        with patch.object(named.time,'sleep'):
            self.assertEqual(0,named.resident(FakeStatus(['State:\tR (running)\n']*20,False)))
    def test_transient_exec_resumes_real_rss_measurement(self):
        with patch.object(named.time,'sleep'):
            self.assertEqual(7168,named.resident(FakeStatus(['State:\tR (running)\n','State:\tS (sleeping)\nVmRSS:\t7 kB\n'])))
    def test_terminal_and_disappeared_processes_have_no_resident_memory(self):
        self.assertEqual(0,named.resident(FakeStatus(['State:\tZ (zombie)\n'])))
        self.assertEqual(0,named.resident(FakeStatus([FileNotFoundError('exited')])) )
    def test_persistent_missing_memory_and_invalid_units_fail_closed(self):
        with patch.object(named.time,'sleep'):
            with self.assertRaises(named.core.Unavailable):named.resident(FakeStatus(['State:\tS (sleeping)\n']*20))
        with self.assertRaises(named.core.Unavailable):named.resident(FakeStatus(['State:\tS (sleeping)\nVmRSS:\t7 MB\n']))
    def test_permission_error_is_not_treated_as_an_exited_process(self):
        with self.assertRaises(PermissionError):named.resident(FakeStatus([PermissionError('forbidden')]))


if __name__=='__main__':unittest.main()
