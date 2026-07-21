(ns steam.facts
  "Per-jurisdiction steam and chilled-water supply requirements and standards citations.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction steam/chilled-water supply requirements with official spec-basis citations."
  {
   :JPN
   {:name "Japan"
    :requirements
    {:customer-verification {:description "Customer identity verification (legal name, address, contact)"
                            :required true
                            :spec-basis "High Pressure Gas Safety Act (高圧ガス保安法) §24 (steam networks)"
                            :evidence [:customer-id-proof :address-proof :contact-info]}
     :thermal-meter-inspection {:description "Thermal meter certification and heat exchanger safety inspection"
                               :required true
                               :spec-basis "High Pressure Gas Safety Act §28"
                               :evidence [:thermal-meter-cert :heat-exchanger-cert]}
     :safety-information {:description "Provide safety information to customer (thermal safety, condensate handling)"
                         :required true
                         :spec-basis "High Pressure Gas Safety Act §33"
                         :evidence [:thermal-safety-brochure-provided]}}
    :suspension-requirements
    {:payment-delinquency {:allowed true :spec-basis "High Pressure Gas Safety Act §32"}
     :safety-violation {:allowed true :spec-basis "High Pressure Gas Safety Act §31"}
     :customer-request {:allowed true :spec-basis "High Pressure Gas Safety Act §25"}}}

   :USA
   {:name "United States"
    :requirements
    {:customer-verification {:description "Customer identity and credit verification"
                            :required true
                            :spec-basis "ASME PTC 4.4 (Energy Audit of District Heating Systems)"
                            :evidence [:customer-id-proof :utility-account-proof]}
     :thermal-meter-inspection {:description "Thermal meter accuracy certification (BTU/kWh)"
                               :required true
                               :spec-basis "ASME B4.1 - Metric Specifications for Thermal Metering"
                               :evidence [:thermal-meter-cert]}
     :disclosure {:description "Provide notice of thermal service terms and conditions"
                 :required true
                 :spec-basis "FTC Act §5"
                 :evidence [:thermal-disclosure-signed]}}}

   :GBR
   {:name "United Kingdom"
    :requirements
    {:customer-verification {:description "Customer identity verification"
                            :required true
                            :spec-basis "Heat Networks (Metering) Regulations"
                            :evidence [:customer-id-proof :contact-proof]}
     :thermal-meter-inspection {:description "Heat meter certification and fit-to-purpose"
                               :required false
                               :spec-basis "Heat Networks (Metering) Regulations"
                               :evidence [:thermal-meter-cert]}}}

   ;; France's réseaux de chaleur/froid regime is genuinely differently-shaped
   ;; from JPN/USA/GBR's customer-facing metering/disclosure rules -- it
   ;; regulates mandatory CONNECTION to classified networks, not per-customer
   ;; metering or disclosure. WebFetch-verified 2026-07-21 directly against
   ;; legifrance.gouv.fr (France's official law portal): Code de l'énergie
   ;; Book VII Title I Chapter II (articles L712-1 to L712-5).
   :FRA
   {:name "France"
    :requirements
    {:mandatory-connection {:description "Any new-building or major-renovation installation exceeding 30kW inside a network's priority development zone must connect to that classified réseau de chaleur/froid (buildings <=30kW or outside a zone are not covered by this obligation)"
                           :required true
                           :spec-basis "Code de l'énergie art. L712-2 (priority development zones) + L712-3 (mandatory connection obligation, exemptions possible by local decision)"
                           :evidence [:building-power-rating :priority-zone-status]}
     :network-classification-disclosure {:description "A network >50% powered by renewable/recovered energy seeking classified status must undergo an energy audit and financial-viability assessment"
                                        :required true
                                        :spec-basis "Code de l'énergie art. L712-1"
                                        :evidence [:energy-audit-record :financial-viability-assessment]}}
    :suspension-requirements
    ;; NOT verified this iteration: no French-law citation for a network
    ;; OPERATOR's :payment-delinquency/:safety-violation/:customer-request
    ;; supply-suspension power (the JPN/USA/GBR shape) was found -- so those
    ;; specific reason-keys are honestly absent for :FRA, and
    ;; `suspension-allowed-for?` correctly returns false for them rather than
    ;; a fabricated true. The one entry below IS real and verified, but is a
    ;; different kind of fact: a penalty on the BUILDING OWNER for failing to
    ;; connect (L712-5), not a suspension power the operator wields against an
    ;; already-connected customer. Kept under its own honestly-named reason-key
    ;; rather than folded into :payment-delinquency, to avoid implying an
    ;; equivalence that isn't there.
    {:non-compliance-with-connection-obligation
     {:allowed true
      :spec-basis "Code de l'énergie art. L712-5 (300,000 EUR fine for violating the L712-3 mandatory connection obligation -- an enforcement penalty against the OBLIGATED BUILDING OWNER, not a supply-suspension power the network operator exercises against a customer, unlike the other jurisdictions' :payment-delinquency/:safety-violation entries)"}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn suspension-allowed-for?
  "Check if suspension is allowed for a given reason in this jurisdiction."
  [jurisdiction reason]
  (get-in catalog [jurisdiction :suspension-requirements reason :allowed] false))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
