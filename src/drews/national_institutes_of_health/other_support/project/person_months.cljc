(ns drews.national-institutes-of-health.other-support.project.person-months
  (:require
   [clojure.spec.alpha :as s]))

(s/def ::value float?)
(s/def ::year int?)
(s/def ::primary-duration ::value)
(s/def ::secondary-duration ::value)
(s/def ::durations (s/keys :req [::primary-duration]
                           :opt [::secondary-duration]))
