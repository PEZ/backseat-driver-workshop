(ns acme.server
  (:require
   [clojure.data.json :as json]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.file :as ring-file]
   [ring.middleware.file-info :as ring-file-info])
  (:import
   [java.net URI]
   [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]))

(defonce http-client (HttpClient/newHttpClient))

(defn fetch-json [url]
  (let [request (-> (HttpRequest/newBuilder)
                    (.uri (URI. url))
                    (.header "Accept" "application/json")
                    .build)
        response (.send http-client request (HttpResponse$BodyHandlers/ofString))]
    (json/read-str (.body response) :key-fn keyword)))

;; --- Cache ---

(defonce !cache
  (atom (let [f (io/file "data/cocktaildb-cache.edn")]
          (if (.exists f)
            (edn/read-string (slurp f))
            {}))))

(defn- persist-cache! [cache]
  (let [path "data/cocktaildb-cache.edn"]
    (io/make-parents path)
    (spit path (pr-str cache)))
  cache)

(defn cached-fetch
  ([url]
   (cached-fetch url nil))
  ([url _ttl-ms]
   (if-let [data (get-in @!cache [url :data])]
     data
     (let [fresh (fetch-json url)
           now (System/currentTimeMillis)]
       (persist-cache!
        (swap! !cache assoc url {:data fresh :fetched-at now}))
       fresh))))

;; --- Data normalization ---

(defn normalize-drink [drink]
  (let [ingredients (->> (range 1 16)
                         (map (fn [i]
                                (let [ing (get drink (keyword (str "strIngredient" i)))
                                      measure (get drink (keyword (str "strMeasure" i)))]
                                  (when (and ing (seq (str/trim ing)))
                                    {:name (str/trim ing)
                                     :measure (when measure (str/trim measure))}))))
                         (filter some?)
                         vec)
        translations (->> {"EN" "" "ES" "ES" "DE" "DE" "FR" "FR" "IT" "IT"
                           "ZH-HANS" "ZH-HANS" "ZH-HANT" "ZH-HANT"}
                          (keep (fn [[lang suffix]]
                                  (let [instr (get drink (keyword (str "strInstructions" suffix)))]
                                    (when (and instr (seq (str/trim instr)))
                                      [lang {:instructions (str/trim instr)}]))))
                          (into {}))]
    {:id (:idDrink drink)
     :name (:strDrink drink)
     :category (:strCategory drink)
     :instructions (:strInstructions drink)
     :thumbnail (:strDrinkThumb drink)
     :ingredients ingredients
     :translations translations}))

(defn- safe-drinks [data]
  (let [drinks (:drinks data)]
    (if (sequential? drinks) drinks [])))

;; --- API routing ---

(def api-base "https://www.thecocktaildb.com/api/json/v1/1")

(defn fetch-all-drinks
  "Fetches all drinks from CocktailDB by iterating letters a-z via search.php.
   Each letter's response is individually cached for 1 hour. Returns normalized drinks."
  []
  (let [letters (map str "abcdefghijklmnopqrstuvwxyz")]
    (->> letters
         (mapcat (fn [letter]
                   (let [data (cached-fetch
                               (str api-base "/search.php?f=" letter)
                               3600000)]
                     (safe-drinks data))))
         (mapv normalize-drink))))

(defn drink-matches? [drink q-lower]
  (or (str/includes? (str/lower-case (or (:name drink) "")) q-lower)
      (some #(str/includes? (str/lower-case (or (:name %) "")) q-lower)
            (:ingredients drink))))

(defn json-response [data]
  {:status 200
   :headers {"Content-Type" "application/json"
             "Access-Control-Allow-Origin" "*"}
   :body (json/write-str data)})

(defn error-response [status msg]
  {:status status
   :headers {"Content-Type" "application/json"
             "Access-Control-Allow-Origin" "*"}
   :body (json/write-str {:error msg})})

(defn parse-query-params [query-string]
  (when query-string
    (->> (str/split query-string #"&")
         (map #(str/split % #"=" 2))
         (into {} (map (fn [[k v]] [k (java.net.URLDecoder/decode (or v "") "UTF-8")]))))))

(defn api-handler [{:keys [uri query-string request-method] :as request}]
  (when (and (= :get request-method)
             (str/starts-with? uri "/api/"))
    (try
      (let [params (parse-query-params query-string)]
        (cond
          (= uri "/api/categories")
          (let [data (cached-fetch (str api-base "/list.php?c=list") 3600000)]
            (json-response (mapv :strCategory (:drinks data))))

          (= uri "/api/total-drinks")
          (json-response {:total (count (fetch-all-drinks))})

          (= uri "/api/search")
          (let [q (get params "q" "")
                c (get params "c" "")
                q-lower (str/lower-case q)
                api-drinks (when (seq q)
                             (mapv normalize-drink
                                   (safe-drinks (fetch-json (str api-base "/search.php?s="
                                                                 (java.net.URLEncoder/encode q "UTF-8"))))))
                ingr-matches (when (seq q)
                               (filterv #(some (fn [i] (str/includes? (str/lower-case (or (:name i) "")) q-lower))
                                               (:ingredients %))
                                        (fetch-all-drinks)))
                combined (if (seq q)
                           (vals (reduce (fn [m d] (assoc m (:id d) d)) {} (concat api-drinks ingr-matches)))
                           (fetch-all-drinks))
                filtered (if (seq c)
                           (filter #(= c (:category %)) combined)
                           combined)]
            (json-response (vec filtered)))

          (= uri "/api/filter")
          (let [c (get params "c" "")
                all (fetch-all-drinks)
                filtered (if (seq c)
                           (filterv #(= c (:category %)) all)
                           all)]
            (json-response filtered))

          (str/starts-with? uri "/api/drink/")
          (let [id (subs uri (count "/api/drink/"))
                data (fetch-json (str api-base "/lookup.php?i=" (java.net.URLEncoder/encode id "UTF-8")))]
            (if-let [drink (first (:drinks data))]
              (json-response (normalize-drink drink))
              (error-response 404 "Drink not found")))

          :else nil))
      (catch Exception e
        (error-response 502 (str "API error: " (.getMessage e)))))))

(defn my-handler [req]
  (or (api-handler req)
      {:status 404
       :headers {"Content-Type" "application/json"}
       :body (json/write-str {:error "Not found"})}))

(def handler
  (-> my-handler
      (ring-file/wrap-file "public")
      (ring-file-info/wrap-file-info)))

(defn -main [& _args]
  (jetty/run-jetty handler {:port 3003}))
