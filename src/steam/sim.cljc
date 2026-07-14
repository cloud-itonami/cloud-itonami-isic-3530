(ns steam.sim
  "Simulation and demo driver for the thermal supply actor.")

#?(:clj
   (do
     (require '[steam.store :as store]
              '[steam.advisor :as advisor]
              '[steam.operation :as operation])
     (defn -main [& args]
       (println "Thermal Supply Actor (ISIC 3530) - Demo")
       (println "========================================")
       (let [st (store/mem-store)
             adv (advisor/mock-advisor)]
         (println "\nDemo: Customer intake and provision proposal")
         (let [result (operation/run-operation st adv "cust-1" :provision)]
           (println "Result:" result))
         (println "\nDemo: Protected recipient suspension guard")
         (let [result (operation/run-operation st adv "cust-2" :suspension)]
           (println "Result - should hold due to protected-recipient:"
             (if (:hold-reason result) "HELD ✓" "ERROR"))))))
   :cljs
   (defn sim [] (println "Simulation driver runs in JVM only")))
