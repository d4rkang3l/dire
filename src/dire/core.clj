(ns dire.core
  (:require [slingshot.slingshot :refer [try+ throw+]]))

(defn with-handler
  ([task-var docstring? exception-type handler-fn]
     (with-handler task-var exception-type handler-fn))
  ([task-var exception-type handler-fn]
     (alter-meta! task-var assoc :error-handlers
                  (merge (:error-handlers (meta task-var) {})
                         {exception-type handler-fn}))))

(defn with-finally
  ([task-var docstring? finally-fn]
     (with-finally task-var finally-fn))
  ([task-var finally-fn]
     (alter-meta! task-var assoc :finally finally-fn)))

(defn with-precondition [task-var description pred-fn]
  (alter-meta! task-var assoc :preconditions
               (assoc (:preconditions (meta task-var) {}) description pred-fn)))

(defn with-postcondition [task-var description pred-fn]
  (alter-meta! task-var assoc :postconditions
               (assoc (:postconditions (meta task-var) {}) description pred-fn)))

(defn eval-preconditions [task-metadata & args]
  (doseq [[pre-name pre-fn] (:preconditions task-metadata)]
    (when-not (apply pre-fn args)
      (throw+ {:type ::precondition :precondition pre-name}))))

(defn eval-postconditions [task-metadata result & args]
  (doseq [[post-name post-fn] (:postconditions task-metadata)]
    (when-not (post-fn result)
      (throw+ {:type ::postcondition :postcondition post-name :result result}))))

(defn eval-finally [task-metadata & args]
  (when-let [finally-fn (:finally task-metadata)]
    (apply finally-fn args)))

(defn default-error-handler [exception & _]
  (throw exception))

(defn supervise [task-var & args]
  (let [task-meta (meta task-var)]
    (try+
     (apply eval-preconditions task-meta args)
     (let [result (apply task-var args)]
       (apply eval-postconditions task-meta result args)
       result)
     (catch [:type :dire.core/precondition] {:as conditions}
       (if-let [pre-handler (get (:error-handlers task-meta) {:precondition (:precondition conditions)})]
         (apply pre-handler conditions args)
         (throw+ conditions)))
     (catch [:type :dire.core/postcondition] {:as conditions}
       (if-let [post-handler (get (:error-handlers task-meta) {:postcondition (:postcondition conditions)})]
         (post-handler conditions (:result conditions))
         (throw+ conditions)))
     (catch Exception e
       (let [handler (get (:error-handlers task-meta) (type e) default-error-handler)]
         (apply handler e args)))
     (finally
      (apply eval-finally task-meta args)))))

