"""Verify package sequencing without running HDC, builds, or a device."""
import contextlib
import io
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('verify.py')

class ReachedRuntime(Exception):
    pass

class VerifyInstallationTests(unittest.TestCase):
    def exercise(self, fail=None, nonzero=False):
        calls = []
        with tempfile.TemporaryDirectory() as directory:
            main, test = Path(directory) / 'main.hap', Path(directory) / 'test.hap'
            main.touch(); test.touch()
            def fake_run(command, **options):
                calls.append(command)
                if 'install' in command:
                    if Path(command[-1]).name == fail:
                        if nonzero:
                            raise subprocess.CalledProcessError(1, command, output='install failed', stderr='transport error')
                        return subprocess.CompletedProcess(command, 0, stdout='install failed', stderr='')
                    return subprocess.CompletedProcess(command, 0, stdout='install bundle successfully', stderr='')
                raise ReachedRuntime()
            args = [str(SCRIPT), '--target', 'test-only', '--main-hap', str(main), '--test-hap', str(test)]
            with patch.object(sys, 'argv', args), patch('subprocess.run', side_effect=fake_run), contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(SystemExit if fail else ReachedRuntime):
                    runpy.run_path(str(SCRIPT), run_name='__main__')
        return calls

    def test_main_is_installed_before_test(self):
        calls = self.exercise()
        self.assertEqual([Path(c[-1]).name for c in calls if 'install' in c], ['main.hap', 'test.hap'])
        self.assertIn('force-stop', calls[-1])

    def test_main_failure_stops_before_test_and_runtime(self):
        calls = self.exercise('main.hap')
        self.assertEqual(len(calls), 1)

    def test_nonzero_main_failure_stops_before_test(self):
        self.assertEqual(len(self.exercise('main.hap', nonzero=True)), 1)

    def test_test_failure_stops_before_runtime(self):
        calls = self.exercise('test.hap')
        self.assertEqual(len(calls), 2)
        self.assertTrue(all('install' in c for c in calls))

if __name__ == '__main__':
    unittest.main()
