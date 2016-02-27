(ns jaggr.jenkins
  (:require [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [omniconf.core :as config]))

;; TODO get credentials from cli
;; calls the Jenkins JSON api for a given url (must end with /),
;; returns the body of the response as JSON with keys converted to clojure keywords
(defn- get-from-jenkins [base-url params]
  (json/read-str
    (:body
      @(http/get
         (str
           base-url "api/json?" params)
         {:basic-auth [(config/get :user) (config/get :user-token)]}))
    :key-fn keyword))

;; TODO get url from cli
;; gets the jobs REST resource for the globally configures base-url
(defn- get-jobs-rsrc []
  (:jobs
    (get-from-jenkins
      (config/get :base-url)
      "tree=jobs[name,color,url]")))

; returns red, yellow or nil, can be used as a predicate
(defn- red-or-yellow [job-rsrc]
  (#{"red" "yellow"} (:color job-rsrc)))

;; gets all failed (red or yellow) jobs from the globally configured base-url
(defn- get-failed-jobs-rsrc []
  (filter red-or-yellow (get-jobs-rsrc)))

; gets the claim info for a job resource and throws away everything else
(defn- get-last-build-rsrc [job-rsrc]
  (let [builds-rsrc
        (get-from-jenkins (:url job-rsrc) "tree=lastBuild[url]")
        last-build-rsrc
        (get-from-jenkins (get-in builds-rsrc [:lastBuild :url]) "tree=actions[claimed,claimedBy,reason]")]
    (first (filter not-empty (:actions last-build-rsrc)))))

(defn get-failed-jobs []
  "fetches all failed jobs from jenkins and returns a map that devides them in three classes:
   :claimed, :unclaimed and :unclaimable. For each job of each class, a map is returned with
   :name, :claimed, :claimedBy and :reason"
  (->>
    (for [failed-job-rsrc (get-failed-jobs-rsrc)]
      (assoc (get-last-build-rsrc failed-job-rsrc) :name (:name failed-job-rsrc)))
    (group-by #(cond
                (= true (:claimed %1)) :claimed
                (= false (:claimed %1)) :unclaimed
                :else :unlcaimable))))