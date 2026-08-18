# Releasing GreyCOS Solver

GreyCOS Solver releases all 34 Maven projects under the verified Maven Central namespace
`io.github.cameleogrey`. Development branches use `999-SNAPSHOT`; the release workflow creates the
requested version only on the temporary `__greycos_release_branch__` branch.

For release `0.11.15`:

- Maven version: `0.11.15`
- Git tag: `v0.11.15`
- Release series branch: `0.11.x`
- Source branch: `main`

Maven Central artifacts are immutable. Never reuse a version that has reached the `PUBLISHED` state.

## One-time credential setup

### Maven Central token

The Central Portal token named `greycos-solver-github-actions` provides a generated username and
password. Store them as separate GitHub repository secrets:

| GitHub secret | Value |
| --- | --- |
| `JRELEASER_MAVEN_CENTRAL_TOKEN_USER` | Portal token username |
| `JRELEASER_MAVEN_CENTRAL_TOKEN` | Portal token password |

The workflow combines these values into the base64-encoded Bearer token required by the Publisher
API. Do not pre-encode either secret. Portal tokens cannot be viewed again after their creation
dialog is closed; generate a replacement if either value was lost.

To set the secrets with the GitHub CLI without putting their values in shell history:

```bash
read -r -p "Central token username: " central_token_user
read -r -s -p "Central token password: " central_token_password
printf '\n'
printf '%s' "$central_token_user" | gh secret set JRELEASER_MAVEN_CENTRAL_TOKEN_USER \
  --repo CameleoGrey/greycos-solver
printf '%s' "$central_token_password" | gh secret set JRELEASER_MAVEN_CENTRAL_TOKEN \
  --repo CameleoGrey/greycos-solver
unset central_token_user central_token_password
```

See the [Central Portal token documentation](https://central.sonatype.org/publish/generate-portal-token/).

### Personal GPG signing key

Maven Central requires a personal signing key whose public half is available from a supported
keyserver. The commands below also work when the local keyring is initially empty. Do not type
placeholder text such as `YOUR_PRIMARY_KEY_FINGERPRINT` into a command.

First inspect the keyring:

```bash
gpg --list-secret-keys --keyid-format LONG
```

If this prints no `sec` entry, create a key on a trusted machine:

```bash
gpg --full-generate-key
```

Select `RSA and RSA`, use at least 3072 bits, set a finite expiry such as two years, and protect the
key with a strong unique passphrase. Use the public release identity and email that should appear on
artifact signatures; for GreyCOS this is normally `CameleoGrey <cameleogrey@yandex.ru>`. The primary
RSA key must have signing capability; do not create or select a signing-only subkey for this release.

Capture the primary fingerprint without copying a placeholder. Change `release_email` only if the
key was created with a different email address:

```bash
release_email='cameleogrey@yandex.ru'
release_key_fingerprint="$(
  gpg --batch --with-colons --list-secret-keys "$release_email" |
    awk -F: '$1 == "sec" { primary = 1; next }
             primary && $1 == "fpr" { print $10; exit }'
)"

printf 'Primary fingerprint: %s\n' "$release_key_fingerprint"
[[ "$release_key_fingerprint" =~ ^[0-9A-F]{40,64}$ ]]
gpg --fingerprint "$release_key_fingerprint"
```

Do not continue if the fingerprint is blank or if the displayed identity is wrong. Publish the
public key and confirm that the keyserver can return it:

```bash
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys "$release_key_fingerprint"
gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys "$release_key_fingerprint"
```

Keyserver propagation may not be immediate. Retry the receive command before running the release
workflow if the first attempt cannot find the key.

Export both halves of the selected key into a temporary directory outside the repository and verify
that neither export is empty:

```bash
umask 077
release_export_dir="$(mktemp -d)"

gpg --armor --export "$release_key_fingerprint" \
  > "$release_export_dir/greycos-public.asc"
gpg --armor --export-secret-keys "$release_key_fingerprint" \
  > "$release_export_dir/greycos-secret.asc"

test -s "$release_export_dir/greycos-public.asc"
test -s "$release_export_dir/greycos-secret.asc"
stat -c '%s bytes %n' \
  "$release_export_dir/greycos-public.asc" \
  "$release_export_dir/greycos-secret.asc"
```

Add the exported keys and the same passphrase entered during key generation as repository secrets:

```bash
gh secret set JRELEASER_GPG_PUBLIC_KEY \
  --repo CameleoGrey/greycos-solver \
  < "$release_export_dir/greycos-public.asc"
gh secret set JRELEASER_GPG_SECRET_KEY \
  --repo CameleoGrey/greycos-solver \
  < "$release_export_dir/greycos-secret.asc"

read -r -s -p "Enter the same GPG passphrase: " gpg_passphrase
printf '\n'
printf '%s' "$gpg_passphrase" | gh secret set JRELEASER_GPG_PASSPHRASE \
  --repo CameleoGrey/greycos-solver
unset gpg_passphrase
```

Confirm that all five secret names exist:

```bash
gh secret list --repo CameleoGrey/greycos-solver
```

Before deleting the temporary exports, store an encrypted offline backup of the secret-key export
and the revocation certificate at
`$HOME/.gnupg/openpgp-revocs.d/$release_key_fingerprint.rev`. Then remove only the temporary files:

```bash
rm -- \
  "$release_export_dir/greycos-public.asc" \
  "$release_export_dir/greycos-secret.asc"
rmdir -- "$release_export_dir"
unset release_export_dir release_email release_key_fingerprint
```

Never create or commit exported keys inside the repository. If an earlier failed command created a
zero-byte `greycos-public.asc` in the repository, remove that empty file before continuing. Consult
[Central's GPG requirements](https://central.sonatype.org/publish/requirements/gpg/) for supported
keyservers and signing-key restrictions.

The required repository secrets are therefore:

```text
JRELEASER_GPG_PUBLIC_KEY
JRELEASER_GPG_SECRET_KEY
JRELEASER_GPG_PASSPHRASE
JRELEASER_MAVEN_CENTRAL_TOKEN_USER
JRELEASER_MAVEN_CENTRAL_TOKEN
```

A separate GitHub token is not required; the workflow uses its scoped `github.token`.

## Release procedure

1. Confirm `main` is green and still uses `999-SNAPSHOT`. Review all changes intended for the
   release. In particular, confirm the license, developer, SCM, and project URLs in `pom.xml`.
2. Run the **Release** workflow with `version=0.11.15`, `sourceBranch=main`, and `dryRun=true`.
3. Inspect the complete workflow log and the `jreleaser-release-0.11.15-*` artifact. Both dry and
   real runs execute the complete Maven test suite. The run must verify 34 POMs, 23 main JARs, 23
   source JARs, and 21 Javadoc JARs. A dry run must not create a remote release branch, tag, GitHub
   release, or Central deployment.
4. Repeat the workflow with the same version and source branch and `dryRun=false`. This pushes the
   temporary branch, uploads a user-managed deployment to Central, and creates a draft GitHub
   release. It does not publish the Central deployment.
5. Open [Central Portal deployments](https://central.sonatype.com/publishing/deployments), locate the
   deployment ID recorded in `out/jreleaser/output.properties`, and inspect its validation results.
6. When the deployment reaches `VALIDATED`, manually publish it in Central Portal. Wait for
   `PUBLISHED`, then verify at least the parent POM, BOM, core JAR, sources, Javadocs, signatures, and
   checksums under `io/github/cameleogrey`.
7. Publish the existing GitHub draft release only after Central reports `PUBLISHED`. Publishing the
   draft triggers **Finish Release**, which validates the tag, creates or updates `0.11.x`, restores
   `999-SNAPSHOT` on that series branch, and removes the temporary branch.
8. Confirm:

   ```bash
   git ls-remote --tags origin refs/tags/v0.11.15
   git ls-remote --heads origin refs/heads/0.11.x
   git ls-remote --exit-code --heads origin refs/heads/__greycos_release_branch__
   ```

   The first two commands must return a revision. The last command must exit with status 2 and print
   nothing. `main` must remain at `999-SNAPSHOT`.

## Failure and recovery

- If a dry run fails, fix the cause on the source branch and run the dry run again. It leaves no
  remote release state.
- If a real run fails before pushing `__greycos_release_branch__`, fix the source branch and retry.
- If a real run fails after pushing that branch, do not delete it. The next release run deliberately
  refuses to overwrite it. Inspect the branch, the JReleaser trace, and Central Portal first.
- If Central validation fails, do not publish the GitHub draft. Preserve the failed deployment when
  requesting Central support; otherwise drop it in the Portal before preparing a corrected release.
- If Central has already published `0.11.15`, never retry or replace that version. Correct defects in
  a new version.
- If **Finish Release** fails, the remote temporary branch is retained unless the series branch was
  already updated successfully. Resolve the reported branch or merge problem before rerunning the
  finish workflow.

JReleaser performs Maven Central POM checks, source/Javadoc checks, checksums, and PGP signing before
upload. Its staged deployment behavior is documented in the
[JReleaser Maven Central guide](https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html).
