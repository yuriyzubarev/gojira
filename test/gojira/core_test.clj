(ns gojira.core-test
  (:use clojure.test
        gojira.core))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

(def server-opts {:user "yzubarev" :password "" :jira-api-url "https://jira.kokanee.abebooks.com/jira/rest/api/2"})

(download-sprint-issues! 723 server-opts)
