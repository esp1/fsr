(ns esp1.fsr.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [esp1.fsr.schema :refer [file?]]))

(defn file-ns-name-components
  "If this file is part of a clojure namespace,
   returns a vector of the namespace name components associated with this file.
   If this file is not part of a clojure namespace, returns nil."
  [f]
  (let [[clj-file] (->> (file-seq f)
                        (filter #(string/ends-with? (.getPath %) ".clj")))]
    (when clj-file
      (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader clj-file)))]
        (->> (string/split (str ns-name) #"\.")
             (drop-last (- (.getNameCount (.toPath clj-file))
                           (.getNameCount (.toPath f)))))))))

(defn get-root-ns-prefix
  "Given a root filesystem path,
   returns the associated root namespace prefix string.
   It does this by scanning the root fileystem path for a clojure file,
   and returning the portion of the namepace corresponding to the filesystem path."
  [root-fs-path]
  (string/join "." (file-ns-name-components (io/file root-fs-path))))

(defn- filename-match-info
  "Given a filename,
   returns a vector 2-tuple containing a regex string for matching against this filename in a regex pattern,
   and a (possibly empty) vector of path parameter names.

   Parameters in filenames are delineated by single angle brackets `<` `>`
   to match a parameter value that does not contain a slash `/` character,
   or double angle brackets `<<` `>>`
   to match a parameter value that may contain a slash `/` character."
  {:malli/schema [:=> [:catn
                       [:filename string?]]
                  [:catn
                   [:regex-str string?]
                   [:param-names [:vector string?]]]]}
  [filename]
  (let [matcher (re-matcher #"<([^<>]+)>" filename)
        param-names (loop [[_ param-name] (re-find matcher)
                           param-names []]
                      (if param-name
                        (recur (re-find matcher) (conj param-names param-name))
                        param-names))]
    [(str "^"
          (-> filename
              (string/replace #"<<([^<>]+)>>" "(.*)")
              (string/replace #"<([^<>]+)>" "([^/]*)"))
          "(/(.*))?$")
     param-names]))

(defn- step-match
  "Tries to match the beginning of the given URI against the filenames in the given current working directory.
   If a match is found, the matched file is returned, along with a map of any matched path parameters, and the remaining unmatched portion of the URI.
   If no match is found, returns nil.
   If multiple matches are found, throws an error."
  {:malli/schema [:=> [:catn
                       [:uri string?]
                       [:cwd file?]]
                  [:maybe [:catn
                           [:remaining-uri string?]
                           [:matched-file file?]
                           [:path-params map?]]]]}
  [uri cwd]
  (let [match-infos
        (->> (.listFiles cwd)
             (map (fn [f]
                    (let [match-str (or (last (file-ns-name-components f))
                                        (as-> (.getName f) match-str
                                          (cond-> match-str
                                            (string/ends-with? match-str ".clj") ; if this file is a clojure file, strip the .clj extension
                                            (subs 0 (- (count match-str) (count ".clj"))))))
                          [match-pattern param-names] (filename-match-info match-str)
                          [_ & matches] (re-matches (re-pattern match-pattern) uri)]
                      (when matches
                        (let [[param-values [_ remaining-uri]] (split-at (count param-names) matches)]
                          [remaining-uri
                           f
                           (zipmap param-names param-values)])))))
             (keep identity))]
    (cond
      (empty? match-infos) nil
      (= 1 (count match-infos)) (first match-infos)
      (> (count match-infos) 1) (let [[file-match-infos dir-match-infos]
                                      (partition-by (fn [[_ f _]]
                                                      (.isFile f))
                                                    match-infos)]
                                  (if (and
                                       ;; if there is only one file match
                                       (= 1 (count file-match-infos))
                                       ;; and none of the directory matches contain an index.clj
                                       (empty? (filter (fn [[_ f _]]
                                                         (.exists (io/file f "index.clj")))
                                                       dir-match-infos)))
                                    
                                    ;; then return the file match-info
                                    (first file-match-infos)

                                    ;; otherwise throw an exception
                                    (throw (Exception. (str "URI '" uri "' has multiple matches in " (.getPath cwd) ": " (pr-str match-infos)))))))))

(defn uri->file+params
  "Resolves the given URI to a filesystem file or dir in the given current working directory.
   If a matching filesystem file or dir is found, returns a vector 2-tuple
   of the matched file/dir, and a map of matched path parameters.
   If no matching filesystem file or dir is found, returns nil."
  [uri cwd]
  (loop [uri (string/replace uri #"^/" "") ; strip leading /
         f cwd
         path-params {}]
    (if-let [[uri1 f1 path-params1] (and uri (step-match uri f))]
      (recur uri1 f1 (merge path-params path-params1))
      (when (and (empty? uri)
                 (.exists f))
        [f path-params]))))

(defn file->clj
  [f]
  (let [f (if (.isDirectory f)
            (io/file f "index.clj")
            f)]
    (when (and (.exists f)
               (.isFile f)
               (string/ends-with? (.getName f) ".clj"))
      f)))

(defn clj->ns-sym
  "Returns the namespace name symbol for the clojure file."
  {:malli/schema [:=> [:catn
                       [:path [:maybe file?]]]
                  [:maybe symbol?]]}
  [file]
  (when (and file (.exists file) (.isFile file) (string/ends-with? (.getName file) ".clj"))
    (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader file)))]
      ns-name)))

(defn ns-sym->uri
  "Converts a given namespace name into a URI.
   If a `ns-prefix` is provided, it will be stripped from the URI."
  {:malli/schema [:=> [:catn
                       [:path [:maybe symbol?]]
                       [:ns-prefix string?]]
                  [:maybe string?]]}
  [ns-sym ns-prefix]
  (when ns-sym
    (-> (str ns-sym)
        (string/replace (re-pattern (str "^" ns-prefix "\\.")) "") ; strip ns prefix
        (string/replace #"(^|\.)index$" "") ; strip trailing index
        (string/replace "." "/")))) ; convert . to /

(defn ns-endpoint-meta
  "Returns the endpoint metadata associated with the given namespace symbol, or nil if no such namespace is found.
   The returned endpoint metadata will contain a `:endpoint/ns` key whose value is the symbol for this namespace,
   along with any other metadata associated with the namespace."
  {:malli/schema [:=> [:catn
                       [:ns-sym [:maybe symbol?]]
                       [:ns-prefix string?]]
                  [:maybe map?]]}
  [ns-sym]
  (when ns-sym
    (require ns-sym)
    (when-let [ns (find-ns ns-sym)]
      (assoc (meta ns) :endpoint/ns ns-sym))))

(defn resolve-sym
  "Resolves the given symbol and returns it.
   If the given symbol is not namespaced, the default namespace is used to resolve the symbol.
   If the first argument is not a symbol, it is returned as is."
  [sym default-ns]
  (if (symbol? sym)
    (if (namespace sym)
      (resolve sym)
      (ns-resolve default-ns sym))
    sym))

(defn http-endpoint-fn
  "Returns the http endpoint function corresponding to the given request method (e.g. :get, :post, etc)
   in the given endpoint metadata map.
   
   Will first look for a the given request method in the `:endpoint/http` map
   and if found, resolves its matching endpoint function and returns it.
   
   If no matching function is found,
   will look for a `:endpoint/type` attribute whose value is a symbol of another namespace
   in which to recursively look for a `:endpoint/http` or `:endpoint/type` key
   and a matching endpoint function.
   
   If no function is found, returns nil."
  [method endpoint-meta]
  (or (resolve-sym (get-in endpoint-meta [:endpoint/http method])
                   (:endpoint/ns endpoint-meta))
      (when-let [endpoint-type-sym (:endpoint/type endpoint-meta)]
        (require endpoint-type-sym)
        (when-let [type-ns (find-ns endpoint-type-sym)]
          (http-endpoint-fn method (ns-endpoint-meta type-ns))))))
