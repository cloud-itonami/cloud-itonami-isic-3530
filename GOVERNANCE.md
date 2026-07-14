# Governance

This repository follows the governance model established by the cloud-itonami project.

## Decision Making

- Technical decisions regarding thermal supply safety requirements are made by contributors with domain expertise in thermal systems and regulatory compliance.
- All changes to the Thermal Safety Governor (particularly HARD violation rules) require review and consensus from project maintainers.
- Safety-critical changes (protected-recipient gates, meter verification logic, suspension authority) require explicit approval before merging.

## Liability and Responsibility

Whoever deploys and operates a live instance of this thermal supply actor bears the liability and responsibility for:

1. Jurisdictional licensing and regulatory compliance (thermal utility operating licenses)
2. Jurisdiction-specific safety standards and enforcement
3. Real-world SCADA integrations and hardware safety systems
4. Customer notification and dispute resolution procedures
5. Audit trail maintenance and regulatory reporting

This software provides the compliance scaffold; the operator supplies the jurisdiction-specific policy, monitoring, and enforcement.

## Contributing

See CONTRIBUTING.md for contribution guidelines.
