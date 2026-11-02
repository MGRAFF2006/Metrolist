"""Cheap contract checks; no Android builds, network or runner allocation."""
import copy
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import unittest

from select_gms import select
from check_ci_results import failures


class Selection(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)
        self.git('init', '-q')
        self.write('app/src/main/res/values-de/strings.xml')
        self.write('fastlane/existing')
        self.write('app/source.kt')
        self.base = self.commit()

    def git(self, *args):
        env = dict(os.environ, GIT_AUTHOR_NAME='CI fixture', GIT_COMMITTER_NAME='CI fixture',
                   GIT_AUTHOR_EMAIL='fixture@example.invalid', GIT_COMMITTER_EMAIL='fixture@example.invalid')
        return subprocess.check_output(['git', *args], cwd=self.repo, env=env,
                                       stderr=subprocess.PIPE).decode().strip()

    def write(self, name, text='fixture'):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)

    def commit(self):
        self.git('add', '-A')
        self.git('commit', '-qm', 'fixture')
        return self.git('rev-parse', 'HEAD')

    def selected(self, base=None, head=None, event='pull_request'):
        return select(event, {'pull_request': {'base': {'sha': base or self.base},
                                              'head': {'sha': head or self.git('rev-parse', 'HEAD')}}}, self.repo)[0]

    def test_localization_and_fastlane_only(self):
        self.write('app/src/main/res/values-de/strings.xml', 'translated')
        self.write('app/src/main/res/values-zh-rCN/metrolist_strings.xml')
        self.write('fastlane/a\nname\twith spaces')
        self.commit()
        self.assertFalse(self.selected())

    def test_unknown_default_strings_shared_ci_and_nul_paths(self):
        for name in ['app/src/main/res/values/strings.xml', 'unknown', '.github/workflows/checks.yml',
                     'fastlane-lookalike/file', 'app/src/main/res/values-de/nested/strings.xml',
                     'unknown\nfastlane/fake']:
            with self.subTest(name=name):
                self.write(name)
                self.commit()
                self.assertTrue(self.selected())

    def test_delete_and_rename_both_sides(self):
        (self.repo / 'app/source.kt').rename(self.repo / 'fastlane/source.kt')
        self.commit()
        self.assertTrue(self.selected())
        self.base = self.git('rev-parse', 'HEAD')
        (self.repo / 'fastlane/source.kt').rename(self.repo / 'app/new.kt')
        self.commit()
        self.assertTrue(self.selected())
        self.base = self.git('rev-parse', 'HEAD')
        (self.repo / 'app/new.kt').unlink()
        self.commit()
        self.assertTrue(self.selected())

    def test_ignored_deletion(self):
        (self.repo / 'fastlane/existing').unlink()
        self.commit()
        self.assertFalse(self.selected())

    def test_three_dot_ignores_base_only_changes(self):
        self.git('checkout', '-qb', 'pr')
        self.write('fastlane/pr')
        head = self.commit()
        self.git('checkout', '-qb', 'advanced-base', self.base)
        self.write('base-only.kt')
        base = self.commit()
        self.assertFalse(self.selected(base=base, head=head))

    def test_multicommit_pr_does_not_use_head_parent(self):
        self.write('app/source.kt', 'changed')
        self.commit()
        self.write('fastlane/last-commit')
        self.commit()
        self.assertTrue(self.selected())

    def test_missing_zero_malformed_and_empty_diff_run_gms(self):
        self.assertTrue(self.selected())
        for base in ['0' * 40, 'f' * 40, '--bad']:
            self.assertTrue(self.selected(base=base))
        self.assertTrue(select('pull_request', {}, self.repo)[0])
        # Missing git/repository information also fails open to running GMS.
        self.assertTrue(select('pull_request', {'pull_request': {'base': {'sha': self.base},
                             'head': {'sha': self.base}}}, self.repo / 'absent')[0])

    def test_push_and_manual_are_foss_only(self):
        for event in ['push', 'workflow_dispatch']:
            self.assertFalse(self.selected(event=event))

    def test_summary_failure_can_leave_an_exclusion_output(self):
        self.write('fastlane/changed')
        head = self.commit()
        event = self.repo / 'event.json'
        event.write_text(json.dumps({'pull_request': {'base': {'sha': self.base},
                                                      'head': {'sha': head}}}))
        output = self.repo / 'output'
        env = dict(os.environ, GITHUB_EVENT_NAME='pull_request', GITHUB_EVENT_PATH=str(event),
                   GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(self.repo / 'absent' / 'summary'))
        result = subprocess.run([sys.executable, str(Path(__file__).with_name('select_gms.py'))],
                                cwd=self.repo, env=env, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('gms=false', output.read_text())


class Outcomes(unittest.TestCase):
    def setUp(self):
        self.steps = {name: {'outcome': 'success'} for name in
                      ['checkout', 'select', 'java', 'android', 'sdk', 'gradle', 'executable',
                       'foss_key', 'foss', 'foss_reports', 'pr_keystore', 'gms_key',
                       'save_key', 'gms', 'gms_apk']}
        self.steps['pr_keystore']['outputs'] = {'cache-hit': 'false'}

    def test_required_failure_or_accidental_skip_cannot_pass(self):
        self.assertEqual([], failures(self.steps, True))
        for name in self.steps:
            for outcome in ['failure', 'skipped', 'cancelled']:
                steps = copy.deepcopy(self.steps)
                steps[name]['outcome'] = outcome
                self.assertIn(name, failures(steps, True), (name, outcome))

    def test_intentional_gms_skip_and_cache_hit(self):
        for name in ['pr_keystore', 'gms_key', 'save_key', 'gms', 'gms_apk']:
            self.steps[name]['outcome'] = 'skipped'
        self.assertEqual([], failures(self.steps, False))
        self.steps['pr_keystore'] = {'outcome': 'success', 'outputs': {'cache-hit': 'true'}}
        for name in ['gms', 'gms_apk']:
            self.steps[name]['outcome'] = 'success'
        self.assertEqual([], failures(self.steps, True))

    def condition(self, name):
        workflow = Path(__file__).parents[1] / 'workflows/checks.yml'
        for block in workflow.read_text().split('\n      - '):
            if f'        id: {name}\n' in block:
                return re.search(r'if: \$\{\{ (.*) \}\}', block)[1]
        self.fail('missing step ' + name)

    def runs(self, name, gms='true'):
        return self.evaluate(self.condition(name), gms)

    def evaluate(self, expression, gms='true'):
        expression = expression.replace('!cancelled()', 'True')
        expression = expression.replace("github.event_name", repr('pull_request'))
        expression = expression.replace('steps.select.outputs.gms', repr(gms))
        def value(match):
            step, field = match.groups()
            return repr(self.steps[step].get('outputs', {}).get('cache-hit', '') if field == 'outputs.cache-hit'
                        else self.steps[step]['outcome'])
        expression = re.sub(r'steps\.(\w+)\.(outcome|outputs.cache-hit)', value, expression)
        return eval(expression.replace('&&', ' and ').replace('||', ' or '), {'__builtins__': {}})

    def test_workflow_attempts_independent_gms_after_foss_failures(self):
        for failed in ['foss_key', 'foss', 'foss_reports', 'save_key']:
            self.steps[failed]['outcome'] = 'failure'
            self.assertTrue(self.runs('pr_keystore'))
            self.assertTrue(self.runs('gms'))
            self.assertTrue(failures(self.steps, True))
            self.steps[failed]['outcome'] = 'success'
        self.assertFalse(self.runs('pr_keystore', 'false'))
        self.assertTrue(self.runs('pr_keystore', ''))

    def test_selector_failure_does_not_block_shared_setup_or_suites(self):
        self.steps['select']['outcome'] = 'failure'
        for name in ['java', 'android', 'sdk', 'gradle', 'executable', 'foss_key', 'foss', 'pr_keystore', 'gms']:
            self.assertTrue(self.runs(name), name)
        self.assertTrue(failures(self.steps, True))

    def test_failed_selector_exclusion_is_not_trusted_by_scheduling_or_gate(self):
        self.steps['select']['outcome'] = 'failure'
        self.assertTrue(self.runs('pr_keystore', 'false'))
        workflow = Path(__file__).parents[1] / 'workflows/checks.yml'
        expression = re.search(r'GMS_SELECTED: \$\{\{ (.*) \}\}', workflow.read_text())[1]
        self.assertTrue(self.evaluate(expression, 'false'))
        self.steps['gms']['outcome'] = 'skipped'
        self.assertIn('gms', failures(self.steps, self.evaluate(expression, 'false')))

    def test_shared_and_signing_failure_blocks_only_dependent_steps(self):
        self.steps['gradle']['outcome'] = 'failure'
        self.assertFalse(self.runs('executable'))
        self.steps['executable']['outcome'] = 'skipped'
        self.assertFalse(self.runs('foss_key'))
        self.assertFalse(self.runs('pr_keystore'))
        self.steps['pr_keystore']['outcome'] = 'failure'
        self.assertFalse(self.runs('gms'))


if __name__ == '__main__':
    unittest.main()
