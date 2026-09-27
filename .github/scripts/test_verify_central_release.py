"""Focused checks for the staged and signed Maven Central release inventory."""

import hashlib
import json
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import central_release_inventory as release  # noqa: E402


VERSION = "0.12.0"
SIGNATURE = b"-----BEGIN PGP SIGNATURE-----\n\ndGVzdA==\n-----END PGP SIGNATURE-----\n"
MESSAGE = b"-----BEGIN PGP MESSAGE-----\nVersion: BCPG v1.84\n\ndGVzdA==\n-----END PGP MESSAGE-----\n"


class ReleaseFixture(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.staging = self.root / "staging"
        self.bundle = self.root / "bundle.zip"
        self.output = self.root / "output"
        self.primaries = release.expected_primaries(VERSION)
        for name in self.primaries:
            path = self.staging / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"<project/>" if name.endswith(".pom") else name.encode())

    def signed_bundle(self, *, drop=None, mutate_zip=None, duplicate=None, directory=False, signature=SIGNATURE):
        for name in self.primaries:
            primary = self.staging / name
            payload = primary.read_bytes()
            Path(f"{primary}.asc").write_bytes(signature)
            for algorithm in release.CHECKSUMS:
                Path(f"{primary}.{algorithm}").write_text(
                    hashlib.new(algorithm, payload).hexdigest(), encoding="ascii"
                )
        with zipfile.ZipFile(self.bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            if directory:
                archive.writestr("io/github/cameleogrey/", b"")
            for name in sorted(release.expected_bundle_paths(self.primaries)):
                if name == drop:
                    continue
                data = (self.staging / name).read_bytes()
                if name == mutate_zip:
                    data = b"tampered" + data
                archive.writestr(name, data)
            if duplicate:
                with warnings.catch_warnings():
                    warnings.simplefilter("ignore", UserWarning)
                    archive.writestr(duplicate, (self.staging / duplicate).read_bytes())


class StagingTests(ReleaseFixture):
    def test_exact_inventory_ignores_maven_metadata_and_writes_manifest(self):
        metadata = self.staging / "io/github/cameleogrey/greycos-solver-core/maven-metadata.xml"
        metadata.write_text("metadata", encoding="utf-8")
        self.assertEqual(
            0,
            release.main(
                ["staging", VERSION, "--staging", str(self.staging), "--output-dir", str(self.output)]
            ),
        )
        manifest = (self.output / "central-staging-manifest.txt").read_text()
        self.assertIn("maven-metadata.xml", manifest)
        self.assertEqual(107, len(manifest.splitlines()))

    def test_missing_core_test_jar_is_rejected(self):
        core_test = next(name for name in self.primaries if name.endswith("-tests.jar"))
        (self.staging / core_test).unlink()
        with self.assertRaisesRegex(release.InventoryError, "missing=.*tests.jar"):
            release.verify_staging(VERSION, self.staging)

    def test_extra_test_jar_and_snapshot_are_rejected(self):
        extra = self.staging / "io/github/cameleogrey/greycos-solver-jackson/0.12.0/greycos-solver-jackson-0.12.0-tests.jar"
        extra.write_bytes(b"extra")
        with self.assertRaisesRegex(release.InventoryError, "unexpected=.*tests.jar"):
            release.verify_staging(VERSION, self.staging)
        extra.unlink()
        snapshot = self.staging / "io/github/cameleogrey/greycos-solver-core/999-SNAPSHOT/maven-metadata.xml"
        snapshot.parent.mkdir(parents=True)
        snapshot.write_bytes(b"metadata")
        with self.assertRaisesRegex(release.InventoryError, "SNAPSHOT"):
            release.verify_staging(VERSION, self.staging)

    def test_obsolete_coordinate_is_rejected(self):
        pom = next(name for name in self.primaries if name.endswith(".pom"))
        (self.staging / pom).write_bytes(b"<groupId>greycos.solver</groupId>")
        with self.assertRaisesRegex(release.InventoryError, "Obsolete Maven coordinate"):
            release.verify_staging(VERSION, self.staging)


class BundleTests(ReleaseFixture):
    def test_pgp_armor_accepts_jreleaser_message_and_direct_signature(self):
        self.assertTrue(release.is_armored_pgp_signature(SIGNATURE))
        self.assertTrue(release.is_armored_pgp_signature(MESSAGE))
        self.signed_bundle(signature=MESSAGE)
        report, _ = release.verify_bundle(VERSION, self.staging, self.bundle)
        self.assertEqual(636, report["fileCount"])

    def test_pgp_armor_rejects_empty_mismatched_and_key_blocks(self):
        for bad in (
            b"",
            b"-----BEGIN PGP MESSAGE-----\n-----END PGP MESSAGE-----\n",
            b"-----BEGIN PGP MESSAGE-----\ndGVzdA==\n-----END PGP SIGNATURE-----\n",
            b"-----BEGIN PGP PRIVATE KEY BLOCK-----\ndGVzdA==\n-----END PGP PRIVATE KEY BLOCK-----\n",
            b"-----BEGIN PGP PUBLIC KEY BLOCK-----\ndGVzdA==\n-----END PGP PUBLIC KEY BLOCK-----\n",
        ):
            with self.subTest(bad=bad):
                self.assertFalse(release.is_armored_pgp_signature(bad))

    def test_bundle_rejects_invalid_signature_content(self):
        self.signed_bundle(signature=b"-----BEGIN PGP PRIVATE KEY BLOCK-----\ndGVzdA==\n-----END PGP PRIVATE KEY BLOCK-----\n")
        with self.assertRaisesRegex(release.InventoryError, "Missing armored PGP signature content"):
            release.verify_bundle(VERSION, self.staging, self.bundle)

    def test_signed_bundle_and_reports(self):
        self.signed_bundle(directory=True)
        self.assertEqual(
            0,
            release.main(
                [
                    "bundle", VERSION, "--staging", str(self.staging),
                    "--bundle", str(self.bundle), "--output-dir", str(self.output),
                ]
            ),
        )
        report = json.loads((self.output / "central-bundle-report.json").read_text())
        self.assertEqual(106, report["primaryFiles"])
        self.assertEqual(636, report["fileCount"])
        self.assertEqual(
            636, len((self.output / "central-bundle-manifest.txt").read_text().splitlines())
        )

    def test_missing_signature_or_checksum_is_rejected(self):
        primary = sorted(self.primaries)[0]
        for suffix in ("asc", "sha512"):
            with self.subTest(suffix=suffix):
                self.signed_bundle(drop=f"{primary}.{suffix}")
                with self.assertRaisesRegex(release.InventoryError, "missing="):
                    release.verify_bundle(VERSION, self.staging, self.bundle)

    def test_duplicate_zip_entry_is_rejected(self):
        self.signed_bundle(duplicate=sorted(self.primaries)[0])
        with self.assertRaisesRegex(release.InventoryError, "Duplicate ZIP entry"):
            release.verify_bundle(VERSION, self.staging, self.bundle)

    def test_zip_payload_must_match_staging(self):
        self.signed_bundle(mutate_zip=sorted(self.primaries)[0])
        with self.assertRaisesRegex(release.InventoryError, "differs from staging"):
            release.verify_bundle(VERSION, self.staging, self.bundle)

    def test_checksum_content_is_verified(self):
        self.signed_bundle()
        primary = sorted(self.primaries)[0]
        checksum = self.staging / f"{primary}.sha256"
        checksum.write_text("0" * 64, encoding="ascii")
        with zipfile.ZipFile(self.bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in sorted(release.expected_bundle_paths(self.primaries)):
                archive.write(self.staging / name, name)
        with self.assertRaisesRegex(release.InventoryError, "Incorrect sha256 checksum"):
            release.verify_bundle(VERSION, self.staging, self.bundle)

    def test_budget_exclusive_boundaries(self):
        release.enforce_budget(799, 79_999_999)
        with self.assertRaisesRegex(release.InventoryError, "800 files"):
            release.enforce_budget(800, 0)
        with self.assertRaisesRegex(release.InventoryError, "80000000 uncompressed bytes"):
            release.enforce_budget(0, 80_000_000)


if __name__ == "__main__":
    unittest.main()
