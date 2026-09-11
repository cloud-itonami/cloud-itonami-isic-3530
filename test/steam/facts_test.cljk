(ns steam.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [steam.facts :as facts]))

(deftest ^{:doc "Verify jurisdiction catalog has expected structure."} jurisdiction-coverage
  (is (seq facts/catalog) "Catalog should not be empty")
  (is (contains? facts/catalog :JPN) "Should have Japan jurisdiction")
  (is (contains? facts/catalog :USA) "Should have USA jurisdiction")
  (is (contains? facts/catalog :GBR) "Should have UK jurisdiction")
  (is (contains? facts/catalog :FRA) "Should have France jurisdiction")
  (is (contains? facts/catalog :DEU) "Should have Germany jurisdiction"))

(deftest ^{:doc "Verify Germany's AVBFernwärmeV requirements -- a genuinely differently-shaped
  regime from JPN/USA/GBR/FRA: a procedural/proportionality safeguard on
  payment-delinquency disconnection (2-week notice-after-warning, waivable),
  distinct from the flat :allowed-true shape used elsewhere."} germany-requirements
  (let [deu-reqs (facts/requirement-citations :DEU)]
    (is (contains? deu-reqs :contract-formation-disclosure))
    (is (contains? deu-reqs :billing-disclosure))
    (is (every? :spec-basis (vals deu-reqs)) "Every requirement should have an official spec-basis citation"))
  (is (facts/suspension-allowed-for? :DEU :safety-violation)
    "Immediate suspension for safety reasons should be allowed under §33(1)")
  (is (facts/suspension-allowed-for? :DEU :payment-delinquency)
    "Payment-delinquency suspension should be allowed under §33(2)")
  (is (= 14 (facts/notice-period-days-for :DEU :payment-delinquency))
    "Germany requires a 14-day notice period after warning before payment-delinquency suspension")
  (is (nil? (facts/notice-period-days-for :DEU :safety-violation))
    "No notice-period safeguard applies to immediate safety suspensions")
  (is (nil? (facts/notice-period-days-for :JPN :payment-delinquency))
    "Japan's catalog entry does not model a notice-period safeguard -- must not be fabricated"))

(deftest ^{:doc "Verify France's district-heating requirements -- a genuinely differently-
  shaped regime (mandatory connection to classified networks) from JPN/USA/GBR's
  customer-facing metering/disclosure rules."} france-requirements
  (let [fra-reqs (facts/requirement-citations :FRA)]
    (is (contains? fra-reqs :mandatory-connection) "Should require mandatory connection")
    (is (contains? fra-reqs :network-classification-disclosure) "Should require classification disclosure")
    (is (every? :spec-basis (vals fra-reqs)) "Every requirement should have an official spec-basis citation"))
  (is (facts/suspension-allowed-for? :FRA :non-compliance-with-connection-obligation)
    "Should allow the L712-5 connection-obligation penalty")
  (is (not (facts/suspension-allowed-for? :FRA :payment-delinquency))
    "France has no verified operator-side payment-delinquency suspension power -- must not be fabricated as true"))

(deftest ^{:doc "Verify Japan thermal supply requirements."} japan-requirements
  (let [jpn-reqs (facts/requirement-citations :JPN)]
    (is (contains? jpn-reqs :customer-verification) "Should require customer verification")
    (is (contains? jpn-reqs :thermal-meter-inspection) "Should require thermal meter inspection")
    (is (contains? jpn-reqs :safety-information) "Should require safety information")))

(deftest ^{:doc "Verify evidence satisfaction logic."} required-evidence
  (is (facts/required-evidence-satisfied? :JPN
        {:customer-id-proof true :thermal-meter-cert true :heat-exchanger-cert true
         :address-proof true :contact-info true
         :thermal-safety-brochure-provided true})
    "Should satisfy Japan requirements")
  (is (not (facts/required-evidence-satisfied? :JPN
             {:customer-id-proof true}))
    "Should reject incomplete evidence"))

(deftest ^{:doc "Verify suspension reason validation."} suspension-allowed
  (is (facts/suspension-allowed-for? :JPN :payment-delinquency)
    "Should allow payment suspension in Japan")
  (is (facts/suspension-allowed-for? :JPN :safety-violation)
    "Should allow safety suspension in Japan")
  (is (not (facts/suspension-allowed-for? :JPN :invalid-reason))
    "Should reject invalid suspension reason"))

(deftest ^{:doc "Verify coverage reporting is honest."} coverage-report
  (let [cov (facts/coverage)]
    (is (contains? cov :implemented) "Should report implemented count")
    (is (contains? cov :worldwide-jurisdictions) "Should report worldwide jurisdictions")
    (is (< (:coverage-pct cov) 100) "Should be less than 100% coverage (honest)")))

;; ───────── Evidence gate must fail closed (2026-07-25) ─────────

(deftest unknown-jurisdiction-fails-the-evidence-gate-closed
  ;; get-in returns nil for a jurisdiction absent from `catalog`, and
  ;; `(every? f nil)` is true, so this predicate used to pass VACUOUSLY --
  ;; a subject with an unrecognised jurisdiction cleared the Governor's
  ;; :evidence-incomplete gate carrying an empty checklist.
  (is (false? (facts/required-evidence-satisfied? :atlantis #{}))
      "an unknown jurisdiction must never satisfy the evidence requirements")
  (is (false? (facts/required-evidence-satisfied? :atlantis #{:anything}))
      "and must not be rescued by supplying unrelated evidence")
  (is (false? (facts/required-evidence-satisfied? nil #{}))
      "a missing jurisdiction is not a pass either"))

(deftest known-jurisdictions-still-evaluate-normally
  ;; Guards against "fixed" by making everything false.
  (let [jurisdictions (keys facts/catalog)]
    (is (seq jurisdictions) "catalog must be non-empty for this test to mean anything")
    (doseq [j jurisdictions]
      (is (boolean? (facts/required-evidence-satisfied? j #{}))
          (str j " must still produce a boolean verdict"))
      (is (true? (facts/required-evidence-satisfied?
                  j
                  (set (mapcat (comp :evidence val)
                               (get-in facts/catalog [j :requirements])))))
          (str j " must be satisfiable when every listed evidence key is present")))))

(deftest string-jurisdictions-resolve-to-the-catalog
  ;; Subject records carry :jurisdiction as a STRING while catalog is keyed by
  ;; keyword. Until 2026-07-25 nothing bridged the two, so the Governor's only
  ;; catalog lookup missed for EVERY real subject and the evidence gate was
  ;; dead code.
  (is (= :JPN (facts/normalize-jurisdiction "JPN")))
  (is (= :JPN (facts/normalize-jurisdiction :JPN)))
  (is (nil? (facts/normalize-jurisdiction 42)) "unusable values fail closed")
  (is (nil? (facts/normalize-jurisdiction nil)))

  (testing "the string form now sees the same requirements as the keyword form"
    (is (= (facts/requirement-citations :JPN)
           (facts/requirement-citations "JPN")))
    (is (seq (facts/requirement-citations "JPN"))
        "a string jurisdiction used to resolve to nil -- that was the dead gate")))

(deftest evidence-gate-actually-bites-for-string-jurisdictions
  (let [required (set (mapcat (comp :evidence val)
                              (facts/requirement-citations "JPN")))]
    (is (seq required) "there must be real requirements to satisfy")
    (is (true? (facts/required-evidence-satisfied? "JPN" required))
        "a complete checklist passes")
    (is (false? (facts/required-evidence-satisfied? "JPN" #{}))
        "an EMPTY checklist must now fail -- it silently passed before")
    (doseq [k required]
      (is (false? (facts/required-evidence-satisfied? "JPN" (disj required k)))
          (str "dropping " k " must fail the gate")))))
