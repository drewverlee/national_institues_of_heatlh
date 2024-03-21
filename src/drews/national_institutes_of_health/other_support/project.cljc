(ns drews.national-institutes-of-health.other-support.project
  (:require [clojure.spec.alpha :as s]
            [drews.national-institutes-of-health.other-support.project.person-months :as person-months]))

(s/def ::name string?)
(s/def ::start-year ::person-months/year)
(s/def ::end-year ::person-months/year)
(s/def ::person-months-table (s/coll-of ::person-months/person-months))
(s/def ::project (s/keys :req [::name]
                         :opt [::person-months-table ::start-year ::end-year]))
