# Signing and releasing mistri

Every release is built, signed and published by GitHub Actions (`.github/workflows/release.yml`). The installer is signed with **Azure Artifact Signing** (formerly Trusted Signing), so Windows shows **GITAV** as the verified publisher, and mistri's own updater refuses any download that isn't signed by GITAV.

There is no certificate file or password to look after: GitHub signs in to Azure with a short-lived token (OIDC) each time it builds, and Microsoft keeps the signing key.

## One-time setup

Allow about an hour of clicking, plus Microsoft's identity check (1 to 20 business days).

### 1. Azure (portal.azure.com)

1. **Register the provider.** Subscriptions → your subscription → Resource providers → `Microsoft.CodeSigning` → Register.
2. **Create the signing account.** Search "Artifact Signing Accounts" → Create. Pick a resource group, an account name (for example `gitavsigning`), a region near you, and the **Basic** pricing tier. Note the region's endpoint, for example `eus.codesigning.azure.net` for East US.
3. **Give yourself the verifier role.** On the new account: Access control (IAM) → Add role assignment → **Artifact Signing Identity Verifier** → your own user.
4. **Validate GITAV's identity.** Account → Identity validations → Organization → New identity → **Public**. Enter GITAV's legal name, website, emails, business identifier (EIN or registration number) and address *exactly* as they appear in public business records, and the representative's name exactly as on their ID. Microsoft emails you if it needs documents (issued in the last 12 months). You get three upload attempts.
   - Public Trust organizations must be in the US, Canada, the EU, the UK, Australia, New Zealand, Japan, South Korea, Singapore, Switzerland, Norway or Israel.
   - The certificate's publisher name is the validated legal name and can't be changed. If that name doesn't contain "GITAV", change `PUBLISHER` in `mistri/__init__.py` to match, because the updater checks for it.
5. **Create the certificate profile** once validation shows *Completed*: Certificate profiles → Create → **Public Trust** → name it (for example `mistri`) → choose the validated identity.

### 2. Let GitHub sign (Microsoft Entra ID)

1. **App registration.** Entra ID → App registrations → New registration → name `mistri-github-signing`. Copy its **Application (client) ID** and **Directory (tenant) ID**.
2. **Federated credential.** In the app: Certificates & secrets → Federated credentials → Add → *GitHub Actions deploying Azure resources*. Organization `GITAV`, repository `mistri`, entity type **Environment**, environment name `release`.
3. **Signing role.** On the Artifact Signing account (or just the certificate profile): Access control (IAM) → Add role assignment → **Artifact Signing Certificate Profile Signer** → the `mistri-github-signing` app.

### 3. GitHub (github.com/GITAV/mistri → Settings)

1. **Environment.** Environments → New environment → `release`. Add yourself under *Required reviewers* so each signing waits for your approval.
2. **Secrets** (Secrets and variables → Actions → Secrets): `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`.
3. **Variables** (same page → Variables):
   - `AZURE_SIGNING_ENDPOINT`: the host only, for example `eus.codesigning.azure.net`
   - `AZURE_SIGNING_ACCOUNT`: for example `gitavsigning`
   - `AZURE_SIGNING_PROFILE`: for example `mistri`

To test, run Actions → Release → *Run workflow*. It builds and signs without publishing, and leaves the installer as a downloadable artifact.

## Releasing a new version

1. Change `__version__` in `mistri/__init__.py` (and `version` in `extension/manifest.json`).
2. Write `release-notes/<version>.md`. This is what users see under "What's new".
3. Commit, then tag and push:
   ```
   git tag v1.2.0
   git push origin main v1.2.0
   ```
4. Approve the run under Actions. It builds the installer on Linux, signs the uninstaller and the installer, checks the signature says GITAV, and publishes a GitHub release with:
   - `mistri-setup-<version>.exe`, the signed installer, which the in-app updater downloads
   - `mistri-setup-<version>.zip`, the same installer zipped, for browsers that block .exe downloads
   - `mistri-setup-<version>.exe.sha256`, a checksum
   - `mistri-shield-<version>.zip`, the Chrome extension

Within a day, installed copies of mistri see the new version on their Home page.

## How updates stay safe

- mistri reads `https://api.github.com/repos/GITAV/mistri/releases/latest`, a plain public request with no account and no data about the PC.
- It only offers versions newer than the one installed, and only installs after the user clicks **Update now**.
- After downloading, it checks the size, then asks Windows to verify the Authenticode signature. Anything not validly signed by GITAV is deleted, not run.
- Users can turn off the daily check on the Home page under *About mistri*.
