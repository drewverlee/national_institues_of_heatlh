(ns drews.national-institutes-of-health.principle-investigator.appointment_type
  (:require [clojure.spec.alpha :as s]
            [drews.national-institutes-of-health.other-support.project.person-months :as pm]
            ))

#_(s/def ::primary-duration (s/and pos-int? #(<= 1 % 12)))
#_(s/def ::secondary-duration (s/and pos-int? #(<= 1 % 3)))

(s/def ::durations (s/keys :req [::pm/primary-duration]
                           :opt [::pm/secondary-duration]))

(defn duration-in-months->display-names
  [duration-in-months]
  (cond
    (<= duration-in-months 3) {:short "SM" :long "Summer Term"}
    (<= duration-in-months 9) {:short "AY" :long "Academic Year"}
    :else {:short "CY" :long "Calendar Year"}))
