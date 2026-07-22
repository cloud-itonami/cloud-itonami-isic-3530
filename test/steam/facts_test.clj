(ns steam.facts-test
  (:require [clojure.test :refer [deftest is]]
            [steam.facts :as facts]))

(deftest jurisdiction-coverage
  "Verify jurisdiction catalog has expected structure."
  (is (seq facts/catalog) "Catalog should not be empty")
  (is (contains? facts/catalog :JPN) "Should have Japan jurisdiction")
  (is (contains? facts/catalog :USA) "Should have USA jurisdiction")
  (is (contains? facts/catalog :GBR) "Should have UK jurisdiction")
  (is (contains? facts/catalog :FRA) "Should have France jurisdiction")
  (is (contains? facts/catalog :DEU) "Should have Germany jurisdiction"))

(deftest germany-requirements
  "Verify Germany's AVBFernwärmeV requirements -- a genuinely differently-shaped
  regime from JPN/USA/GBR/FRA: a procedural/proportionality safeguard on
  payment-delinquency disconnection (2-week notice-after-warning, waivable),
  distinct from the flat :allowed-true shape used elsewhere."
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

(deftest france-requirements
  "Verify France's district-heating requirements -- a genuinely differently-
  shaped regime (mandatory connection to classified networks) from JPN/USA/GBR's
  customer-facing metering/disclosure rules."
  (let [fra-reqs (facts/requirement-citations :FRA)]
    (is (contains? fra-reqs :mandatory-connection) "Should require mandatory connection")
    (is (contains? fra-reqs :network-classification-disclosure) "Should require classification disclosure")
    (is (every? :spec-basis (vals fra-reqs)) "Every requirement should have an official spec-basis citation"))
  (is (facts/suspension-allowed-for? :FRA :non-compliance-with-connection-obligation)
    "Should allow the L712-5 connection-obligation penalty")
  (is (not (facts/suspension-allowed-for? :FRA :payment-delinquency))
    "France has no verified operator-side payment-delinquency suspension power -- must not be fabricated as true"))

(deftest japan-requirements
  "Verify Japan thermal supply requirements."
  (let [jpn-reqs (facts/requirement-citations :JPN)]
    (is (contains? jpn-reqs :customer-verification) "Should require customer verification")
    (is (contains? jpn-reqs :thermal-meter-inspection) "Should require thermal meter inspection")
    (is (contains? jpn-reqs :safety-information) "Should require safety information")))

(deftest required-evidence
  "Verify evidence satisfaction logic."
  (is (facts/required-evidence-satisfied? :JPN
        {:customer-id-proof true :thermal-meter-cert true :heat-exchanger-cert true
         :address-proof true :contact-info true
         :thermal-safety-brochure-provided true})
    "Should satisfy Japan requirements")
  (is (not (facts/required-evidence-satisfied? :JPN
             {:customer-id-proof true}))
    "Should reject incomplete evidence"))

(deftest suspension-allowed
  "Verify suspension reason validation."
  (is (facts/suspension-allowed-for? :JPN :payment-delinquency)
    "Should allow payment suspension in Japan")
  (is (facts/suspension-allowed-for? :JPN :safety-violation)
    "Should allow safety suspension in Japan")
  (is (not (facts/suspension-allowed-for? :JPN :invalid-reason))
    "Should reject invalid suspension reason"))

(deftest coverage-report
  "Verify coverage reporting is honest."
  (let [cov (facts/coverage)]
    (is (contains? cov :implemented) "Should report implemented count")
    (is (contains? cov :worldwide-jurisdictions) "Should report worldwide jurisdictions")
    (is (< (:coverage-pct cov) 100) "Should be less than 100% coverage (honest)")))
