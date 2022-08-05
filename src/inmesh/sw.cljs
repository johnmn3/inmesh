(ns inmesh.sw
  (:require
   [clojure.edn :as edn]
   [inmesh.env :as e]
   [inmesh.on-when :refer [on-when]]
   [inmesh.state :as s]
   [inmesh.util :as u]))

(defn ^:export respond [id res]
  (swap! s/responses assoc id res))

(defn sleep-time [t]
  (js/Promise.
   (fn [r] (js/setTimeout r t))))

(defn response [result]
  (js/Response. result
                #js {"headers"
                     #js {"content-type" "text/html; charset=utf-8"
                          "Cache-Control" "max-age=31556952,immutable"}}))

(defn wake []
  (response "Wake up!"))

(defn sleep [e]
  (let [u (js/URL. (.-url e.request))
        t (pr-str (->> u .-search str rest (apply str) edn/read-string))]
    (.respondWith e (-> (sleep-time t)
                        (.then wake)))))

(defn handle-request [e]
  (let [u (js/URL. (.-url e.request))
        r (->> u .-search u/decode-qp)
        {:keys [request-id requester duration max-time no-park]} r]
    (if-not no-park
      (if-let [res (get @s/responses request-id)]
        (do (swap! s/responses dissoc request-id)
            (swap! s/requests dissoc request-id)
            (.respondWith e (js/Promise.resolve (response res))))
        (let [p (js/Promise. (fn [resolve _reject]
                               (swap! s/requests assoc request-id resolve)))]
          (.respondWith e p)))
      (.respondWith e (-> (on-when (contains? @s/responses request-id) {:duration duration :max-time max-time}
                                   (let [result (get @s/responses request-id)]
                                     (swap! s/responses dissoc request-id)
                                     (response result)))
                          (.then #(js/Promise.resolve %))
                          (.catch #(do (println :error %)
                                       (println :request-id request-id)
                                       (println :requester requester)
                                       (println :stack (.-stack %))
                                       (println :event (pr-str e))
                                       (println :url (pr-str u))
                                       (println :handle-request (pr-str r))
                                       (js/Promise.resolve (response (clj->js {:error %}))))))))))

(defn handle-respond [e]
  (-> (-> e .-request .clone .text)
      (.then (fn [data]
               (let [{:keys [request-id] res :response} (edn/read-string data)]
                 (if-let [resolve (get @s/requests request-id)]
                   (do (swap! s/requests dissoc request-id)
                       (resolve (response res)))
                   (swap! s/responses assoc request-id res))))))
  (.respondWith e (js/Promise.resolve (response "done"))))

(defn fetch-response [e]
  (let [url (js/URL. (.-url e.request))
        path (.-pathname url)]
    (cond (= path "/intercept/sleep/t.js")
          (sleep e)
          (= path "/intercept/request/key.js")
          (handle-request e)
          (= path "/intercept/response/key.js")
          (handle-respond e))))

(when (e/in-sw?)

  (.addEventListener js/self "install" #(js/self.skipWaiting))
  (.addEventListener js/self "activate" #(.waitUntil % (js/self.clients.claim)))
  (.addEventListener js/self "fetch" fetch-response)

  :end)

(defn init! []
  #_
  (println :starting :sw))
