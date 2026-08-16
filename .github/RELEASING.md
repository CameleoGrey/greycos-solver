# Releasing GreyCOS Solver

GreyCOS Solver releases all 34 Maven projects under the verified Maven Central namespace
`io.github.cameleogrey`. Development branches use `999-SNAPSHOT`; the release workflow creates the
requested version only on the temporary `__greycos_release_branch__` branch.

For release `0.11.14`:

- Maven version: `0.11.14`
- Git tag: `v0.11.14`
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
printf '%s' "$central_token_user" | gh secret set JRELEASER_MAVEN_CENTRAL_TOKEN_USER
printf '%s' "$central_token_password" | gh secret set JRELEASER_MAVEN_CENTRAL_TOKEN
unset central_token_user central_token_password
```

See the [Central Portal token documentation](https://central.sonatype.org/publish/generate-portal-token/).

### Personal GPG signing key

Use the existing personal primary signing key. Maven Central requires the public key to be available
from a supported keyserver and warns that signatures made only by a signing subkey may not validate.
Confirm the primary fingerprint before exporting anything:

```bash
gpg --list-secret-keys --keyid-format LONG
gpg --fingerprint YOUR_PRIMARY_KEY_FINGERPRINT
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_PRIMARY_KEY_FINGERPRINT
```

Keep an offline backup of the secret key and its revocation certificate. Export the selected key on
a trusted machine into files outside the repository:

```bash
umask 077
gpg --armor --export YOUR_PRIMARY_KEY_FINGERPRINT > greycos-public.asc
gpg --armor --export-secret-keys YOUR_PRIMARY_KEY_FINGERPRINT > greycos-secret.asc
```

Add the key and passphrase as repository secrets:

```bash
gh secret set JRELEASER_GPG_PUBLIC_KEY < greycos-public.asc
gh secret set JRELEASER_GPG_SECRET_KEY < greycos-secret.asc
read -r -s -p "GPG passphrase: " gpg_passphrase
printf '\n'
printf '%s' "$gpg_passphrase" | gh secret set JRELEASER_GPG_PASSPHRASE
unset gpg_passphrase
```

Securely remove the exported secret-key file after verifying that the repository secrets were
created. Never commit either exported key. Consult [Central's GPG requirements](https://central.sonatype.org/publish/requirements/gpg/)
for supported keyservers and signing-key restrictions.

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
2. Run the **Release** workflow with `version=0.11.14`, `sourceBranch=main`, and `dryRun=true`.
3. Inspect the complete workflow log and the `jreleaser-release-0.11.14-*` artifact. The run must
   verify 34 POMs, 23 main JARs, 23 source JARs, and 21 Javadoc JARs. A dry run must not create a
   remote release branch, tag, GitHub release, or Central deployment.
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
   git ls-remote --tags origin refs/tags/v0.11.14
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
- If Central has already published `0.11.14`, never retry or replace that version. Correct defects in
  a new version.
- If **Finish Release** fails, the remote temporary branch is retained unless the series branch was
  already updated successfully. Resolve the reported branch or merge problem before rerunning the
  finish workflow.

JReleaser performs Maven Central POM checks, source/Javadoc checks, checksums, and PGP signing before
upload. Its staged deployment behavior is documented in the
[JReleaser Maven Central guide](https://jreleaser.org/guide/latest/reference/deploy/maven/maven-central.html).
