(ns steam.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: before this namespace
  existed there was no demo page and no generator at all.

  Everything on the generated page is REAL actor output. This file
  drives the actual compiled langgraph StateGraph
  (`steam.operation/build` -> `steam.advisor` -> `steam.governor` ->
  `steam.store`) with `langgraph.graph/run*`, resumes the
  `interrupt-before #{:request-approval}` gate exactly the way a human
  thermal operator would, and then renders the resulting store, ledger
  and per-run audit trail. No status, id, count, citation or hold
  reason on the page is a literal typed by hand:

    - customer rows come from `steam.store/all-customers`
    - ledger rows come from `steam.store/ledger`
    - hold reasons (`:rule`/`:detail`) come from `steam.governor`'s own
      violation maps, as emitted by the graph's `:decide` node
    - the phase table is rendered straight out of `steam.phase/phases`
    - the confidence floor and the high-stakes op set are read from
      `steam.governor/confidence-floor` and `steam.governor/high-stakes`
    - jurisdiction coverage comes from `steam.facts/coverage` and
      `steam.facts/catalog`
    - the `meter/verify` citation is resolved out of this repo's own
      `steam.facts/catalog` for the customer's real jurisdiction rather
      than typed into the request

  Determinism: the scenario is a fixed sequence against the fixed
  `steam.store/demo-data` seed, there is no clock, no random id and no
  timestamp anywhere in the page body, and every collection rendered is
  either an append-only vector or explicitly sorted. Two consecutive
  runs are byte-identical.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [steam.advisor :as advisor]
            [steam.facts :as facts]
            [steam.governor :as governor]
            [steam.operation :as op]
            [steam.phase :as phase]
            [steam.store :as store]))

;; ----------------------------- scenario -----------------------------

(def ^:private operator
  "The human thermal operator this run is attributed to. Constant, not a
  generated id -- the page must stay byte-identical across reruns."
  {:actor-id "op-1" :actor-role :thermal-operator})

(def ^:private approved
  {:status :approved :by "op-1"})

(defrecord UncitedAdvisor [inner]
  advisor/Advisor
  (intake [_ customer-id jurisdiction]
    (advisor/intake inner customer-id jurisdiction))
  (verify-meter [_ customer-id jurisdiction]
    (advisor/verify-meter inner customer-id jurisdiction))
  (provision-proposal [_ customer-id]
    ;; A draft that names no official spec basis. This is the failure the
    ;; Thermal Safety Governor's FIRST hard gate exists for: an advisor
    ;; that recommends an actuation without citing the jurisdiction rule
    ;; it relies on. The governor -- not this record -- decides what
    ;; happens to it.
    (-> (advisor/provision-proposal inner customer-id)
        (assoc :cites [] :spec-basis nil)
        (assoc-in [:value :cites] [])
        (assoc-in [:value :spec-basis] nil)))
  (suspension-proposal [_ customer-id reason]
    (advisor/suspension-proposal inner customer-id reason)))

(defn- verify-citation
  "The spec-basis argument a `:meter/verify` request carries, resolved out
  of this repo's own jurisdiction catalog for the customer's real
  jurisdiction (`steam.facts/requirement-citations` ->
  `:thermal-meter-inspection`) instead of being typed here. Returns the
  requirement map, which is the shape `steam.advisor/verify-meter`
  reads `:spec-basis` off."
  [st customer-id]
  (-> (store/customer st customer-id)
      :jurisdiction
      facts/requirement-citations
      :thermal-meter-inspection))

(defn- run-op!
  "Executes ONE operation through a real compiled actor graph.

  `actor` is `{:actor <compiled graph> :phase <phase-num> :advisor <label>}`.
  When the graph interrupts at `:request-approval` and `approval` is
  non-nil, resumes it the way a human operator would. Returns a record of
  what the actor actually did -- nothing here is decided by this
  function, it only observes."
  [{:keys [actor phase advisor-label]} thread-id request approval]
  (let [first-pass (g/run* actor {:request request :context operator}
                           {:thread-id thread-id})
        escalated? (= :interrupted (:status first-pass))
        final (if (and escalated? approval)
                (g/run* actor {:approval approval}
                        {:thread-id thread-id :resume? true})
                first-pass)
        audit (vec (get-in final [:state :audit]))]
    {:thread        thread-id
     :phase         phase
     :advisor-label advisor-label
     :op            (:op request)
     :subject       (:subject request)
     :escalated?    escalated?
     :approval      approval
     :status        (:status final)
     :disposition   (get-in final [:state :disposition])
     :audit         audit
     :hold          (first (filter #(= :governor-hold (:t %)) audit))
     :escalation    (first (filter #(= :approval-requested (:t %)) audit))}))

(defn run-demo!
  "Drives a freshly seeded store through a scenario that reaches every
  disposition this actor can produce, and every HARD gate its governor
  owns. Returns `{:store st :runs [...]}` -- both are real output.

  Clean / approved paths:
    - cust-1 customer intake: the mock advisor's intake draft carries no
      confidence, so the governor's confidence floor catches it, a human
      approves, it commits.
    - cust-1 meter verification: confident enough for the governor, but
      phase 3 has an EMPTY `:auto` set, so the phase gate escalates it;
      a human approves, it commits.
    - cust-1 supply provisioning: always high-stakes, always escalates
      at any phase; a human approves, the supply is provisioned.
    - cust-3 meter verification through a PHASE-2 actor: `:meter/verify`
      IS in phase 2's `:auto` set, so this one auto-commits with no human
      in the loop at all -- the only run on the page that does.
    - cust-3 supply suspension for payment delinquency: escalates,
      approved, committed.

  HARD holds (never reach a human, cannot be overridden):
    - re-provisioning cust-1            -> :already-provisioned
    - re-suspending cust-3              -> :already-suspended
    - suspending cust-2 (hospital)      -> :evidence-incomplete AND
                                           :protected-recipient
    - provisioning cust-4               -> :evidence-incomplete
      (the seeded verification for cust-2 and cust-4 is missing
      `:address-proof`, which :JPN's customer-verification requirement
      lists as required evidence)
    - provisioning cust-3 from an advisor that cited nothing
                                        -> :no-spec-basis

  Still pending:
    - cust-2 meter verification is escalated and deliberately NOT
      approved, so the console shows a live approval queue entry."
  []
  (let [st (store/mem-store)
        a3 {:actor (op/build st) :phase 3 :advisor-label "mock"}
        a2 {:actor (op/build st {:phase-num 2}) :phase 2 :advisor-label "mock"}
        au {:actor (op/build st {:advisor (->UncitedAdvisor (advisor/mock-advisor))})
            :phase 3 :advisor-label "uncited"}
        runs
        [(run-op! a3 "intake-cust-1"
                  {:op :customer/intake :subject "cust-1"
                   :jurisdiction (verify-citation st "cust-1")}
                  approved)
         (run-op! a3 "verify-cust-1"
                  {:op :meter/verify :subject "cust-1"
                   :jurisdiction (verify-citation st "cust-1")}
                  approved)
         (run-op! a3 "provision-cust-1"
                  {:op :actuation/provision-supply :subject "cust-1"}
                  approved)
         (run-op! a3 "reprovision-cust-1"
                  {:op :actuation/provision-supply :subject "cust-1"}
                  approved)
         (run-op! a2 "verify-cust-3-phase2"
                  {:op :meter/verify :subject "cust-3"
                   :jurisdiction (verify-citation st "cust-3")}
                  nil)
         (run-op! a3 "suspend-cust-3"
                  {:op :actuation/suspend-supply :subject "cust-3"
                   :reason :payment-delinquency}
                  approved)
         (run-op! a3 "resuspend-cust-3"
                  {:op :actuation/suspend-supply :subject "cust-3"
                   :reason :payment-delinquency}
                  approved)
         (run-op! au "uncited-provision-cust-3"
                  {:op :actuation/provision-supply :subject "cust-3"}
                  approved)
         (run-op! a3 "verify-cust-2"
                  {:op :meter/verify :subject "cust-2"
                   :jurisdiction (verify-citation st "cust-2")}
                  nil)
         (run-op! a3 "suspend-cust-2"
                  {:op :actuation/suspend-supply :subject "cust-2"
                   :reason :payment-delinquency}
                  approved)
         (run-op! a3 "provision-cust-4"
                  {:op :actuation/provision-supply :subject "cust-4"}
                  approved)]]
    {:store st :runs (vec runs)}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (if (nil? v) "" (str v))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(def ^:private dash "<span class=\"muted\">&mdash;</span>")

(defn- kw-str [k]
  (cond
    (keyword? k) (if-let [ns' (namespace k)] (str ns' "/" (name k)) (name k))
    :else (str k)))

(defn- code [v] (str "<code>" (esc (kw-str v)) "</code>"))

(defn- yes-no [b ok-class]
  (if b
    (str "<span class=\"" ok-class "\">yes</span>")
    "<span class=\"muted\">no</span>"))

(defn- cite-list [cites]
  (let [xs (remove nil? (or cites []))]
    (if (seq xs) (esc (str/join "; " xs)) dash)))

(defn- pct1 [x]
  (String/format java.util.Locale/ROOT "%.1f" (into-array Object [(double x)])))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" % "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- card [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       (if lede (str "    <p class=\"muted\">" lede "</p>\n") "")
       body
       "  </section>\n"))

;; ----------------------------- derived views (all from real output) -----------------------------

(defn- last-ledger-fact [ledger customer-id]
  (last (filter #(= (:subject %) customer-id) ledger)))

(defn- customer-status-cell [ledger customer-id]
  (let [f (last-ledger-fact ledger customer-id)]
    (cond
      (nil? f) "<span class=\"muted\">no committed activity</span>"
      (= :governor-hold (:t f))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (str/join ", " (map #(kw-str (:rule %)) (:violations f))))
           "</span>")
      (= :committed (:t f))
      (str "<span class=\"ok\">committed &middot; " (esc (kw-str (:op f))) "</span>")
      :else (str "<span class=\"muted\">" (esc (kw-str (:t f))) "</span>"))))

(defn- customer-row [ledger {:keys [id customer-name meter-id usage-profile
                                    protected-recipient? jurisdiction
                                    supply-provisioned? supply-suspended?]}]
  (str "        <tr><td>" (esc id) "</td><td>" (esc customer-name) "</td>"
       "<td>" (esc meter-id) "</td>"
       "<td>" (code usage-profile) "</td>"
       "<td>" (esc jurisdiction) "</td>"
       "<td>" (yes-no protected-recipient? "critical") "</td>"
       "<td>" (yes-no supply-provisioned? "ok") "</td>"
       "<td>" (yes-no supply-suspended? "warn") "</td>"
       "<td>" (customer-status-cell ledger id) "</td></tr>"))

(defn- run-outcome-cell [{:keys [hold escalated? approval disposition]}]
  (cond
    hold        "<span class=\"critical\">HARD hold &middot; never reached a human</span>"
    (and escalated? (nil? approval)) "<span class=\"warn\">awaiting operator approval</span>"
    (and escalated? (= :commit disposition)) "<span class=\"ok\">approved by operator &amp; committed</span>"
    (= :commit disposition) "<span class=\"ok\">auto-committed &middot; no human in the loop</span>"
    :else (str "<span class=\"muted\">" (esc (kw-str disposition)) "</span>")))

(defn- run-reason-cell [{:keys [hold escalation phase op]}]
  (cond
    hold
    (esc (str/join " / " (map #(str (kw-str (:rule %)) " — " (:detail %))
                              (:violations hold))))

    escalation
    (let [r (:reason escalation)]
      (if (coll? r)
        (esc (str/join " / " (map #(str (kw-str (:rule %)) " — " (:detail %)) r)))
        (esc (kw-str r))))

    (phase/can-auto-commit? phase op)
    (str "<span class=\"ok\">in phase " phase "&rsquo;s <code>:auto</code> set</span>")

    :else dash))

(defn- run-row [{:keys [thread phase advisor-label op subject] :as r}]
  (str "        <tr><td><code>" (esc thread) "</code></td>"
       "<td>" phase "</td>"
       "<td>" (esc advisor-label) "</td>"
       "<td>" (code op) "</td>"
       "<td>" (esc subject) "</td>"
       "<td>" (run-outcome-cell r) "</td>"
       "<td>" (run-reason-cell r) "</td></tr>"))

(defn- ledger-row [i {:keys [t op subject basis violations]}]
  (str "        <tr><td>" i "</td>"
       "<td>" (if (= :governor-hold t)
                (str "<span class=\"critical\">" (esc (kw-str t)) "</span>")
                (str "<span class=\"ok\">" (esc (kw-str t)) "</span>"))
       "</td>"
       "<td>" (code op) "</td>"
       "<td>" (esc subject) "</td>"
       "<td>" (if (seq violations)
                (esc (str/join ", " (map #(kw-str (:rule %)) violations)))
                (cite-list basis))
       "</td></tr>"))

(defn- observed-gate-rows
  "Every distinct HARD gate this run actually tripped, with the governor's
  own rule keyword and detail text. Derived from the ledger, not listed
  by hand -- if a gate never fires it does not appear here."
  [ledger]
  (->> ledger
       (filter #(= :governor-hold (:t %)))
       (mapcat :violations)
       (map (juxt :rule :detail))
       distinct
       (sort-by (comp str first))
       (map (fn [[rule detail]]
              (str "        <tr><td>" (code rule) "</td>"
                   "<td>" (esc detail) "</td>"
                   "<td><span class=\"critical\">HARD &middot; not overridable</span></td></tr>")))))

(defn- phase-rows []
  (map (fn [{:keys [phase name description auto human-approval-required]}]
         (str "        <tr><td>" phase "</td>"
              "<td>" (code name) "</td>"
              "<td>" (esc description) "</td>"
              "<td>" (if (seq auto)
                       (str/join " " (map code (sort-by str auto)))
                       dash) "</td>"
              "<td>" (if (seq human-approval-required)
                       (str/join " " (map code (sort-by str human-approval-required)))
                       dash) "</td></tr>"))
       phase/phases))

(defn- jurisdiction-rows []
  (->> facts/catalog
       (sort-by (comp str key))
       (map (fn [[k v]]
              (let [reqs (:requirements v)
                    susp (:suspension-requirements v)]
                (str "        <tr><td>" (code k) "</td>"
                     "<td>" (esc (:name v)) "</td>"
                     "<td>" (count reqs) "</td>"
                     "<td>" (if (seq susp)
                              (str/join " " (map code (sort-by str (keys susp))))
                              dash) "</td>"
                     "<td>" (esc (str/join "; " (sort (map (comp :spec-basis val) reqs))))
                     "</td></tr>"))))))

;; ----------------------------- page -----------------------------

(defn render
  "Renders the full operator console from the real `{:store :runs}` a
  `run-demo!` produced."
  [{:keys [store runs]}]
  (let [ledger (vec (store/ledger store))
        customers (sort-by :id (store/all-customers store))
        holds (filter #(= :governor-hold (:t %)) ledger)
        commits (filter #(= :committed (:t %)) ledger)
        approved-runs (filter #(and (:escalated? %) (= :commit (:disposition %))) runs)
        auto-runs (filter #(and (not (:escalated? %)) (= :commit (:disposition %))) runs)
        pending-runs (filter #(and (:escalated? %) (nil? (:approval %))) runs)
        cov (facts/coverage)]
    (str
     "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-3530 &middot; steam and air conditioning supply</title>\n"
     "<style>\n" (jp-go-dds.skin/dds+skin) "\n</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Steam and air conditioning supply (ISIC 3530) &mdash; Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample &middot; governor-gated &middot; provisioning and suspension are never autonomous</span>\n"
     "</header>\n"
     "<main>\n"

     (card "This run"
           (str "Build-time snapshot generated from the real actor by "
                "<code>steam.render-html</code> (<code>clojure -M:dev:render-html</code>). "
                "Every value below is output of the compiled "
                "<code>steam.operation</code> StateGraph running against the "
                "<code>steam.store/demo-data</code> seed &mdash; no hand-written figures, no timestamps, "
                "byte-identical on rerun.")
           (table ["Measure" "Count"]
                  [(str "        <tr><td>operations executed</td><td>" (count runs) "</td></tr>")
                   (str "        <tr><td>ledger facts appended</td><td>" (count ledger) "</td></tr>")
                   (str "        <tr><td><span class=\"critical\">HARD governor holds</span></td><td>"
                        (count holds) "</td></tr>")
                   (str "        <tr><td><span class=\"ok\">commits</span></td><td>" (count commits) "</td></tr>")
                   (str "        <tr><td>&nbsp;&nbsp;&middot; approved by a human operator</td><td>"
                        (count approved-runs) "</td></tr>")
                   (str "        <tr><td>&nbsp;&nbsp;&middot; auto-committed by the phase gate</td><td>"
                        (count auto-runs) "</td></tr>")
                   (str "        <tr><td><span class=\"warn\">awaiting operator approval</span></td><td>"
                        (count pending-runs) "</td></tr>")
                   (str "        <tr><td>supplies provisioned</td><td>"
                        (count (store/provision-history store)) "</td></tr>")
                   (str "        <tr><td>supplies suspended</td><td>"
                        (count (store/suspension-history store)) "</td></tr>")]))

     (card "Customer meters"
           "State of the SSoT after this run. <code>provisioned</code> and <code>suspended</code> are the store's own double-actuation guards, flipped only by a committed actuation."
           (table ["Customer" "Name" "Meter" "Profile" "Jurisdiction"
                   "Protected recipient" "Provisioned" "Suspended" "Last ledger fact"]
                  (map (partial customer-row ledger) customers)))

     (card "Operations this run"
           "One row per graph run. <em>HARD hold</em> means the Thermal Safety Governor stopped the proposal at <code>:decide</code> and it never reached the human approval gate at all."
           (table ["Thread" "Phase" "Advisor" "Op" "Customer" "Outcome" "Governor / phase reason"]
                  (map run-row runs)))

     (card "HARD gates tripped in this run"
           "Rule keyword and detail text taken verbatim from the violation maps <code>steam.governor</code> emitted. A gate that never fired is not listed."
           (table ["Rule" "Governor detail" "Overridable?"]
                  (observed-gate-rows ledger)))

     (card "Governor configuration"
           "Read live out of <code>steam.governor</code> and <code>steam.phase</code>."
           (table ["Setting" "Value"]
                  [(str "        <tr><td>confidence floor</td><td>" governor/confidence-floor "</td></tr>")
                   (str "        <tr><td>always high-stakes (human required at every phase)</td><td>"
                        (str/join " " (map code (sort-by str governor/high-stakes))) "</td></tr>")
                   (str "        <tr><td>actuation never auto-commits at any phase</td><td>"
                        (if (phase/actuation-never-auto?)
                          "<span class=\"ok\">verified true</span>"
                          "<span class=\"critical\">VIOLATED</span>") "</td></tr>")]))

     (card "Phase table"
           "Rendered directly from <code>steam.phase/phases</code>. Note that neither actuation op appears in any phase&rsquo;s <code>:auto</code> column."
           (table ["Phase" "Name" "Description" "Auto-commit" "Human approval required"]
                  (phase-rows)))

     (card "Audit ledger"
           "The store&rsquo;s append-only decision-fact log, in order. Commits carry the citation basis the proposal relied on; holds carry the governor rules that stopped them."
           (table ["#" "Fact" "Op" "Customer" "Basis / rules"]
                  (map-indexed (fn [i f] (ledger-row (inc i) f)) ledger)))

     (card "Jurisdiction catalog"
           (str "Coverage is reported honestly: <strong>" (:implemented cov) "</strong> of "
                (:worldwide-jurisdictions cov) " jurisdictions (" (pct1 (:coverage-pct cov))
                "%). " (esc (:note cov)))
           (table ["Code" "Jurisdiction" "Requirements" "Suspension grounds" "Spec basis"]
                  (jurisdiction-rows)))

     "</main>\n</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [store runs] :as result} (run-demo!)
        ledger (vec (store/ledger store))
        holds (filter #(= :governor-hold (:t %)) ledger)
        commits (filter #(= :committed (:t %)) ledger)]
    ;; Build-time invariant, not a convention: this console exists to show
    ;; that the Thermal Safety Governor can refuse. A page rendered from a
    ;; run where nothing was ever refused would be a demo of nothing, so
    ;; refuse to write it.
    (when (zero? (count holds))
      (throw (ex-info
              (str "REFUSING to write " out
                   ": the scenario produced ZERO :governor-hold ledger entries. "
                   "The operator console must demonstrate at least one HARD governor hold "
                   "(a refusal that never reaches a human). Fix steam.render-html/run-demo! "
                   "so the governor is actually exercised.")
              {:out out
               :ledger-facts (count ledger)
               :governor-holds 0
               :ops-run (count runs)})))
    (let [f (io/file out)]
      (when-let [parent (.getParentFile f)]
        (.mkdirs parent))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count runs) " operations, "
                  (count ledger) " ledger facts, "
                  (count holds) " HARD governor holds, "
                  (count commits) " commits, "
                  (count (store/provision-history store)) " provisioned, "
                  (count (store/suspension-history store)) " suspended)"))))
