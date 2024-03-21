(ns drews.national-institutes-of-health.principle-investigator
  (:require [clojure.spec.alpha :as s]
            [drews.national-institutes-of-health.principle-investigator.appointment_type :as at]))


(s/def ::appointment-type (s/keys :req [::at/primary-duration]
                                  :opt [::at/secondary-duration]))

(defn display
  [{:keys [::primary-duration ::secondary-duration]}]
  (if secondary-duration
    "Academic and Summer"
    "Calendar"))
