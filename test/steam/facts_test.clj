(ns steam.facts-test
  (:require [clojure.test :refer [deftest is]]
            [steam.facts :as facts]))

(deftest jurisdiction-coverage
  "Verify jurisdiction catalog has expected structure."
  (is (seq facts/catalog) "Catalog should not be empty")
  (is (contains? facts/catalog :JPN) "Should have Japan jurisdiction")
  (is (contains? facts/catalog :USA) "Should have USA jurisdiction")
  (is (contains? facts/catalog :GBR) "Should have UK jurisdiction")
  (is (contains? facts/catalog :FRA) "Should have France jurisdiction"))

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
