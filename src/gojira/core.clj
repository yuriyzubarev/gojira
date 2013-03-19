(ns gojira.core
    (:require 
        [clj-http.client :as client]
        [clojure.walk :as w]
        [clojure.tools.cli :as cli]
        [clojure.string :as s]))

(defn select-values [map ks]
    "From http://blog.jayfields.com/2011/01/clojure-select-keys-select-values-and.html"
    (remove nil? (reduce #(conj %1 (map %2)) [] ks)))

(defn call-jira-api! [url query-params server-options]
    (let [fhash (str ".tmp-" (hash  url) (hash query-params))]
        (try
            (read-string (slurp fhash))
        (catch Exception e
            (let [content
                    (client/get (if (= "http" (apply str (take 4 url))) url (str (:jira-api-url server-options) "/" url))
                               {:basic-auth    [(:user server-options) (:password server-options)]
                                :content-type  :json
                                :insecure?     true
                                :query-params  query-params
                                :as            :json})]
                (spit fhash content)
                content)))))

(defn download-sprint-issues! [sprint-id server-options]
    (get-in 
        (call-jira-api! "search" {:jql (str "sprint = " sprint-id " AND issuetype in standardIssueTypes()")} server-options) 
        [:body :issues]))

(defn download-issue-by-key! [issue-key server-options]
    (get-in 
        (call-jira-api! "search" {:jql (str "issue = " issue-key)} server-options) 
        [:body :issues 0]))

(defn download-issue-changelog-histories-by-url! [url server-options]
    (get-in 
        (call-jira-api! url {:expand "changelog"} server-options) 
        [:body :changelog :histories]))

(def issue-map-keys [:order :self :type :status :points :owner :epic-key :key :summary])

(defn issue-map [jissue]
    "jissue: issue in json as returned by JIRA API"
    (zipmap issue-map-keys [
        (get-in jissue [:fields :customfield_10780])
        (:self jissue)
        (get-in jissue [:fields :issuetype :name])
        (get-in jissue [:fields :status :name])
        (int (or (get-in jissue [:fields :customfield_10243]) 1))
        (get-in jissue [:fields :assignee :displayName])
        ; (get-in (download-issue-by-key! (:customfield_11180 (:fields jissue)) server-options) [:fields :customfield_11181])
        (get-in jissue [:fields :customfield_11180])
        (:key jissue)
        (:summary (:fields jissue))]))

(defn format-issue-map [issue-map]
    (s/join ","
        [
            (:self issue-map)
            (:type issue-map)
            (:status issue-map)
            (:epic-name issue-map)
            (:owner issue-map)
            (str "\"" (:key issue-map) " " (s/replace (:summary issue-map) "\"" "\"\"") "\"")
            (:points issue-map)]))

(defn print-sprint-snapshot! [issue-maps]
    (dorun (map println (map format-issue-map issue-maps))))

(defn get-changelog [changelog-histories]
    (let [items (map vector (w/walk #(:items %) seq changelog-histories) (map #(into {} {:date (:created %) :author (get-in % [:author :displayName])}) changelog-histories))]
        (let [status-items (filter #(= "status" (:field (ffirst %))) items)]
            (map #(into {} { 
                :from-to    (str (:fromString (ffirst %)) " -> " (:toString (ffirst %)))
                :date       (apply str (take 10 (:date (last %))))
                :author     (:author (last %))}) status-items))))

(defn assoc-changelog! [mapped-issues server-options]
    (map #(assoc % :changelog (get-changelog (download-issue-changelog-histories-by-url! (:self %) server-options))) mapped-issues))

(defn print-sprint-flow! [l]
    (let [changelog (first l)]
        (cond
            (nil? changelog) nil
            :else
                (do
                    (print-sprint-snapshot! (list changelog))
                    (dorun (map #(println "," (s/join "," %)) (map #(select-values % [:from-to :date :author]) (:changelog changelog))))
                    (print-sprint-flow! (rest l))))))

(defn -main [& args]
    (let [[opts args banner]
        (cli/cli args
            ["-h" "--help" "Show help" :flag true :default false]
            ["-u" "--user" "Username"]
            ["-p" "--password" "Password"]
            ["-s" "--sprint" "Sprint ID"]
            ["-j" "--jira-api-url" "JIRA API URL"])]
    (when (:help opts)
        (println banner)
        (System/exit 0))
    (if
        (and
            (:user opts)
            (:password opts)
            (:sprint opts)
            (:jira-api-url opts))
        (let [sprint-issues (sort-by :order (map #(assoc % :epic-name (get-in (download-issue-by-key! (:epic-key %) opts) [:fields :customfield_11181])) (map issue-map (download-sprint-issues! (:sprint opts) opts))))]
            (cond
                (= "snapshot" (first args)) (print-sprint-snapshot!                     sprint-issues)
                (= "flow" (first args))     (print-sprint-flow!     (assoc-changelog!   sprint-issues opts))
                :else (println "Nothing to do")))
        (println banner))))
  
