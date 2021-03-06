(ns flower.messaging.core
  (:require [com.stuartsierra.component :as component]
            [flower.common :as common]
            [flower.resolver :as resolver]
            [flower.messaging.proto :as proto]))


;;
;; Public definitions
;;

(defrecord MessagingComponent [auth context]
  component/Lifecycle
  (start [component] (into component {:auth auth
                                      :context context}))
  (stop [component] (into component {:auth {}
                                     :context {}})))


(defn messaging [messaging-component messaging]
  (into {}
        (map (fn [[messaging-name {messaging-type :messaging-type
                                   messaging-url :messaging-url}]]
               (let [msg-root (get-in messaging-component [:context :msg-root])]
                 [messaging-name [((resolver/resolve-implementation messaging-type :messaging)
                                   (merge {:msg-component messaging-component
                                           :msg-name messaging-name}
                                          (when messaging-url
                                            {:msg-url messaging-url})
                                          (when msg-root
                                            {:msg-root msg-root})))]]))
             messaging)))


(defn start-component [args]
  (map->MessagingComponent args))


(def ^:dynamic *messaging-type* nil)
(def ^:dynamic *messaging-url* nil)


(defmacro with-messaging-type [messaging-type & body]
  `(binding [flower.messaging.core/*messaging-type* ~messaging-type]
     ~@body))


(defn get-messaging-info
  ([] (get-messaging-info nil))
  ([messaging-full-url] (merge {:messaging-type (or *messaging-type* :default)
                                :messaging-name (or *messaging-type* "default")}
                               (when messaging-full-url
                                 (let [messaging-url (common/url messaging-full-url)
                                       messaging-domain (or (get messaging-url :host) "global")]
                                   {:messaging-type (or *messaging-type* :default)
                                    :messaging-name (keyword (str (name (or *messaging-type* :default))
                                                                  "-"
                                                                  messaging-domain))
                                    :messaging-url (or *messaging-url* (str messaging-url))})))))


(defn get-messaging
  ([] (get-messaging nil))
  ([messaging-full-url] (let [messaging-info (get-messaging-info messaging-full-url)
                              messaging-name (get messaging-info :messaging-name :messaging)]
                          (first (get (messaging (start-component {:auth common/*component-auth*
                                                                   :context common/*component-context*})
                                                 {messaging-name messaging-info})
                                      messaging-name)))))


(defn message [message-data]
  (let [messaging (get message-data :msg-box)
        messaging-type (proto/get-messaging-type messaging)
        messaging-function (resolver/resolve-implementation messaging-type :message)]
    (messaging-function message-data)))
