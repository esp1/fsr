(ns esp1.fsr.core
  "Core functions used internally by fsr.
   Can also be used to construct other mechanisms
   that take advantage of fsr's namespace metadata and route resolution functionality."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; Route cache for performance
(def ^:private route-cache (atom {}))

(defn clear-route-cache!
  "Clear the route cache - useful for development mode with hot reloading"
  []
  (reset! route-cache {}))

(defn clojure-file-ext
  "Returns the extension of the given file or filename if it is .clj or .cljc.
   Otherwise returns nil."
  {:malli/schema [:=> [:cat
                       [:or :file :file-name]]
                  [:maybe [:string {:title "File extension"}]]]}
  [file-or-filename]
  (let [filename (if (instance? java.io.File file-or-filename)
                   (.getName file-or-filename)
                   file-or-filename)]
    (re-find #"\.cljc?$" filename)))

(defn file-ns-name-components
  "If this file is part of a clojure namespace,
   returns a vector of the namespace name components associated with this file.
   If this file is not part of a clojure namespace, returns nil."
  {:malli/schema [:=> [:cat
                       :file]
                  [:maybe [:sequential [:string {:title "Namespace name component"}]]]]}
  [f]
  (let [[clj-file] (->> (file-seq f)
                        (filter clojure-file-ext))]
    (when clj-file
      (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader clj-file)))]
        (->> (str/split (str ns-name) #"\.")
             (drop-last (- (.getNameCount (.toPath clj-file))
                           (.getNameCount (.toPath f)))))))))

(defn get-root-ns-prefix
  "Given a root filesystem path,
   returns the associated root namespace prefix string.
   It does this by scanning the root fileystem path for a clojure file,
   and returning the portion of the namepace corresponding to the filesystem path."
  {:malli/schema [:=> [:cat
                       [:dir-path {:title "Root filesystem path"}]]
                  [:string {:title "Root namespace prefix"}]]}
  [root-fs-path]
  (str/join "." (file-ns-name-components (io/file root-fs-path))))

(defn- filename-match-info
  "Given a filename,
   returns a vector 2-tuple containing a regex string for matching against this filename in a regex pattern,
   and a (possibly empty) vector of path parameter names.

   Parameters in filenames are delineated by single angle brackets `<` `>`
   to match a parameter value that does not contain a slash `/` character,
   or double angle brackets `<<` `>>`
   to match a parameter value that may contain a slash `/` character."
  {:malli/schema [:=> [:cat
                       :file-name]
                  [:cat
                   [:string {:title "Regex pattern"}]
                   [:vector [:string {:title "Parameter name"}]]]]}
  [filename]
  (let [matcher (re-matcher #"<([^<>]+)>" filename)
        param-names (loop [[_ param-name] (re-find matcher)
                           param-names []]
                      (if param-name
                        (recur (re-find matcher) (conj param-names param-name))
                        param-names))]
    [(str "^"
          (-> filename
              (str/replace #"<<([^<>]+)>>" "(.*)")
              (str/replace #"<([^<>]+)>" "([^/]*)"))
          "(/(.*))?$")
     param-names]))

(defn- step-match
  "Tries to match the beginning of the given URI against the filenames in the given current working directory.
   If a match is found, the matched file is returned, along with a map of any matched path parameters, and the remaining unmatched portion of the URI.
   If no match is found, returns nil.
   If multiple matches are found, throws an error."
  {:malli/schema [:=> [:cat
                       [:string {:title "URI"}]
                       [:file {:title "CWD"}]]
                  [:maybe [:cat
                           [:maybe :string {:title "Remaining URI"}]
                           [:file {:title "Matched file"}]
                           [:map-of {:title "Path parameters"} :string :string]]]]}
  [uri cwd]
  (let [match-infos
        (->> (.listFiles cwd)
             (map (fn [f]
                    (let [match-str (or (last (file-ns-name-components f))
                                        (let [match-str (.getName f)]
                                          ;; if this file is a clojure file, strip the extension
                                          (if-let [ext (clojure-file-ext match-str)]
                                            (subs match-str 0 (- (count match-str) (count ext)))
                                            match-str)))
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
                                       ;; and none of the directory matches contain an index.clj or index.cljc
                                       (empty? (filter (fn [[_ f _]]
                                                         (or (.exists (io/file f "index.clj"))
                                                             (.exists (io/file f "index.cljc"))))
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
  {:malli/schema [:=> [:cat
                       [:string {:title "URI"}]
                       [:file {:title "CWD"}]]
                  [:maybe [:cat
                           [:file {:title "Matched file"}]
                           [:map-of
                            [:string {:title "Path parameter name"}]
                            [:string {:title "Path parameter value"}]]]]]}
  [uri cwd]
  (loop [uri (str/replace uri #"^/" "") ; strip leading /
         f cwd
         path-params {}]
    (if-let [[uri1 f1 path-params1] (and uri (step-match uri f))]
      (recur uri1 f1 (merge path-params path-params1))
      (when (and (empty? uri)
                 (.exists f))
        [f path-params]))))

(defn file->clj
  "Returns the Clojure .clj file corresponding to the given java.io.File argument.
   If the file argument is a Clojure file, returns the file.
   If the file argument is a directory, returns the index.clj or index.cljc file under that directory if it exists.
   Otherwise returns nil."
  {:malli/schema [:=> [:cat
                       :file]
                  [:maybe [:file {:title "Clojure file"}]]]}
  [f]
  (let [f (if (.isDirectory f)
            (cond
              (.exists (io/file f "index.clj")) (io/file f "index.clj")
              (.exists (io/file f "index.cljc")) (io/file f "index.cljc"))
            f)]
    (when (and (.exists f)
               (.isFile f)
               (clojure-file-ext f))
      f)))

(defn clj->ns-sym
  "Returns the namespace name symbol for the clojure file."
  {:malli/schema [:=> [:cat
                       [:maybe [:file {:title "Clojure file"}]]]
                  [:maybe [:symbol {:title "Namespace symbol"}]]]}
  [file]
  (when (and file (.exists file) (.isFile file) (clojure-file-ext file))
    (let [[_ns ns-name] (edn/read (java.io.PushbackReader. (io/reader file)))]
      ns-name)))

(defn ns-sym->uri
  "Converts a given namespace name into a URI.
   If a namespace prefix is provided, it will be stripped from the URI."
  {:malli/schema [:=> [:cat
                       [:maybe [:symbol {:title "Namespace symbol"}]]
                       [:string {:title "Namespace prefix"}]]
                  [:maybe [:string {:title "URI"}]]]}
  [ns-sym ns-prefix]
  (when ns-sym
    (-> (str ns-sym)
        (str/replace (re-pattern (str "^" ns-prefix "\\.")) "") ; strip ns prefix
        (str/replace #"(^|\.)index$" "") ; strip trailing index
        (str/replace "." "/")))) ; convert . to /

(defn ns-endpoint-meta
  "Returns the endpoint metadata associated with the given namespace symbol, or nil if no such namespace is found.
   The returned endpoint metadata will contain a `:endpoint/ns` key whose value is the symbol for this namespace,
   along with any other metadata associated with the namespace."
  {:malli/schema [:=> [:cat
                       [:maybe [:symbol {:title "Namespace symbol"}]]]
                  [:maybe [:map-of :keyword :any]]]}
  [ns-sym]
  (when ns-sym
    (require ns-sym)
    (when-let [ns (find-ns ns-sym)]
      (assoc (meta ns) :endpoint/ns ns-sym))))

(defn resolve-sym
  "Resolves the given symbol and returns it.
   If the given symbol is not namespaced, the default namespace is used to resolve the symbol."
  {:malli/schema [:=> [:cat
                       :symbol
                       [:symbol {:title "Default namespace"}]]
                  :any]}
  [sym default-ns]
  (if (namespace sym)
    (resolve sym)
    (ns-resolve default-ns sym)))

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
  {:malli/schema [:=> [:cat
                       [:keyword {:title "HTTP method"}]
                       [:map {:title "Endpoint metadata map"}]]
                  [:maybe [:fn {:title "Endpoint function"} #(fn? (if (var? %)
                                                                    (var-get %)
                                                                    %))]]]}
  [method endpoint-meta]
  (or (resolve-sym (get-in endpoint-meta [:endpoint/http method])
                   (:endpoint/ns endpoint-meta))
      (when-let [endpoint-type-sym (:endpoint/type endpoint-meta)]
        (require endpoint-type-sym)
        (when (find-ns endpoint-type-sym)
          (http-endpoint-fn method (ns-endpoint-meta endpoint-type-sym))))))
