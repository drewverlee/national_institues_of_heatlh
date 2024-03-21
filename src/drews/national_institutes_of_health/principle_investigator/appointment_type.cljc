(ns drews.national-institutes-of-health.principle-investigator.appointment_type
  (:require [clojure.spec.alpha :as s]))

(s/def ::primary-duration (s/and pos-int? #(<= 1 % 12)))
(s/def ::secondary-duration (s/and pos-int? #(<= 1 % 3)))

(s/def ::durations (s/keys :req [::primary-duration]
                           :opt [::secondary-duration]))

(defn duration-in-months->display-names
  [duration-in-months]
  (cond
    (<= duration-in-months 3) {:short "SM" :long "Summer Term"}
    (<= duration-in-months 9) {:short "AY" :long "Academic Year"}
    :else {:short "CY" :long "Calendar Year"}))
