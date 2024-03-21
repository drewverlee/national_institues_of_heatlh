(ns drews.national-institutes-of-health
  (:require
   [odoyle.rules :as o]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [drews.national-institutes-of-health.principle-investigator :as pi]
   [drews.national-institutes-of-health.other-support.project :as project]
   [drews.national-institutes-of-health.other-support.project.person-months :as pm]
   [drews.national-institutes-of-health.principle-investigator.appointment_type :as at]
   [drews.national-institutes-of-health.other-support :as os]))

(st/instrument)
(s/def ::all-other-support any?)
(s/def ::upsert-other-support any?)
(s/def ::all-person-months any?)
(s/def ::conn any?)
(s/def ::all-person-months-by-project any?)

(def conn
  (let [schema {:os/projects      {:db/cardinality :db.cardinality/many
                                   :db/valueType   :db.type/ref
                                   :db/isComponent true}
                ::id {:db/unique :db.unique/identity}
                :pm/person-months {:db/cardinality :db.cardinality/many
                                   :db/valueType   :db.type/ref
                                   :db/isComponent true}}
        conn   (d/create-conn schema)]
    conn))

(defn all-person-months->all-person-months-by-project
  [all-person-months]
  (->> all-person-months
       (group-by (comp ::other-support-id :id))
       (reduce-kv
         (fn [other-supports other-support-id other-support-info]
           (conj other-supports {::other-support-id other-support-id
                                 ::os/projects      (->> other-support-info
                                                         (group-by (comp ::project-id :id))
                                                         (reduce-kv
                                                           (fn [projects project-id project-info]
                                                             (conj projects {::project-id project-id
                                                                             ::pm/person-months
                                                                             (->> project-info
                                                                                  (map (comp ::id :id))
                                                                                  vec)}))
                                                           []))}))
         [])))

(defn all-person-months->all-person-months-by-year
  [all-person-months]
  (->> all-person-months
       (group-by (comp ::other-support-id :id))
       (reduce-kv
         (fn [other-supports other-support-id other-support-info]
           (conj other-supports {::other-support-id other-support-id
                                 ::years            (->> other-support-info
                                                         (group-by :year ))}))
         [])))

(def rules
  (o/ruleset
    {::person-months
     [:what
      [id ::pm/year year]
      [id ::pm/durations durations]
      :then-finally
      (->> (o/query-all session ::person-months)
           (o/insert! ::global ::all-person-months))]
     ::all-person-months
     [:what
      [::global ::all-person-months all-person-months]
      #_:then
      #_(->> session
        (o/insert ::global ::all-person-months-by-project (all-person-months->all-person-months-by-project all-person-months))
        (o/insert ::global ::all-person-months-by-year (all-person-months->all-person-months-by-year all-person-months))
        o/reset!)]
     ::transact
     [:what
      [id attr value]
      [::global ::conn conn {:then not=}]
      :when (not= attr ::conn)
      :then
      (d/transact! conn [[:db/add (str id) attr value]
                         [:db/add (str id) ::id id]])]}))

(def *session
  (atom (reduce o/add-rule (o/->session) rules)))

(swap! *session
       (fn [session]
         (-> session
             (o/insert ::global ::conn conn)
             (o/insert :0 {::at/durations {::at/primary-duration 9 ::at/secondary-duration 3}} )
             (o/insert ::os {::os/name "Other support"})
             (o/insert ::project {::project/name "Project" ::project/start-year 2022 ::project/end-year 2022})
             (o/insert {::project-id ::project
                        ::other-support-id ::os
                        ::id 10} {::pm/year 2023 ::pm/durations {::pm/primary-duration 9.0 ::pm/secondary-duration 3.0}})
             (o/insert {::project-id       ::project
                        ::other-support-id ::os
                        ::id               11} {::pm/year 2024 ::pm/durations {::pm/primary-duration 9.0 ::pm/secondary-duration 3.0}})
             o/fire-rules)))

(require '[datascript.core :as d])

(d/q
  '[:find ?name
    :where
    [[::id ::os] ::os/name ?name]]
  @conn)

(->(o/query-all @*session ::all-person-months)
   first
   :all-person-months
   all-person-months->all-person-months-by-year)
;; => [{:drews.national-institutes-of-health/other-support-id :drews.national-institutes-of-health/os,
;;      :drews.national-institutes-of-health/years {2023 [{:durations {:drews.national-institutes-of-health.other-support.project.person-months/primary-duration 9.0,
;;                                                                     :drews.national-institutes-of-health.other-support.project.person-months/secondary-duration 3.0},
;;                                                         :id {:drews.national-institutes-of-health/id 10,
;;                                                              :drews.national-institutes-of-health/other-support-id :drews.national-institutes-of-health/os,
;;                                                              :drews.national-institutes-of-health/project-id :drews.national-institutes-of-health/project},
;;                                                         :year 2023}],
;;                                                  2024 [{:durations {:drews.national-institutes-of-health.other-support.project.person-months/primary-duration 9.0,
;;                                                                     :drews.national-institutes-of-health.other-support.project.person-months/secondary-duration 3.0},
;;                                                         :id {:drews.national-institutes-of-health/id 11,
;;                                                              :drews.national-institutes-of-health/other-support-id :drews.national-institutes-of-health/os,
;;                                                              :drews.national-institutes-of-health/project-id :drews.national-institutes-of-health/project},
;;                                                         :year 2024}]}}]
