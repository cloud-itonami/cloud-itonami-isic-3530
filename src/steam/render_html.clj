(ns steam.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL actor stack -- `steam.operation/build` (a genuinely
  compiled langgraph-clj StateGraph) over the REAL seeded
  `steam.store/mem-store`, through the REAL Thermal Safety Governor
  (`steam.governor/evaluate`) and the REAL rollout phase gate
  (`steam.phase`) -- and renders whatever those actually produced.

  Nothing on the page is a hand-typed result:

    - every ledger row is read back out of the store after the run
      (`store/ledger`, `store/all-customers`, `store/provision-history`,
      `store/suspension-history`),
    - every HARD-hold rule name and every violation detail string is the
      governor's OWN `:violations` entry off the ledger fact -- never a
      literal in this namespace,
    - the phase table is derived from `steam.phase/phases`, the governor
      configuration from `steam.governor`'s public vars, and the
      jurisdiction/evidence tables from `steam.facts/catalog` +
      `steam.facts/coverage` + `steam.facts/required-evidence-satisfied?`
      re-run independently over each stored verification.

  The single exception is `op-contract-rows`, a static description of this
  actor's fixed op-gate contract; it is labelled as such at its definition.

  Subject provenance: every subject driven below is one of the four
  customers `steam.store/demo-data` actually seeds (`cust-1` `cust-2`
  `cust-3` `cust-4`). This demo invents no subject -- `store/with-customers`
  is never called, so the seed the page describes is the seed the governor
  judged.

  Why this file exists rather than reusing `steam.sim`: `steam.sim/-main`
  calls `steam.operation/run-operation`, a var that does not exist in this
  repo (`clojure -M:dev:run` fails to compile on main, confirmed before
  writing this). The real entry point is `operation/build` +
  `langgraph.graph/run*`, which is what `test/steam/operation_test.cljc`
  drives and what this namespace drives too.

  Deterministic: no clock, no randomness, no network, no timestamps in the
  page. Re-running writes a byte-identical file.

  Run: `clojure -M:dev:render-html [out-file]`
  (default out-file `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [steam.advisor :as advisor]
            [steam.facts :as facts]
            [steam.governor :as governor]
            [steam.operation :as op]
            [steam.phase :as phase]
            [steam.store :as store]))

;; ----------------------------- the run -----------------------------

(def ^:private phase-num
  "The actor's own default rollout phase (`steam.operation/build`)."
  3)

(def ^:private uncited-advisor
  "A second Thermal Supply Advisor that drafts an actuation WITHOUT citing
  a standard.

  This is not a hand-made violation: `steam.operation/build` documents
  `:advisor` as a swappable seam precisely because the advisor is the
  contained, fallible intelligence node, and `steam.governor`'s first HARD
  rule (`:no-spec-basis`) exists for exactly this failure mode -- an
  advisor that recommends provisioning a customer's thermal supply while
  citing no jurisdictional authority for the recommendation. Swapping this
  advisor in is the only way to exercise that rule; the hold itself is
  still produced by the real governor on the real graph."
  (reify advisor/Advisor
    (intake [_ customer-id _jurisdiction]
      {:op :customer/intake :subject customer-id :status :success})
    (verify-meter [_ customer-id _jurisdiction]
      {:op :meter/verify :subject customer-id
       :value {:checklist {} :confidence 0.85}
       :cites []})
    (provision-proposal [_ customer-id]
      {:op :actuation/provision-supply :subject customer-id
       :value {:cites []
               :checklist {:customer-id-proof true :thermal-meter-cert true
                           :address-proof true :contact-info true}
               :confidence 0.9
               :notes "Customer looks ready; provisioning recommended"}
       :cites []})
    (suspension-proposal [_ customer-id reason]
      {:op :actuation/suspend-supply :subject customer-id
       :value {:cites [] :reason reason :checklist {} :confidence 0.8
               :notes (str "Suspension requested: " reason)}
       :cites []})))

(def ^:private scenarios
  "One entry = one coordination request driven through the real compiled
  actor. `:approval`, when present, is the human decision handed back to
  the graph paused at `interrupt-before #{:request-approval}`; `:actor`
  selects which advisor drafts the proposal (default the repo's own
  `steam.advisor/mock-advisor`)."
  [{:tid "t01"
    :exercises "Customer intake for the fully-verified office complex. The mock advisor attaches no :confidence at all, so the governor's confidence gate reads the 0.5 default, below the 0.6 floor, and soft-escalates even at a phase whose :auto set nominally contains this op. The human operator approves."
    :request {:op :customer/intake :subject "cust-1" :jurisdiction "JPN"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t02"
    :exercises "Thermal meter verification for cust-1. Governor-clean (confidence 0.85, not an actuation), but phase 3's :auto set is empty, so the phase gate alone escalates it. Approved."
    :request {:op :meter/verify :subject "cust-1" :jurisdiction "JPN"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t03"
    :exercises "Provision cust-1's supply. Cited, evidence complete, not a protected recipient, not already provisioned -- so no HARD rule fires; but provisioning is a real-world actuation, so the governor escalates regardless of confidence. Approved: this commit is what flips :supply-provisioned? in the SSoT."
    :request {:op :actuation/provision-supply :subject "cust-1"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t04"
    :exercises "The SAME provisioning, requested a second time. The double-actuation guard reads a dedicated :supply-provisioned? boolean the t03 commit wrote, never a :status value. HARD hold -- never reaches a human."
    :request {:op :actuation/provision-supply :subject "cust-1"}
    :approval {:status :approved :by "op-1"}}

   {:tid "t05"
    :exercises "Suspend the manufacturing plant for payment delinquency. Not a protected recipient, and its stored verification satisfies every JPN evidence requirement, so it clears the HARD gates and escalates as an actuation. Approved."
    :request {:op :actuation/suspend-supply :subject "cust-3" :reason :payment-delinquency}
    :approval {:status :approved :by "op-1"}}

   {:tid "t06"
    :exercises "The SAME suspension again. Guarded off its own :supply-suspended? boolean, separate from the provisioning guard. HARD hold."
    :request {:op :actuation/suspend-supply :subject "cust-3" :reason :payment-delinquency}}

   {:tid "t07"
    :exercises "Provision the district heating centre. Its seeded verification carries no :address-proof, which JPN's customer-verification requirement lists as required evidence. The governor re-derives this from steam.facts rather than believing the advisor's own checklist. HARD hold."
    :request {:op :actuation/provision-supply :subject "cust-4"}}

   {:tid "t08"
    :exercises "Suspend the hospital surgical wing. TWO HARD rules fire on one fact: a protected-recipient (life-support) meter can never be suspended, and its stored verification is also missing :address-proof. Neither is overridable."
    :request {:op :actuation/suspend-supply :subject "cust-2" :reason :payment-delinquency}
    :approval {:status :approved :by "op-1"}}

   {:tid "t09"
    :exercises "A governor-clean meter verification that a person VETOES. Distinct from a HARD hold: the governor cleared it, a human declined. This actor's :hold node only persists a :governor-hold fact, so a veto correctly leaves no compliance violation on the ledger -- it is visible here, in the run timeline, where it is real."
    :request {:op :meter/verify :subject "cust-3" :jurisdiction "JPN"}
    :approval {:status :rejected :by "op-1"}}

   {:tid "t10"
    :actor :uncited
    :exercises "An advisor recommends provisioning cust-3's supply while citing no jurisdictional authority at all. cust-3's evidence is complete and it is not yet provisioned, so this is the one rule that fires. HARD hold -- the actor never invents a jurisdiction's requirements."
    :request {:op :actuation/provision-supply :subject "cust-3"}
    :approval {:status :approved :by "op-1"}}])

(defn- drive!
  "Runs one scenario through the real compiled graph and returns the
  scenario enriched with what the graph actually did."
  [actors {:keys [tid request approval actor] :as scenario}]
  (let [a (get actors (or actor :default))
        r1 (g/run* a {:request request} {:thread-id tid})
        paused? (= :interrupted (:status r1))
        r2 (when (and approval paused?)
             (g/run* a {:approval approval} {:thread-id tid :resume? true}))
        final (:state (or r2 r1))]
    (assoc scenario
           :evaluation (:evaluation final)
           :proposal (:proposal final)
           :paused? paused?
           :escalation (first (filter #(= :approval-requested (:t %)) (:audit final [])))
           :human (when r2 (:status approval))
           :disposition (:disposition final))))

(defn run-demo!
  "Seeds a MemStore, compiles the real actor over it (twice -- once with
  this repo's own mock advisor, once with the uncited advisor above, both
  bound to the SAME store so the ledger is one append-only log), and
  drives every scenario. Returns {:db store :runs [..]}."
  []
  (let [db (store/mem-store)
        actors {:default (op/build db {:phase-num phase-num})
                :uncited (op/build db {:phase-num phase-num :advisor uncited-advisor})}]
    {:db db :runs (mapv #(drive! actors %) scenarios)}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- fmt
  "Render a stored value, or an em dash when the domain model carries no
  value for that field on that record."
  [v]
  (if (or (nil? v) (and (coll? v) (empty? v))) "&mdash;" (esc v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- codes
  "Render a SEQUENCE in the order the code produced it (e.g. `:basis`,
  whose order is the advisor's own citation order)."
  [coll]
  (if (seq (remove nil? coll))
    (str/join " " (map code (remove nil? coll)))
    "&mdash;"))

(defn- kw-codes
  "Render a SET. Sorted, because a set has no order and an unsorted render
  would make the output non-deterministic."
  [coll]
  (str/join " " (map code (sort-by str coll))))

(defn- flag [v]
  (if (true? v)
    "<span class=\"err\">yes</span>"
    "<span class=\"muted\">no</span>"))

(defn- yes-no [v]
  (if v
    "<span class=\"ok\">yes</span>"
    "<span class=\"err\">no</span>"))

(defn- tr [& cells]
  (str "<tr>" (apply str (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "<table><thead><tr>"
       (apply str (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead><tbody>\n"
       (str/join "\n" rows)
       "\n</tbody></table>"))

(defn- card [title note body]
  (str "<section class=\"card\"><h2>" (esc title) "</h2>"
       (when note (str "<p class=\"muted\">" note "</p>"))
       body "</section>"))

;; ----------------------------- derived views -----------------------------

(defn- ledger-of [db] (vec (store/ledger db)))

(defn- holds
  "Every `:governor-hold` fact on the append-only ledger."
  [db]
  (filterv #(= :governor-hold (:t %)) (ledger-of db)))

(defn- stat [label value]
  (str "<div class=\"card\"><p class=\"num\">" (esc value) "</p>"
       "<p class=\"muted\">" (esc label) "</p></div>"))

(defn- summary-section [db runs]
  (let [led (ledger-of db)
        n (fn [t] (count (filter #(= t (:t %)) led)))]
    (card "Run summary"
          (str "Every number below is a count over the actor's own append-only ledger after "
               "driving " (count runs) " requests through " (code "steam.operation/build")
               " at phase " (esc phase-num) ".")
          (str (stat "requests driven" (count runs))
               (stat "ledger facts" (count led))
               (stat "commits" (n :committed))
               (stat "governor HARD holds" (n :governor-hold))
               (stat "human approvals" (count (filter #(= :approved (:human %)) runs)))
               (stat "human vetoes" (count (filter #(= :rejected (:human %)) runs)))
               "<p class=\"muted\">A human veto produces no ledger fact in this actor: "
               (code "steam.operation") "'s " (code ":hold") " node persists a fact only when "
               "the audit channel carries a " (code ":governor-hold") ", so a declined "
               "governor-clean proposal is a decision about a person, not a recorded "
               "compliance violation. It is shown in the timeline below instead.</p>"))))

(defn- verdict-cell [{:keys [evaluation]}]
  (let [{:keys [hard-violations soft-violations]} evaluation]
    (cond
      (nil? evaluation) "<span class=\"muted\">&mdash;</span>"
      (seq hard-violations)
      (str "<span class=\"critical\">HARD</span> "
           (str/join " " (map code (map :rule hard-violations))))
      (seq soft-violations)
      (str "<span class=\"warn\">escalate</span> "
           (str/join " " (map code (map :rule soft-violations))))
      :else "<span class=\"ok\">clean</span>")))

(defn- human-cell [{:keys [approval human paused?]}]
  (cond
    (= :approved human) "<span class=\"ok\">approved</span>"
    (= :rejected human) "<span class=\"err\">vetoed</span>"
    (and approval (not paused?)) "<span class=\"muted\">never offered (no interrupt)</span>"
    :else "<span class=\"muted\">&mdash;</span>"))

(defn- disposition-cell [{:keys [disposition]}]
  (case disposition
    :commit "<span class=\"ok\">commit</span>"
    :hold "<span class=\"critical\">hold</span>"
    :escalate "<span class=\"warn\">escalate</span>"
    (str "<span class=\"muted\">" (fmt disposition) "</span>")))

(defn- timeline-section [runs]
  (card "Request timeline"
        (str "One row = one " (code "langgraph.graph/run*") " over the compiled actor. The "
             "governor column is the verdict map " (code "steam.governor/evaluate")
             " itself returned; the human column is the decision handed back to the graph "
             "while it was paused at " (code ":request-approval") ". Confidence is the "
             "advisor's own number off the proposal it drafted.")
        (table ["Thread" "Op" "Subject" "Advisor" "Confidence" "Governor" "Human" "Final"
                "What this exercises"]
               (for [{:keys [tid request actor proposal] :as r} runs]
                 (tr (code tid)
                     (code (:op request))
                     (code (:subject request))
                     (if (= :uncited actor)
                       "<span class=\"warn\">uncited</span>"
                       "<span class=\"muted\">mock</span>")
                     (fmt (get-in proposal [:value :confidence]))
                     (verdict-cell r)
                     (human-cell r)
                     (str (disposition-cell r)
                          (when-let [reason (:reason (:escalation r))]
                            (str " <span class=\"muted\">after "
                                 (if (keyword? reason)
                                   (code reason)
                                   (str/join " " (map #(code (:rule %)) reason)))
                                 "</span>")))
                     (str "<span class=\"muted\">" (esc (:exercises r)) "</span>"))))))

(defn- holds-section [db]
  (card "Governor HARD holds"
        (str "Each row is one violation inside a " (code ":governor-hold") " fact on the "
             "append-only ledger. The rule name and the detail text are the governor's own "
             (code ":violations") " entries &mdash; this page holds no rule text of its own. A "
             "HARD hold never reaches a human approver: " (code "steam.operation")
             "'s decide node routes it straight to " (code ":hold") ".")
        (table ["#" "Rule" "Op" "Subject" "Governor's own detail"]
               (let [rows (for [h (holds db) v (:violations h)] [h v])]
                 (map-indexed
                  (fn [i [h v]]
                    (tr (esc (inc i))
                        (str "<span class=\"critical\">" (esc (:rule v)) "</span>")
                        (code (:op h))
                        (code (:subject h))
                        (esc (:detail v))))
                  rows)))))

(def ^:private op-contract-rows
  ;; STATIC. The only hand-written content on this page: a description of
  ;; this actor's fixed op-gate contract (README "The core contract",
  ;; `steam.operation/proposal-for`'s closed `case`, `steam.governor`'s
  ;; docstring). It documents behaviour that is fixed in source, not
  ;; telemetry from the run -- everything else on this page is derived.
  [[":customer/intake" "Register a customer against a jurisdiction. No actuation; drafts only."]
   [":meter/verify" "Verify the thermal meter and heat-exchanger evidence for a customer. No actuation."]
   [":actuation/provision-supply" "Dispatch a REAL thermal supply provisioning. Always human-approved; guarded against double provisioning."]
   [":actuation/suspend-supply" "Dispatch a REAL supply suspension/disconnection. Always human-approved; can never touch a protected-recipient meter."]])

(defn- op-contract-section []
  (card "Op-gate contract"
        (str "The closed set of ops " (code "steam.operation/proposal-for")
             " will draft a proposal for. Anything outside it has no advisor method and "
             "cannot enter the graph. <em>This table is a static description of fixed "
             "source behaviour, not run output</em> &mdash; the per-phase gate below it is "
             "derived from " (code "steam.phase/phases") ".")
        (table ["Op" "What it does"]
               (for [[o d] op-contract-rows] (tr (code o) (esc d))))))

(defn- phase-section []
  (let [all-ops (->> phase/phases
                     (mapcat (fn [p] (concat (:auto p) (:human-approval-required p))))
                     distinct
                     (sort-by str))]
    (card "Rollout phase gate"
          (str "Derived from " (code "steam.phase/phases") ". The console above runs at phase "
               (esc phase-num) " (" (code (:name (phase/phase-by-number phase-num))) "). A "
               "governor HARD hold always stays a hold; an op that is not auto-eligible in "
               "the running phase escalates to a human even when the governor is clean. "
               (code "steam.phase/actuation-never-auto?") " re-derives across every phase and "
               "currently returns "
               (yes-no (phase/actuation-never-auto?)) ".")
          (table (into ["Op"] (map #(str "phase " (:phase %) " — " (name (:name %))) phase/phases))
                 (for [o all-ops]
                   (apply tr (code o)
                          (for [p phase/phases]
                            (cond
                              (contains? (:auto p) o) "<span class=\"ok\">auto when clean</span>"
                              (contains? (:human-approval-required p) o)
                              "<span class=\"warn\">human approval</span>"
                              :else "<span class=\"muted\">not offered</span>"))))))))

(defn- governor-section
  "Governor configuration, including the HARD rule names this run actually
  produced (read off the ledger, not listed by hand)."
  [db]
  (card "Governor configuration"
        (str "Read straight off the public vars of " (code "steam.governor")
             ", plus the HARD rule names this run actually produced &mdash; taken from the "
             "ledger, never enumerated by hand.")
        (table ["Setting" "Value"]
               [(tr "confidence floor" (code governor/confidence-floor))
                (tr "always-human stakes (actuation)" (kw-codes governor/high-stakes))
                (tr "HARD rules fired on this run"
                    (kw-codes (distinct (mapcat #(map :rule (:violations %)) (holds db)))))])))

(defn- required-evidence-for
  "Re-derive the evidence keys a jurisdiction requires, straight out of
  `steam.facts/catalog` -- the same source the governor's
  `:evidence-incomplete` gate reads."
  [jurisdiction]
  (->> (facts/requirement-citations jurisdiction)
       (filter (fn [[_ spec]] (:required spec)))
       (mapcat (fn [[_ spec]] (:evidence spec)))
       distinct
       (sort-by str)))

(defn- customers-section [db]
  (let [led (ledger-of db)
        last-fact (fn [id] (last (filter #(= id (:subject %)) led)))]
    (card "Customers (SSoT after the run)"
          (str "Read back from " (code "steam.store/all-customers") " after the run. The "
               "<em>evidence satisfied?</em> column is "
               (code "steam.facts/required-evidence-satisfied?") " re-run here over each "
               "customer's stored verification &mdash; the same independent re-derivation the "
               "governor performs, which is why the missing-evidence column below explains "
               "the " (code ":evidence-incomplete") " holds above rather than restating them.")
          (table ["Customer" "Name" "Meter" "Profile" "Jurisdiction" "protected recipient?"
                  "provisioned?" "suspended?" "evidence satisfied?" "missing required evidence"
                  "Last ledger fact"]
                 (for [c (sort-by :id (store/all-customers db))
                       :let [v (store/meter-verification-of db (:id c))
                             checklist (:checklist v)
                             missing (remove #(contains? checklist %)
                                             (required-evidence-for (:jurisdiction c)))
                             f (last-fact (:id c))]]
                   (tr (code (:id c)) (esc (:customer-name c)) (code (:meter-id c))
                       (code (:usage-profile c)) (code (:jurisdiction c))
                       (flag (:protected-recipient? c))
                       (if (:supply-provisioned? c)
                         "<span class=\"ok\">yes</span>" "<span class=\"muted\">no</span>")
                       (if (:supply-suspended? c)
                         "<span class=\"warn\">yes</span>" "<span class=\"muted\">no</span>")
                       (yes-no (facts/required-evidence-satisfied? (:jurisdiction c) checklist))
                       (codes missing)
                       (cond
                         (nil? f) "<span class=\"muted\">no ledger activity</span>"
                         (= :committed (:t f)) (str "<span class=\"ok\">" (code (:t f)) "</span>")
                         :else (str "<span class=\"critical\">" (code (:t f)) "</span>"))))))))

(defn- actuation-section [db]
  (let [prov (store/provision-history db)
        susp (store/suspension-history db)]
    (card "Actuation history"
          (str "The two append-only actuation logs "
               (code "steam.store/provision-history") " and "
               (code "steam.store/suspension-history") ". Each real-world actuation event "
               "has its OWN history collection and its own double-actuation guard boolean, "
               "never a shared " (code ":status") " value. A record appears here only "
               "because a human approved the interrupt and the graph reached "
               (code ":commit") ".")
          (if (or (seq prov) (seq susp))
            (table ["Log" "Op" "Subject"]
                   (concat
                    (for [r prov] (tr "<span class=\"ok\">provision</span>"
                                      (code (:op r)) (code (:subject r))))
                    (for [r susp] (tr "<span class=\"warn\">suspension</span>"
                                      (code (:op r)) (code (:subject r))))))
            "<p class=\"muted\">no actuation committed in this run</p>"))))

(defn- jurisdiction-section []
  (let [{:keys [implemented worldwide-jurisdictions coverage-pct note]} (facts/coverage)]
    (card "Jurisdiction catalog"
          (str "Derived from " (code "steam.facts/catalog") " and "
               (code "steam.facts/coverage") ". Coverage is reported honestly: "
               (esc implemented) " of " (esc worldwide-jurisdictions) " jurisdictions ("
               ;; Locale/ROOT, not clojure.core/format: the default locale
               ;; decides the decimal separator, and this file must render
               ;; byte-identically on every machine.
               (esc (String/format java.util.Locale/ROOT "%.2f"
                                   (into-array Object [(double coverage-pct)])))
               "%). " (esc note) ". An unknown "
               "jurisdiction fails CLOSED &mdash; "
               (code "steam.facts/required-evidence-satisfied?")
               " returns false rather than passing vacuously.")
          (table ["Jurisdiction" "Name" "Required evidence keys" "Suspension reasons on file"]
                 (for [k (sort-by str (keys facts/catalog))
                       :let [entry (get facts/catalog k)]]
                   (tr (code k) (esc (:name entry))
                       (codes (required-evidence-for k))
                       (kw-codes (keys (:suspension-requirements entry)))))))))

(defn- ledger-section [db]
  (card "Audit ledger (append-only)"
        (str "The full ledger, in append order, exactly as " (code "steam.store/ledger")
             " returns it. " (code ":basis") " is the advisor's citation list carried onto "
             "the commit fact; a hold carries the governor's rule names instead.")
        (table ["#" "Fact" "Op" "Subject" "Disposition" "Basis / rules"]
               (map-indexed
                (fn [i f]
                  (tr (esc (inc i))
                      (let [cls (case (:t f)
                                  :committed "ok"
                                  :governor-hold "critical"
                                  "muted")]
                        (str "<span class=\"" cls "\">" (esc (:t f)) "</span>"))
                      (code (:op f)) (code (:subject f))
                      (fmt (:disposition f))
                      (if (= :governor-hold (:t f))
                        (str/join " " (map #(code (:rule %)) (:violations f)))
                        (codes (:basis f)))))
                (ledger-of db)))))

;; ----------------------------- page -----------------------------

(defn render
  "The whole page, from the post-run store and the run log."
  [{:keys [db runs]}]
  (str "<!DOCTYPE html>\n<html lang=\"en\">\n<head><meta charset=\"utf-8\">"
       "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
       "<title>Operator console &mdash; cloud-itonami-isic-3530 (steam)</title>"
       "<style>" (jp-go-dds.skin/dds+skin) "</style></head>\n<body>\n"
       "<header class=\"bar\">"
       "<h1>Steam &amp; air conditioning supply &mdash; operator console</h1>"
       "<p><span class=\"badge\">ISIC 3530</span> <span class=\"badge\">steam</span> "
       "governor " (code "thermal-safety-governor") " &middot; phase " (esc phase-num)
       " &middot; read-only sample &middot; provisioning and suspension are always "
       "human-approved</p></header>\n<main>\n"
       (str/join "\n"
                 [(summary-section db runs)
                  (timeline-section runs)
                  (holds-section db)
                  (op-contract-section)
                  (phase-section)
                  (governor-section db)
                  (customers-section db)
                  (actuation-section db)
                  (jurisdiction-section)
                  (ledger-section db)])
       "\n</main>\n<footer class=\"footer\">"
       "Generated at build time by <code>steam.render-html</code> "
       "(<code>clojure -M:dev:render-html</code>) by driving the real "
       "<code>steam.operation</code> actor graph over the real "
       "<code>steam.store</code> seed. Deterministic &mdash; no clock, no randomness, no "
       "network. No usage, revenue or performance metric is claimed anywhere on this page. "
       "This actor holds no thermal distribution licence and dispatches no hardware."
       "</footer>\n</body>\n</html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db runs] :as result} (run-demo!)
        hs (holds db)]
    ;; Build-time invariant: a console that shows no real HARD hold is not
    ;; evidence of a governor. Refuse to write one.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))
                       :requests (count runs)})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count (mapcat :violations hs)) " HARD violations, "
                  (count runs) " requests)"))))
