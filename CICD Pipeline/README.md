# Moved to `.github/workflows/`

CI/CD pipeline definitions now live in:

- `.github/workflows/ci.yml` — build, test, Docker verify on PRs
- `.github/workflows/cd.yml` — ECR push and EC2 deploy
- `.github/workflows/nightly.yml` — OWASP, Postman, ECR cleanup

See `.github/CICD_SETUP.md` for GitHub Secrets and EC2 setup.
