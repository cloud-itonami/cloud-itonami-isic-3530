# Operator Guide: Deploying the Thermal Supply Actor

## Prerequisites

1. **Thermal Utility License**: Obtain all required operating licenses for your jurisdiction
2. **Meter Infrastructure**: Certified thermal meters (BTU/kWh) that report consumption and temperature
3. **SCADA System**: Integration capability with your thermal network SCADA (pressure, temperature, flow monitoring)
4. **Customer Database**: Customer records with verified identification and contact information
5. **Regulatory Knowledge**: Familiarity with your jurisdiction's thermal utility regulations

## Deployment Steps

### 1. Initialize the Actor

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-3530.git
cd cloud-itonami-isic-3530
clj -M:dev:run  # Test the demo
clj -M:test    # Run the test suite
```

### 2. Customize for Your Jurisdiction

Edit `src/steam/facts.cljc` to add your jurisdiction:

```clojure
:YOUR-COUNTRY-CODE
{:name "Your Country"
 :requirements
 {:customer-verification {:description "Your local requirement"
                         :required true
                         :spec-basis "Citation of official regulation (statute, code section, URL)"
                         :evidence [:your-evidence-keys]}}
 :suspension-requirements
 {:payment-delinquency {:allowed true :spec-basis "Your citation"}}}
```

**Critical**: Every requirement MUST have an official spec-basis citation. We never invent requirements.

### 3. Integrate with Your SCADA

Implement adapters in the `operation.cljc` layer to:

- Read meter consumption data from your thermal meters
- Trigger real provisioning/suspension on physical systems
- Record all actuation events to the audit ledger
- Send notifications to customers

Example hook points:

```clojure
(defn node-provision-scada
  "Trigger real provision on physical system."
  [state]
  ;; Your SCADA integration here
  (your-scada/activate-meter (:meter-id state)))

(defn node-suspend-scada
  "Trigger real suspension on physical system."
  [state]
  ;; Your SCADA integration here
  (your-scada/deactivate-meter (:meter-id state)))
```

### 4. Seed Your Customer Database

Use `steam.store/with-customers` to load your customer registry:

```clojure
(store/with-customers st
  {"cust-001" {:id "cust-001"
               :customer-name "Building A"
               :meter-id "THERMAL-001"
               :protected-recipient? false
               :jurisdiction "YOUR-CODE"
               :status :intake}
   ;; ... more customers
   })
```

Mark hospitals, fire stations, and emergency services as `protected-recipient? true`. These meters can **never** be suspended.

### 5. Configure the Governor

The Thermal Safety Governor is pre-configured with sound defaults:

- **Confidence Floor**: 0.6 (recommendations below 60% require human review)
- **Protected-Recipient Protection**: CANNOT be overridden
- **Spec-Basis Requirement**: CANNOT be bypassed

Do **not** weaken these settings. If they seem too restrictive for your use case, file an issue with your jurisdiction's regulatory citation, and the governance council will evaluate it.

### 6. Create an Audit Trail

The actor maintains an append-only ledger of every customer intake, meter verification, provisioning, and suspension decision:

```clojure
(store/ledger st)
;; => [{:op :customer/intake :subject "cust-001" :timestamp ...}
;;     {:op :meter/verify :subject "cust-001" :timestamp ...}
;;     ...]
```

Back up this ledger regularly. It is your evidence for regulatory compliance audits.

### 7. Test End-to-End

Run a provisioning flow:

```clojure
(let [st (store/mem-store)
      adv (advisor/mock-advisor)]
  (operation/run-operation st adv "cust-001" :provision))
```

Verify:
- Customer is found and verified
- Meter verification is complete
- Governor evaluation passes or holds appropriately
- Audit ledger is updated

## Operational Procedures

### Customer Onboarding

1. **Intake**: Collect verified customer identity and address
2. **Meter Assignment**: Install and certify thermal meter
3. **Verification**: Confirm all required evidence is collected
4. **Review**: Human operator reviews verification and approves
5. **Provision**: Supply is activated; ledger is updated

### Payment Delinquency Suspension

1. **Detection**: Delinquency system flags non-paying customer
2. **Proposal**: Advisor generates suspension proposal with payment delinquency reason
3. **Governor Review**: Governor checks if suspension is allowed in jurisdiction and if customer is protected-recipient
4. **Human Approval**: Operator reviews and approves suspension (never automatic)
5. **Execution**: Supply is suspended; notification is sent to customer
6. **Ledger**: Suspension is recorded with date, reason, and approver

### Emergency Reconnection

If a protected-recipient customer (hospital, emergency service) has their supply interrupted due to system failure:

1. **Alert**: Monitor system alerts for protected-recipient meter interruption
2. **Restore**: Immediately restore supply
3. **Investigate**: Determine root cause (equipment failure, SCADA error, etc.)
4. **Report**: Document incident in audit trail

### Dispute Resolution

If a customer disputes a provisioning or suspension decision:

1. **Query Ledger**: Retrieve the decision record with spec-basis citation
2. **Review Evidence**: Confirm all required verification was complete
3. **Appeal Process**: Define your operator's appeal process (not managed by this software)
4. **Update Record**: Record the appeal outcome in ledger

## Monitoring and Compliance

### Key Metrics

- **Coverage**: % of jurisdiction's thermal demand in your network
- **Protected-Recipient Count**: Number of life-support/critical-infrastructure meters (should never be suspended)
- **Audit Ledger Size**: Append-only record of all decisions
- **Governor Hold Rate**: % of proposals rejected by Governor (should be low; investigate spikes)

### Regulatory Reporting

Use the audit ledger to generate regulatory reports:

- Customer intake and verification summary
- Provisioning and suspension statistics by reason
- Protected-recipient protection compliance (zero suspensions)
- Dispute resolution outcomes

## Troubleshooting

### Governor Holds a Proposal

Check:

1. **Spec-Basis**: Does the jurisdiction have a requirement in facts.cljc?
2. **Evidence**: Is meter verification complete?
3. **Protected-Recipient**: Is the customer marked as protected-recipient? (Cannot suspend)
4. **Double-Actuation**: Has the customer already been provisioned/suspended?

### SCADA Integration Fails

1. Check network connectivity to SCADA system
2. Verify meter IDs match between actor and SCADA
3. Log all SCADA errors to ledger for audit trail
4. Do NOT proceed with actuation if SCADA is unreachable

### Ledger Corruption

If the audit ledger is corrupted or incomplete:

1. Stop all operations
2. Restore from backup
3. Report to your regulatory body
4. File an issue with this project

## Support

For technical questions, open an issue on GitHub.
For regulatory compliance questions, consult your local thermal utility authority.
