# Business Model: Community Thermal Supply Distribution

## Overview

This blueprint codifies a sustainable business model for community-owned thermal utilities (steam and chilled water distribution). It empowers municipal utilities, cooperatives, and independent operators to manage thermal distribution safely and transparently without renting a closed SaaS platform.

## Actors and Roles

### Operator (Licensee)

The entity licensed to distribute thermal energy in a given jurisdiction. Responsibilities:

- Obtain required thermal utility operating licenses
- Supply jurisdiction-specific safety standards and requirements
- Integrate with real SCADA and monitoring systems
- Review and approve all customer provisioning and supply suspensions
- Maintain audit trail and regulatory compliance

### Customer

The end consumer of thermal energy (steam for heating/industrial processes, or chilled water for air conditioning).

### Thermal Safety Governor

The independent compliance layer that prevents unsafe operations. It applies hard safety rules that even the operator's human staff cannot override:

- Protected-recipient meter protection (hospitals, emergency services, critical infrastructure can never be suspended)
- Evidence verification requirements
- Jurisdictional spec-basis citations
- Double-actuation prevention

## Revenue Model

This is a foundation for a self-sustaining thermal utility:

1. **Thermal Supply Revenue**: The operator charges customers for thermal energy consumed (measured by certified thermal meters)

2. **Operational Sustainability**: Software cost is minimal (open-source infrastructure), allowing the operator to pass savings to customers or reinvest in network resilience

3. **Competitive Advantage**: Transparent audit trails and spec-cited governance build customer trust and regulatory confidence

## Social Impact

- **Energy Access**: Eliminates high barrier-to-entry for thermal utilities in communities too small for traditional carriers
- **Public Health**: Prevents life-threatening disconnections of hospitals and emergency services through hard protected-recipient gates
- **Economic Sovereignty**: Communities can operate their own thermal grids without corporate intermediaries

## Scalability

This architecture scales from:

- A single district heating plant serving a neighborhood
- Multi-node thermal networks with interconnected substations
- Municipal utilities integrating multiple thermal sources (waste heat recovery, geothermal, solar thermal)

The core governance pattern (Advisor → Governor → Actuation) remains constant; only SCADA integration complexity grows.

## Risk Mitigation

The governance model mitigates several categories of risk:

1. **Safety Risk**: Hard gates prevent unsafe operations (suspension of critical infrastructure)
2. **Compliance Risk**: Spec-basis citations ensure regulatory alignment
3. **Operational Risk**: Audit ledger creates an immutable record for disputes and regulatory audits
4. **Liability Risk**: Separation of Advisor (LLM recommendations) from Governor (safety gates) creates clear accountability

The operator bears final liability but has governance tools to minimize exposure.
