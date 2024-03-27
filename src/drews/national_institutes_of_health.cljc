(ns drews.national-institutes-of-health
  (:require
   [odoyle.rules :as o]
   [datascript.core :as d]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [drews.national-institutes-of-health.principle-investigator :as pi]
   [drews.national-institutes-of-health.other-support.project :as project]
   [drews.national-institutes-of-health.other-support.project.person-months :as pm]
   [drews.national-institutes-of-health.principle-investigator.appointment_type :as at]
   [drews.national-institutes-of-health.other-support :as os]))

;;TOOD id of the thing itself should just be "id" So we have to change the project-ids to id, and then change them to project-id when we use them for the pm ids.
(st/instrument)
(s/def ::all-other-support any?)
(s/def ::upsert-other-support any?)
(s/def ::all-person-months any?)
(s/def ::conn any?)
(s/def ::all-person-months-by-project any?)
(s/def ::retract? boolean?)

(defn short [form] (clojure.walk/postwalk (fn [form] (if-not (keyword? form) form (name form))) form))


(def conn
  (let [schema {::os/projects                 {:db/cardinality :db.cardinality/many
                                               :db/valueType   :db.type/ref
                                               :db/isComponent true}
                ::id                          {:db/unique :db.unique/identity}
                ::project/person-months-table {:db/cardinality :db.cardinality/many
                                               :db/valueType   :db.type/ref
                                               :db/isComponent true}}
        conn   (d/create-conn schema)]
    conn))

(defn all-person-months->all-person-months-by-project
  [all-person-months]
  (def all-person-months all-person-months)
  (->> all-person-months
       (group-by (comp ::other-support-id :id))
       (reduce-kv
         (fn [other-supports other-support-id pm-info]
           (conj other-supports {::id          other-support-id
                                 ::os/projects (->> pm-info
                                                    (group-by (comp ::project-id :id))
                                                    (reduce-kv
                                                      (fn [projects project-id pm-info]
                                                        (conj projects {::id {::other-support-id other-support-id
                                                                              ::project-id project-id}
                                                                        ::project/person-months-table
                                                                        (->> pm-info
                                                                             (map #(hash-map ::id (-> % :id)))
                                                                             vec)}))
                                                      []))}))
         [])))



#_(defn all-person-months->all-person-months-by-year
  [all-person-months]
  (->> all-person-months
       (group-by (comp ::other-support-id :id))
       (reduce-kv
         (fn [other-supports other-support-id other-support-info]
           (conj other-supports {:id     other-support-id
                                 ::years (->> other-support-info
                                                         (group-by :year ))}))
         [])))

(def rules
  (o/ruleset
    {::project-range
     [:what
      [::global ::conn conn {:then false}]
      [::global ::at/durations at-durations {:then false}]
      [id ::project/start-year start-year]
      [id ::project/end-year end-year]
      :then
      (let [year->pm-id
            (->>
              (d/q '[:find  ?year ?pm-id
                     :in $ ?project-id
                     :where
                     [?project ::id ?project-id]
                     [?project ::project/person-months-table ?pm]
                     [?pm ::id ?pm-id]
                     [?pm ::pm/durations ?durations]
                     [?pm ::pm/year ?year]]
                   (d/db conn)  id)
              (reduce
                (fn [year->pm-id [year pm-id]]
                  (assoc year->pm-id year pm-id))
                {}))
            existing-years (-> year->pm-id keys set)
            new-years (set (range start-year (inc end-year)))
            all-years (->> year->pm-id keys (concat new-years) sort)
            session (->> (clojure.set/difference new-years existing-years)
                         (reduce
                           (fn [session year]
                             (o/insert session
                                       (assoc id ::id (random-uuid))
                                       {::pm/year year ::pm/durations (reduce-kv (fn [durations duration-key _]
                                                                                   (assoc durations duration-key 0.00))
                                                                                 {} at-durations)}))
                           session))]
        (->> (clojure.set/difference existing-years new-years)
             (reduce
               (fn [session year]
                 (o/insert session (year->pm-id year) ::retract? true))
               session)
             o/reset!))]
     ::person-months
     [:what
      [id ::pm/year year]
      [id ::pm/durations durations]
      :then-finally
      (->> (o/query-all session ::person-months)
           (o/insert! ::global ::all-person-months))]
     ::all-person-months
     [:what
      [::global ::all-person-months all-person-months]
      [::global ::conn conn {:then not=} ]
      :then
      (d/transact! conn (all-person-months->all-person-months-by-project all-person-months))
      (-> session
          (o/insert ::global ::all-person-months-by-project  (d/pull (d/db conn) '[{::os/projects [* {::project/person-months-table [*]}]} *] [::id ::os]))
          #_(o/insert ::global ::all-person-months-by-year (all-person-months->all-person-months-by-year all-person-months))
          o/reset!)]

     ::all-person-months-by-project
     [:what [::global ::all-person-months-by-project all-person-months->all-person-months-by-project]]
     ::durations
     [:what
      [::global ::at/durations at-durations]
      [id ::pm/durations pm-durations {:then false}]
      :then
      (o/insert! id ::pm/durations (select-keys pm-durations (keys at-durations)))]


     ::transact
     [:what
      [id attr value]
      [::global ::conn conn {:then not=}]
      :when (not= attr ::conn)
      :then
      (d/transact! conn [[:db/add (str id) attr value]
                         [:db/add (str id) ::id id]])]
     ::retract
     [:what
      [::global ::conn conn {:then false}]
      [id ::retract? true]
      [id attr value]
      :then
      (o/retract! id attr)
      (d/transact! conn [[:db/retract [::id id] attr value]])]}))

(def *session
  (atom (reduce o/add-rule (o/->session) rules)))

(swap! *session
       (fn [session] (-> session
                        (o/insert ::global ::conn conn)
                        (o/insert ::global {::at/durations {::pm/primary-duration 9.0 ::pm/secondary-duration 3.0}})
                        (o/insert ::os {::os/name "Other support"})
                        (o/insert {::other-support-id ::os ::project-id ::project} {::project/name "Project" ::project/start-year 2022 ::project/end-year 2023})

                        o/fire-rules)))

(swap! *session
       (fn [session]
         (-> session
             (o/insert {::other-support-id ::os ::project-id ::project} {::project/start-year 2020 ::project/end-year 2022})
             o/fire-rules)))

(-> (o/query-all @*session ::all-person-months-by-project)
    short)
