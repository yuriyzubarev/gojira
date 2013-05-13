(ns gojira.core-test
  (:use clojure.test)
  (:require [gojira.core :as core]))

(deftest select-values-should-return-expected
    (testing "not selecting right thing"
        (is (= [:v1 :v2] (core/select-values { :k1 :v1 :k2 :v2} [:k1 :k2])))))

(defn mock-download-sprint-issues! [sprint-id server-options]
    (get-in (read-string (slurp "test/list.json")) [:body :issues]))

(defn mock-download-issue-by-key! [issue-key server-options]
    (get-in (read-string (slurp "test/epic.json")) [:body :issues 0]))

(deftest issue-map-test
    (with-redefs [core/download-sprint-issues! mock-download-sprint-issues!]
        (is (=  {   :summary "Possible double charge by Global Collect for some orders", 
                    :key "TMS-10697", 
                    :epic-key "TMS-8941", 
                    :owner "Dennis Bennett", 
                    :points 1, 
                    :status "Open", 
                    :type "Trouble Ticket", 
                    :self "https://jira.kokanee.abebooks.com/jira/rest/api/2/issue/76976", 
                    :order "1089"}
                (first (map core/issue-map (core/download-sprint-issues! nil nil)))))))

(deftest get-sprint-issues!-test
    (with-redefs 
            [core/download-sprint-issues! mock-download-sprint-issues!
            core/download-issue-by-key! mock-download-issue-by-key!]
        (is (= 25 (count (core/get-sprint-issues! nil nil))))))

; (def server-opts {:user "yzubarev" :password "" :jira-api-url "https://jira.kokanee.abebooks.com/jira/rest/api/2"})

; (download-sprint-issues! 723 server-opts)
