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
        (get-in jissue [:fields :customfield_11180])
        (:key jissue)
        (:summary (:fields jissue))]))

(defn get-sprint-issues! [sprint-id server-options]
    (defn assoc-epic-name [m]
        (assoc m :epic-name
            (get-in 
                (download-issue-by-key! (:epic-key m) server-options) 
                [:fields :customfield_11181])))
    (sort-by :order (map assoc-epic-name (map issue-map (download-sprint-issues! sprint-id server-options)))))

(defn format-issue-map-as-csv [issue-map]
    (s/join ","
        [
            (:self issue-map)
            (:type issue-map)
            (:status issue-map)
            (:epic-name issue-map)
            (:owner issue-map)
            (str "\"" (:key issue-map) " " (s/replace (:summary issue-map) "\"" "\"\"") "\"")
            (:points issue-map)]))

(defn html-table-top []
    (str 
        "<table cellpadding='2'>"
        "<tr>"
        "<th width='100' align='left'>Type</th>"
        "<th width='100' align='left'>Epic</th>"
        "<th width='100' align='left'>Owner</th>"
        "<th width='150' align='left'>Summary</th>"
        "</tr>"))

(defn format-issue-map-as-html [issue-map]
    (str
        "<tr>"
        "<td>" (:type issue-map) "</td>"
        "<td>" (:epic-name issue-map) "</td>"
        "<td>" (:owner issue-map) "</td>"
        "<td>" (:key issue-map) " " (:summary issue-map) "</td>"
        "</tr>"))

(defn print-sprint-snapshot! [issue-maps f-format]
    (dorun (map println (map f-format issue-maps))))

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
                    (print-sprint-snapshot! (list changelog) format-issue-map-as-csv)
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
        (let [sprint-issues (get-sprint-issues! (:sprint opts) opts)]
            (cond
                (= "snapshot" (first args))     (print-sprint-snapshot! sprint-issues format-issue-map-as-csv)
                (= "flow" (first args))         (print-sprint-flow! (assoc-changelog! sprint-issues opts))
                (= "by-status" (first args))    (do 
                                                    (println "<p>Please update the Jira tickets if they don't accurately reflect the reality.</p>")
                                                    (println "<h3>In flight</h3>")
                                                    (println (html-table-top))
                                                    (print-sprint-snapshot! (filter #(or (= "In Progress" (:status %)) (= "In Test" (:status %))) sprint-issues) format-issue-map-as-html)
                                                    (println "</table>")
                                                    (println "<h3>Not started</h3>")
                                                    (println (html-table-top))
                                                    (print-sprint-snapshot! (filter #(= "Open" (:status %)) sprint-issues) format-issue-map-as-html)
                                                    (println "</table>")
                                                    (println "<h3>Closed</h3>")
                                                    (println (html-table-top))
                                                    (print-sprint-snapshot! (filter #(= "Closed" (:status %)) sprint-issues) format-issue-map-as-html)
                                                    (println "</table>"))
                :else (println "Nothing to do")))
        (println banner))))
  
