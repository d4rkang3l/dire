(ns dire.test.multiple-exception-selectors
  (:require [midje.sweet :refer :all]
            [dire.core :refer :all]))

(defn divider [a b]
  (/ a b))

(with-handler #'divider
  "Catches divide by 0 and null exception errors."
  [java.lang.ArithmeticException
   java.lang.NullPointerException]
  (fn [e & args] :dbz-and-npe-handler))

(fact (supervise #'divider 10 2) => 5)
(fact (supervise #'divider 10 0) => :dbz-and-npe-handler)
(fact (supervise #'divider 10 nil) => :dbz-and-npe-handler)

