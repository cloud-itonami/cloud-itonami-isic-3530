(ns steam.phase-test
  (:require [clojure.test :refer [deftest is]]
            [steam.phase :as phase]))

(deftest ^{:doc "Test phase lookup by number."} phase-by-number
  (is (some? (phase/phase-by-number 0)) "Should find phase 0")
  (is (= (:name (phase/phase-by-number 0)) :read-only) "Phase 0 should be read-only")
  (is (= (:name (phase/phase-by-number 3)) :supervised) "Phase 3 should be supervised"))

(deftest ^{:doc "Test phase auto-commit eligibility."} auto-commit-logic
  (is (not (phase/can-auto-commit? 0 :customer/intake)) "Phase 0 should not auto-commit intake")
  (is (phase/can-auto-commit? 1 :customer/intake) "Phase 1 should auto-commit intake")
  (is (phase/can-auto-commit? 2 :meter/verify) "Phase 2 should auto-commit meter verification"))

(deftest ^{:doc "Test human approval requirements by phase."} human-approval-required
  (is (phase/can-human-approve? 0 :site/view) "Phase 0 should allow view approval")
  (is (phase/can-human-approve? 1 :meter/verify) "Phase 1 should require meter verification")
  (is (phase/can-human-approve? 2 :actuation/provision-supply) "Phase 2 should require provision approval")
  (is (phase/can-human-approve? 3 :actuation/suspend-supply) "Phase 3 should require suspension approval"))

(deftest ^{:doc "CRITICAL: Actuation operations must NEVER auto-commit at any phase."} actuation-never-auto-critical
  (is (phase/actuation-never-auto?) "Actuation should never be in any :auto set")
  ;; Explicit checks
  (is (not (phase/can-auto-commit? 0 :actuation/provision-supply)))
  (is (not (phase/can-auto-commit? 1 :actuation/provision-supply)))
  (is (not (phase/can-auto-commit? 2 :actuation/provision-supply)))
  (is (not (phase/can-auto-commit? 3 :actuation/provision-supply)))
  (is (not (phase/can-auto-commit? 0 :actuation/suspend-supply)))
  (is (not (phase/can-auto-commit? 1 :actuation/suspend-supply)))
  (is (not (phase/can-auto-commit? 2 :actuation/suspend-supply)))
  (is (not (phase/can-auto-commit? 3 :actuation/suspend-supply))))
