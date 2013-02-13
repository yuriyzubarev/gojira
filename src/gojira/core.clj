(ns gojira.core
   (:require 
    [clj-http.client :as client]
    [clojure.walk :as w]
    [clojure.tools.cli :as cli]
    [clojure.string :as s]))

; globals for a CLI tool: evil or pragmatic?
(def jira-api-url)
(def username)
(def password)
(def sprint-id)

(defn select-values [map ks]
  "From http://blog.jayfields.com/2011/01/clojure-select-keys-select-values-and.html"
  (remove nil? (reduce #(conj %1 (map %2)) [] ks)))

(defn jira-api-call [url query-params]
  (let [fhash (str ".tmp-" (hash  url) (hash query-params))]
    (try
      (read-string (slurp fhash))
      (catch Exception e
        (let [content
        (client/get (if (= "http" (apply str (take 4 url))) url (str jira-api-url "/" url))
          {:basic-auth    [username password]
           :content-type  :json
           :insecure?     true
           :query-params  query-params
           :as            :json})]
          (spit fhash content)
          content)))))

(defn search-result []
  (:body (jira-api-call "search" {:jql (str "sprint = " sprint-id " AND issuetype in standardIssueTypes()")})))

(defn extract-epic-data [issue-number]
  (get-in (jira-api-call "search" {:jql (str "issue = " issue-number)}) [:body :issues 0 :fields :summary]))

(defn extract-story-points [issue]
  (int (or (get-in issue [:fields :customfield_10243]) 1)))

(defn extract-status [issue]
  (get-in issue [:fields :status :name]))

(defn extract-changelog [issue-url]
    (let [histories (get-in (jira-api-call issue-url {:expand "changelog"}) [:body :changelog :histories])]
        (let [items (map vector (w/walk #(:items %) seq histories) (map #(:created %) histories))]
          (let [status-items (filter #(= "status" (:field (ffirst %))) items)]
            (map #(into {} { :from (:fromString (ffirst %)) :to (:toString (ffirst %)) :date (last %) }) status-items)))))

(defn extract-search-issue-data [issue]
    "Issue in json from 'search' command"
    {
      :self (:self issue)
      :status (extract-status issue)
      :points (extract-story-points issue)
      :epic-name (extract-epic-data (:customfield_11180 (:fields issue)))
      :summary (str (:key issue) " " (:summary (:fields issue))) })

(defn sprint-snapshot []
    (map extract-search-issue-data (:issues (search-result))))

(defn format-sprint-snapshot [l]
    (map #(select-values % [:self :status :points :epic-name :summary]) l))

(defn print-sprint-snapshot [l]
    (dorun (map println (map #(s/join "\t" %) (format-sprint-snapshot l)))))

(defn sprint-flow []
    (map #(assoc % :changelog (extract-changelog (:self %))) (sprint-snapshot)))

(defn p-flow [l]
  (let [[changelog sprint] (take 2 l)]
    (cond
      (nil? changelog) '()
      :else
        (do
          (print-sprint-snapshot (list changelog))
          ; (println (:changelog changelog))
          (dorun (map #(println (s/join "\t" %)) (map #(select-values % [:from :to :date]) (:changelog changelog))))
          (p-flow (drop 2 l))))))

(defn print-sprint-flow [l]
    (let [flow (interleave l (format-sprint-snapshot l))]
      (p-flow flow)))
      ; (println flow)))
    ; (dorun (map println (map #(s/join "\t" %) flow)))))

(defn -main [& args]
  (let [[opts args banner]
        (cli/cli args
             ["-h" "--help" "Show help" :flag true :default false]
             ["-u" "--user" "Username"]
             ["-p" "--password" "Password"]
             ["-s" "--sprint" "Sprint ID"]
             ["-j" "--jira-api-url" "JIRA API URL"]
             )]
    (when (:help opts)
      (println banner)
      (System/exit 0))
    (if
        (and
         (:password opts)
         (:sprint opts))
      (do
        (def jira-api-url (:jira-api-url opts))
        (def username (:user opts))
        (def password (:password opts))
        (def sprint-id (:sprint opts))
        (cond
          (= "snapshot" (first args)) (print-sprint-snapshot (sprint-snapshot))
          (= "flow" (first args))     (print-sprint-flow (sprint-flow))
          :else (println "Nothing to do")))
      (println banner))))
  
