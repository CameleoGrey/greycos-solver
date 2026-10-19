"""Exercise the finish-release workflow against disposable, local Git remotes."""

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).resolve().parents[1] / "workflows" / "finish_release.yml"
RELEASE_BRANCH = "__greycos_release_branch__"
SERIES_BRANCH = "0.13.x"
RELEASE_TAG = "v0.13.0"
FIRST_RESTORE_DATE = "2026-10-19T00:10:00+0000"
RETRY_RESTORE_DATE = "2026-10-19T00:11:00+0000"


def workflow_run_block(step_name):
    """Read the actual literal shell block, failing if the workflow layout changes."""
    lines = WORKFLOW.read_text(encoding="utf-8").splitlines()
    matches = [index for index, line in enumerate(lines) if line == f"      - name: {step_name}"]
    if len(matches) != 1:
        raise AssertionError(f"Expected exactly one workflow step named {step_name!r}")
    start = matches[0] + 1
    end = next((index for index in range(start, len(lines)) if lines[index].startswith("      - ")),
               len(lines))
    matches = [index for index in range(start, end) if lines[index] == "        run: |"]
    if len(matches) != 1:
        raise AssertionError(f"Expected one literal run block in {step_name!r}")
    commands = []
    for line in lines[matches[0] + 1:end]:
        if line and not line.startswith("          "):
            raise AssertionError(f"Unexpected content after run block in {step_name!r}: {line}")
        commands.append(line[10:] if line else "")
    if not any(commands):
        raise AssertionError(f"Empty run block in {step_name!r}")
    return "\n".join(commands) + "\n"


class FinishReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="greycos-finish-release-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.origin = self.root / "origin.git"
        self.seed = self.root / "seed"
        # Ignore the caller's Git configuration and forbid network transports.
        self.environment = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
        self.environment.update({
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_TERMINAL_PROMPT": "0",
            "GIT_ALLOW_PROTOCOL": "file",
            "GIT_AUTHOR_NAME": "Release Test",
            "GIT_AUTHOR_EMAIL": "release-test@example.invalid",
            "GIT_COMMITTER_NAME": "Release Test",
            "GIT_COMMITTER_EMAIL": "release-test@example.invalid",
            "GIT_AUTHOR_DATE": "2026-10-19T00:00:00+0000",
            "GIT_COMMITTER_DATE": "2026-10-19T00:00:00+0000",
            "LC_ALL": "C",
        })
        self.git("init", "--bare", "--initial-branch=main", str(self.origin))
        self.git("init", "--initial-branch=main", str(self.seed))
        self.write_version(self.seed, "999-SNAPSHOT")
        (self.seed / "release.txt").write_text("before release\n", encoding="utf-8")
        self.commit(self.seed, "Initial development state")
        self.main_commit = self.git("rev-parse", "HEAD", cwd=self.seed).stdout.strip()
        self.git("remote", "add", "origin", str(self.origin), cwd=self.seed)
        self.git("switch", "-c", RELEASE_BRANCH, cwd=self.seed)
        self.write_version(self.seed, "0.13.0")
        (self.seed / "release.txt").write_text("released changes\n", encoding="utf-8")
        self.commit(self.seed, "Prepare release")
        self.release_commit = self.git("rev-parse", "HEAD", cwd=self.seed).stdout.strip()
        self.git("tag", RELEASE_TAG, cwd=self.seed)
        self.git("push", "origin", "main", RELEASE_BRANCH, f"refs/tags/{RELEASE_TAG}", cwd=self.seed)

    def git(self, *arguments, cwd=None):
        return subprocess.run(
            ["git", *arguments], cwd=cwd or self.root, env=self.environment,
            text=True, capture_output=True, check=True,
        )

    def write_version(self, repository, version):
        (repository / "pom.xml").write_text(
            f"<project><version>{version}</version></project>\n", encoding="utf-8"
        )

    def commit(self, repository, message):
        self.git("add", ".", cwd=repository)
        self.git("commit", "-m", message, cwd=repository)

    def publish_existing_series(self, *, conflict=False):
        self.git("switch", "-c", SERIES_BRANCH, self.main_commit, cwd=self.seed)
        (self.seed / "series-only.txt").write_text("retain series changes\n", encoding="utf-8")
        if conflict:
            (self.seed / "release.txt").write_text("conflicting series changes\n", encoding="utf-8")
        self.commit(self.seed, "Existing series changes")
        self.git("push", "origin", SERIES_BRANCH, cwd=self.seed)

    def restored_checkout(self, name, date=FIRST_RESTORE_DATE):
        repository = self.root / name
        self.git("clone", str(self.origin), str(repository))
        self.git("switch", "-c", RELEASE_BRANCH, f"origin/{RELEASE_BRANCH}", cwd=repository)
        self.write_version(repository, "999-SNAPSHOT")
        self.git("add", "pom.xml", cwd=repository)
        environment = dict(self.environment, GIT_AUTHOR_DATE=date, GIT_COMMITTER_DATE=date)
        subprocess.run(
            ["git", "commit", "-m", "build: move back to 999-SNAPSHOT"],
            cwd=repository, env=environment, text=True, capture_output=True, check=True,
        )
        return repository

    def run_update(self, repository):
        environment = dict(
            self.environment, RELEASE_BRANCH_NAME=RELEASE_BRANCH,
            SERIES_BRANCH=SERIES_BRANCH, RELEASE_TAG=RELEASE_TAG,
        )
        return subprocess.run(
            ["bash", "-c", workflow_run_block("Update the release series branch")],
            cwd=repository, env=environment, text=True, capture_output=True,
        )

    def remote_refs(self):
        output = self.git("--git-dir", str(self.origin), "for-each-ref", "--format=%(refname) %(objectname)")
        return dict(line.split() for line in output.stdout.splitlines())

    def assert_main_and_tag_unchanged(self):
        refs = self.remote_refs()
        self.assertEqual(self.main_commit, refs["refs/heads/main"])
        self.assertEqual(self.release_commit, refs[f"refs/tags/{RELEASE_TAG}"])

    def install_hook(self, directory, name, body):
        hook = directory / "hooks" / name
        hook.write_text("#!/bin/sh\nset -eu\n" + body, encoding="utf-8")
        hook.chmod(0o755)
        return hook

    def test_deletion_failure_retry_skips_unchanged_commit(self):
        first = self.restored_checkout("first-attempt")
        first_restore = self.git("rev-parse", "HEAD", cwd=first).stdout.strip()
        hook = self.install_hook(self.origin, "update", f"""
if [ "$1" = "refs/heads/{RELEASE_BRANCH}" ] && [ "$3" = "{'0' * 40}" ]; then
  echo "Reject temporary branch deletion for test" >&2
  exit 1
fi
""")
        result = self.run_update(first)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Reject temporary branch deletion for test", result.stderr)
        refs = self.remote_refs()
        self.assertEqual(first_restore, refs[f"refs/heads/{SERIES_BRANCH}"])
        self.assertEqual(self.release_commit, refs[f"refs/heads/{RELEASE_BRANCH}"])
        self.assert_main_and_tag_unchanged()
        hook.unlink()

        retry = self.restored_checkout("retry", RETRY_RESTORE_DATE)
        retry_restore = self.git("rev-parse", "HEAD", cwd=retry).stdout.strip()
        self.assertNotEqual(first_restore, retry_restore)
        self.assertEqual(
            self.git("rev-parse", f"{first_restore}^{{tree}}", cwd=retry).stdout,
            self.git("rev-parse", f"{retry_restore}^{{tree}}", cwd=retry).stdout,
        )
        result = self.run_update(retry)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        refs = self.remote_refs()
        self.assertEqual(first_restore, refs[f"refs/heads/{SERIES_BRANCH}"])
        self.assertNotIn(f"refs/heads/{RELEASE_BRANCH}", refs)
        self.assert_main_and_tag_unchanged()

    def test_existing_series_receives_one_squash_commit(self):
        self.publish_existing_series()
        before = self.remote_refs()[f"refs/heads/{SERIES_BRANCH}"]
        repository = self.restored_checkout("update")
        result = self.run_update(repository)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        refs = self.remote_refs()
        after = refs[f"refs/heads/{SERIES_BRANCH}"]
        self.assertNotEqual(before, after)
        self.assertEqual(before, self.git("show", "-s", "--format=%P", after, cwd=repository).stdout.strip())
        self.assertEqual(
            "build: release version v0.13.0",
            self.git("show", "-s", "--format=%s", after, cwd=repository).stdout.strip(),
        )
        for filename, contents in (
            ("pom.xml", "<project><version>999-SNAPSHOT</version></project>\n"),
            ("release.txt", "released changes\n"),
            ("series-only.txt", "retain series changes\n"),
        ):
            self.assertEqual(contents, self.git("show", f"{after}:{filename}", cwd=repository).stdout)
        self.assertNotIn(f"refs/heads/{RELEASE_BRANCH}", refs)
        self.assert_main_and_tag_unchanged()

    def test_merge_conflict_retains_remote_branches(self):
        self.publish_existing_series(conflict=True)
        before = self.remote_refs()
        repository = self.restored_checkout("conflict")
        result = self.run_update(repository)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("CONFLICT", result.stdout)
        self.assertEqual("release.txt", self.git(
            "diff", "--name-only", "--diff-filter=U", cwd=repository
        ).stdout.strip())
        self.assertEqual(before, self.remote_refs())

    def assert_series_push_rejection_retains_remote_branches(self):
        before = self.remote_refs()
        repository = self.restored_checkout("rejected-push")
        self.install_hook(self.origin, "update", f"""
if [ "$1" = "refs/heads/{SERIES_BRANCH}" ]; then
  echo "Reject series push for test" >&2
  exit 1
fi
""")
        result = self.run_update(repository)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Reject series push for test", result.stderr)
        self.assertEqual(before, self.remote_refs())

    def test_series_creation_push_rejection_retains_remote_branches(self):
        self.assert_series_push_rejection_retains_remote_branches()

    def test_series_update_push_rejection_retains_remote_branches(self):
        self.publish_existing_series()
        self.assert_series_push_rejection_retains_remote_branches()

    def test_commit_failure_retains_remote_branches(self):
        self.publish_existing_series()
        before = self.remote_refs()
        repository = self.restored_checkout("rejected-commit")
        self.install_hook(repository / ".git", "pre-commit", "echo 'Reject commit for test' >&2\nexit 1\n")
        result = self.run_update(repository)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Reject commit for test", result.stderr)
        self.assertEqual(before, self.remote_refs())


if __name__ == "__main__":
    unittest.main()
