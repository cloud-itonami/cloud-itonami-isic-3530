# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in this repository, please email
security@kotoba-lang.org with the following information:

- Description of the vulnerability
- Steps to reproduce (if applicable)
- Potential impact
- Suggested fix (if you have one)

Please do not open a public issue for security vulnerabilities.

## Safety-Critical Design

This thermal supply actor is designed with safety as a primary concern:

### HARD Gates (Cannot Be Overridden)

1. **Spec-Basis Requirement**: All provisioning and suspension proposals must cite official regulatory documentation. We never invent jurisdiction-specific requirements.

2. **Evidence Checklist**: Meter provisioning requires complete evidence of customer verification per jurisdiction requirements. Incomplete evidence blocks provisioning.

3. **Protected-Recipient Protection**: Customers marked as protected-recipient (life-support, hospitals, emergency services) can NEVER have their thermal supply suspended, regardless of other factors or human approval.

4. **Double-Actuation Guards**: A customer's supply can only be provisioned once and suspended once. The system prevents accidental duplicate provisioning or suspension.

### SOFT Gates (Requiring Human Approval)

1. **Confidence Floor**: Proposals below 60% confidence require human review.
2. **Actuation Gate**: All real provisioning and suspension events require human sign-off, enforced by both the Governor and the Phase table (two independent layers).

## Security Considerations for Operators

1. **Jurisdiction Licensing**: Ensure your deployment has appropriate thermal utility operating licenses for your jurisdiction.

2. **Access Control**: Restrict access to customer data and meter verification records per privacy regulations (GDPR, etc.).

3. **Audit Trail**: The audit ledger is append-only. Maintain backups and ensure immutability in your deployment.

4. **Integration Points**: When integrating with SCADA or physical systems, implement additional safety interlocks at the hardware level.

5. **Dependency Updates**: Regularly update langgraph-clj and other dependencies to patch security vulnerabilities.

## Known Limitations

- This actor does not model real pressure-relief systems or fluid mechanics.
- No direct hardware dispatch protocol is implemented.
- Operator must supply all jurisdiction-specific monitoring and enforcement.

## Security Audit

This codebase undergoes security review as part of the cloud-itonami project governance. Critical changes affecting protected-recipient gates or safety checks require explicit security review.
