(ns drews.national-institutes-of-health.other-support
  (:require
   [odoyle.rules :as o]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [drews.national-institutes-of-health.other-support.project.person-months :as pm]
   [drews.national-institutes-of-health.other-support.project :as project]))

(s/def ::name string?)
(s/def ::project-ids (s/coll-of any?))
(s/def ::projects (s/coll-of ::project/project))
(s/def ::other-support (s/keys :req [::name ::projects ::project-ids]))

(s/def ::person-months-yearly-summary (s/coll-of any?))
