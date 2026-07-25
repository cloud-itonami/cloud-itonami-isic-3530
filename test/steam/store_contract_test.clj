(ns steam.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [steam.store :as store]
            [steam.registry :as registry]))

(deftest ^{:doc "Test MemStore customer lookup."} mem-store-customer-access
  (let [st (store/mem-store)]
    (is (some? (store/customer st "cust-1")) "Should find customer")
    (is (nil? (store/customer st "nonexistent")) "Should return nil for missing customer")))

(deftest ^{:doc "Test MemStore all-customers listing."} mem-store-all-customers
  (let [st (store/mem-store)
        all (store/all-customers st)]
    (is (seq all) "Should have customers")
    (is (= (count all) 4) "Should have 4 demo customers")))

(deftest ^{:doc "Test MemStore meter verification access."} mem-store-verification
  (let [st (store/mem-store)
        verification (store/meter-verification-of st "cust-1")]
    (is (some? verification) "Should find verification")
    (is (contains? verification :checklist) "Should have checklist")))

(deftest ^{:doc "Test MemStore provision history append."} mem-store-provision-history
  (let [st (store/mem-store)
        hist-before (store/provision-history st)
        proposal (registry/provision-draft "cust-1" ["Test citation"]
                   {:customer-id-proof true} 0.9 "test")
        _ (store/commit-record! st proposal)
        hist-after (store/provision-history st)]
    (is (< (count hist-before) (count hist-after))
      "History should grow after commit")))

(deftest ^{:doc "Test double-provisioning prevention."} mem-store-double-provision-guard
  (let [st (store/mem-store)
        before (store/customer-already-provisioned? st "cust-1")
        proposal (registry/provision-draft "cust-1" ["Test citation"]
                   {:customer-id-proof true} 0.9 "test")
        _ (store/commit-record! st proposal)
        after (store/customer-already-provisioned? st "cust-1")]
    (is (false? before) "Should not be provisioned initially")
    (is (true? after) "Should be provisioned after commit")))

(deftest ^{:doc "Test immutable ledger append."} mem-store-ledger-append
  (let [st (store/mem-store)
        ledger-before (store/ledger st)
        fact {:op :test/event :detail "test"}
        _ (store/append-ledger! st fact)
        ledger-after (store/ledger st)]
    (is (= (inc (count ledger-before)) (count ledger-after))
      "Ledger should grow")))

(deftest ^{:doc "Test provision/suspension sequence numbering."} mem-store-sequence-counters
  (let [st (store/mem-store)
        seq1 (store/next-provision-sequence st :JPN)
        seq2 (store/next-provision-sequence st :JPN)]
    (is (= seq1 1) "First sequence should be 1")
    (is (= seq2 2) "Second sequence should be 2")))
