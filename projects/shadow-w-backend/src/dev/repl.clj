(ns repl
  (:require
   [ring.adapter.jetty :as jetty]
   #_{:clj-kondo/ignore [:unused-namespace]}
   [shadow.cljs.devtools.api :as shadow]
   [clj-reload.core :as reload]))

(defonce !jetty-ref (atom nil))

(defn stop! []
  (when-some [jetty @!jetty-ref]
    (reset! !jetty-ref nil)
    (.stop jetty))
  ::stopped)

(defn go! []
  ;; No need to start shadow.cljs watcher if using Calva
  #_(shadow/watch :frontend)
  (reload/init {:dirs ["src/main" "src/dev"]
                :no-reload '[repl]})
  (require 'acme.server :reload)
  (reset! !jetty-ref
          (jetty/run-jetty #(@(requiring-resolve 'acme.server/handler) %)
                           {:port 3003
                            :join? false}))
  ::started)

(defn restart! []
  (reload/reload))

(comment
  (go!)
  (restart!)
  (stop!)
  :rcf)

(comment
  ;; To open as a Joyride flare
  ;; pin the **joyride** repl session and evaluate
  (require '[flares :as flares])
  ;; A runtime
  (flares/open-url-in-sidebar!+ "http://localhost:3003")
  ;; One more runtime
  (flares/open-url-in-sidebar!+ "http://localhost:3003")
  :rcf)
