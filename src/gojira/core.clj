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

(defn select-values [map ks]
    "From http://blog.jayfields.com/2011/01/clojure-select-keys-select-values-and.html"
    (remove nil? (reduce #(conj %1 (map %2)) [] ks)))

(defn call-jira-api [url query-params]
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

(defn download-sprint-issues [sprint-id]
    (get-in 
        (call-jira-api "search" {:jql (str "sprint = " sprint-id " AND issuetype in standardIssueTypes()")}) 
        [:body :issues]))

(defn download-issue-by-key [issue-key]
    (get-in 
        (call-jira-api "search" {:jql (str "issue = " issue-key)}) 
        [:body :issues 0]))

(defn download-issue-changelog-histories-by-url [url]
    (get-in 
        (call-jira-api url {:expand "changelog"}) 
        [:body :changelog :histories]))

(def issue-map-keys [:self :status :points :epic-name :summary])

(defn issue-map [jissue]
    "jissue: issue in json as returned by JIRA API"
    (zipmap issue-map-keys [
        (:self jissue)
        (get-in jissue [:fields :status :name])
        (int (or (get-in jissue [:fields :customfield_10243]) 1))
        (get-in (download-issue-by-key (:customfield_11180 (:fields jissue))) [:fields :summary])
        (str (:key jissue) " " (:summary (:fields jissue)))]))

(defn format-issue-map [issue-map]
    (s/join "\t" (select-values issue-map issue-map-keys)))

(defn print-sprint-snapshot [l]
    (dorun (map println (map format-issue-map l))))

(defn extract-changelog [issue-url]
    (let [histories (download-issue-changelog-histories-by-url issue-url)]
        (let [items (map vector (w/walk #(:items %) seq histories) (map #(:created %) histories))]
            (let [status-items (filter #(= "status" (:field (ffirst %))) items)]
                (map #(into {} { :from (:fromString (ffirst %)) :to (:toString (ffirst %)) :date (last %) }) status-items)))))

(defn sprint-flow [mapped-issues]
    (map #(assoc % :changelog (extract-changelog (:self %))) mapped-issues))

(defn p-flow [l]
    (let [[changelog sprint] (take 2 l)]
        (cond
            (nil? changelog) '()
            :else
                (do
                    (print-sprint-snapshot (list changelog))
                    (dorun (map #(println (s/join "\t" %)) (map #(select-values % [:from :to :date]) (:changelog changelog))))
                    (p-flow (drop 2 l))))))

(defn print-sprint-flow [l]
    (let [flow (interleave l (map #(select-values % issue-map-keys) l))]
        (p-flow flow)))

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
            (:password opts)
            (:sprint opts))
        (do
            (def jira-api-url (:jira-api-url opts))
            (def username (:user opts))
            (def password (:password opts))
            (cond
                (= "snapshot" (first args)) (print-sprint-snapshot          (map issue-map (download-sprint-issues (:sprint opts))))
                (= "flow" (first args))     (print-sprint-flow (sprint-flow (map issue-map (download-sprint-issues (:sprint opts)))))
                :else (println "Nothing to do")))
        (println banner))))
  